package dev.slinky.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.client.ForgeInputAdapter;
import dev.slinky.client.KeyState;
import dev.slinky.client.NativeKeyMap;
import dev.slinky.client.hook.GlStateScope;
import dev.slinky.client.hook.RenderHooks;
import dev.slinky.inject.Platform;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.GuiModule;
import dev.slinky.module.impl.ModOverlayModule;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.RangeSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Native port of the supplied OpenB4 ClickGui and its components. Geometry,
 * Minecraft font, item icons, easing, title and widget rendering come from the
 * donor. Only live Slinky modules are adapted; nonexistent donor modules are not
 * advertised. The original GUI is a separate, persistently selected screen.
 */
public final class OpenB4GuiScreen extends GuiScreen implements RenderHooks.GuiFrame {
   private static final String[] CATEGORIES = {"combat", "movement", "player", "world", "render", "network", "minigames", "other", "client", "scripts", "profiles"};
   private static final OpenB4GuiBlur BLUR = new OpenB4GuiBlur();
   // Keep native-size text readable; wrap long controls instead of widening every panel.
   private static final float PANEL_WIDTH = 124.0F;
   private static final float HEADER_HEIGHT = 18.0F;
   private static final float BODY_PADDING = 2.0F;
   private static final float CONTROL_INSET = 6.0F;
   private static final float TEXT_PADDING = 4.0F;
   private static final float TRACK_HEIGHT = 4.0F;
   private static final int TEXT_WIDTH = (int)(PANEL_WIDTH - CONTROL_INSET * 2.0F);
   // LWJGL 2 glGetInteger validates 16 remaining elements even for GL_SCISSOR_BOX.
   private final IntBuffer scissorBox = BufferUtils.createIntBuffer(16);
   private final ModuleManager manager;
   private final GuiModule module;
   private final List<Panel> panels = new ArrayList<>();
   private final Map<String, List<String>> wrappedLabels = new HashMap<>();
   private final OpenB4GuiBranding branding = new OpenB4GuiBranding();
   private final OpenB4ConsoleWidget console;
   private final OpenB4GuiDrawing.Animation background = new OpenB4GuiDrawing.Animation(500.0F);
   private final OpenB4GuiDrawing.Animation watermark = new OpenB4GuiDrawing.Animation(500.0F);
   private Control binding;
   private Control sliding;
   private boolean leftDown;
   private boolean movementActive;
   private boolean dirty;
   private float mouseX;
   private float mouseY;
   private int drawScale = 2;
   private int viewWidth;
   private int viewHeight;
   private String error = "";
   private long errorUntil;

   public OpenB4GuiScreen(ModuleManager manager, GuiModule module) {
      this.manager = manager;
      this.module = module;
      this.console = new OpenB4ConsoleWidget(manager);
      ItemStack[] icons = {new ItemStack(Items.diamond_sword), new ItemStack(Items.diamond_boots),
         new ItemStack(Items.golden_apple, 1, 0), new ItemStack(Items.filled_map), new ItemStack(Items.ender_eye),
         new ItemStack(Items.comparator), new ItemStack(Items.gold_ingot), new ItemStack(Items.clock),
         new ItemStack(Items.compass), new ItemStack(Items.redstone), new ItemStack(Items.book)};
      for (int i = 0; i < CATEGORIES.length; i++) {
         this.panels.add(new Panel(CATEGORIES[i], icons[i], i == 2 ? new ItemStack(Items.golden_apple, 1, 1) : icons[i], 5.0F + i * 20.0F));
      }
      Set<Module> added = new HashSet<>();
      for (JsonElement element : catalog()) {
         if (element == null || !element.isJsonObject()) continue;
         JsonObject record = element.getAsJsonObject();
         String donorName = catalogString(record, "name");
         if (donorName == null || donorName.trim().isEmpty()) continue;
         Module entry = this.findDonorModule(donorName);
         if (entry != null && added.add(entry)) {
            String category = catalogString(record, "category");
            JsonElement lite = record.get("lite");
            boolean liteEnabled = lite != null && lite.isJsonPrimitive()
               && lite.getAsJsonPrimitive().isBoolean() && lite.getAsBoolean();
            this.panel(category == null ? this.fallbackCategory(entry) : category).entries.add(new Entry(entry, donorName, liteEnabled));
         }
      }
      for (Module entry : manager.all()) {
         if (added.add(entry)) {
            this.panel(this.fallbackCategory(entry)).entries.add(new Entry(entry, entry.getName(), true));
         }
      }
      // Donor profiles/Manager is backed by the real recovered profile screen.
      this.panel("profiles").entries.add(new Entry("Manager", this.module::showProfiles));
      this.restorePositions();
   }

   private static String catalogString(JsonObject record, String key) {
      JsonElement value = record.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
         ? value.getAsString() : null;
   }

   private static JsonArray catalog() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/OpenB4GuiCatalog.json");
      if (stream == null) {
         System.err.println("[Slinky] Missing OpenB4 GUI catalog; using live module categories.");
         return new JsonArray();
      }
      try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         JsonElement root = new JsonParser().parse(reader);
         if (root != null && root.isJsonArray()) return root.getAsJsonArray();
         System.err.println("[Slinky] Invalid OpenB4 GUI catalog root; using live module categories.");
      } catch (IOException | com.google.gson.JsonParseException failure) {
         System.err.println("[Slinky] Cannot read OpenB4 GUI catalog; using live module categories: " + failure);
      }
      return new JsonArray();
   }

   private static String normalized(String name) {
      return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   private Module findDonorModule(String name) {
      if ("HUD".equals(name)) return this.manager.find("mod_overlay");
      if ("Settings".equals(name)) return this.manager.find("raven_settings");
      for (Module entry : this.manager.all()) {
         if (normalized(entry.getName()).equals(normalized(name))) return entry;
      }
      return null;
   }

   private String fallbackCategory(Module entry) {
      if (entry == this.module) return "client";
      int category = entry.metadataCategory();
      return category == 0 ? "combat" : category == 1 ? "movement" : category == 2 ? "render" : category == 3 ? "player" : "other";
   }

   private Panel panel(String name) {
      for (Panel panel : this.panels) if (panel.name.equals(name)) return panel;
      return this.panels.get(7);
   }

   public void opened() {
      this.cancelInput();
      this.branding.opened();
      this.background.start();
      this.watermark.start();
      this.error = "";
   }

   @Override
   public void initGui() {
      this.wrappedLabels.clear();
      this.geometry();
      ScaledResolution vanilla = new ScaledResolution(this.manager.context().minecraft());
      this.console.init(vanilla.getScaledWidth(), vanilla.getScaledHeight());
      this.allowUserInput = this.module.ravenFlag("allow_movement");
   }

   private void geometry() {
      Minecraft mc = this.manager.context().minecraft();
      int desired = this.module.appliedRavenScale();
      int factor = 1;
      while (factor < desired && mc.displayWidth / (factor + 1) >= 320 && mc.displayHeight / (factor + 1) >= 240) factor++;
      if (mc.isUnicode() && factor % 2 != 0 && factor != 1) factor--;
      this.drawScale = factor;
      this.viewWidth = (int)Math.ceil((double)mc.displayWidth / factor);
      this.viewHeight = (int)Math.ceil((double)mc.displayHeight / factor);
      for (Panel panel : this.panels) panel.clampPosition();
   }

   @Override
   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public void drawScreen(int x, int y, float partial) {
      // ForgeUiSurface owns drawing; vanilla only pumps the native input adapter.
   }

   @Override
   public void drawRecoveredFrame(float partial) {
      this.geometry();
      this.mouseX = Mouse.getX() / (float)this.drawScale;
      this.mouseY = (this.manager.context().minecraft().displayHeight - Mouse.getY() - 1) / (float)this.drawScale;
      this.allowUserInput = this.module.ravenFlag("allow_movement") && !this.capturingInput();
      int oldMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      try (GlStateScope state = GlStateScope.open()) {
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPushMatrix();
         try {
            GL11.glScalef(this.drawScale, this.drawScale, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            int alpha = (int)(this.background.getValue(0.0F, 0.75F, 2) * 255.0F);
            Module hud = this.manager.find("mod_overlay");
            if (hud instanceof ModOverlayModule && hud.isEnabled()) {
               ((ModOverlayModule)hud).renderForGui();
            }
            drawBackground(this.manager, this.viewWidth, this.viewHeight, alpha, alpha / 191.0F);
            if (!this.module.ravenFlag("hide_watermark")) {
               this.branding.drawTitle(this.viewWidth / 2.0F, this.viewHeight / 4.0F);
            }
            for (Panel panel : this.panels) {
               panel.mousePosition();
               panel.render();
               for (Entry entry : panel.entries) entry.updatePosition();
            }
            OpenB4GuiDrawing.resetColor();
            Minecraft mc = this.manager.context().minecraft();
            if (!this.module.ravenFlag("hide_player_model") && mc.thePlayer != null) {
               try (GlStateScope entityState = GlStateScope.open()) {
                  GlStateManager.enableDepth();
                  GlStateManager.depthMask(true);
                  GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                  GuiInventory.drawEntityOnScreen(this.viewWidth + 15 - this.background.getValueInt(0, 40, 2), this.viewHeight - 10, 40,
                     this.viewWidth - 25 - this.mouseX, this.viewHeight - 50 - this.mouseY, mc.thePlayer);
               }
            }
            if (System.currentTimeMillis() < this.errorUntil) {
               mc.fontRendererObj.drawStringWithShadow(this.error, 4.0F, this.viewHeight - 45.0F, 0xFFFA5F55);
            }
            ScaledResolution vanilla = new ScaledResolution(mc);
            this.console.draw(Mouse.getX() / vanilla.getScaleFactor(),
               (mc.displayHeight - Mouse.getY() - 1) / vanilla.getScaleFactor(), partial);
            int wy = this.viewHeight + 18 - this.watermark.getValueInt(0, 30, 2);
            drawWatermark(this.manager, 4, wy, true);
         } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMatrix);
         }
      }
   }

   private static GuiModule gui(ModuleManager manager) {
      return (GuiModule)manager.find("gui");
   }

   /** Native GUI-unit backdrop shared with donor HUD editor ports. */
   public static void drawEditorBackground(ModuleManager manager, int width, int height) {
      drawBackground(manager, width, height, 191, 1.0F);
   }

   private static void drawBackground(ModuleManager manager, int width, int height, int alpha, float progress) {
      GuiModule gui = gui(manager);
      if (gui.ravenFlag("dark_background")) {
         OpenB4GuiDrawing.rect(0.0F, 0.0F, width, height, 0x101010 | alpha << 24);
      }
      // Blur composites world + the current premultiplied UI first, retaining
      // donor HUD -> dark tint -> blur ordering even on a separate UI target.
      BLUR.draw(width, height, 3.4F * (gui.ravenNumber("background_blur") / 100.0F) * progress);
   }

   public static void drawWatermark(ModuleManager manager, int x, int y, boolean animate) {
      OpenB4GuiBranding.watermark(x, y, animate);
   }

   @Override
   public void updateScreen() {
      this.console.update();
   }

   public void updateMovement() {
      Minecraft mc = this.manager.context().minecraft();
      boolean movement = this.module.ravenFlag("allow_movement") && !this.capturingInput() && !this.console.visible();
      if (movement || this.movementActive) {
         KeyBinding[] keys = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak};
         for (KeyBinding key : keys) {
            if (movement) KeyState.set(key, KeyState.physical(key));
            else KeyState.restore(key);
         }
      }
      this.movementActive = movement;
      if (this.module.ravenFlag("allow_rotating") && !this.capturingInput() && !this.console.visible() && mc.thePlayer != null) {
         float yaw = 0.0F, pitch = 0.0F;
         if (Keyboard.isKeyDown(205)) yaw += 8.0F;
         if (Keyboard.isKeyDown(203)) yaw -= 8.0F;
         if (Keyboard.isKeyDown(200)) pitch -= 5.0F;
         if (Keyboard.isKeyDown(208)) pitch += 5.0F;
         if (yaw != 0.0F) mc.thePlayer.rotationYaw += yaw + (Math.random() - 0.5);
         if (pitch != 0.0F) mc.thePlayer.rotationPitch = MathHelper.clamp_float((float)(mc.thePlayer.rotationPitch + pitch + Math.random() - 0.5), -90.0F, 90.0F);
      }
   }

   /** Native key IDs, UTF-8 text and top-left raw display pixels, once per event. */
   public boolean dispatchInput(int kind, int key, boolean pressed, boolean unchanged, byte[] utf8, int rawX, int rawY, float wheel) {
      this.geometry();
      boolean mouse = kind == 1 || kind == 3 || kind == 2 && key >= 1 && key <= 5;
      if (mouse) {
         this.mouseX = rawX / (float)this.drawScale;
         this.mouseY = rawY / (float)this.drawScale;
      }
      if (kind == 1) {
         for (Panel panel : this.panels) {
            panel.mousePosition();
            panel.updateHeight();
            for (Entry entry : panel.entries) entry.updatePosition();
         }
         return true;
      }
      if (kind == 3) {
         if (!this.capturingInput() && !this.console.visible()) {
            // LWJGL positive means wheel-up: return the negative content offset toward zero.
            int direction = wheel > 0.0F ? 1 : wheel < 0.0F ? -1 : 0;
            Panel panel = this.hoveredPanel();
            if (panel != null && panel.open && !panel.opening && !panel.closing
               && panel.visibleBody(this.mouseX, this.mouseY)) panel.onScroll(direction);
         }
         return true;
      }
      if (kind != 2) return true;
      if (key == 1) {
         this.leftDown = pressed;
         if (!pressed) {
            for (Panel panel : this.panels) panel.dragging = false;
            this.sliding = null;
            if (this.dirty) {
               this.savePositions();
               this.manager.persist();
               this.dirty = false;
            }
         }
      }
      if (!pressed) return this.capturingInput() || !this.module.ravenFlag("allow_movement") || key <= 5;
      if (unchanged && key >= 1 && key <= 5) return true;
      if (key >= 1 && key <= 5) {
         ScaledResolution vanilla = new ScaledResolution(this.manager.context().minecraft());
         if (this.console.mouseClicked(rawX / vanilla.getScaleFactor(), rawY / vanilla.getScaleFactor(), key - 1)) return true;
      } else {
         String text = utf8 == null ? "" : new String(utf8, StandardCharsets.UTF_8);
         char character = text.isEmpty() ? '\0' : text.charAt(0);
         if (this.console.keyTyped(character, NativeKeyMap.gameCode(key))) return true;
      }
      if (unchanged) return this.capturingInput() || !this.module.ravenFlag("allow_movement") || key <= 5;
      if (this.binding != null) {
         if (key >= 4) {
            Module target = this.binding.entry.mod;
            if (key != 87 || target != this.module) this.manager.setBinding(target, key == 87 ? 0 : key);
            this.binding = null;
            this.manager.persist();
            return true;
         }
      } else if (key == 87 || key != 0 && key == this.module.getBinding() && !this.console.visible()) {
         this.manager.requestEnabled(this.module, false);
         return true;
      }
      if (key >= 1 && key <= 5) {
         this.click(key - 1);
         return true;
      }
      return this.capturingInput() || !this.module.ravenFlag("allow_movement");
   }

   private boolean capturingInput() {
      return this.binding != null || this.console.capturesKeyboard();
   }

   private Panel hoveredPanel() {
      // Paint order is also input order: never click through an overlapping panel.
      for (int i = this.panels.size() - 1; i >= 0; i--) {
         Panel panel = this.panels.get(i);
         if (panel.overTitle(this.mouseX, this.mouseY) || panel.visibleBody(this.mouseX, this.mouseY)) return panel;
      }
      return null;
   }

   private void click(int button) {
      Panel panel = this.hoveredPanel();
      if (panel == null) return;
      this.panels.remove(panel);
      this.panels.add(panel);
      panel.updateHeight();
      if (button == 0) {
         if (panel.overIcon(this.mouseX, this.mouseY)) {
            panel.setOpened(!panel.open);
            return;
         }
         if (panel.overTitle(this.mouseX, this.mouseY)) {
            panel.dragging = true;
            panel.dragX = this.mouseX - panel.x;
            panel.dragY = this.mouseY - panel.y;
            return;
         }
      } else if ((button == 1 || button == 2) && panel.overTitle(this.mouseX, this.mouseY)) {
         panel.setOpened(!panel.open);
         return;
      }
      if (!panel.open || panel.opening || panel.closing || !panel.visibleBody(this.mouseX, this.mouseY)) return;
      for (Entry entry : panel.entries) {
         if (this.module.ravenFlag("lite_mode") && !entry.lite) continue;
         if (entry.click(button)) return;
      }
   }

   private void changed(Module target, Setting<?> setting, boolean persist) {
      target.afterSettingControl(setting.getKey(), this.manager.context());
      this.manager.configurationSession().controlChanged();
      if (persist) this.manager.persist();
      else this.dirty = true;
   }

   private void error(RuntimeException failure) {
      this.error = failure.getMessage() == null ? "Could not apply this setting" : failure.getMessage();
      this.errorUntil = System.currentTimeMillis() + 5000L;
   }

   private void resetPositions() {
      for (int i = 0; i < CATEGORIES.length; i++) {
         Panel panel = this.panel(CATEGORIES[i]);
         panel.x = 5.0F;
         panel.y = 5.0F + i * 20.0F;
         panel.setOpened(false);
         for (Entry entry : panel.entries) if (entry.open) entry.toggleOpen();
      }
      this.savePositions();
      this.manager.persist();
   }

   private void restorePositions() {
      if (!this.module.ravenFlag("save_category_positions")) return;
      for (String position : ((StringListSetting)this.module.findSetting("raven_category_positions")).get()) {
         String[] fields = position.split(":");
         if (fields.length != 4) continue;
         try {
            float x = Float.parseFloat(fields[1]), y = Float.parseFloat(fields[2]);
            if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
            Panel panel = this.panel(fields[0]);
            if (!panel.name.equals(fields[0])) continue;
            panel.x = x;
            panel.y = y;
            if (Boolean.parseBoolean(fields[3])) panel.setOpened(true);
         } catch (NumberFormatException ignored) {
         }
      }
   }

   private void savePositions() {
      if (!this.module.ravenFlag("save_category_positions")) return;
      List<String> positions = new ArrayList<>();
      for (Panel panel : this.panels) positions.add(panel.name + ":" + panel.x + ":" + panel.y + ":" + panel.open);
      ((StringListSetting)this.module.findSetting("raven_category_positions")).set(positions);
   }

   public void cancelInput() {
      this.leftDown = false;
      this.binding = this.sliding = null;
      for (Panel panel : this.panels) panel.dragging = false;
      if (this.movementActive) {
         Minecraft mc = this.manager.context().minecraft();
         KeyBinding[] keys = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak};
         for (KeyBinding key : keys) KeyState.restore(key);
         this.movementActive = false;
      }
   }

   public void releaseResources() {
      this.cancelInput();
      BLUR.release();
   }

   @Override
   public void onGuiClosed() {
      this.savePositions();
      this.cancelInput();
      this.console.closed();
      this.module.saveOnClose();
      this.module.screenClosed(this);
   }

   @Override
   public void handleMouseInput() throws IOException {
      ForgeInputAdapter.mouse(this.manager);
   }

   @Override
   public void handleKeyboardInput() throws IOException {
      ForgeInputAdapter.keyboard(this.manager);
      this.mc.dispatchKeypresses();
   }

   private FontRenderer font() {
      return this.manager.context().minecraft().fontRendererObj;
   }

   private int lineHeight() {
      return this.font().FONT_HEIGHT + 1;
   }

   private List<String> wrapped(String text) {
      List<String> lines = this.wrappedLabels.get(text);
      if (lines == null) {
         if (this.wrappedLabels.size() >= 1024) this.wrappedLabels.clear();
         lines = this.font().listFormattedStringToWidth(text, TEXT_WIDTH);
         this.wrappedLabels.put(text, lines);
      }
      return lines;
   }

   private void drawLines(List<String> lines, float x, float y, int color) {
      for (String line : lines) {
         this.font().drawStringWithShadow(line, Math.round(x), Math.round(y), color);
         y += this.lineHeight();
      }
   }

   /** Intersect GUI-unit clips with the viewport and any enclosing animation clip. */
   private void clip(float left, float top, float right, float bottom) {
      Minecraft mc = this.manager.context().minecraft();
      int x1 = Math.max(0, (int)Math.ceil(left * this.drawScale));
      int x2 = Math.min(mc.displayWidth, (int)Math.floor(right * this.drawScale));
      int y1 = Math.max(0, mc.displayHeight - (int)Math.floor(bottom * this.drawScale));
      int y2 = Math.min(mc.displayHeight, mc.displayHeight - (int)Math.ceil(top * this.drawScale));
      if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
         this.scissorBox.clear();
         GL11.glGetInteger(GL11.GL_SCISSOR_BOX, this.scissorBox);
         x1 = Math.max(x1, this.scissorBox.get(0));
         x2 = Math.min(x2, this.scissorBox.get(0) + this.scissorBox.get(2));
         y1 = Math.max(y1, this.scissorBox.get(1));
         y2 = Math.min(y2, this.scissorBox.get(1) + this.scissorBox.get(3));
      }
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
   }

   private final class Panel {
      final String name;
      final ItemStack icon;
      final ItemStack activeIcon;
      final List<Entry> entries = new ArrayList<>();
      final OpenB4GuiDrawing.Animation openAnimation = new OpenB4GuiDrawing.Animation(400.0F);
      OpenB4GuiDrawing.Animation scrollAnimation;
      boolean animated;
      boolean open;
      boolean opening;
      boolean closing;
      boolean dragging;
      float x = 5.0F;
      float y;
      float dragX;
      float dragY;
      float targetScroll;
      float scroll;
      float bigSettingsHeight;
      float bodyHeight;

      Panel(String name, ItemStack icon, ItemStack activeIcon, float y) {
         this.name = name;
         this.icon = icon;
         this.activeIcon = activeIcon;
         this.y = y;
      }

      void setOpened(boolean value) {
         if (value == this.open) return;
         this.open = value;
         this.opening = value;
         this.closing = !value;
         this.animated = true;
         this.openAnimation.start();
         OpenB4GuiScreen.this.dirty = true;
      }

      void render() {
         boolean active;
         boolean lite = OpenB4GuiScreen.this.module.ravenFlag("lite_mode");
         this.updateHeight();
         if ((this.open || this.closing) && !this.entries.isEmpty()) {
            float target = this.visibleHeight();
            float minimumScroll = Math.min(0.0F, target - this.bigSettingsHeight);
            this.targetScroll = Math.max(minimumScroll, Math.min(0.0F, this.targetScroll));
            if (this.scrollAnimation != null && this.scroll != this.targetScroll) {
               this.scroll = this.scroll > this.targetScroll
                  ? this.scroll - this.scrollAnimation.getValue(0.0F, this.scroll - this.targetScroll, 0)
                  : this.targetScroll == 0.0F ? this.scroll + this.scrollAnimation.getValue(this.targetScroll, -this.scroll, 0)
                  : this.scrollAnimation.getValue(this.scroll, this.targetScroll, 0);
            }
            this.scroll = Math.max(minimumScroll, Math.min(0.0F, this.scroll));
            this.updateHeight();
            if (this.opening) {
               this.bodyHeight = this.openAnimation.getValue(0.0F, target, 2);
               if (this.bodyHeight == target) this.opening = false;
            } else if (this.closing) {
               this.bodyHeight = target - this.openAnimation.getValue(0.0F, target, 2);
               if (this.bodyHeight == 0.0F) this.closing = false;
            } else this.bodyHeight = target;
            this.outline(this.y + HEADER_HEIGHT + this.bodyHeight, true);
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
            try {
               OpenB4GuiScreen.this.clip(this.x, this.y + HEADER_HEIGHT, this.x + PANEL_WIDTH,
                  this.y + HEADER_HEIGHT + this.bodyHeight);
               for (Entry entry : this.entries) {
                  if ((!lite || entry.lite) && entry.offset < HEADER_HEIGHT + this.bodyHeight
                     && entry.offset + entry.height() > HEADER_HEIGHT) entry.render();
               }
            } finally {
               GL11.glPopAttrib();
            }
            active = true;
         } else {
            this.bodyHeight = 0.0F;
            active = this.overTitle(OpenB4GuiScreen.this.mouseX, OpenB4GuiScreen.this.mouseY);
            this.outline(this.y + HEADER_HEIGHT, active);
         }
         float nameX = 14.0F;
         if (this.animated) {
            float center = (PANEL_WIDTH - OpenB4GuiScreen.this.font().getStringWidth(this.name)) / 2.0F;
            if (this.opening) nameX = this.openAnimation.getValue(nameX, center, 2);
            else if (this.closing) nameX = nameX + center - this.openAnimation.getValue(nameX, center, 2);
            else if (this.open) nameX = center;
         }
         OpenB4GuiScreen.this.font().drawString(this.name, Math.round(this.x + nameX),
            Math.round(this.y + (HEADER_HEIGHT - OpenB4GuiScreen.this.font().FONT_HEIGHT) / 2.0F), 0xFFDCDCDC, false);
         OpenB4GuiDrawing.resetColor();
         GL11.glPushMatrix();
         try {
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            OpenB4GuiDrawing.drawItem(OpenB4GuiScreen.this.manager.context().minecraft(), active ? this.activeIcon : this.icon,
               (this.x + 2.0F) * 2.0F, (this.y + 4.0F) * 2.0F);
         } finally {
            GL11.glPopMatrix();
         }
      }

      void outline(float bottom, boolean active) {
         int left, right;
         if (active) {
            boolean rainbow = OpenB4GuiScreen.this.module.ravenFlag("rainbow_outlines");
            left = rainbow ? OpenB4GuiDrawing.withAlpha(OpenB4Theme.rainbow(2L, 0L), 130) : 0x96FFFFFF;
            right = rainbow ? OpenB4GuiDrawing.withAlpha(OpenB4Theme.rainbow(2L, 700L), 130) : 0x96FFFFFF;
         } else {
            left = 0xFF516395;
            right = 0xFF614385;
         }
         int fill = OpenB4GuiScreen.this.module.ravenFlag("dark_background") ? 0x6E000000 : 0x82000000;
         OpenB4GuiDrawing.roundedRectOutlined(this.x - 2.0F, this.y, this.x + PANEL_WIDTH + 2.0F, bottom, 10.0F, fill, left, right);
      }

      float visibleHeight() {
         return Math.max(0.0F, Math.min(this.bigSettingsHeight,
            Math.min(OpenB4GuiScreen.this.viewHeight * 0.8F, OpenB4GuiScreen.this.viewHeight - this.y - HEADER_HEIGHT - 4.0F)));
      }

      void updateHeight() {
         float offset = HEADER_HEIGHT + BODY_PADDING + this.scroll;
         for (Entry entry : this.entries) {
            if (OpenB4GuiScreen.this.module.ravenFlag("lite_mode") && !entry.lite) continue;
            entry.panel = this;
            entry.offset = offset;
            if (entry.open || entry.closing) {
               float controlY = offset + entry.headerHeight();
               for (Control control : entry.controls) {
                  control.offset = controlY;
                  controlY += control.height();
               }
            }
            offset += entry.height();
         }
         this.bigSettingsHeight = offset - HEADER_HEIGHT - this.scroll + BODY_PADDING;
      }

      void clampPosition() {
         this.x = Math.round(Math.max(4.0F, Math.min(this.x, OpenB4GuiScreen.this.viewWidth - PANEL_WIDTH - 4.0F)));
         this.y = Math.round(Math.max(4.0F, Math.min(this.y, OpenB4GuiScreen.this.viewHeight - HEADER_HEIGHT - 4.0F)));
      }

      void mousePosition() {
         if (this.dragging && OpenB4GuiScreen.this.leftDown) {
            this.x = OpenB4GuiScreen.this.mouseX - this.dragX;
            this.y = OpenB4GuiScreen.this.mouseY - this.dragY;
            this.clampPosition();
            OpenB4GuiScreen.this.dirty = true;
         }
      }

      boolean overIcon(float x, float y) {
         return x >= this.x && x < this.x + 12.0F && y >= this.y && y < this.y + HEADER_HEIGHT;
      }

      boolean overTitle(float x, float y) {
         return x >= this.x && x < this.x + PANEL_WIDTH && y >= this.y && y < this.y + HEADER_HEIGHT;
      }

      boolean visibleBody(float x, float y) {
         return this.bodyHeight > 0.0F && x >= this.x && x < this.x + PANEL_WIDTH
            && y >= this.y + HEADER_HEIGHT && y < this.y + HEADER_HEIGHT + this.bodyHeight;
      }

      void onScroll(int direction) {
         if (this.scrollAnimation == null) this.scrollAnimation = new OpenB4GuiDrawing.Animation(1000.0F);
         this.scrollAnimation.start();
         this.targetScroll += direction * OpenB4GuiScreen.this.module.ravenNumber("scroll_speed");
      }
   }

   private final class Entry {
      final Module mod;
      final String name;
      final boolean lite;
      final Runnable action;
      final List<Control> controls = new ArrayList<>();
      final OpenB4GuiDrawing.Animation animation = new OpenB4GuiDrawing.Animation(200.0F);
      Panel panel;
      boolean open;
      boolean opening;
      boolean closing;
      boolean hovered;
      boolean wasEnabled;
      long enabledAt;
      long disabledAt;
      long hoverEndedAt;
      float offset;

      Entry(Module mod, String name, boolean lite) {
         this.mod = mod;
         this.name = name;
         this.lite = lite;
         this.action = null;
         this.wasEnabled = mod != OpenB4GuiScreen.this.module && mod.isEnabled();
         if (mod == OpenB4GuiScreen.this.module) {
            String[] keys = {"raven_scale", "raven_background_blur", "raven_scroll_speed", "raven_allow_movement", "raven_allow_rotating",
               "raven_dark_background", "raven_hide_player_model", "raven_hide_watermark", "raven_lite_mode", "raven_rainbow_outlines"};
            for (String key : keys) this.addSetting(mod.findSetting(key));
            this.controls.add(new Control(this, "Reset positions", OpenB4GuiScreen.this::resetPositions));
            this.addSetting(mod.findSetting("raven_save_category_positions"));
            this.controls.add(new Control(this, "Original GUI", () -> OpenB4GuiScreen.this.module.selectGui(false)));
            this.controls.add(new Control(this, "Profiles", OpenB4GuiScreen.this.module::showProfiles));
         } else {
            if ("mod_overlay".equals(mod.getId())) this.controls.add(new Control(this, "Right click bind to hide modules."));
            if ("sprint".equals(mod.getId())) this.controls.add(new Control(this, "Command: '§esprint [text]§r'"));
            if ("bridge_info".equals(mod.getId())) this.controls.add(new Control(this, "Only for solos."));
            if ("raven_settings".equals(mod.getId())) this.controls.add(new Control(this, "General"));
            for (Setting<?> setting : mod.getSettings()) {
               if ("raven_settings".equals(mod.getId())) {
                  if ("edit_position".equals(setting.getKey())) this.controls.add(new Control(this, "Info HUD"));
                  if ("theme_offset".equals(setting.getKey())) this.controls.add(new Control(this, "Theme colors"));
               }
               this.addSetting(setting);
            }
         }
         this.controls.add(new Control(this));
      }

      Entry(String name, Runnable action) {
         this.mod = null;
         this.name = name;
         this.lite = true;
         this.action = action;
      }

      boolean cannotBind() {
         return this.mod == null || (this.mod.descriptorFlags() & 1) != 0
            || "raven_settings".equals(this.mod.getId()) || "command_line".equals(this.mod.getId());
      }

      void addSetting(Setting<?> setting) {
         if (setting instanceof RangeSetting) {
            this.controls.add(new Control(this, setting, 1));
            this.controls.add(new Control(this, setting, 2));
         } else this.controls.add(new Control(this, setting, 0));
      }

      float headerHeight() {
         return Math.max(16.0F, OpenB4GuiScreen.this.wrapped(this.name).size() * OpenB4GuiScreen.this.lineHeight() + 6.0F);
      }

      float height() {
         float header = this.headerHeight();
         float height = header;
         if (this.open || this.closing) {
            for (Control control : this.controls) height += control.height();
            if (this.opening) {
               float animated = this.animation.getValue(header, height, 2);
               if (animated == height) this.opening = false;
               height = animated;
            } else if (this.closing) {
               float animated = header + height - this.animation.getValue(header, height, 2);
               if (animated == header) this.closing = false;
               height = animated;
            }
         }
         return height;
      }

      void toggleOpen() {
         this.open = !this.open;
         this.opening = this.open;
         this.closing = !this.open;
         this.animation.start();
         this.panel.updateHeight();
      }

      boolean hovered() {
         return this.panel.visibleBody(OpenB4GuiScreen.this.mouseX, OpenB4GuiScreen.this.mouseY)
            && OpenB4GuiScreen.this.mouseY >= this.panel.y + this.offset
            && OpenB4GuiScreen.this.mouseY < this.panel.y + this.offset + this.headerHeight();
      }

      void render() {
         Panel panel = this.panel;
         int color = 0xFFC0C0C0;
         long now = System.currentTimeMillis();
         if (this.mod != null) {
            boolean enabled = this.mod != OpenB4GuiScreen.this.module && this.mod.isEnabled();
            if (enabled != this.wasEnabled) {
               if (enabled) this.enabledAt = now;
               else this.disabledAt = now;
               this.wasEnabled = enabled;
            }
            if (enabled) {
               double fade = 1.0 - Math.min(1.0, (now - this.enabledAt) / 75.0);
               color = fade > 0.0 ? OpenB4GuiDrawing.blend(0xFFC0C0C0, 0xFF189AFF, fade) : 0xFF189AFF;
            } else {
               double fade = 1.0 - Math.min(1.0, (now - this.disabledAt) / 75.0);
               color = fade > 0.0 ? OpenB4GuiDrawing.blend(0xFF189AFF, 0xFFC0C0C0, fade) : 0xFFC0C0C0;
            }
         }
         boolean wasHovered = this.hovered;
         this.hovered = this.hovered() && OpenB4GuiScreen.this.hoveredPanel() == panel;
         int alpha;
         if (this.hovered) alpha = 90;
         else {
            if (wasHovered) this.hoverEndedAt = now;
            alpha = (int)(Math.max(0.0, 1.0 - (now - this.hoverEndedAt) / 150.0) * 90.0);
         }
         float header = this.headerHeight();
         if (alpha > 0) OpenB4GuiDrawing.rect(panel.x, panel.y + this.offset, panel.x + PANEL_WIDTH,
            panel.y + this.offset + header, alpha << 24);
         List<String> nameLines = OpenB4GuiScreen.this.wrapped(this.name);
         float textY = panel.y + this.offset + (header - nameLines.size() * OpenB4GuiScreen.this.lineHeight()) / 2.0F;
         for (String line : nameLines) {
            OpenB4GuiScreen.this.font().drawStringWithShadow(line,
               Math.round(panel.x + (PANEL_WIDTH - OpenB4GuiScreen.this.font().getStringWidth(line)) / 2.0F), Math.round(textY), color);
            textY += OpenB4GuiScreen.this.lineHeight();
         }
         if ((this.open || this.closing) && !this.controls.isEmpty()) {
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
            try {
               OpenB4GuiScreen.this.clip(panel.x, panel.y + this.offset + header,
                  panel.x + PANEL_WIDTH, panel.y + this.offset + this.height());
               for (Control control : this.controls) {
                  if (control.visible() && control.offset < HEADER_HEIGHT + panel.bodyHeight
                     && control.offset + control.height() > HEADER_HEIGHT) control.render();
               }
            } finally {
               GL11.glPopAttrib();
            }
         }
      }

      void updatePosition() {
         if (this.panel == null) return;
         if (OpenB4GuiScreen.this.sliding != null && OpenB4GuiScreen.this.sliding.entry == this && OpenB4GuiScreen.this.leftDown) {
            if (!this.panel.open || !this.open || !OpenB4GuiScreen.this.sliding.visible()
               || OpenB4GuiScreen.this.module.ravenFlag("lite_mode") && !this.lite) {
               OpenB4GuiScreen.this.sliding = null;
               return;
            }
            try {
               OpenB4GuiScreen.this.sliding.slide();
            } catch (RuntimeException failure) {
               OpenB4GuiScreen.this.sliding = null;
               OpenB4GuiScreen.this.error(failure);
            }
         }
      }

      boolean click(int button) {
         if (this.hovered()) {
            try {
               if (button == 0) {
                  if (this.action != null) this.action.run();
                  else if (this.mod != OpenB4GuiScreen.this.module && this.mod.canToggleFromGui()
                     && !"raven_settings".equals(this.mod.getId())) OpenB4GuiScreen.this.manager.toggle(this.mod);
                  return true;
               }
               if (button == 1 && !this.controls.isEmpty()) {
                  this.toggleOpen();
                  return true;
               }
            } catch (RuntimeException failure) {
               this.open = true;
               OpenB4GuiScreen.this.error(failure);
               return true;
            }
         }
         if (!this.open || this.opening || this.closing) return false;
         for (Control control : this.controls) if (control.visible() && control.click(button)) return true;
         return false;
      }
   }

   private final class Control {
      final Entry entry;
      final Setting<?> setting;
      final int part;
      final String label;
      final Runnable action;
      final boolean bind;
      final boolean description;
      float offset;
      boolean wasToggled;
      long toggledAt;
      long clickedAt;

      Control(Entry entry, Setting<?> setting, int part) {
         this.entry = entry;
         this.setting = setting;
         this.part = part;
         this.label = setting.getLabel() + (part == 1 ? " min" : part == 2 ? " max" : "");
         this.action = null;
         this.bind = false;
         this.description = false;
         this.wasToggled = setting instanceof BooleanSetting && ((BooleanSetting)setting).isEnabled();
      }

      Control(Entry entry, String label, Runnable action) {
         this.entry = entry;
         this.setting = null;
         this.part = 0;
         this.label = label;
         this.action = action;
         this.bind = false;
         this.description = false;
      }

      Control(Entry entry, String label) {
         this.entry = entry;
         this.setting = null;
         this.part = 0;
         this.label = label;
         this.action = null;
         this.bind = false;
         this.description = true;
      }

      Control(Entry entry) {
         this.entry = entry;
         this.setting = null;
         this.part = 0;
         this.label = "";
         this.action = null;
         this.bind = true;
         this.description = false;
      }

      boolean visible() {
         return this.setting == null || this.entry.mod.settingVisible(this.setting.getKey());
      }

      boolean slider() {
         return this.setting instanceof NumberSetting || this.setting instanceof ChoiceSetting || this.setting instanceof RangeSetting;
      }

      String text() {
         if (this.bind) {
            if (this.entry.cannotBind()) return "Module cannot be bound.";
            if (OpenB4GuiScreen.this.binding == this) {
               int dots = (int)(System.currentTimeMillis() / 300L % 4L);
               return "Press a key" + (dots == 0 ? "" : dots == 1 ? "." : dots == 2 ? ".." : "...");
            }
            return "Current bind: '§e" + this.keyName() + "§r'";
         }
         return this.label + (this.setting != null && !(this.setting instanceof BooleanSetting) ? "..." : "");
      }

      String sliderDisplay() {
         if (!this.setting.isConfigured()) return "§cChoose...";
         if (this.setting instanceof ChoiceSetting) return "§e" + ((ChoiceSetting)this.setting).selectedLabel();
         return "§b" + OpenB4GuiDrawing.number(this.value()) + (this.setting.getKey().equals("raven_background_blur") ? "%" : "");
      }

      int valueLines() {
         int count = OpenB4GuiScreen.this.wrapped(this.sliderDisplay()).size();
         if (this.setting instanceof ChoiceSetting) {
            // Reserve the tallest option: dragging must not move this or later tracks.
            count = Math.max(count, OpenB4GuiScreen.this.wrapped("§cChoose...").size());
            for (String option : ((ChoiceSetting)this.setting).getOptions()) {
               count = Math.max(count, OpenB4GuiScreen.this.wrapped("§e" + option).size());
            }
         }
         return count;
      }

      float height() {
         if (!this.visible()) return 0.0F;
         int lines = this.slider() ? OpenB4GuiScreen.this.wrapped(this.label).size() + this.valueLines()
            : OpenB4GuiScreen.this.wrapped(this.text()).size();
         return TEXT_PADDING * 2.0F + lines * OpenB4GuiScreen.this.lineHeight() + (this.slider() ? 3.0F + TRACK_HEIGHT : 0.0F);
      }

      boolean method() {
         if (this.action != null) return true;
         if (!(this.setting instanceof BooleanSetting)) return false;
         String key = this.setting.getKey();
         return key.equals("edit_position") || key.equals("open_editor") || key.equals("edit_text_position") || key.equals("reset_position");
      }

      double minimum() {
         return this.setting instanceof NumberSetting ? ((NumberSetting)this.setting).getMinimum()
            : this.setting instanceof RangeSetting ? ((RangeSetting)this.setting).minimum() : 0.0;
      }

      double maximum() {
         return this.setting instanceof NumberSetting ? ((NumberSetting)this.setting).getMaximum()
            : this.setting instanceof RangeSetting ? ((RangeSetting)this.setting).maximum() : ((ChoiceSetting)this.setting).getOptions().size() - 1;
      }

      double value() {
         if (this.setting instanceof NumberSetting) return ((NumberSetting)this.setting).doubleValue();
         if (this.setting instanceof ChoiceSetting) return ((ChoiceSetting)this.setting).index();
         RangeSetting.Value value = ((RangeSetting)this.setting).get();
         return this.part == 1 ? value.lower : value.upper;
      }

      void render() {
         Panel panel = this.entry.panel;
         float x = panel.x + CONTROL_INSET;
         float y = panel.y + this.offset;
         if (this.slider()) {
            List<String> labels = OpenB4GuiScreen.this.wrapped(this.label);
            OpenB4GuiScreen.this.drawLines(labels, x, y + TEXT_PADDING, -1);
            OpenB4GuiScreen.this.drawLines(OpenB4GuiScreen.this.wrapped(this.sliderDisplay()), x,
               y + TEXT_PADDING + labels.size() * OpenB4GuiScreen.this.lineHeight(), -1);
            float trackY = y + this.height() - TEXT_PADDING - TRACK_HEIGHT;
            float width = this.maximum() > this.minimum()
               ? (float)(TEXT_WIDTH * (this.value() - this.minimum()) / (this.maximum() - this.minimum())) : 0.0F;
            OpenB4GuiDrawing.roundedRect(x, trackY, x + TEXT_WIDTH, trackY + TRACK_HEIGHT, 4.0F, -12302777);
            OpenB4GuiDrawing.roundedRect(x, trackY, x + Math.min(TEXT_WIDTH, Math.max(0.0F, width)), trackY + TRACK_HEIGHT, 4.0F,
               Color.getHSBColor((float)(System.currentTimeMillis() % 11000L) / 11000.0F, 0.75F, 0.9F).getRGB());
            return;
         }
         int color = -1;
         long now = System.currentTimeMillis();
         if (this.bind) {
            color = OpenB4GuiScreen.this.module.hidden(this.entry.mod) ? 0xFFF33A6A : OpenB4Theme.getRGB(14, 0.0);
         } else if (this.description) {
            color = OpenB4Theme.getRGB(14, 0.0);
         } else if (this.setting instanceof BooleanSetting && !this.method()) {
            boolean toggled = ((BooleanSetting)this.setting).isEnabled();
            if (toggled != this.wasToggled) {
               this.toggledAt = now;
               this.wasToggled = toggled;
            }
            double fade = 1.0 - Math.min(1.0, (now - this.toggledAt) / 75.0);
            color = toggled ? fade > 0.0 ? OpenB4GuiDrawing.blend(-1, 0xFF14FF00, fade) : 0xFF14FF00
               : fade > 0.0 ? OpenB4GuiDrawing.blend(0xFF14FF00, -1, fade) : -1;
         } else if (this.method() && now - this.clickedAt < 175L) {
            color = OpenB4GuiDrawing.blend(0xFF14FF00, -1, 1.0 - (now - this.clickedAt) / 175.0);
         }
         OpenB4GuiScreen.this.drawLines(OpenB4GuiScreen.this.wrapped(this.text()), x, y + TEXT_PADDING, color);
      }

      String keyName() {
         int key = this.entry.mod.getBinding();
         if (key >= 1 && key <= 5) return "MB" + (key - 1);
         String name = Keyboard.getKeyName(NativeKeyMap.gameCode(key));
         return name == null ? "NONE" : name;
      }

      boolean hovered() {
         Panel panel = this.entry.panel;
         return panel.visibleBody(OpenB4GuiScreen.this.mouseX, OpenB4GuiScreen.this.mouseY)
            && OpenB4GuiScreen.this.mouseY >= panel.y + this.offset
            && OpenB4GuiScreen.this.mouseY < panel.y + this.offset + this.height();
      }

      boolean click(int button) {
         if (this.description || !this.hovered()) return false;
         try {
            if (this.bind) {
               if (this.entry.cannotBind()) return false;
               if (button == 0) OpenB4GuiScreen.this.binding = OpenB4GuiScreen.this.binding == this ? null : this;
               else if (button == 1) OpenB4GuiScreen.this.module.toggleHidden(this.entry.mod);
               else return false;
            } else if (button == 0) {
               if (this.action != null) {
                  this.clickedAt = System.currentTimeMillis();
                  this.action.run();
               } else if (this.slider()) {
                  OpenB4GuiScreen.this.sliding = this;
                  this.slide();
               } else if (this.setting instanceof BooleanSetting) {
                  BooleanSetting value = (BooleanSetting)this.setting;
                  value.set(!value.isEnabled());
                  this.clickedAt = System.currentTimeMillis();
                  OpenB4GuiScreen.this.changed(this.entry.mod, this.setting, true);
               } else OpenB4GuiScreen.this.module.showOriginalSettings(this.entry.mod);
            } else return false;
            return true;
         } catch (RuntimeException failure) {
            OpenB4GuiScreen.this.error(failure);
            return true;
         }
      }

      void slide() {
         double distance = Math.min(TEXT_WIDTH, Math.max(0.0, OpenB4GuiScreen.this.mouseX - this.entry.panel.x - CONTROL_INSET));
         double value = distance / TEXT_WIDTH * (this.maximum() - this.minimum()) + this.minimum();
         double rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
         rounded = Math.max(this.minimum(), Math.min(this.maximum(), rounded));
         String before = this.setting.format();
         boolean configured = this.setting.isConfigured();
         if (this.setting instanceof NumberSetting) ((NumberSetting)this.setting).set(rounded);
         else if (this.setting instanceof ChoiceSetting) ((ChoiceSetting)this.setting).set((int)Math.round(rounded));
         else {
            RangeSetting setting = (RangeSetting)this.setting;
            RangeSetting.Value old = setting.get();
            setting.set(this.part == 1 ? new RangeSetting.Value(Math.min(rounded, old.upper), old.upper)
               : new RangeSetting.Value(old.lower, Math.max(rounded, old.lower)));
         }
         if (!configured || !before.equals(this.setting.format())) OpenB4GuiScreen.this.changed(this.entry.mod, this.setting, false);
      }
   }
}
