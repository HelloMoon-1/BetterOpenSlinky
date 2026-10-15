package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.CanvasFonts;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.HudPositionEditor;
import dev.slinky.client.render.OpenB4Theme;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.client.render.RenderModuleAccess;
import dev.slinky.client.render.ScreenshotModuleList;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

public final strictfp class ModOverlayModule extends EvidenceModule {
   private static final long ANIM_MS = 135L;
   private static final Set<String> CLOSET = new HashSet<>(Arrays.asList(
      "auto_clicker", "aim_assist", "backtrack", "hitboxes", "hit_select", "reach", "velocity", "sprint",
      "auto_tool", "fast_mine", "fast_place", "ghost_hand", "anti_bot"));
   private static final Set<String> DONOR_SETTINGS = new HashSet<>(Arrays.asList(
      "hud_style", "screenshot_lowercase", "hud_theme", "hud_outline", "hud_scale", "edit_position", "align_right", "alphabetical_sort", "animate",
      "draw_background", "lowercase", "remove_closet_modules", "remove_render_modules", "remove_scripts", "show_module_info"));
   private final ModuleManager manager;
   private final List<Row> rows = new ArrayList<>();
   private ScreenshotModuleList.Layout cachedScreenshot;
   private long entriesCheckedAt;
   private List<ScreenshotModuleList.Entry> previewEntries;
   private float cachedWidth, cachedHeight, cachedX, cachedY, cachedScale;
   private int cachedPixelRatio;
   private long cachedFontGeneration;
   private List<ScreenshotModuleList.Entry> cachedEntries;
   private final ScreenshotModuleList.Animator screenshotAnimator = new ScreenshotModuleList.Animator();

   public ModOverlayModule(ModuleManager manager) {
      super("mod_overlay", "Mod Overlay", ModuleCategory.RENDER);
      this.manager = manager;
      this.option(new ChoiceSetting("hud_style", "Style", 1, "Raven", "Drip Lite"));
      this.option(new BooleanSetting("screenshot_lowercase", "Drip Lite lowercase", false));
      this.option(new ChoiceSetting("hud_outline", "Outline", 2, "None", "Full", "Left", "Bottom left", "Right", "Bottom right"));
      this.option(new NumberSetting("hud_scale", "Scale", 1.0, 0.5, 2.0, 0.02));
      this.option(new ChoiceSetting("hud_theme", "Theme", 0, OpenB4Theme.NAMES));
      this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.option(new BooleanSetting("align_right", "Align right", false));
      this.option(new BooleanSetting("alphabetical_sort", "Alphabetical sort", false));
      this.option(new BooleanSetting("animate", "Animate", true));
      this.option(new BooleanSetting("draw_background", "Draw background", true));
      this.option(new BooleanSetting("remove_closet_modules", "Remove closet modules", false));
      this.option(new BooleanSetting("remove_render_modules", "Remove render modules", false));
      this.option(new BooleanSetting("remove_scripts", "Remove scripts", false));
      this.option(new BooleanSetting("show_module_info", "Show module info", true));
      this.option(new NumberSetting("hud_x", "Position X", 5.0, -7680.0, 7680.0, 1.0));
      this.option(new NumberSetting("hud_y", "Position Y", 70.0, -4320.0, 4320.0, 1.0));
      // Separate anchors keep switching styles from overwriting the native Raven position.
      // -1 follows the screen's right edge until the existing position editor moves it.
      this.option(new NumberSetting("screenshot_x", "Drip Lite position X", -1.0, -1.0, 7680.0, 1.0));
      this.option(new NumberSetting("screenshot_y", "Drip Lite position Y", 7.0, -4320.0, 4320.0, 1.0));
      List<String> order = new ArrayList<>(Arrays.asList("hud_style", "screenshot_lowercase", "hud_outline", "hud_scale", "hud_theme", "edit_position",
         "align_right", "alphabetical_sort", "animate", "draw_background", "lowercase", "remove_closet_modules",
         "remove_render_modules", "remove_scripts", "show_module_info"));
      for (Setting<?> setting : this.getSettings()) {
         if (!order.contains(setting.getKey())) { order.add(setting.getKey()); }
      }
      this.reorderSettings(order.toArray(new String[0]));
   }

   private <T> void option(Setting<T> setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      this.add(setting);
   }

   @Override
   public boolean settingVisible(String key) {
      if ("screenshot_lowercase".equals(key)) return this.screenshotStyle();
      if ("lowercase".equals(key) || "hud_outline".equals(key) || "hud_theme".equals(key)
         || "align_right".equals(key) || "alphabetical_sort".equals(key)) {
         return !this.screenshotStyle();
      }
      return DONOR_SETTINGS.contains(key);
   }

   public boolean screenshotStyle() { return this.choice("hud_style") == 1; }

   public void resetScreenshotPosition() {
      ((NumberSetting)this.findSetting("screenshot_x")).set(-1.0);
      ((NumberSetting)this.findSetting("screenshot_y")).set(7.0);
   }

   public int positionX() {
      if (!this.screenshotStyle()) { return Math.round(this.number("hud_x")); }
      return Math.round(this.screenshotAnchor(new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth()));
   }

   private float screenshotAnchor(float screenWidth) {
      float stored = this.number("screenshot_x");
      return stored < 0.0F ? screenWidth - 8.0F * this.hudScale() : stored;
   }

   public int positionY() { return Math.round(this.number(this.screenshotStyle() ? "screenshot_y" : "hud_y")); }
   public float hudScale() { return this.number("hud_scale"); }
   // The editor treats X as the text's right edge in this mode, not an inset from it.
   public boolean alignRight() { return this.screenshotStyle() || this.flag("align_right"); }
   public int theme() { return this.choice("hud_theme"); }

   public void position(int x, int y) {
      if (this.screenshotStyle()) {
         NumberSetting sx = (NumberSetting)this.findSetting("screenshot_x");
         // Opening/closing the editor without moving must not pin the automatic anchor.
         if (sx.floatValue() >= 0.0F || x != this.positionX()) {
            sx.set((double)Math.max(0, Math.min(7680, x)));
         }
         ((NumberSetting)this.findSetting("screenshot_y")).set((double)Math.max(-4320, Math.min(4320, y)));
      } else {
         ((NumberSetting)this.findSetting("hud_x")).set((double)x);
         ((NumberSetting)this.findSetting("hud_y")).set((double)y);
      }
   }

   public void toggleAlignment() {
      // Screenshot is deliberately right-aligned; leave Raven's saved alignment alone.
      if (!this.screenshotStyle()) {
         ((BooleanSetting)this.findSetting("align_right")).set(!this.alignRight());
      }
   }

   private boolean eligible(Module module) {
      GuiModule gui = (GuiModule)this.manager.find("gui");
      if (module == this || module == gui
         || module.getId().equals("command_line") || module.getId().equals("raven_settings")
         || gui != null && gui.hidden(module) || (RenderModuleAccess.observedFlags(module) & 4) != 0) {
         return false;
      }
      if (this.flag("remove_closet_modules") && CLOSET.contains(module.getId())) {
         return false;
      }
      if (this.flag("remove_render_modules") && module.getCategory() == ModuleCategory.RENDER) {
         return false;
      }
      if (this.flag("remove_scripts") && module.getId().startsWith("sc-")) {
         return false;
      }
      Module antiKb = this.manager.find("AntiKnockback");
      return !module.getId().equals("velocity") || antiKb == null || !antiKb.isEnabled();
   }

   private Row row(Module module) {
      for (Row row : this.rows) {
         if (row.module == module) { return row; }
      }
      Row row = new Row(module);
      row.enabled = module.isEnabled();
      row.changedAt = System.currentTimeMillis() - ANIM_MS;
      row.progress = row.enabled ? 1.0F : 0.0F;
      this.rows.add(row);
      return row;
   }

   public void refresh() {
      if (this.screenshotStyle()) return;
      long now = System.currentTimeMillis();
      for (Module module : this.manager.all()) {
         Row row = this.row(module);
         if (row.enabled != module.isEnabled()) {
            this.transition(row, module.isEnabled(), now);
         }
      }
   }

   private void transition(Row row, boolean enabled, long now) {
      float visibility = this.visibility(row, now);
      long elapsed = enabled ? (long)((visibility - 0.1F) / 0.9F * ANIM_MS)
         : (long)((1.0F - visibility) / 0.9F * ANIM_MS);
      row.changedAt = now - Math.max(0L, Math.min(ANIM_MS, elapsed));
      row.enabled = enabled;
   }

   public void control(Module module, boolean enabled) {
      this.cachedScreenshot = null; this.entriesCheckedAt = 0;
      if (module != this && !this.screenshotStyle()) {
         this.transition(this.row(module), enabled, System.currentTimeMillis());
      }
   }

   public void collect(ClientContext context) { this.refresh(); }

   @Override
   public void onClientTick(ClientContext context) { this.collect(context); }

   @Override
   protected void onEnable(ClientContext context) {
      this.rows.clear();
      this.cachedScreenshot = null; this.entriesCheckedAt = 0;
      this.screenshotAnimator.clear();
      this.refresh();
   }

   @Override
   protected void onDisable(ClientContext context) { this.rows.clear(); this.cachedScreenshot = null; this.entriesCheckedAt = 0; this.screenshotAnimator.clear(); }

   @Override
   public boolean listens(int type) { return type == 5 || type == 11 || type == 24; }

   public int redrawFlags(int old) { return old | 3; }
   public void settingAction(int kind) { this.cachedScreenshot = null; this.entriesCheckedAt = 0; this.refresh(); }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      this.cachedScreenshot = null; this.entriesCheckedAt = 0;
      if (key.equals("hud_style")) this.screenshotAnimator.clear();
      if (key.equals("edit_position") && ((BooleanSetting)this.findSetting(key)).isEnabled()) {
         ((BooleanSetting)this.findSetting(key)).set(false);
         GuiModule gui = (GuiModule)this.manager.find("gui");
         if (gui != null) { gui.openEditor(new HudPositionEditor(this.manager, this)); }
         return true;
      }
      this.refresh();
      return false;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 5 && event.module != null) {
         this.control(event.module, event.enabled);
      } else if (event.type == 11) {
         RenderEventPayload payload = RenderEventPayload.get(event);
         if (payload != null) { this.settingAction(payload.action); }
      } else if (event.type == 24) {
         this.cachedScreenshot = null; this.entriesCheckedAt = 0;
         this.refresh();
      }
   }

   @Override
   public void onRenderTick(ClientContext context) {
      if (RavenRender.visible(context.minecraft())) {
         this.renderForGui();
      }
   }

   public void renderForGui() {
      try (RavenRender.Scope scope = RavenRender.begin()) {
         RenderAccess.submit(new SemanticEvent(10), this.plan(this.manager.context(), 0.0F));
      }
   }

   public DrawPlan plan(ClientContext context, float elapsed) {
      ScaledResolution screen = new ScaledResolution(context.minecraft());
      return this.plan(screen.getScaledWidth(), screen.getScaledHeight(), elapsed);
   }

   public DrawPlan plan(float screen, float elapsed) {
      return this.plan(screen, new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight(), elapsed);
   }

   private DrawPlan screenshotPlan(float screenWidth, float screenHeight) {
      float x = this.screenshotAnchor(screenWidth), y = this.positionY();
      return this.screenshotAnimator.frame(this.dripLiteEntries(), screenWidth, screenHeight, x, y,
         this.hudScale(), this.flag("draw_background"), this.flag("animate"));
   }

   private List<ScreenshotModuleList.Entry> dripLiteEntries() {
      long now = System.nanoTime();
      if (this.cachedEntries != null && this.entriesCheckedAt != 0 && now - this.entriesCheckedAt < 50000000L) return this.cachedEntries;
      List<ScreenshotModuleList.Entry> entries = new ArrayList<>();
      for (Module module : this.manager.all()) {
         // No example rows or fading disabled modules in the screenshot list.
         if (!module.isEnabled() || !this.eligible(module)) { continue; }
         String name = module.getName();
         String status = this.flag("show_module_info") ? RenderModuleAccess.status(module) : null;
         if (this.flag("screenshot_lowercase")) {
            name = name.toLowerCase(Locale.ROOT);
            if (status != null) { status = status.toLowerCase(Locale.ROOT); }
         }
         entries.add(new ScreenshotModuleList.Entry(module.getId(), name, status));
      }
      this.entriesCheckedAt = now;
      if (!entries.equals(this.cachedEntries)) this.cachedEntries = entries;
      return this.cachedEntries;
   }

   public ScreenshotModuleList.Layout screenshotPreview(float screenWidth, float screenHeight, float anchorX, float anchorY) {
      List<ScreenshotModuleList.Entry> entries = this.dripLiteEntries();
      int pixelRatio = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
      boolean sameGeometry = this.cachedScreenshot != null
         && screenWidth == this.cachedWidth && screenHeight == this.cachedHeight
         && anchorX == this.cachedX && anchorY == this.cachedY && this.hudScale() == this.cachedScale
         && pixelRatio == this.cachedPixelRatio && CanvasFonts.generation() == this.cachedFontGeneration;
      if (sameGeometry && entries.equals(this.previewEntries)) return this.cachedScreenshot;
      this.cachedScreenshot = ScreenshotModuleList.layout(Minecraft.getMinecraft().fontRendererObj, entries,
         screenWidth, screenHeight, anchorX, anchorY,
         this.hudScale(), this.flag("draw_background"));
      this.previewEntries = entries;
      this.cachedWidth = screenWidth; this.cachedHeight = screenHeight;
      this.cachedX = anchorX; this.cachedY = anchorY; this.cachedScale = this.hudScale();
      this.cachedPixelRatio = pixelRatio;
      this.cachedFontGeneration = CanvasFonts.generation();
      return this.cachedScreenshot;
   }

   private DrawPlan plan(float screen, float screenHeight, float elapsed) {
      if (this.screenshotStyle()) { return this.screenshotPlan(screen, screenHeight); }
      this.refresh();
      FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
      long now = System.currentTimeMillis();
      List<Row> listed = new ArrayList<>();
      for (Row row : this.rows) {
         row.progress = this.height(row, now, 1.0F);
         if (!this.eligible(row.module) || this.height(row, now, font.FONT_HEIGHT + 1.0F) <= 0.0F
            || this.visibility(row, now) <= 0.1F) { continue; }
         row.name = row.module.getName();
         row.status = this.flag("show_module_info") ? RenderModuleAccess.status(row.module) : null;
         String display = row.name + (row.status == null || row.status.isEmpty() ? "" : "§7 " + row.status);
         row.display = this.flag("lowercase") ? display.toLowerCase(Locale.ROOT) : display;
         row.width = font.getStringWidth(row.display);
         row.height = font.FONT_HEIGHT;
         listed.add(row);
      }
      if (this.flag("alphabetical_sort")) {
         Collections.sort(listed, (a, b) -> a.module.getName().compareTo(b.module.getName()));
      } else {
         Collections.sort(listed, (a, b) -> Float.compare(b.width, a.width));
      }
      DrawPlan plan = new DrawPlan();
      float scale = this.hudScale();
      float anchor = this.positionX() / scale;
      float y = this.positionY() / scale;
      float lastWidth = 0.0F;
      double offset = 0.0;
      int outline = this.choice("hud_outline");
      for (int i = 0; i < listed.size(); i++) {
         Row row = listed.get(i);
         float h = this.height(row, now, font.FONT_HEIGHT + 1.0F);
         float alpha = this.visibility(row, now);
         float slide = this.flag("animate") ? (this.alignRight() ? 12.0F : -12.0F) * (1.0F - row.progress) : 0.0F;
         int rgb = OpenB4Theme.getRGB(this.theme(), offset);
         offset -= OpenB4Theme.offset() * (this.theme() == 1 ? 240.0 : 1.0);
         int color = ((int)(alpha * 255.0F) & 255) << 24 | rgb & 0xFFFFFF;
         float width = row.width;
         float x = this.alignRight() ? anchor - width : anchor;
         float ty = y + (h - font.FONT_HEIGHT) / 2.0F;
         int bg = ((int)(26.0F + (alpha - 0.1F) / 0.9F * 64.0F) & 255) << 24;
         plan.push().scale(scale, scale, 1.0F).translate(slide / scale, 0.0F, 0.0F);
         if (this.flag("draw_background")) {
            float left = x - 1.0F;
            float right = x + width;
            if (outline == 2 || outline == 3) { left -= 0.5F; }
            else if (outline == 4 || outline == 5) { right += 0.5F; }
            else if (outline == 1) { left -= 0.5F; right += 0.5F; }
            rect(plan, left, ty + font.FONT_HEIGHT, right, ty - 1.0F, bg);
         }
         if (outline == 1) {
            this.side(plan, true, i, x, ty, width, lastWidth, color, bg, font.FONT_HEIGHT);
            this.side(plan, false, i, x, ty, width, lastWidth, color, bg, font.FONT_HEIGHT);
            if (i == 0) { rect(plan, x - 2.5F, ty - 1.0F, x + width + 1.5F, ty - 2.0F, color); }
            if (i == listed.size() - 1) { bottom(plan, x - 2.5F, x + width + 1.5F, ty, font.FONT_HEIGHT, color); }
         } else if (outline != 0) {
            this.side(plan, outline == 2 || outline == 3, i, x, ty, width, lastWidth, color, bg, font.FONT_HEIGHT);
            if ((outline == 3 || outline == 5) && i == listed.size() - 1) {
               bottom(plan, outline == 3 ? x - 2.5F : x - 1.0F, outline == 3 ? x + width : x + width + 1.5F, ty, font.FONT_HEIGHT, color);
            }
         }
         plan.text(row.display, x, ty, color, true, font.FONT_HEIGHT).pop();
         y += h;
         lastWidth = width;
      }
      return plan;
   }

   private float height(Row row, long now, float height) {
      if (!this.flag("animate")) { return row.enabled ? height : 0.0F; }
      float progress = Math.max(0.0F, Math.min(1.0F, (float)(now - row.changedAt) / ANIM_MS));
      return height * (row.enabled ? progress : 1.0F - progress);
   }

   private float visibility(Row row, long now) {
      if (!this.flag("animate")) { return row.enabled ? 1.0F : 0.0F; }
      long elapsed = Math.max(0L, now - row.changedAt);
      if (elapsed >= ANIM_MS) { return row.enabled ? 1.0F : 0.0F; }
      return row.enabled ? 0.1F + 0.9F * elapsed / ANIM_MS : 1.0F - 0.9F * elapsed / ANIM_MS;
   }

   private void side(DrawPlan plan, boolean left, int index, float x, float y, float width, float lastWidth, int color, int bg, int fontHeight) {
      float edge = left ? x - 2.5F : x + width + 0.5F;
      rect(plan, edge, y + fontHeight, edge + 1.0F, y - 1.0F, color);
      if (!this.flag("draw_background")) {
         rect(plan, edge + 1.0F, y + fontHeight, edge + 1.5F, y - 1.0F, bg);
      }
      if (index > 0 && (left == this.alignRight())) {
         float end = left ? x - 2.5F : x + width + 1.5F;
         rect(plan, end + (left ? -(lastWidth - width) : lastWidth - width), y, end, y - 1.0F, color);
      }
   }

   private static void bottom(DrawPlan plan, float left, float right, float y, int fontHeight, int color) {
      rect(plan, left, y + fontHeight, right, y + fontHeight + 1.0F, color);
   }

   private static void rect(DrawPlan plan, float left, float top, float right, float bottom, int color) {
      plan.disable(3553).color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
         (color & 255) / 255.0F, (color >>> 24) / 255.0F)
         .vertices(7, 2, left, bottom, right, bottom, right, top, left, top);
   }

   public List<Row> rows() { return Collections.unmodifiableList(this.rows); }

   @Override
   public void onWorldUnload(ClientContext context) { this.rows.clear(); this.cachedScreenshot = null; this.entriesCheckedAt = 0; this.screenshotAnimator.clear(); }

   public static final strictfp class Row {
      public final Module module;
      public String name;
      public String status;
      public float width;
      public float height;
      public float progress;
      public boolean enabled;
      private String display;
      private long changedAt;
      private Row(Module module) { this.module = module; }
   }
}
