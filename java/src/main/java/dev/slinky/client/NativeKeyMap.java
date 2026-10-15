package dev.slinky.client;

import org.lwjgl.input.Keyboard;

public final class NativeKeyMap {
   private static final int[] CODES = createCodes();

   private NativeKeyMap() {
   }

   private static int[] createCodes() {
      int[] result = new int[104];

      for (int id = 1; id <= 5; id++) {
         result[id] = id - 101;
      }

      for (int digit = 0; digit < 10; digit++) {
         result[6 + digit] = Keyboard.getKeyIndex(Integer.toString(digit));
         result[16 + digit] = Keyboard.getKeyIndex("NUMPAD" + digit);
      }

      for (int letter = 0; letter < 26; letter++) {
         result[26 + letter] = Keyboard.getKeyIndex(Character.toString((char)(65 + letter)));
      }

      String[] punctuation = new String[]{
         "SEMICOLON", "EQUALS", "COMMA", "MINUS", "PERIOD", "SLASH", "GRAVE", "LBRACKET", "BACKSLASH", "RBRACKET", "APOSTROPHE"
      };

      for (int index = 0; index < punctuation.length; index++) {
         result[52 + index] = Keyboard.getKeyIndex(punctuation[index]);
      }

      String[] arrows = new String[]{"LEFT", "UP", "RIGHT", "DOWN"};

      for (int index = 0; index < arrows.length; index++) {
         result[63 + index] = Keyboard.getKeyIndex(arrows[index]);
      }

      for (int function = 1; function <= 16; function++) {
         result[66 + function] = Keyboard.getKeyIndex("F" + function);
      }

      String[] controls = new String[]{
         "BACK",
         "TAB",
         "RETURN",
         "CAPITAL",
         "ESCAPE",
         "SPACE",
         "PRIOR",
         "NEXT",
         "END",
         "HOME",
         "SYSRQ",
         "INSERT",
         "DELETE",
         "NUMLOCK",
         "SCROLL",
         "LSHIFT",
         "RSHIFT",
         "LCONTROL",
         "RCONTROL",
         "LMENU",
         "RMENU"
      };

      for (int index = 0; index < controls.length; index++) {
         result[83 + index] = Keyboard.getKeyIndex(controls[index]);
      }

      return result;
   }

   public static boolean supported(int nativeId) {
      return nativeId > 0 && nativeId < CODES.length && CODES[nativeId] != 0;
   }

   public static int gameCode(int nativeId) {
      return supported(nativeId) ? CODES[nativeId] : 0;
   }

   public static int keyboardId(int code) {
      if (code <= 0) {
         return 0;
      } else if (code == 156) {
         return 85;
      } else {
         for (int id = 6; id < CODES.length; id++) {
            if (CODES[id] == code) {
               return id;
            }
         }

         return 0;
      }
   }

   public static int mouseId(int button) {
      return button >= 0 && button < 5 ? button + 1 : 0;
   }

   public static int capturedId(int nativeId) {
      return nativeId != 1 && nativeId != 87 ? nativeId : 0;
   }
}
