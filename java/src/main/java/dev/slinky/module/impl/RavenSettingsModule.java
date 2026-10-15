package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.client.render.OpenB4Theme;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import java.util.Arrays;

public final class RavenSettingsModule extends Module {
   private final ModuleManager manager;
   private final BooleanSetting editPosition;
   public final ChoiceSetting defaultTheme;
   public final ChoiceSetting background;
   public final NumberSetting offset;
   public final NumberSetting timeMultiplier;
   public final BooleanSetting chatAsInventory;
   public final BooleanSetting healthAsHearts;
   public final BooleanSetting heartSymbol;

   public RavenSettingsModule() {
      this(null);
   }

   public RavenSettingsModule(ModuleManager manager) {
      super("raven_settings", "Settings", "Raven HUD and theme settings", ModuleCategory.UTILITY);
      this.manager = manager;
      this.defaultTheme = this.option(new ChoiceSetting("default_theme", "Default theme", 7,
         Arrays.copyOfRange(OpenB4Theme.NAMES, 1, OpenB4Theme.NAMES.length)));
      this.chatAsInventory = this.option(new BooleanSetting("chat_as_inventory", "Set chat as inventory", false));
      this.healthAsHearts = this.option(new BooleanSetting("health_as_hearts", "Show health as hearts", false));
      this.heartSymbol = this.option(new BooleanSetting("heart_symbol", "Show heart symbol", false));
      this.background = this.option(new ChoiceSetting("background", "Background", 2, "None", "Rect", "Round"));
      this.editPosition = this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.offset = this.option(new NumberSetting("theme_offset", "Offset", 0.5, -4.0, 4.0, 0.1));
      this.timeMultiplier = this.option(new NumberSetting("time_multiplier", "Time multiplier", 0.5, 0.1, 4.0, 0.1));
   }

   private <T extends Setting<?>> T option(T setting) {
      setting.parseAndSet(setting.format());
      setting.saveCurrent();
      return this.add(setting);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if ("edit_position".equals(key) && this.editPosition.isEnabled()) {
         this.editPosition.set(false);
         Module info = this.manager == null ? null : this.manager.find("info_hud");
         if (info instanceof InfoHudModule) {
            ((InfoHudModule)info).openEditor();
         }
         return true;
      }
      return false;
   }

}
