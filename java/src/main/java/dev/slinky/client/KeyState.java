package dev.slinky.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import dev.slinky.inject.ReflectionHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeyState {
   private static final Field PRESSED = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");
   private static final String[] NAMES = readNames();

   private KeyState() {
   }

   private static String[] readNames() {
      InputStream stream = Platform.resource("/assets/slinkysemantic/KeyNames.json");
      if (stream == null) {
         throw new IllegalStateException("Missing recovered key labels");
      } else {
         try (Reader reader = new InputStreamReader(stream, "UTF-8")) {
            JsonArray rows = new JsonParser().parse(reader).getAsJsonArray();
            String[] result = new String[rows.size()];

            for (int i = 0; i < result.length; i++) {
               result[i] = rows.get(i).getAsString();
            }

            return result;
         } catch (IOException var17) {
            throw new IllegalStateException(var17);
         }
      }
   }

   public static String displayName(int binding) {
      int index = binding - 1;
      return Integer.compareUnsigned(index, 102) > 0 ? NAMES[0] : NAMES[binding];
   }

   public static boolean physical(KeyBinding key) {
      int code = key.getKeyCode();
      return code < 0 ? Mouse.isButtonDown(code + 100) : code <= 256 && Keyboard.isKeyDown(code);
   }

   public static void restore(KeyBinding key) {
      set(key, physical(key));
   }

   public static void set(KeyBinding key, boolean value) {
      try {
         PRESSED.setBoolean(key, value);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("Cannot set key pressed state", var3);
      }
   }
}
