package dev.slinky.client.inventory;

import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class InventoryActions {
   private static final Field PRESS_COUNT = ReflectionHelper.findField(KeyBinding.class, "pressTime", "field_151474_i");
   private static final Method CLICK = ReflectionHelper.findMethod(
      GuiContainer.class, null, new String[]{"handleMouseClick", "func_146984_a"}, Slot.class, int.class, int.class, int.class
   );
   private static final Method KEY = ReflectionHelper.findMethod(GuiScreen.class, null, new String[]{"keyTyped", "func_73869_a"}, char.class, int.class);
   private static final Field CHEST_INVENTORY = ReflectionHelper.findField(GuiChest.class, "lowerChestInventory", "field_147015_w");
   private static final Field INVENTORY_TITLE = ReflectionHelper.findField(InventoryBasic.class, "inventoryTitle", "field_70483_a");

   private InventoryActions() {
   }

   public static int type(ItemStack stack) {
      return stack != null && stack.getItem() != null ? Item.getIdFromItem(stack.getItem()) : 0;
   }

   public static void press(KeyBinding key) {
      try {
         PRESS_COUNT.setInt(key, PRESS_COUNT.getInt(key) + 1);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException("Cannot increment key press count", var2);
      }
   }

   public static void pressCount(KeyBinding key, int count) {
      try {
         PRESS_COUNT.setInt(key, count);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("Cannot set key press count", var3);
      }
   }

   public static void click(GuiContainer screen, int slot, int button, int mode) {
      invoke(CLICK, screen, null, slot, button, mode);
   }

   public static void escape(GuiScreen screen) {
      invoke(KEY, screen, '\u0000', 1);
   }

   public static String chestTitle(GuiChest screen) {
      try {
         IInventory inventory = (IInventory)CHEST_INVENTORY.get(screen);
         return inventory instanceof InventoryBasic ? (String)INVENTORY_TITLE.get(inventory) : null;
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException("Cannot read chest title", var2);
      }
   }

   private static void invoke(Method method, Object target, Object... args) {
      try {
         method.invoke(target, args);
      } catch (ReflectiveOperationException var4) {
         throw new IllegalStateException("Minecraft inventory action failed", var4);
      }
   }

   public static int truncateDouble(double value) {
      return !Double.isNaN(value) && !(value >= 2.1474836E9F) && !(value < -2.1474836E9F) ? (int)value : Integer.MIN_VALUE;
   }

   public static long truncateDoubleLong(double value) {
      return !Double.isNaN(value) && !(value >= 9.223372E18F) && !(value < -9.223372E18F) ? (long)value : Long.MIN_VALUE;
   }

   public static float[] grid(int slot) {
      long index = Integer.toUnsignedLong(slot);
      if (index <= 8L) {
         return new float[]{(float)Integer.toUnsignedLong(slot - 5), 1.0F};
      } else {
         int quotient = (int)(index * 954437177L >>> 33);
         return new float[]{(float)Integer.toUnsignedLong(quotient + 3), (float)(slot - quotient * 9)};
      }
   }
}
