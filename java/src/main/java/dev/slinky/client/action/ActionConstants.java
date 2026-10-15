package dev.slinky.client.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class ActionConstants {
   private static final Map<Integer, Byte> BYTES = new HashMap<>();

   private ActionConstants() {
   }

   private static long word(int address, int length) {
      long result = 0L;

      for (int i = 0; i < length; i++) {
         Byte b = BYTES.get(address + i);
         if (b == null) {
            throw new IllegalArgumentException("Unrecovered action operand " + Integer.toHexString(address + i));
         }

         result |= (long)(b & 255) << 8 * i;
      }

      return result;
   }

   public static byte byteAt(int a) {
      return (byte)((int)word(a, 1));
   }

   public static int shortAt(int a) {
      return (int)word(a, 2);
   }

   public static int intAt(int a) {
      return (int)word(a, 4);
   }

   public static long longAt(int a) {
      return word(a, 8);
   }

   public static float f(int a) {
      return Float.intBitsToFloat(intAt(a));
   }

   public static double d(int a) {
      return Double.longBitsToDouble(longAt(a));
   }

   static {
      try (Reader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/ActionConstants.json"), "UTF-8")) {
         for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
            JsonObject row = element.getAsJsonObject();
            int address = row.get("address").getAsInt();
            String hex = row.get("bytes").getAsString();

            for (int i = 0; i < hex.length() / 2; i++) {
               BYTES.put(address + i, (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16));
            }
         }
      } catch (IOException var18) {
         throw new ExceptionInInitializerError(var18);
      }
   }
}
