package dev.slinky.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/**
 * The HUD's last locally attacked entity, not an aim-assist/nearest-player target.
 * A local attack is an attempt; this deliberately does not claim a server-confirmed hit.
 * Call recordAttack on the client thread at the existing local attack hook.
 */
public final class HudCombatTarget {
   public static final long HOLD_MILLIS = 250L;
   public static final long FADE_MILLIS = 400L;
   private static World world;
   private static Entity entity;
   private static long observedAt = -1L;
   private static long fadingAt = -1L;
   private static String name = "";
   private static float health;
   private static double ratio;

   private HudCombatTarget() {
   }

   public static void recordAttack(Minecraft game, Entity attacked) {
      if (game == null || game.theWorld == null || game.thePlayer == null || attacked == null
         || attacked == game.thePlayer || attacked.worldObj != game.theWorld) {
         return;
      }
      world = game.theWorld;
      entity = attacked;
      observedAt = System.currentTimeMillis();
      fadingAt = -1L;
      sample();
   }

   public static Snapshot snapshot(Minecraft game) {
      if (game == null || game.thePlayer == null || game.theWorld == null || game.theWorld != world) {
         clear();
         return null;
      }
      if (observedAt < 0L) {
         return null;
      }
      long now = System.currentTimeMillis();
      long age = Math.max(0L, now - observedAt);
      if (age >= HOLD_MILLIS + FADE_MILLIS) {
         clear();
         return null;
      }
      // The donor samples the still-selected entity during hold/fade, retaining the
      // last name/ratio when selection is lost. Do not substitute another entity ID.
      if (entity != null && world.getEntityByID(entity.getEntityId()) == entity) {
         sample();
      } else {
         entity = null;
      }
      if (age >= HOLD_MILLIS && fadingAt < 0L) {
         fadingAt = now;
      }
      int alpha = 255;
      if (fadingAt >= 0L) {
         float t = Math.min(1.0F, Math.max(0.0F, (float)(now - fadingAt) / (float)FADE_MILLIS));
         float eased = t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
         alpha -= Math.round(eased * 255.0F);
      }
      return new Snapshot(entity, name, health, ratio, alpha);
   }

   private static void sample() {
      name = entity.getDisplayName().getUnformattedText();
      // KillAura.Target uses 20/20 for non-living targets and includes absorption
      // for living targets. Preserve those data semantics, not invented stats.
      health = entity instanceof EntityLivingBase
         ? ((EntityLivingBase)entity).getHealth() + ((EntityLivingBase)entity).getAbsorptionAmount() : 20.0F;
      float maximum = entity instanceof EntityLivingBase ? ((EntityLivingBase)entity).getMaxHealth() : 20.0F;
      if (!Float.isFinite(health)) {
         health = 0.0F;
      }
      ratio = maximum > 0.0F && Float.isFinite(maximum) ? Math.max(0.0F, Math.min(health / maximum, 1.0F)) : 0.0;
   }

   public static void clear() {
      world = null;
      entity = null;
      observedAt = fadingAt = -1L;
      name = "";
      health = 0.0F;
      ratio = 0.0;
   }

   public static final class Snapshot {
      public final Entity entity;
      public final String name;
      public final float health;
      public final double ratio;
      public final int alpha;

      private Snapshot(Entity entity, String name, float health, double ratio, int alpha) {
         this.entity = entity;
         this.name = name;
         this.health = health;
         this.ratio = ratio;
         this.alpha = alpha;
      }
   }
}
