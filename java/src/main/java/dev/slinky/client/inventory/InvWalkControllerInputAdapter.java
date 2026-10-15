package dev.slinky.client.inventory;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class InvWalkControllerInputAdapter {
   private InvWalkControllerInputAdapter() {
   }

   public static void drain() {
      while (Mouse.next()) {
      }

      while (Keyboard.next()) {
      }
   }
}
