package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class KnockbackDisplaceModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private final ModuleManager modules;
   private boolean packetPending;
   private boolean active;
   private boolean wasActive;
   private boolean inputSeen;
   private int inputTicks;
   private int activationTicks;
   private int movementDirection;
   private float retainedYaw;
   private float retainedPitch;

   public KnockbackDisplaceModule(WeaponsModule weapons, ModuleManager modules) {
      super("knockbackdisplace", "Knockback Displace", ModuleCategory.COMBAT);
      this.weapons = weapons;
      this.modules = modules;
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 3 || t == 13 || t == 14 || t == 15 || t == 18;
   }

   public static strictfp int direction(int mode, int movement, boolean random) {
      switch (mode) {
         case 0:
            return -1;
         case 1:
            return 1;
         case 2:
            return (random ? 1 : 0) - 1 | 1;
         case 3:
            return movement;
         case 4:
            return -movement;
         default:
            throw new IllegalArgumentException("direction");
      }
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 3) {
         if (this.packetPending) {
            e.dispatchMode = 1;
         }
      } else if (e.type == 13) {
         if (this.active || this.wasActive) {
            e.remapMovement = false;
         }
      } else if (e.type == 15) {
         if (e.active && Integer.compareUnsigned(this.inputTicks, 10) >= 0 && this.inputSeen) {
            this.inputTicks = 0;
         }
      } else if (e.type == 18) {
         this.update(c);
      } else if (this.active) {
         e.cancelled = true;
      } else if (e.active && c.ready()) {
         MovingObjectPosition hit = c.minecraft().objectMouseOver;
         if (hit != null
            && hit.typeOfHit == MovingObjectType.ENTITY
            && hit.entityHit instanceof EntityOtherPlayerMP
            && RelationshipService.hostile(hit.entityHit)) {
            this.inputSeen = true;
            if (!this.wasActive
               && Integer.compareUnsigned(this.inputTicks, 9) >= 0
               && !(this.number("cooldown") > (float)Integer.toUnsignedLong(this.activationTicks))) {
               Module timer = this.modules.find("timerrange");
               if (!(timer instanceof TimerRangeModule) || !((TimerRangeModule)timer).frozenOrAdvancing()) {
                  if ((!this.flag("require_no_kb") || !e.reducing) && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))) {
                     boolean enchant = (this.weapons.classificationFlags(c.player()) & 2) != 0;
                     if (enchant || !this.flag("require_kb_enchant") && c.player().isSprinting()) {
                        this.retainedYaw = c.player().rotationYaw;
                        this.retainedPitch = c.player().rotationPitch;
                        float targetYaw = (float)TimingAccess.serverYaw(hit.entityHit);
                        if (!this.flag("require_facing_away") || 100.0F > RotationMath.abs(RotationMath.wrap(targetYaw - this.retainedYaw))) {
                           int mode = this.choice("direction");
                           int sign = direction(mode, this.movementDirection, mode == 2 && RecoveredRandom.SHARED.chance(0.5F));
                           if (sign != 0 && RotationService.claim(200)) {
                              RotationService.retain(c, false, this.retainedYaw, this.retainedPitch);
                              this.active = true;
                              boolean positive = RecoveredRandom.SHARED.chance(0.5F);
                              float yaw = (this.number("angle") - RecoveredRandom.SHARED.interval(0.0F, 10.0F)) * (float)sign;
                              float pitch = RecoveredRandom.SHARED.interval(10.0F, 20.0F);
                              if (!positive) {
                                 pitch = Float.intBitsToFloat(Float.floatToRawIntBits(pitch) ^ Integer.MIN_VALUE);
                              }

                              RotationService.add(yaw, pitch);
                              RotationService.applyPending(c);
                              if (this.flag("hide_rotation")) {
                                 this.packetPending = true;
                              }

                              e.cancelled = true;
                              this.activationTicks = 0;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private strictfp void update(ClientContext c) {
      this.inputTicks++;
      this.activationTicks++;
      this.wasActive = this.active;
      this.inputSeen = false;
      if (!c.inputReady()) {
         this.active = false;
      } else {
         float movement = c.player().moveStrafing;
         int sign = (movement > 0.0F ? 1 : 0) - (movement < 0.0F ? 1 : 0);
         if (!this.flag("use_last_strafe") || sign != 0) {
            this.movementDirection = sign;
         }

         if (this.active) {
            if (RotationService.claim(200)) {
               float yaw = this.retainedYaw - (c.player().rotationYaw + RotationService.pendingYaw());
               float pitch = this.retainedPitch - (c.player().rotationPitch + RotationService.pendingPitch());
               RotationService.add(yaw, pitch);
               RotationService.applyPending(c);
               MinecraftFields.setLeftClickCounter(c.minecraft(), 0);
               KeyBinding.onTick(c.minecraft().gameSettings.keyBindAttack.getKeyCode());
            }

            this.active = false;
         }
      }

      this.packetPending = this.wasActive && this.flag("hide_rotation");
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.clear();
   }

   private strictfp void clear() {
      this.packetPending = this.active = this.wasActive = this.inputSeen = false;
      this.inputTicks = this.activationTicks = this.movementDirection = 0;
      this.retainedYaw = this.retainedPitch = 0.0F;
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("angle").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("angle"));
   }
}
