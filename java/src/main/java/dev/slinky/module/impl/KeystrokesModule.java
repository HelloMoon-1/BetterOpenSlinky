package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hud.KeystrokesClicks;
import dev.slinky.client.hud.KeystrokesGui;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeystrokesModule extends Module {
   public static final String[] COLOR_NAMES = {"White", "Red", "Green", "Blue", "Yellow", "Purple", "Rainbow"};
   private static final int[] COLORS = {16777215, 16711680, 65280, 255, 16776960, 11141290};
   private final ModuleManager manager;
   private final BooleanSetting openEditor, mouseButtons, outline;
   private final ChoiceSetting color;
   private final NumberSetting hudX, hudY;
   private final Key[] keys = new Key[4];
   private final MouseButton[] buttons = new MouseButton[2];
   private boolean physicalSamplingReady;

   public KeystrokesModule(ModuleManager manager) {
      super("keystrokes", "Keystrokes", "OpenB4 movement keys and mouse-button display", ModuleCategory.RENDER);
      this.manager = manager;
      this.openEditor = this.option(new BooleanSetting("open_editor", "Configure Keystrokes", false));
      this.color = this.option(new ChoiceSetting("text_color", "Text color", 0, COLOR_NAMES));
      this.mouseButtons = this.option(new BooleanSetting("show_mouse_buttons", "Show mouse buttons", false));
      this.outline = this.option(new BooleanSetting("outline", "Outline", false));
      this.hudX = this.option(new NumberSetting("hud_x", "Position X", 0, -100000, 100000, 1));
      this.hudY = this.option(new NumberSetting("hud_y", "Position Y", 0, -100000, 100000, 1));
      Minecraft mc = manager.context().minecraft();
      this.keys[0] = new Key(mc.gameSettings.keyBindForward, 26, 2);
      this.keys[1] = new Key(mc.gameSettings.keyBindBack, 26, 26);
      this.keys[2] = new Key(mc.gameSettings.keyBindLeft, 2, 26);
      this.keys[3] = new Key(mc.gameSettings.keyBindRight, 50, 26);
      this.buttons[0] = new MouseButton(0, 2, 50);
      this.buttons[1] = new MouseButton(1, 38, 50);
   }

   private <T, S extends Setting<T>> S option(S setting) {
      setting.set(setting.get()); setting.saveCurrent(); return this.add(setting);
   }

   public int x() { return this.hudX.intValue(); }
   public int y() { return this.hudY.intValue(); }
   public int colorIndex() { return this.color.index(); }
   public boolean mouseButtons() { return this.mouseButtons.isEnabled(); }
   public boolean outline() { return this.outline.isEnabled(); }
   public void position(int x, int y) { this.hudX.set((double)x); this.hudY.set((double)y); }
   public void cycleColor() { this.color.set(this.color.index() == 6 ? 0 : this.color.index() + 1); }
   public void toggleMouseButtons() { this.mouseButtons.set(!this.mouseButtons.isEnabled()); }
   public void toggleOutline() { this.outline.set(!this.outline.isEnabled()); }
   public boolean requestedEnabled() { return this.manager.requestedEnabled(this); }
   public void toggleEnabled() { this.manager.setEnabled(this, !this.requestedEnabled(), false); }

   public void openEditor() {
      KeystrokesGui screen = new KeystrokesGui(this.manager, this);
      Module gui = this.manager.find("gui");
      if (gui instanceof GuiModule) ((GuiModule)gui).openEditor(screen);
      else this.manager.context().minecraft().displayGuiScreen(screen);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if (key.equals("open_editor") && this.openEditor.isEnabled()) {
         this.openEditor.set(false); this.openEditor(); return true;
      }
      return false;
   }

   @Override
   public boolean settingVisible(String key) { return !key.equals("hud_x") && !key.equals("hud_y"); }

   @Override
   protected void onEnable(ClientContext c) { this.physicalSamplingReady = false; }

   @Override
   public void onWorldUnload(ClientContext c) { this.physicalSamplingReady = false; }

   private void sampleGameplay(ClientContext c) {
      if (c.minecraft().currentScreen != null || !c.minecraft().inGameHasFocus || !Mouse.isCreated()) {
         this.physicalSamplingReady = false;
         return;
      }
      if (!this.physicalSamplingReady) {
         // Never turn a button held while closing a GUI into a fallback gameplay click.
         // Raw gameplay hooks still count actual presses while this fallback re-arms.
         if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) return;
         KeystrokesClicks.onMouseButton(0, false);
         KeystrokesClicks.onMouseButton(1, false);
         this.physicalSamplingReady = true;
      }
      KeystrokesClicks.samplePhysical();
   }

   @Override
   public void onClientTick(ClientContext c) { this.sampleGameplay(c); }

   @Override
   public void onRenderTick(ClientContext c) {
      this.sampleGameplay(c);
      if (c.minecraft().currentScreen != null || !c.minecraft().inGameHasFocus || c.minecraft().gameSettings.showDebugInfo) return;
      try (RavenRender.Scope scope = RavenRender.begin()) {
         RenderAccess.submit(new SemanticEvent(10), this.plan(false));
      }
   }

   /** The donor configuration preview is hidden when Mod is Disabled. */
   public DrawPlan plan(boolean preview) {
      DrawPlan p = new DrawPlan();
      if (!(preview ? this.requestedEnabled() : this.isEnabled())) return p;
      Minecraft mc = this.manager.context().minecraft();
      int px = this.x(), py = this.y();
      ScaledResolution sr = new ScaledResolution(mc);
      int h = this.mouseButtons() ? 74 : 50;
      if (px < 0) px = 0;
      else if (px > sr.getScaledWidth() - 74) px = sr.getScaledWidth() - 74;
      if (py < 0) py = 0;
      else if (py > sr.getScaledHeight() - h) py = sr.getScaledHeight() - h;
      this.position(px, py);
      int color = this.color.index() == 6 ? Color.getHSBColor((float)(System.currentTimeMillis() % 3750L) / 3750.0F, 1, 1).getRGB() : COLORS[this.color.index()];
      for (Key key : this.keys) key.draw(p, mc, px, py, color, this.outline());
      if (this.mouseButtons()) for (MouseButton button : this.buttons) button.draw(p, mc, px, py, color, this.outline());
      return p;
   }

   private static void rect(DrawPlan p, int x, int y, int width, int height, int color) {
      p.color((float)(color >> 16 & 255) / 255, (float)(color >> 8 & 255) / 255, (float)(color & 255) / 255, (float)(color >>> 24) / 255)
         .vertices(7, 2, x + width, y, x, y, x, y + height, x + width, y + height);
   }

   private static void box(DrawPlan p, int x, int y, int width, int shade, int color, boolean outline) {
      rect(p, x, y, width, 22, 0x78000000 + (shade << 16) + (shade << 8) + shade);
      if (outline) {
         int line = 0xFF000000 | color;
         rect(p, x, y, width, 1, line); rect(p, x, y + 21, width, 1, line);
         rect(p, x, y, 1, 22, line); rect(p, x + width - 1, y, 1, 22, line);
      }
   }

   private static final class Key {
      private final int ox, oy;
      private final KeyBinding key;
      private long changed;
      private boolean down = true;
      private int shade = 25;
      private double textFade = 1;
      Key(KeyBinding key, int ox, int oy) { this.key = key; this.ox = ox; this.oy = oy; }
      void draw(DrawPlan p, Minecraft mc, int x, int y, int color, boolean outline) {
         boolean pressed = this.key.isKeyDown();
         String name = Keyboard.getKeyName(this.key.getKeyCode());
         if (name == null) name = "NONE";
         if (pressed != this.down) { this.down = pressed; this.changed = System.currentTimeMillis(); }
         long elapsed = System.currentTimeMillis() - this.changed;
         if (pressed) { this.shade = Math.min(255, (int)(2L * elapsed)); this.textFade = Math.max(0, 1 - (double)elapsed / 20); }
         else { this.shade = Math.max(0, 255 - (int)(2L * elapsed)); this.textFade = Math.min(1, (double)elapsed / 20); }
         int g = color >> 8 & 255, b = color & 255;
         box(p, x + this.ox, y + this.oy, 22, this.shade, color, outline);
         // Preserve donor's red-channel-from-green quirk for the movement-key labels.
         int textColor = -16777216 + ((int)(g * this.textFade) << 16) + ((int)(g * this.textFade) << 8) + (int)(b * this.textFade);
         p.text(name, x + this.ox + 8, y + this.oy + 8, textColor, false, mc.fontRendererObj.FONT_HEIGHT);
      }
   }

   private static final class MouseButton {
      private final int button, ox, oy;
      private double textFade = 1;
      private long changed;
      private boolean down = true;
      private int shade = 255;
      MouseButton(int button, int ox, int oy) { this.button = button; this.ox = ox; this.oy = oy; }
      void draw(DrawPlan p, Minecraft mc, int x, int y, int color, boolean outline) {
         boolean pressed = this.button == 0 ? KeystrokesClicks.leftDown() : KeystrokesClicks.rightDown();
         if (pressed != this.down) { this.down = pressed; this.changed = System.currentTimeMillis(); }
         long elapsed = System.currentTimeMillis() - this.changed;
         if (pressed) { this.shade = Math.min(255, (int)(2L * elapsed)); this.textFade = Math.max(0, 1 - (double)elapsed / 20); }
         else { this.shade = Math.max(0, 255 - (int)(2L * elapsed)); this.textFade = Math.min(1, (double)elapsed / 20); }
         int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
         box(p, x + this.ox, y + this.oy, 34, this.shade, color, outline);
         int textColor = -16777216 + ((int)(r * this.textFade) << 16) + ((int)(g * this.textFade) << 8) + (int)(b * this.textFade);
         p.text(this.button == 0 ? "LMB" : "RMB", x + this.ox + 8, y + this.oy + 4, textColor, false, mc.fontRendererObj.FONT_HEIGHT);
         String cps = (this.button == 0 ? KeystrokesClicks.totalLeftCps() : KeystrokesClicks.totalRightCps()) + " CPS";
         int w = mc.fontRendererObj.getStringWidth(cps);
         int fade = (int)(255 * this.textFade);
         int cpsColor = -16777216 + (fade << 16) + (fade << 8) + fade;
         p.push().scale(0.5F, 0.5F, 0.5F).text(cps, (x + this.ox + 17) * 2 - w / 2, (y + this.oy + 14) * 2, cpsColor, false, mc.fontRendererObj.FONT_HEIGHT).pop();
      }
   }
}
