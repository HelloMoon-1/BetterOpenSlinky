package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hud.InfoHud;
import dev.slinky.client.hud.InfoHudEditor;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import net.minecraft.client.gui.ScaledResolution;

public final class InfoHudModule extends Module {
   private final ModuleManager manager;
   private final BooleanSetting editPosition;
   private final NumberSetting hudX, hudY;

   public InfoHudModule(ModuleManager manager) {
      super("info_hud", "Info HUD", "Displays information supplied by game-aware modules", ModuleCategory.RENDER);
      this.manager = manager;
      this.editPosition = this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.hudX = this.option(new NumberSetting("hud_x", "Position X", 4, -100000, 100000, 1));
      this.hudY = this.option(new NumberSetting("hud_y", "Position Y", -1, -100000, 100000, 1));
   }

   private <T, S extends Setting<T>> S option(S setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      return this.add(setting);
   }

   public int x() { return this.hudX.intValue(); }
   public int y() {
      if (this.hudY.intValue() == -1) this.hudY.set((double)(new ScaledResolution(this.manager.context().minecraft()).getScaledHeight() / 3 - 30));
      return this.hudY.intValue();
   }
   public void position(int x, int y) { this.hudX.set((double)x); this.hudY.set((double)y); }
   public void resetPosition() { this.hudX.set(4.0); this.hudY.set(-1.0); this.y(); }

   public void openEditor() {
      InfoHudEditor screen = new InfoHudEditor(this.manager, this);
      Module gui = this.manager.find("gui");
      if (gui instanceof GuiModule) ((GuiModule)gui).openEditor(screen);
      else this.manager.context().minecraft().displayGuiScreen(screen);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if (key.equals("edit_position") && this.editPosition.isEnabled()) {
         this.editPosition.set(false);
         this.openEditor();
         return true;
      }
      return false;
   }

   @Override
   public boolean settingVisible(String key) { return !key.equals("hud_x") && !key.equals("hud_y"); }

   @Override
   public void onRenderTick(ClientContext context) {
      try (RavenRender.Scope scope = RavenRender.begin()) {
         RenderAccess.submit(new SemanticEvent(10), InfoHud.plan(RavenRender.visible(context.minecraft()), this.x(), this.y(), RavenRender.background(), System.currentTimeMillis()));
      }
   }

   @Override
   protected void onDisable(ClientContext context) { InfoHud.clear(); }
   @Override
   public void onWorldUnload(ClientContext context) { InfoHud.clear(); }
}
