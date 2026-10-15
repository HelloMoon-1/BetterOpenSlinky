package dev.slinky.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.slinky.config.ConfigurationJson;
import dev.slinky.config.RecoveredJsonRuntime;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NamedColorListSetting extends Setting<List<NamedColorListSetting.Entry>> {
   public NamedColorListSetting(String key, String label) {
      super(key, label, Collections.emptyList());
   }

   @Override
   public synchronized void restoreSaved() {
      this.set(Collections.emptyList());
   }

   @Override
   public String format() {
      return this.encode().toString();
   }

   public JsonArray encode() {
      JsonArray result = new JsonArray();

      for (NamedColorListSetting.Entry entry : this.get()) {
         JsonObject row = new JsonObject();
         JsonObject rgb = new JsonObject();
         rgb.addProperty("r", (double)entry.color.red);
         rgb.addProperty("g", (double)entry.color.green);
         rgb.addProperty("b", (double)entry.color.blue);
         row.addProperty("name", entry.name);
         row.add("color", rgb);
         result.add(row);
      }

      return result;
   }

   protected List<NamedColorListSetting.Entry> parse(String text) {
      JsonArray rows;
      try {
         rows = ConfigurationJson.parseValue(RecoveredJsonRuntime.cString(text)).getAsJsonArray();
      } catch (IOException var4) {
         throw new IllegalArgumentException("Invalid color-list bytes", var4);
      }

      return decodeRows(rows);
   }

   public void loadRows(JsonArray rows) {
      this.set(decodeRows(rows));
   }

   private static List<NamedColorListSetting.Entry> decodeRows(JsonArray rows) {
      List<NamedColorListSetting.Entry> result = new ArrayList<>();

      for (JsonElement element : rows) {
         if (element.isJsonObject()) {
            JsonObject row = element.getAsJsonObject();
            JsonElement name = row.get("name");
            if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
               ColorSetting.Value color = new ColorSetting.Value(1.0F, 1.0F, 1.0F);
               JsonElement value = row.get("color");
               if (value != null && value.isJsonObject()) {
                  JsonObject c = value.getAsJsonObject();

                  try {
                     for (String channel : new String[]{"r", "g", "b"}) {
                        JsonElement number = c.get(channel);
                        if (number == null || !number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) {
                           throw new IllegalArgumentException("Color channel is not a JSON number");
                        }
                     }

                     float r = c.get("r").getAsFloat();
                     float g = c.get("g").getAsFloat();
                     float b = c.get("b").getAsFloat();
                     if (r >= 0.0F && r <= 1.0F && g >= 0.0F && g <= 1.0F && b >= 0.0F && b <= 1.0F) {
                        color = new ColorSetting.Value(r, g, b);
                     }
                  } catch (RuntimeException var14) {
                  }
               }

               result.add(new NamedColorListSetting.Entry(name.getAsString(), color));
            }
         }
      }

      return result;
   }

   protected List<NamedColorListSetting.Entry> requireValid(List<NamedColorListSetting.Entry> value) {
      return Collections.unmodifiableList(new ArrayList<>(super.requireValid(value)));
   }

   public static final class Entry {
      public final String name;
      public final ColorSetting.Value color;

      public Entry(String name, ColorSetting.Value color) {
         this.name = name;
         this.color = color;
      }
   }
}
