package dev.slinky.client.numeric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class NumericConstants {
   private static final Map<Integer, Byte> BYTES = new HashMap<>();

   private NumericConstants() {
   }

   private static long word(int address, int length) {
      long result = 0L;

      for (int offset = 0; offset < length; offset++) {
         Byte value = BYTES.get(address + offset);
         if (value == null) {
            throw new IllegalArgumentException("Unrecovered numerical operand " + Integer.toHexString(address + offset));
         }

         result |= (long)(value & 255) << offset * 8;
      }

      return result;
   }

   public static int shortAt(int address) {
      return (int)word(address, 2);
   }

   public static int intAt(int address) {
      return (int)word(address, 4);
   }

   public static long longAt(int address) {
      return word(address, 8);
   }

   public static float floatAt(int address) {
      return Float.intBitsToFloat(intAt(address));
   }

   public static double doubleAt(int address) {
      return Double.longBitsToDouble(longAt(address));
   }

   static {
      try (Reader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/NumericConstants.json"), "UTF-8")) {
         for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
            JsonObject row = element.getAsJsonObject();
            int address = row.get("address").getAsInt();
            String hex = row.get("bytes").getAsString();

            for (int offset = 0; offset < hex.length() / 2; offset++) {
               BYTES.put(address + offset, (byte)Integer.parseInt(hex.substring(offset * 2, offset * 2 + 2), 16));
            }
         }
      } catch (IOException var18) {
         throw new ExceptionInInitializerError(var18);
      }
   }
}
