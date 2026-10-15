package dev.slinky.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigStore {
   private final Path path;
   private List<RecoveredConfiguration.ProfileBinding> profileBindings = new ArrayList<>();

   public ConfigStore(Path path) {
      this.path = path;
   }

   public synchronized void load(ModuleManager manager) throws IOException {
      if (Files.isRegularFile(this.path)) {
         JsonObject root = read(this.path);
         JsonObject modules = object(root, "modules");
         if (modules == null) {
            try {
               RecoveredConfiguration.apply(manager, root, RecoveredConfiguration.Scope.GLOBAL);
               this.profileBindings = RecoveredConfiguration.profileBindings(root);
               Path checkpoint = this.checkpointPath();
               if (RecoveredConfiguration.hasRecords(manager, root, RecoveredConfiguration.Scope.PROFILE)) {
                  RecoveredConfiguration.apply(manager, root, RecoveredConfiguration.Scope.PROFILE);
               } else if (Files.isRegularFile(checkpoint)) {
                  RecoveredConfiguration.apply(manager, read(checkpoint), RecoveredConfiguration.Scope.PROFILE);
               }
            } catch (RuntimeException var12) {
               throw new IOException("Could not apply configuration", var12);
            }
         } else {
            for (Module module : manager.all()) {
               JsonObject record = object(modules, module.getId());
               if (record != null) {
                  JsonObject settings = object(record, "settings");
                  if (settings != null) {
                     for (Setting<?> setting : module.getSettings()) {
                        JsonElement value = settings.get(setting.getKey());
                        if (value != null) {
                           try {
                              SettingCodec.decode(setting, value);
                           } catch (RuntimeException var13) {
                           }
                        }
                     }
                  }

                  JsonElement enabled = record.get("enabled");
                  JsonElement hold = record.get("__hold");
                  module.setHoldBinding(hold != null && hold.isJsonPrimitive() && hold.getAsJsonPrimitive().isBoolean() && hold.getAsBoolean());
                  JsonElement binding = record.get("__bind");
                  if (binding != null && binding.isJsonPrimitive() && binding.getAsJsonPrimitive().isNumber()) {
                     manager.setBinding(module, binding.getAsInt());
                  }

                  if (enabled != null && enabled.isJsonPrimitive()) {
                     try {
                        manager.setEnabled(module, enabled.getAsBoolean(), false);
                     } catch (IllegalArgumentException var14) {
                        throw new IOException("Incomplete configuration for " + module.getName(), var14);
                     }
                  }
               }
            }
         }
      }
   }

   public synchronized void save(ModuleManager manager) throws IOException {
      this.write(this.path, RecoveredConfiguration.serialize(manager, RecoveredConfiguration.Scope.GLOBAL, this.profileBindings));
      this.write(this.checkpointPath(), RecoveredConfiguration.serialize(manager, RecoveredConfiguration.Scope.PROFILE, Collections.emptyList()));
   }

   public synchronized List<RecoveredConfiguration.ProfileBinding> profileBindings() {
      return Collections.unmodifiableList(new ArrayList<>(this.profileBindings));
   }

   public synchronized void profileBindings(List<RecoveredConfiguration.ProfileBinding> bindings) {
      this.profileBindings = new ArrayList<>(bindings);
   }

   public synchronized void loadProfile(ModuleManager manager) throws IOException {
      try {
         RecoveredConfiguration.apply(manager, read(this.path), RecoveredConfiguration.Scope.PROFILE);
      } catch (RuntimeException var3) {
         throw new IOException("Could not apply profile", var3);
      }
   }

   public static int importText(ModuleManager manager, byte[] text) throws IOException {
      JsonObject root = ConfigurationJson.parse(text);
      int records = 0;

      for (Module module : manager.all()) {
         if (object(root, module.getName()) != null) {
            records++;
         }
      }

      if (records == 0) {
         throw new IOException("No module records in the configuration");
      } else {
         try {
            if (RecoveredConfiguration.hasRecords(manager, root, RecoveredConfiguration.Scope.GLOBAL)) {
               RecoveredConfiguration.apply(manager, root, RecoveredConfiguration.Scope.GLOBAL);
            }

            RecoveredConfiguration.apply(manager, root, RecoveredConfiguration.Scope.PROFILE);
            return records;
         } catch (RuntimeException var6) {
            throw new IOException("Could not apply configuration", var6);
         }
      }
   }

   public synchronized void saveProfile(ModuleManager manager) throws IOException {
      this.saveProfile(manager, true);
   }

   public synchronized void saveProfile(ModuleManager manager, boolean replace) throws IOException {
      Path parent = this.path.toAbsolutePath().getParent();
      if (parent != null && parent.getParent() != null) {
         ensure(parent.getParent());
      }

      writeBytes(
         this.path, ConfigurationJson.print(RecoveredConfiguration.serialize(manager, RecoveredConfiguration.Scope.PROFILE, Collections.emptyList())), replace
      );
   }

   private Path checkpointPath() {
      return this.path.resolveSibling(this.path.getFileName().toString() + ".active");
   }

   private static JsonObject read(Path target) throws IOException {
      ConfigurationFiles.WidePath file = new ConfigurationFiles.WidePath();
      file.text = ConfigurationFiles.wide(target.toString());
      ConfigurationFiles.Text text = new ConfigurationFiles.Text();

      JsonObject var6;
      try (ConfigurationFiles.NioHost host = new ConfigurationFiles.NioHost(null)) {
         int status = ConfigurationFiles.readText(file, text, host);
         if (status != 0) {
            throw new ConfigurationFiles.Failure(status);
         }

         try {
            var6 = ConfigurationJson.parse(text.bytes);
         } finally {
            host.release(text.bytes);
         }
      }

      return var6;
   }

   private void write(Path target, JsonObject root) throws IOException {
      ensure(target.toAbsolutePath().getParent());
      writeBytes(target, ConfigurationJson.print(root), true);
   }

   private static void ensure(Path directory) throws IOException {
      ConfigurationFiles.WidePath path = new ConfigurationFiles.WidePath();
      path.text = ConfigurationFiles.wide(directory.toString());

      try (ConfigurationFiles.NioHost host = new ConfigurationFiles.NioHost(null)) {
         int status = ConfigurationFiles.ensureDirectory(path, host);
         if (status != 0) {
            throw new ConfigurationFiles.Failure(status);
         }
      }
   }

   private static void writeBytes(Path target, byte[] text, boolean replace) throws IOException {
      ConfigurationFiles.WidePath path = new ConfigurationFiles.WidePath();
      path.text = ConfigurationFiles.wide(target.toString());

      try (ConfigurationFiles.NioHost host = new ConfigurationFiles.NioHost(null)) {
         int status = ConfigurationFiles.writeText(path, text, replace ? 1 : 0, 0, host);
         if (status != 0) {
            throw new ConfigurationFiles.Failure(status);
         }
      }
   }

   private static JsonObject object(JsonObject parent, String key) {
      JsonElement element = parent.get(key);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }
}
