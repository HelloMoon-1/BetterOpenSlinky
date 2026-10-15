package dev.slinky.client;

import dev.slinky.module.ModuleManager;
import java.nio.charset.StandardCharsets;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ForgeInputAdapter {
   private ForgeInputAdapter() {
   }

   public static int keyboard(ModuleManager modules) {
      InputQuery.Request request = new InputQuery.Request(2, NativeKeyMap.keyboardId(Keyboard.getEventKey()), Keyboard.getEventKeyState());
      request.utf8 = String.valueOf(Keyboard.getEventCharacter()).getBytes(StandardCharsets.UTF_8);
      return modules.input().input(request);
   }

   public static int mouse(ModuleManager modules) {
      int button = Mouse.getEventButton();
      InputQuery.Request request;
      if (button >= 0) {
         request = new InputQuery.Request(2, NativeKeyMap.mouseId(button), Mouse.getEventButtonState());
      } else if (Mouse.getEventDWheel() != 0) {
         request = new InputQuery.Request(3, 0, false);
         request.wheel = (float)Mouse.getEventDWheel();
      } else {
         request = new InputQuery.Request(1, 0, false);
      }

      request.x = Mouse.getEventX();
      request.y = modules.context().minecraft().displayHeight - Mouse.getEventY() - 1;
      return modules.input().input(request);
   }
}
