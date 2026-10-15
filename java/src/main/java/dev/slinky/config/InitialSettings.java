package dev.slinky.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class InitialSettings {
   private static final JsonObject MODULES = read();

   private static JsonObject read() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/InitialSettings.json");
      if (stream == null) {
         throw new IllegalStateException("Missing initial configuration");
      } else {
         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("modules");
         } catch (IOException var15) {
            throw new IllegalStateException(var15);
         }
      }
   }

   public static int applyMissing(ModuleManager manager) {
      int applied = 0;

      for (Module module : manager.all()) {
         JsonObject values = MODULES.getAsJsonObject(module.getName());
         if (values != null) {
            for (Setting<?> setting : module.getSettings()) {
               if (!setting.isConfigured()) {
                  JsonObject row = values.getAsJsonObject(setting.getKey());
                  if (row == null) {
                     throw new IllegalStateException("Missing initial value: " + module.getName() + "." + setting.getKey());
                  }

                  SettingCodec.decode(setting, row.get("value"));
                  if (!setting.isConfigured()) {
                     throw new IllegalStateException("Invalid initial value: " + module.getName() + "." + setting.getKey());
                  }

                  setting.saveCurrent();
                  applied++;
               }
            }
         }
      }

      return applied;
   }

   private InitialSettings() {
   }
}
