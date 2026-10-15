package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.packet.PacketKinds;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final strictfp class KnockbackDelayModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private final RecoveredRandom.Smooth chance = new RecoveredRandom.Smooth();
   public final KnockbackDelayModule.State state = new KnockbackDelayModule.State();

   public KnockbackDelayModule(WeaponsModule weapons) {
      super("knockbackdelay", "Knockback Delay", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 2 || type == 18;
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 2) {
         this.state
            .packet(
               e,
               e.packet instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity)e.packet).getEntityID() == this.state.entityId,
               PacketKinds.realtimeDamage(e.packet)
            );
      } else if (c.player() != null) {
         this.state.entityId = c.player().getEntityId();
         this.state.ground = this.flag("require_air") && c.player().onGround;
         boolean allowed = GameHooks.target() != null
            && !(GameHooks.targetDistanceSquared() > (double)(this.number("range") * this.number("range")))
            && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
            && (!this.flag("require_mouse") || AttackInput.pressed(c))
            && (!this.flag("require_target") || TimingAccess.facingPlayer(c));
         if (allowed) {
            double probability = (double)this.number("chance");
            allowed = probability > 0.999
               || this.chance.gate((float)(-0.025 + probability), (float)(probability + 0.025), 60.0, RecoveredRandom.now(), RecoveredRandom.SHARED);
         }

         this.state.allowed = allowed;
         this.state.delay = RecoveredMath.truncateInt(this.number("max_delay")) & 65535;
      }
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.state.clear();
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("max_delay").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("max_delay"));
   }

   public static final class State {
      public int entityId;
      public int delay;
      public int savedSequence;
      public boolean ground;
      public boolean allowed;
      public boolean active;

      public strictfp void packet(SemanticEvent e, boolean matches, boolean immediate) {
         if (e.dispatchMode == 0) {
            if (this.ground) {
               this.active = e.packetAgeMillis == 0L && this.allowed;
            } else if (this.allowed && matches) {
               this.active = true;
            }

            if (matches && this.active) {
               if (this.savedSequence != 0 && this.savedSequence != e.packetSequence) {
                  this.reset();
                  return;
               }

               if (this.savedSequence == 0) {
                  this.savedSequence = e.packetSequence;
               }
            } else if (!this.active) {
               return;
            }

            if (e.packetAgeMillis > (long)this.delay) {
               this.reset();
            } else {
               e.dispatchMode = immediate ? 3 : 1;
            }
         }
      }

      private strictfp void reset() {
         this.active = this.allowed = false;
         this.savedSequence = 0;
      }

      public strictfp void clear() {
         this.reset();
         this.entityId = this.delay = 0;
         this.ground = false;
      }
   }
}
