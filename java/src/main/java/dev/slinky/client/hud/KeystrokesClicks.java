package dev.slinky.client.hud;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;

/** Donor ClickUtils counters. Hook synthetic attack/use clicks separately from physical clicks. */
public final class KeystrokesClicks {
   private static final List<Long> left = new ArrayList<>(), attack = new ArrayList<>(), right = new ArrayList<>(), use = new ArrayList<>();
   private static final boolean[] hardware = new boolean[2];
   private static long lastAttackClick, lastUseClick;

   private KeystrokesClicks() { }

   public static synchronized void onMouseButton(int button, boolean pressed) {
      if (button < 0 || button > 1) return;
      hardware[button] = pressed;
      if (pressed) {
         if (button == 0) onLeftClick(); else onRightClick();
      }
   }

   /** Fallback for hosts without the optional raw MouseEvent hook. */
   public static synchronized void samplePhysical() {
      if (!Mouse.isCreated()) return;
      for (int button = 0; button < 2; ++button) {
         boolean down = Mouse.isButtonDown(button);
         if (down != hardware[button]) onMouseButton(button, down);
      }
   }

   public static synchronized void onLeftClick() { left.add(System.currentTimeMillis()); }
   public static synchronized void onRightClick() { right.add(System.currentTimeMillis()); }
   public static synchronized void onAttackClick() { lastAttackClick = System.currentTimeMillis(); attack.add(lastAttackClick); }
   public static synchronized void onUseClick() { lastUseClick = System.currentTimeMillis(); use.add(lastUseClick); }
   public static synchronized boolean leftDown() { return Mouse.isCreated() && Mouse.isButtonDown(0) || System.currentTimeMillis() - lastAttackClick < 50L; }
   public static synchronized boolean rightDown() { return Mouse.isCreated() && Mouse.isButtonDown(1) || System.currentTimeMillis() - lastUseClick < 50L; }
   public static synchronized int totalLeftCps() { prune(left); prune(attack); return left.size() + attack.size(); }
   public static synchronized int totalRightCps() { prune(right); prune(use); return right.size() + use.size(); }

   private static void prune(List<Long> clicks) {
      long before = System.currentTimeMillis() - 1000L;
      clicks.removeIf(time -> time < before);
   }
}
