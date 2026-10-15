package dev.slinky.module;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
   private final String id;
   private final String name;
   private final String description;
   private final ModuleCategory category;
   private final List<Setting<?>> settings = new ArrayList<>();
   private volatile boolean enabled;
   private volatile boolean holdBinding;
   private volatile int binding;

   protected Module(String id, String name, String description, ModuleCategory category) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.category = category;
   }

   protected final <T extends Setting<?>> T add(T setting) {
      for (Setting<?> current : this.settings) {
         if (current.getKey().equals(setting.getKey())) {
            throw new IllegalArgumentException("duplicate setting key " + setting.getKey());
         }
      }

      // Constructor-configured options also need a baseline when older profiles omit them.
      if (setting.isConfigured()) setting.saveCurrent();
      this.settings.add(setting);
      return setting;
   }

   public final String getId() {
      return this.id;
   }

   public final String getName() {
      return this.name;
   }

   public final String getDescription() {
      return this.description;
   }

   public final ModuleCategory getCategory() {
      return this.category;
   }

   public int metadataCategory() {
      int recovered = EvidenceCatalog.descriptorCategory(this.name);
      if (recovered >= 0) {
         return recovered;
      }
      switch (this.getCategory()) {
         case COMBAT:
            return 0;
         case MOVEMENT:
            return 1;
         case RENDER:
            return 2;
         case WORLD:
            return 3;
         default:
            return 4;
      }
   }

   public int descriptorFlags() {
      return EvidenceCatalog.descriptorFlags(this.name);
   }

   public boolean canToggleFromGui() {
      return (this.descriptorFlags() & 2) == 0;
   }

   public String statusText() {
      return null;
   }

   public final List<Setting<?>> getSettings() {
      return Collections.unmodifiableList(this.settings);
   }

   protected final void reorderSettings(String... keys) {
      if (keys.length != this.settings.size()) {
         throw new IllegalArgumentException("Incomplete setting order for " + this.name);
      } else {
         List<Setting<?>> ordered = new ArrayList<>();

         for (String key : keys) {
            Setting<?> match = null;

            for (Setting<?> setting : this.settings) {
               if (setting.getKey().equals(key)) {
                  match = setting;
               }
            }

            if (match == null || ordered.contains(match)) {
               throw new IllegalArgumentException("Invalid setting order key " + key);
            }

            ordered.add(match);
         }

         this.settings.clear();
         this.settings.addAll(ordered);
      }
   }

   public final Setting<?> findSetting(String key) {
      for (Setting<?> setting : this.settings) {
         if (setting.getKey().equalsIgnoreCase(key)) {
            return setting;
         }
      }

      return null;
   }

   public final boolean isEnabled() {
      return this.enabled;
   }

   public final boolean isHoldBinding() {
      return this.holdBinding;
   }

   public final void setHoldBinding(boolean hold) {
      this.holdBinding = hold;
   }

   public final int getBinding() {
      return this.binding;
   }

   public final void setBinding(int binding) {
      this.binding = binding;
   }

   public boolean afterSettingControl(String key, ClientContext context) {
      return false;
   }

   final void markEnabled(boolean value) {
      this.enabled = value;
   }

   final void notifyLifecycle(boolean value, ClientContext context) {
      if (value) {
         this.onEnable(context);
      } else {
         this.onDisable(context);
      }
   }

   final void setEnabledInternal(boolean enabled, ClientContext context) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (enabled) {
            this.onEnable(context);
         } else {
            this.onDisable(context);
         }
      }
   }

   protected void onEnable(ClientContext context) {
   }

   protected void onDisable(ClientContext context) {
   }

   public void onClientTick(ClientContext context) {
   }

   public void onClientTickStart(ClientContext context) {
   }

   public void onClientInputTick(ClientContext context) {
   }

   public void onWorldUnload(ClientContext context) {
   }

   public void onRenderTickStart(ClientContext context) {
   }

   public void onRenderTick(ClientContext context) {
   }

   public void onEvent(ClientContext context, SemanticEvent event) {
   }

   public boolean listens(int type) {
      return false;
   }

   public boolean settingVisible(String key) {
      return true;
   }

   public final List<Setting<?>> visibleSettings() {
      List<Setting<?>> result = new ArrayList<>();

      for (RecoveredUiLayout.Control control : RecoveredUiLayout.controls(this)) {
         if (control.setting != null) {
            result.add(control.setting);
         }
      }

      return Collections.unmodifiableList(result);
   }
}
