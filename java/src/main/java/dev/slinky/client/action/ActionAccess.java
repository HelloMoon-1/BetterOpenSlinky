package dev.slinky.client.action;

import dev.slinky.client.ClientContext;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.hud.KeystrokesClicks;
import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ActionAccess {
   private static final Field PRESSES = ReflectionHelper.findField(KeyBinding.class, "pressTime", "field_151474_i");
   private static final Field HOVER = ReflectionHelper.findField(GuiContainer.class, "theSlot", "field_147006_u");
   private static final Field USE_COUNT = ReflectionHelper.findField(EntityPlayer.class, "itemInUseCount", "field_71072_f");
   private static volatile ActionAccess.MouseHost mouse = new ActionAccess.MouseHost() {
      @Override
      public boolean physical(int button) {
         return Mouse.isButtonDown(button == 1 ? 0 : 1);
      }

      @Override
      public boolean shift() {
         return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
      }

      @Override
      public void emit(ClientContext c, int button, boolean down) {
         if (c.minecraft().currentScreen instanceof GuiContainer) {
            if (down) {
               GuiContainer screen = (GuiContainer)c.minecraft().currentScreen;
               Slot slot = ActionAccess.hovered(screen);
               if (slot != null) {
                  InventoryActions.click(screen, slot.slotNumber, button == 1 ? 0 : 1, this.shift() ? 1 : 0);
               }
            }
         } else {
            int code = button == 1 ? -100 : -99;
            KeyBinding.setKeyBindState(code, down);
            if (down) {
               KeyBinding.onTick(code);
               if (button == 1) { KeystrokesClicks.onAttackClick(); }
               else { KeystrokesClicks.onUseClick(); }
            }
         }
      }
   };

   private ActionAccess() {
   }

   public static int presses(KeyBinding key) {
      try {
         return PRESSES.getInt(key);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static void increment(KeyBinding key) {
      InventoryActions.pressCount(key, presses(key) + 1);
   }

   public static Slot hovered(GuiContainer screen) {
      try {
         return (Slot)HOVER.get(screen);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static void useCount(EntityPlayer p, int value) {
      try {
         USE_COUNT.setInt(p, value);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException(var3);
      }
   }

   public static boolean canBlock(ItemStack stack) {
      int id = InventoryActions.type(stack) - 267;
      return Integer.compareUnsigned(id, 16) <= 0 && (66083 >>> (id & 31) & 1) != 0;
   }

   public static boolean isBlock(ItemStack stack) {
      if (stack == null) {
         return false;
      } else {
         Item item = stack.getItem();
         if (item == null) {
            return false;
         } else {
            Block block = Block.getBlockFromItem(item);
            return block != null && Block.getIdFromBlock(block) != 0;
         }
      }
   }

   public static boolean registeredAir(IBlockAccess world, BlockPos position) {
      return Block.getIdFromBlock(world.getBlockState(position).getBlock()) == 0;
   }

   public static Vec3 adjacentPosition(MovingObjectPosition hit) {
      BlockPos block = hit.getBlockPos();
      double x = (double)block.getX();
      double y = (double)block.getY();
      double z = (double)block.getZ();
      EnumFacing face = hit.sideHit;
      return new Vec3(x + (double)face.getFrontOffsetX(), y + (double)face.getFrontOffsetY(), (double)face.getFrontOffsetZ() + z);
   }

   public static int hitKind(MovingObjectPosition hit) {
      return hit != null && hit.typeOfHit != MovingObjectType.MISS ? (hit.typeOfHit == MovingObjectType.BLOCK ? 1 : 2) : 0;
   }

   public static ActionAccess.MouseHost mouse() {
      return mouse;
   }

   public static ActionAccess.MouseHost bindMouse(ActionAccess.MouseHost endpoint) {
      ActionAccess.MouseHost previous = mouse;
      mouse = Objects.requireNonNull(endpoint);
      return previous;
   }

   public interface MouseHost {
      boolean physical(int var1);

      boolean shift();

      void emit(ClientContext var1, int var2, boolean var3);
   }
}
