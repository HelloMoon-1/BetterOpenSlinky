package dev.slinky.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.client.FixedWidth;
import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ProfileMath {
   private static final Map<Integer, byte[]> LABELS = labels();
   private static final ProfileMath.Formatter JAVA_FORMAT = (pattern, value, capacity) -> {
      String format = new String(pattern, StandardCharsets.UTF_8).replace("%lld", "%d");
      byte[] result = String.format(Locale.ROOT, format, value).getBytes(StandardCharsets.UTF_8);
      return Arrays.copyOf(result, Math.min(result.length, capacity - 1));
   };

   private ProfileMath() {
   }

   private static Map<Integer, byte[]> labels() {
      Map<Integer, byte[]> result = new HashMap<>();
      InputStream stream = Platform.resource("/assets/slinkysemantic/ProfileLabels.json");
      if (stream == null) {
         throw new IllegalStateException("Missing immutable profile labels");
      } else {
         try (Reader reader = new InputStreamReader(stream, "UTF-8")) {
            for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
               JsonObject row = element.getAsJsonObject();
               String hex = row.get("bytes").getAsString();
               byte[] bytes = new byte[hex.length() / 2];

               for (int i = 0; i < bytes.length; i++) {
                  bytes[i] = (byte)Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
               }

               result.put(row.get("rva").getAsInt(), bytes);
            }

            return result;
         } catch (IOException var20) {
            throw new ExceptionInInitializerError(var20);
         }
      }
   }

   public static long ageSeconds(long nowFileTime, long modifiedFileTime) {
      return Long.compareUnsigned(modifiedFileTime - 1L, nowFileTime) >= 0
         ? -1L
         : FixedWidth.unsignedMultiplyHigh(nowFileTime - modifiedFileTime, -2972493582642298179L) >>> 23;
   }

   public static long days(long ageSeconds) {
      long high = FixedWidth.signedMultiplyHigh(ageSeconds, 1749024623285053783L);
      return (high >> 13) + (high >>> 63);
   }

   public static byte[] dateBytes(long ageSeconds, ProfileMath.Formatter formatter) {
      byte[] buffer = new byte[32];
      if (ageSeconds == -1L) {
         System.arraycopy(LABELS.get(1310590), 0, buffer, 0, 16);
         byte[] end = new byte[]{116, 105, 111, 110, 32, 100, 97, 116};
         System.arraycopy(end, 0, buffer, 16, end.length);
      } else {
         long day = days(ageSeconds);
         if (day == 1L) {
            System.arraycopy(LABELS.get(1310641), 0, buffer, 0, 16);
            buffer[16] = 103;
            buffer[17] = 111;
            buffer[18] = 46;
         } else if (day == 0L) {
            System.arraycopy(LABELS.get(1310665), 0, buffer, 0, 16);
         } else {
            byte[] text = formatter.format(cString(LABELS.get(1310617)), day, 25);
            System.arraycopy(text, 0, buffer, 0, Math.min(text.length, 24));
         }
      }

      return buffer;
   }

   public static String date(long ageSeconds) {
      return new String(cString(dateBytes(ageSeconds, JAVA_FORMAT)), StandardCharsets.UTF_8);
   }

   private static byte[] cString(byte[] text) {
      int length = 0;

      while (length < text.length && text[length] != 0) {
         length++;
      }

      return Arrays.copyOf(text, length);
   }

   public static int compareNames(String left, String right) {
      byte[] a = left.getBytes(StandardCharsets.UTF_8);
      byte[] b = right.getBytes(StandardCharsets.UTF_8);
      int i = 0;

      while (true) {
         int x = i < a.length ? a[i] & 255 : 0;
         int y = i < b.length ? b[i] & 255 : 0;
         if (x != y) {
            return x - y;
         }

         if (x == 0) {
            return 0;
         }

         i++;
      }
   }

   public interface Formatter {
      byte[] format(byte[] var1, long var2, int var4);
   }
}
