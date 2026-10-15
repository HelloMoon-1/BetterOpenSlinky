package dev.slinky.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NamedColorListSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.RangeSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
import java.util.ArrayList;
import java.util.List;

public final class SettingCodec {
   private SettingCodec() {
   }

   public static JsonElement encode(Setting<?> setting) {
      if (setting instanceof BooleanSetting) {
         return new JsonPrimitive(((BooleanSetting)setting).isEnabled());
      } else if (setting instanceof NumberSetting) {
         return new JsonPrimitive((double)((NumberSetting)setting).floatValue());
      } else if (setting instanceof RangeSetting) {
         RangeSetting.Value v = ((RangeSetting)setting).get();
         JsonObject range = new JsonObject();
         range.addProperty("min", v.lower);
         range.addProperty("max", v.upper);
         return range;
      } else if (setting instanceof ColorSetting) {
         ColorSetting.Value c = ((ColorSetting)setting).get();
         JsonObject rgb = new JsonObject();
         rgb.addProperty("r", (double)c.red);
         rgb.addProperty("g", (double)c.green);
         rgb.addProperty("b", (double)c.blue);
         return rgb;
      } else if (setting instanceof NamedColorListSetting) {
         return ((NamedColorListSetting)setting).encode();
      } else if (!(setting instanceof StringListSetting)) {
         return new JsonPrimitive(setting.format());
      } else {
         JsonArray rows = new JsonArray();

         for (String name : ((StringListSetting)setting).get()) {
            rows.add(new JsonPrimitive(name));
         }

         return rows;
      }
   }

   public static void decode(Setting<?> setting, JsonElement value) {
      if (setting instanceof BooleanSetting) {
         if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            ((BooleanSetting)setting).set(Boolean.valueOf(value.getAsBoolean()));
         }
      } else if (!(setting instanceof ChoiceSetting)) {
         if (setting instanceof NumberSetting) {
            if (numeric(value)) {
               ((NumberSetting)setting).loadNumber(value.getAsDouble());
            }
         } else if (setting instanceof RangeSetting && value.isJsonObject()) {
            RangeSetting range = (RangeSetting)setting;
            JsonObject row = value.getAsJsonObject();
            float low = numeric(row.get("min")) ? row.get("min").getAsFloat() : Float.NaN;
            float high = numeric(row.get("max")) ? row.get("max").getAsFloat() : Float.NaN;
            if (!Float.isNaN(low) && !Float.isNaN(high)) {
               if (high < low) {
                  low = high;
               }

               if ((float)range.minimum() > low) {
                  low = (float)range.minimum();
               } else if (low > (float)range.maximum()) {
                  low = (float)range.maximum();
               }

               if ((float)range.minimum() > high) {
                  high = (float)range.minimum();
               } else if (high > (float)range.maximum()) {
                  high = (float)range.maximum();
               }

               range.set(new RangeSetting.Value((double)low, (double)high));
            }
         } else if (setting instanceof ColorSetting && value.isJsonObject()) {
            JsonObject row = value.getAsJsonObject();
            if (numeric(row.get("r")) && numeric(row.get("g")) && numeric(row.get("b"))) {
               float r = row.get("r").getAsFloat();
               float g = row.get("g").getAsFloat();
               float b = row.get("b").getAsFloat();
               if (r >= 0.0F && r <= 1.0F && g >= 0.0F && g <= 1.0F && b >= 0.0F && b <= 1.0F) {
                  ((ColorSetting)setting).set(new ColorSetting.Value(r, g, b));
               }
            }
         } else if (setting instanceof NamedColorListSetting && value.isJsonArray()) {
            ((NamedColorListSetting)setting).loadRows(value.getAsJsonArray());
         } else if (setting instanceof StringListSetting && value.isJsonArray()) {
            List<String> list = new ArrayList<>();

            for (JsonElement element : value.getAsJsonArray()) {
               if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                  list.add(element.getAsString());
               }
            }

            ((StringListSetting)setting).set(list);
         }
      } else {
         ChoiceSetting choice = (ChoiceSetting)setting;
         if (numeric(value)) {
            double number = value.getAsDouble();
            int index = (int)number;
            if (Double.isFinite(number) && number == index && index >= 0 && index < choice.getOptions().size()) choice.set(index);
         } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String label = value.getAsString();
            // Profiles store labels, not indices. Keep the former HUD style readable after renaming.
            if ("hud_style".equals(choice.getKey()) && "Screenshot".equals(label)
               && choice.getOptions().contains("Drip Lite")) label = "Drip Lite";

            for (int index = 0; index < choice.getOptions().size(); index++) {
               if (choice.getOptions().get(index).equals(label)) {
                  choice.set(Integer.valueOf(index));
                  break;
               }
            }
         }
      }
   }

   private static boolean numeric(JsonElement node) {
      return node != null && node.isJsonPrimitive() && node.getAsJsonPrimitive().isNumber();
   }
}
