package dev.slinky.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NamedColorListSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.RangeSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
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

public final class EvidenceCatalog {
   private static final Map<String, JsonObject> MODULES = read();
   private static final Map<String, JsonObject> DESCRIPTORS = readDescriptors();

   private EvidenceCatalog() {
   }

   private static Map<String, JsonObject> readDescriptors() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/ModuleDescriptors.json");
      if (stream == null) {
         throw new IllegalStateException("Missing module role metadata");
      } else {
         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, JsonObject> result = new LinkedHashMap<>();

            for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
               JsonObject row = element.getAsJsonObject();
               result.put(row.get("module").getAsString(), row);
            }

            return Collections.unmodifiableMap(result);
         } catch (IOException var17) {
            throw new IllegalStateException(var17);
         }
      }
   }

   public static int descriptorFlags(String name) {
      JsonObject descriptor = DESCRIPTORS.get(name);
      JsonArray observations = descriptor == null ? null : descriptor.getAsJsonArray("observations");
      return observations == null || observations.size() == 0 ? 0 : observations.get(0).getAsJsonObject().get("flags").getAsInt();
   }

   public static int descriptorCategory(String name) {
      JsonObject descriptor = DESCRIPTORS.get(name);
      JsonArray observations = descriptor == null ? null : descriptor.getAsJsonArray("observations");
      return observations == null || observations.size() == 0 ? -1 : observations.get(0).getAsJsonObject().get("category").getAsInt();
   }

   private static Map<String, JsonObject> read() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/MetadataCatalog.json");
      if (stream == null) {
         throw new IllegalStateException("Missing recovered setting catalogue");
      } else {
         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            Map<String, JsonObject> modules = new LinkedHashMap<>();

            for (JsonElement row : root.getAsJsonArray("modules")) {
               JsonObject m = row.getAsJsonObject();
               if (modules.put(m.get("module").getAsString(), m) != null) {
                  throw new IllegalStateException("Duplicate module descriptor");
               }
            }

            return Collections.unmodifiableMap(modules);
         } catch (IOException var18) {
            throw new IllegalStateException(var18);
         }
      }
   }

   /** Optional metadata lookup for source-authored modules/settings without recovered evidence. */
   public static JsonObject moduleOrNull(String name) {
      return MODULES.get(name);
   }

   public static JsonObject module(String name) {
      JsonObject record = MODULES.get(name);
      if (record == null) {
         throw new IllegalArgumentException("Unknown recovered module " + name);
      } else {
         return record;
      }
   }

   public static List<String> moduleNames() {
      return Collections.unmodifiableList(new ArrayList<>(MODULES.keySet()));
   }

   public static List<Setting<?>> settings(String name) {
      List<Setting<?>> result = new ArrayList<>();

      for (JsonElement element : module(name).getAsJsonArray("settings")) {
         JsonObject row = element.getAsJsonObject();
         String key = row.get("key").getAsString();
         String label = text(row, "label", text(row, "format", key));
         String type = row.get("type").getAsString();
         double min = observation(row, "minimum_observation", 0.0);
         double max = observation(row, "maximum_observation", 0.0);
         Setting<?> setting;
         if (type.equals("boolean")) {
            setting = new BooleanSetting(key, label, false);
         } else if (type.equals("number")) {
            setting = new NumberSetting(key, label, min, min, max, 0.0);
         } else if (type.equals("range")) {
            setting = new RangeSetting(key, label, min, min, min, max);
         } else if (!type.equals("choice")) {
            if (type.equals("list")) {
               setting = (Setting<?>)(row.get("kind_observation").getAsInt() == 1
                  ? new NamedColorListSetting(key, label)
                  : new StringListSetting(key, label, Collections.emptyList()));
            } else {
               if (!type.equals("color")) {
                  throw new IllegalStateException("Unmodelled setting type " + type);
               }

               setting = new ColorSetting(key, label);
            }
         } else {
            JsonArray choices = row.getAsJsonArray("options");
            String[] options = new String[choices.size()];

            for (int i = 0; i < choices.size(); i++) {
               JsonObject choice = choices.get(i).getAsJsonObject();
               if (choice.get("value").getAsInt() != i) {
                  throw new IllegalStateException("Non-contiguous choice");
               }

               options[i] = choice.get("label").getAsString();
            }

            setting = new ChoiceSetting(key, label, 0, options);
         }

         setting.markUnconfigured();
         result.add(setting);
      }

      return result;
   }

   private static String text(JsonObject object, String key, String fallback) {
      JsonElement value = object.get(key);
      return value != null && !value.isJsonNull() ? value.getAsString() : fallback;
   }

   private static double observation(JsonObject row, String key, double fallback) {
      JsonElement value = row.get(key);
      return value != null && !value.isJsonNull() ? value.getAsJsonObject().get("value").getAsDouble() : fallback;
   }

   public static int priority(String name, int event) {
      JsonObject record = MODULES.get(name);
      if (record == null) {
         return 0;
      }
      for (JsonElement descriptor : record.getAsJsonArray("descriptor_observations")) {
         for (JsonElement callback : descriptor.getAsJsonObject().getAsJsonArray("callbacks")) {
            JsonObject c = callback.getAsJsonObject();
            if (c.get("event").getAsInt() == event) {
               return c.get("priority_signed").getAsInt();
            }
         }
      }

      return 0;
   }
}
