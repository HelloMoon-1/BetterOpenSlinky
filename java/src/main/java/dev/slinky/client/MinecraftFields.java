package dev.slinky.client;

import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Timer;

public final class MinecraftFields {
   private static final Field TIMER = field(Minecraft.class, "timer", "field_71428_T");
   private static final Field JUMP_TICKS = field(EntityLivingBase.class, "jumpTicks", "field_70773_bE");
   private static final Field POTION_DURATION = field(PotionEffect.class, "duration", "field_76460_b");
   private static final Field LEFT_CLICK_COUNTER = field(Minecraft.class, "leftClickCounter", "field_71429_W");
   private static final Field BLOCK_HIT_DELAY = field(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
   private static final Field BLOCK_DAMAGE = field(PlayerControllerMP.class, "curBlockDamageMP", "field_78770_f");
   private static final Field BREAKING = field(PlayerControllerMP.class, "isHittingBlock", "field_78778_j");

   public static boolean breaking(PlayerControllerMP controller) {
      return (Boolean)get(BREAKING, controller);
   }

   public static void setBreaking(PlayerControllerMP controller, boolean value) {
      try {
         BREAKING.setBoolean(controller, value);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException(var3);
      }
   }

   private MinecraftFields() {
   }

   public static Timer timer(Minecraft minecraft) {
      return (Timer)get(TIMER, minecraft);
   }

   public static void setJumpTicks(EntityLivingBase entity, int ticks) {
      setInt(JUMP_TICKS, entity, ticks);
   }

   public static int jumpTicks(EntityLivingBase entity) {
      return getInt(JUMP_TICKS, entity);
   }

   public static void setPotionDuration(PotionEffect effect, int ticks) {
      setInt(POTION_DURATION, effect, ticks);
   }

   public static int leftClickCounter(Minecraft minecraft) {
      return getInt(LEFT_CLICK_COUNTER, minecraft);
   }

   public static void setLeftClickCounter(Minecraft minecraft, int ticks) {
      setInt(LEFT_CLICK_COUNTER, minecraft, ticks);
   }

   public static int blockHitDelay(PlayerControllerMP controller) {
      return getInt(BLOCK_HIT_DELAY, controller);
   }

   public static void setBlockHitDelay(PlayerControllerMP controller, int ticks) {
      setInt(BLOCK_HIT_DELAY, controller, ticks);
   }

   public static float blockDamage(PlayerControllerMP controller) {
      try {
         return BLOCK_DAMAGE.getFloat(controller);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException("cannot read " + BLOCK_DAMAGE.getName(), var2);
      }
   }

   public static void setBlockDamage(PlayerControllerMP controller, float value) {
      try {
         BLOCK_DAMAGE.setFloat(controller, value);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("cannot write " + BLOCK_DAMAGE.getName(), var3);
      }
   }

   private static Field field(Class<?> type, String mcpName, String srgName) {
      Field field = ReflectionHelper.findField(type, mcpName, srgName);
      field.setAccessible(true);
      return field;
   }

   private static Object get(Field field, Object target) {
      try {
         return field.get(target);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("cannot read " + field.getName(), var3);
      }
   }

   private static void setInt(Field field, Object target, int value) {
      try {
         field.setInt(target, value);
      } catch (IllegalAccessException var4) {
         throw new IllegalStateException("cannot write " + field.getName(), var4);
      }
   }

   private static int getInt(Field field, Object target) {
      try {
         return field.getInt(target);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("cannot read " + field.getName(), var3);
      }
   }
}
