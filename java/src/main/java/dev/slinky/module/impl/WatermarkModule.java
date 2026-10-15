package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.WatermarkEditor;
import dev.slinky.client.render.WatermarkRenderer;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import net.minecraft.client.gui.ScaledResolution;

/** Standalone in-game branding; unrelated to the ClickGUI's welcome/footer text. */
public final class WatermarkModule extends Module {
   private final ModuleManager manager;
   private final BooleanSetting editPosition;
   private final NumberSetting hudX, hudY, scale, backgroundOpacity;

   public WatermarkModule(ModuleManager manager) {
      super("watermark", "Watermark", "Drip Lite style watermark with Slinky b4 branding", ModuleCategory.RENDER);
      this.manager = manager;
      this.editPosition = this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.scale = this.option(new NumberSetting("scale", "Scale", 1.0, 0.75, 2.0, 0.05));
      this.backgroundOpacity = this.option(new NumberSetting("background_opacity", "Background opacity", 80, 0, 100, 1));
      this.hudX = this.option(new NumberSetting("hud_x", "Position X", 5, 0, 100000, 1));
      this.hudY = this.option(new NumberSetting("hud_y", "Position Y", 5, 0, 100000, 1));
   }

   private <T, S extends Setting<T>> S option(S setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      return this.add(setting);
   }

   public int x() { return this.hudX.intValue(); }
   public int y() { return this.hudY.intValue(); }
   public float scale() { return this.scale.floatValue(); }
   public float backgroundOpacity() { return this.backgroundOpacity.floatValue() / 100.0F; }
   public int width() { return (int)Math.ceil(WatermarkRenderer.width() * this.scale()); }
   public int height() { return (int)Math.ceil(WatermarkRenderer.HEIGHT * this.scale()); }
   public int clampX(int x, int screenWidth) { return Math.max(0, Math.min(x, screenWidth - this.width())); }
   public int clampY(int y, int screenHeight) { return Math.max(0, Math.min(y, screenHeight - this.height())); }
   public void position(int x, int y) { this.hudX.set((double)x); this.hudY.set((double)y); }
   public void resetPosition() { this.position(5, 5); }

   public void openEditor() {
      WatermarkEditor screen = new WatermarkEditor(this.manager, this);
      Module gui = this.manager.find("gui");
      if (gui instanceof GuiModule) ((GuiModule)gui).openEditor(screen);
      else this.manager.context().minecraft().displayGuiScreen(screen);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if ("edit_position".equals(key) && this.editPosition.isEnabled()) {
         this.editPosition.set(false);
         this.openEditor();
         return true;
      }
      return false;
   }

   @Override
   public boolean settingVisible(String key) { return !"hud_x".equals(key) && !"hud_y".equals(key); }

   @Override
   public void onRenderTick(ClientContext context) {
      if (!RavenRender.visible(context.minecraft()) || context.minecraft().gameSettings.hideGUI) return;
      ScaledResolution sr = new ScaledResolution(context.minecraft());
      try (RavenRender.Scope scope = RavenRender.begin()) {
         RenderAccess.submit(new SemanticEvent(10), WatermarkRenderer.plan(
            this.clampX(this.x(), sr.getScaledWidth()), this.clampY(this.y(), sr.getScaledHeight()),
            this.scale(), this.backgroundOpacity()));
      }
   }
}
