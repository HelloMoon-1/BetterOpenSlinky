package dev.slinky.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class StatusText {
   private static final Map<String, JsonObject> METADATA = read();
   private static volatile StatusText.Formatter formatter = (pattern, argument) -> String.format(Locale.ROOT, pattern, argument);

   private StatusText() {
   }

   private static Map<String, JsonObject> read() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/StatusMetadata.json");
      if (stream == null) {
         throw new IllegalStateException("Missing recovered status metadata");
      } else {
         Map<String, JsonObject> result = new LinkedHashMap<>();

         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
               JsonObject row = element.getAsJsonObject();
               String name = row.get("module").getAsString();
               if (result.put(name, row) != null) {
                  throw new IllegalStateException("Duplicate status " + name);
               }
            }
         } catch (IOException var18) {
            throw new IllegalStateException(var18);
         }

         return Collections.unmodifiableMap(result);
      }
   }

   public static StatusText.Formatter bind(StatusText.Formatter endpoint) {
      StatusText.Formatter previous = formatter;
      formatter = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static String label(String name, int index) {
      JsonObject row = METADATA.get(name);
      String label = row.getAsJsonArray("labels").get(index).getAsString();
      return render(row, 0, label);
   }

   public static String number(String name, double value) {
      return number(name, 0, value);
   }

   public static String number(String name, int formatIndex, double value) {
      return render(METADATA.get(name), formatIndex, value);
   }

   private static String render(JsonObject row, int formatIndex, Object argument) {
      String pattern = row.getAsJsonArray("formats").get(formatIndex).getAsJsonObject().get("value").getAsString();
      String value = formatter.format(pattern, argument);
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      return bytes.length < 32 ? value : new String(bytes, 0, 31, StandardCharsets.UTF_8);
   }

   public interface Formatter {
      String format(String var1, Object var2);
   }
}
