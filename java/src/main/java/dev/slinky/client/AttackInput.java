package dev.slinky.client;

import org.lwjgl.input.Mouse;

public final class AttackInput {
   private AttackInput() {
   }

   public static int button(int attackCode) {
      return attackCode == -99 ? 1 : 0;
   }

   public static boolean pressed(ClientContext context) {
      return Mouse.isButtonDown(button(context.minecraft().gameSettings.keyBindAttack.getKeyCode()));
   }
}
