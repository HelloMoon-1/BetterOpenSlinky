package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class CriticalsModule extends EvidenceModule {
   private final WeaponsModule weapons;
   public final CriticalsModule.State state = new CriticalsModule.State();

   public CriticalsModule(WeaponsModule weapons) {
      super("criticals", "Criticals", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 1 || type == 3;
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 0) {
         boolean eligible = false;
         if (c.inputReady()) {
            if (this.state.chanceCounter == 0) {
               this.state.chanceCounter = 10;
               this.state.cachedChance = RecoveredRandom.SHARED.chance(this.number("chance"));
            }

            this.state.chanceCounter--;
            eligible = this.state.cachedChance
               && (!this.flag("require_mouse_pressed") || AttackInput.pressed(c))
               && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
               && c.player().ticksExisted - c.player().getLastAttackerTime() < 11;
         }

         if (this.choice("mode") == 0) {
            this.state.eligible = eligible;
            this.state.limit = RecoveredMath.truncateInt(this.number("max_delay")) & 65535;
            this.state.active = false;
         } else {
            this.state.eligible = false;
            MovingObjectPosition hit = c.minecraft().objectMouseOver;
            this.state.active = eligible
               && !c.player().onGround
               && !(c.player().motionY >= 0.0)
               && c.player().hurtTime != 10
               && hit != null
               && hit.typeOfHit == MovingObjectType.ENTITY;
         }
      } else if (e.type == 1) {
         Timer timer = MinecraftFields.timer(c.minecraft());
         if (this.state.active) {
            timer.timerSpeed = this.number("timer_speed");
            this.state.restore = true;
            this.state.accumulated = (double)(1.0F - this.number("timer_speed")) * (e.elapsedSeconds * 20.0) + this.state.accumulated;
            if (this.state.accumulated > (double)this.number("boost")) {
               this.state.accumulated = (double)this.number("boost");
            }
         } else {
            if (this.state.accumulated > 1.0) {
               int whole = !(this.state.accumulated >= 2.1474836E9F) && !(this.state.accumulated < -2.1474836E9F) && !Double.isNaN(this.state.accumulated)
                  ? (int)this.state.accumulated
                  : Integer.MIN_VALUE;
               timer.elapsedPartialTicks += (float)whole;
               this.state.accumulated -= (double)whole;
            }

            if (this.state.restore) {
               timer.timerSpeed = 1.0F;
               this.state.restore = false;
            }
         }
      } else {
         C03PacketPlayer packet = e.packet instanceof C03PacketPlayer ? (C03PacketPlayer)e.packet : null;
         this.state.packet(e, packet != null && packet.isMoving(), packet == null ? 0.0 : packet.getPositionY(), packet != null && packet.isOnGround());
      }
   }

   @Override
   protected strictfp synchronized void onDisable(ClientContext c) {
      if (this.state.restore) {
         MinecraftFields.timer(c.minecraft()).timerSpeed = 1.0F;
      }

      this.state.restore = false;
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.reset(c);
   }

   private strictfp void reset(ClientContext c) {
      if (this.state.restore) {
         MinecraftFields.timer(c.minecraft()).timerSpeed = 1.0F;
      }

      this.state.clear();
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         return StatusText.label(this.getName(), index);
      }
   }

   public static final class State {
      public int chanceCounter;
      public int limit;
      public int lastPacketId;
      public int packetCount;
      public boolean cachedChance;
      public boolean eligible;
      public boolean active;
      public boolean restore;
      public boolean falling;
      public double accumulated;
      public double previousY;

      public strictfp void packet(SemanticEvent e, boolean moving, double y, boolean ground) {
         if (moving && e.packetSequence > this.lastPacketId) {
            if (ground || this.previousY > y) {
               this.falling = !ground;
            }

            if (this.eligible && this.falling) {
               this.packetCount = this.packetCount + (this.limit * 52429 >>> 18);
               if (this.packetCount > this.limit) {
                  this.packetCount = this.limit;
               }
            } else {
               this.packetCount = 0;
            }

            this.previousY = y;
            this.lastPacketId = e.packetSequence;
         }

         if (e.packetAgeMillis < (long)this.packetCount) {
            e.dispatchMode = 1;
         }
      }

      public strictfp void clear() {
         this.chanceCounter = this.limit = this.lastPacketId = this.packetCount = 0;
         this.cachedChance = this.eligible = this.active = this.restore = this.falling = false;
         this.accumulated = this.previousY = 0.0;
      }
   }
}
