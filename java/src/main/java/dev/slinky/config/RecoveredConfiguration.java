package dev.slinky.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.slinky.client.RecoveredMath;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecoveredConfiguration {
   private RecoveredConfiguration() {
   }

   private static boolean included(Module module, RecoveredConfiguration.Scope scope) {
      boolean global = (module.descriptorFlags() & 16) != 0;
      return global == (scope == RecoveredConfiguration.Scope.GLOBAL);
   }

   public static JsonObject serialize(
      ModuleManager manager, RecoveredConfiguration.Scope scope, Iterable<RecoveredConfiguration.ProfileBinding> profileBindings
   ) {
      JsonObject root = new JsonObject();

      for (Module module : manager.all()) {
         if (included(module, scope)) {
            JsonObject record = new JsonObject();
            int flags = module.descriptorFlags();
            if ((flags & 2) == 0 && manager.requestedEnabled(module)) {
               record.addProperty("__on", true);
            }

            if ((flags & 1) == 0) {
               if (module.getBinding() != 0) {
                  record.addProperty("__bind", (double)Integer.toUnsignedLong(module.getBinding()));
               }

               if ((flags & 8) == 0 && module.isHoldBinding()) {
                  record.addProperty("__hold", true);
               }
            }

            for (Setting<?> setting : module.getSettings()) {
               if (setting.isConfigured()) {
                  record.add(setting.getKey(), SettingCodec.encode(setting));
               }
            }

            root.add(module.getName(), record);
         }
      }

      if (scope == RecoveredConfiguration.Scope.GLOBAL) {
         JsonArray bindings = new JsonArray();

         for (RecoveredConfiguration.ProfileBinding profile : profileBindings) {
            if (profile.binding != 0) {
               JsonObject recordx = new JsonObject();
               recordx.addProperty("name", profile.name);
               recordx.addProperty("bind", (double)Integer.toUnsignedLong(profile.binding));
               bindings.add(recordx);
            }
         }

         root.add("profile_binds", bindings);
      }

      return root;
   }

   public static List<RecoveredConfiguration.ProfileBinding> profileBindings(JsonObject root) {
      List<RecoveredConfiguration.ProfileBinding> result = new ArrayList<>();
      JsonElement rows = root.get("profile_binds");
      if (rows != null && rows.isJsonArray()) {
         for (JsonElement element : rows.getAsJsonArray()) {
            if (element.isJsonObject()) {
               JsonObject row = element.getAsJsonObject();
               JsonElement name = row.get("name");
               if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
                  result.add(new RecoveredConfiguration.ProfileBinding(name.getAsString(), binding(row.get("bind"))));
               }
            }
         }

         return result;
      } else {
         return result;
      }
   }

   public static int binding(JsonElement node) {
      if (node != null && node.isJsonPrimitive() && node.getAsJsonPrimitive().isNumber()) {
         int converted = (int)RecoveredMath.truncateLong(node.getAsDouble() + 0.5);
         return Integer.compareUnsigned(converted - 104, -103) >= 0 ? converted : 0;
      } else {
         return 0;
      }
   }

   private static boolean enabled(JsonElement node) {
      return node != null && node.isJsonPrimitive() && node.getAsJsonPrimitive().isBoolean() && node.getAsBoolean();
   }

   public static boolean hasRecords(ModuleManager manager, JsonObject root, RecoveredConfiguration.Scope scope) {
      for (Module module : manager.all()) {
         JsonElement node = root.get(module.getName());
         if (included(module, scope) && node != null && node.isJsonObject()) {
            return true;
         }
      }

      return false;
   }

   public static void apply(ModuleManager manager, JsonObject root, RecoveredConfiguration.Scope scope) {
      List<Runnable> settings = new ArrayList<>();
      List<Runnable> bindings = new ArrayList<>();
      List<Runnable> states = new ArrayList<>();
      for (Module module : manager.all()) {
         if (!included(module, scope)) continue;
         for (Setting<?> setting : module.getSettings()) settings.add(restoreSetting(setting));
         int key = module.getBinding();
         boolean hold = module.isHoldBinding();
         boolean enabled = manager.requestedEnabled(module);
         bindings.add(() -> {
            module.setHoldBinding(hold);
            manager.setBinding(module, key);
         });
         states.add(() -> manager.requestEnabled(module, enabled));
      }
      try {
         applyUnchecked(manager, root, scope);
      } catch (RuntimeException failure) {
         // No lifecycle batch is applied here. Restore values before restoring queued enable requests.
         restoreAll(settings, failure);
         restoreAll(bindings, failure);
         restoreAll(states, failure);
         throw failure;
      }
   }

   private static <T> Runnable restoreSetting(Setting<T> setting) {
      T value = setting.get();
      boolean configured = setting.isConfigured();
      return () -> {
         setting.set(value);
         if (!configured) setting.markUnconfigured();
      };
   }

   private static void restoreAll(List<Runnable> restorations, RuntimeException failure) {
      for (Runnable restore : restorations) {
         try { restore.run(); }
         catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
      }
   }

   private static void applyUnchecked(ModuleManager manager, JsonObject root, RecoveredConfiguration.Scope scope) {
      for (Module module : manager.all()) {
         if (included(module, scope)) {
            JsonElement node = root.get(module.getName());
            JsonObject record = node != null && node.isJsonObject() ? node.getAsJsonObject() : new JsonObject();
            int flags = module.descriptorFlags();
            if ((flags & 1) == 0) {
               manager.setBinding(module, binding(record.get("__bind")));
               module.setHoldBinding((flags & 8) == 0 && enabled(record.get("__hold")));
            }

            for (Setting<?> setting : module.getSettings()) {
               JsonElement value = record.get(setting.getKey());
               if (value == null) {
                  setting.restoreSaved();
               } else {
                  setting.restoreSaved();
                  SettingCodec.decode(setting, value);
               }
            }

            if ((flags & 2) == 0) {
               manager.requestEnabled(module, enabled(record.get("__on")));
            }
         }
      }
   }

   public static void resetProfile(ModuleManager manager) {
      apply(manager, new JsonObject(), RecoveredConfiguration.Scope.PROFILE);
   }

   public static final class ProfileBinding {
      public final String name;
      public final int binding;

      public ProfileBinding(String name, int binding) {
         this.name = Objects.requireNonNull(name);
         this.binding = binding;
      }
   }

   public static enum Scope {
      GLOBAL,
      PROFILE;
   }
}
