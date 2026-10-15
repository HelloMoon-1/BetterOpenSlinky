package dev.slinky.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecoveredUiLayout {
   private static final Map<String, JsonObject> LAYOUTS = read();

   private RecoveredUiLayout() {
   }

   private static Map<String, JsonObject> read() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/ModuleUi.json");
      if (stream == null) {
         throw new IllegalStateException("Missing recovered UI layouts");
      } else {
         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, JsonObject> result = new LinkedHashMap<>();

            for (JsonElement row : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("modules")) {
               JsonObject layout = row.getAsJsonObject();
               String name = layout.get("module").getAsString();
               if (result.put(name, layout) != null) {
                  throw new IllegalStateException("Duplicate UI layout " + name);
               }
            }

            return Collections.unmodifiableMap(result);
         } catch (IOException var18) {
            throw new IllegalStateException(var18);
         }
      }
   }

   public static List<RecoveredUiLayout.Control> controls(Module module) {
      JsonObject layout = LAYOUTS.get(module.getName());
      if (layout == null) {
         List<RecoveredUiLayout.Control> result = new ArrayList<>();
         for (Setting<?> setting : module.getSettings()) {
            if (module.settingVisible(setting.getKey())) {
               result.add(new RecoveredUiLayout.Control(null, setting));
            }
         }
         return Collections.unmodifiableList(result);
      } else {
         JsonArray gates = layout.getAsJsonArray("gates");

         for (JsonElement row : layout.getAsJsonArray("variants")) {
            JsonObject variant = row.getAsJsonObject();
            JsonArray values = variant.getAsJsonArray("values");
            boolean match = true;

            for (int i = 0; i < gates.size(); i++) {
               Setting<?> setting = module.findSetting(gates.get(i).getAsString());
               int value;
               if (setting instanceof BooleanSetting) {
                  value = ((BooleanSetting)setting).isEnabled() ? 1 : 0;
               } else {
                  if (!(setting instanceof ChoiceSetting)) {
                     throw new IllegalStateException("Invalid recovered UI gate");
                  }

                  value = ((ChoiceSetting)setting).index();
               }

               if (value != values.get(i).getAsInt()) {
                  match = false;
                  break;
               }
            }

            if (match) {
               List<RecoveredUiLayout.Control> result = new ArrayList<>();

               for (JsonElement element : variant.getAsJsonArray("controls")) {
                  JsonObject control = element.getAsJsonObject();
                  if (control.has("section")) {
                     result.add(new RecoveredUiLayout.Control(control.get("section").getAsString(), null));
                  } else {
                     String key = control.get("key").getAsString();
                     Setting<?> settingx = module.findSetting(key);
                     if (settingx == null) {
                        throw new IllegalStateException("Missing recovered UI setting " + module.getName() + "." + key);
                     }

                     if (module.settingVisible(key)) {
                        result.add(new RecoveredUiLayout.Control(null, settingx));
                     }
                  }
               }

               for (Setting<?> setting : module.getSettings()) {
                  boolean present = false;
                  for (RecoveredUiLayout.Control control : result) {
                     if (control.setting == setting) { present = true; break; }
                  }
                  if (!present && !declared(layout, setting.getKey()) && module.settingVisible(setting.getKey())) {
                     result.add(new RecoveredUiLayout.Control(null, setting));
                  }
               }
               return Collections.unmodifiableList(result);
            }
         }

         throw new IllegalStateException("No recovered UI variant for " + module.getName());
      }
   }

   private static boolean declared(JsonObject layout, String key) {
      for (JsonElement element : layout.getAsJsonArray("variants")) {
         for (JsonElement row : element.getAsJsonObject().getAsJsonArray("controls")) {
            JsonObject control = row.getAsJsonObject();
            if (control.has("key") && control.get("key").getAsString().equals(key)) {
               return true;
            }
         }
      }
      return false;
   }

   public static final class Control {
      public final String section;
      public final Setting<?> setting;

      private Control(String section, Setting<?> setting) {
         this.section = section;
         this.setting = setting;
      }
   }
}
