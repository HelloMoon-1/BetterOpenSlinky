package dev.slinky.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderConstants {
   private static final Map<Integer, byte[]> SPANS = new LinkedHashMap<>();
   private static final long CAPTURE_BASE = captureBase();
   private static final ConcurrentHashMap<Integer, String> TEXTS;

   private static long captureBase() {
      try (Reader r = new InputStreamReader(Platform.resource("/assets/slinkysemantic/MetadataCatalog.json"), "UTF-8")) {
         return Long.decode(new JsonParser().parse(r).getAsJsonObject().get("runtime_base").getAsString());
      } catch (IOException var15) {
         throw new ExceptionInInitializerError(var15);
      }
   }

   private RenderConstants() {
   }

   private static ByteBuffer at(int address, int count) {
      for (Entry<Integer, byte[]> row : SPANS.entrySet()) {
         int offset = address - row.getKey();
         if (offset >= 0 && offset + count <= row.getValue().length) {
            return ByteBuffer.wrap(row.getValue(), offset, count).order(ByteOrder.LITTLE_ENDIAN);
         }
      }

      throw new IllegalArgumentException("Uncaptured immutable drawing operand 0x" + Integer.toHexString(address));
   }

   public static int i(int address) {
      return at(address, 4).getInt();
   }

   public static long l(int address) {
      return at(address, 8).getLong();
   }

   public static float f(int address) {
      return at(address, 4).getFloat();
   }

   public static double d(int address) {
      return at(address, 8).getDouble();
   }

   public static int u16(int address) {
      return at(address, 2).getShort() & 65535;
   }

   public static String text(int address) {
      String cached = TEXTS.get(address);
      if (cached == null) {
         TEXTS.put(address, cached = decode(address));
      }

      return cached;
   }

   private static String decode(int address) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      for (int n = 0; n < 256; n++) {
         int b = at(address + n, 1).get() & 255;
         if (b == 0) {
            break;
         }

         bytes.write(b);
      }

      try {
         return new String(bytes.toByteArray(), "UTF-8");
      } catch (UnsupportedEncodingException var4) {
         throw new AssertionError(var4);
      }
   }

   public static String pointerText(int address) {
      long p = l(address);
      return p == 0L ? null : text((int)(p - CAPTURE_BASE));
   }

   static {
      try (Reader r = new InputStreamReader(Platform.resource("/assets/slinkysemantic/RenderConstants.json"), "UTF-8")) {
         for (JsonElement e : new JsonParser().parse(r).getAsJsonArray()) {
            JsonObject row = e.getAsJsonObject();
            String hex = row.get("bytes").getAsString();
            byte[] bytes = new byte[hex.length() / 2];

            for (int i = 0; i < bytes.length; i++) {
               bytes[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }

            SPANS.put(row.get("address").getAsInt(), bytes);
         }
      } catch (IOException var18) {
         throw new ExceptionInInitializerError(var18);
      }

      TEXTS = new ConcurrentHashMap<>();
   }
}
