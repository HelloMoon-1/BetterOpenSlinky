package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.packet.PacketKinds;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final strictfp class FakeLagModule extends EvidenceModule {
   private final WeaponsModule weapons;
   public final FakeLagModule.State state = new FakeLagModule.State();

   public FakeLagModule(WeaponsModule weapons) {
      super("fakelag", "Fake Lag", ModuleCategory.UTILITY);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 2 || type == 3;
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 0) {
         this.state
            .update(
               this.choice("mode"),
               this.number("inbound"),
               this.number("outbound"),
               this.flag("realtime_damage"),
               c.world() != null,
               c.inputReady(),
               this.flag("require_ingame"),
               this.flag("require_weapon"),
               this.weapons.isWeapon(c.player()),
               this.flag("require_attack"),
               c.player() == null ? 0 : c.player().getLastAttackerTime()
            );
      } else {
         this.state.packet(e, PacketKinds.realtimeDamage(e.packet));
      }
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
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
      public int lastState;
      public int pulseTimer;
      public int inboundValue;
      public int outboundValue;
      public int inboundAmount;
      public int outboundAmount;
      public boolean realtime;
      public boolean inboundBlock;
      public boolean outboundBlock;

      public strictfp void update(
         int mode,
         float inbound,
         float outbound,
         boolean realtime,
         boolean world,
         boolean ready,
         boolean requireGame,
         boolean requireWeapon,
         boolean weapon,
         boolean requireAttack,
         int sampled
      ) {
         if (this.pulseTimer > 0) {
            this.pulseTimer--;
         }

         this.realtime = realtime;
         if (mode == 1) {
            int previous = this.inboundValue;
            this.inboundValue = previous < 1 ? RecoveredMath.truncateInt((float)previous + inbound) : previous - 50;
            this.inboundBlock = previous >= 1 && Integer.compareUnsigned(previous, 51) < 0;
            previous = this.outboundValue;
            this.outboundValue = previous < 1 ? RecoveredMath.truncateInt((float)previous + outbound) : previous - 50;
            this.outboundBlock = previous >= 1 && Integer.compareUnsigned(previous, 51) < 0;
         }

         if (world && (!requireGame || ready)) {
            boolean send;
            if (requireWeapon && !weapon) {
               this.pulseTimer = 0;
               send = false;
            } else {
               send = !requireAttack || this.pulseTimer != 0;
            }

            if (sampled != this.lastState) {
               this.pulseTimer = 20;
            }

            this.lastState = sampled;
            if (send) {
               this.inboundAmount = RecoveredMath.truncateInt(inbound) & 65535;
               this.outboundAmount = RecoveredMath.truncateInt(outbound) & 65535;
               return;
            }
         } else {
            this.pulseTimer = 0;
         }

         this.inboundAmount = this.outboundAmount = 0;
      }

      public strictfp void packet(SemanticEvent e, boolean damage) {
         if (e.type == 2 && this.realtime && damage) {
            e.dispatchMode = 3;
         } else if (e.type == 2 ? !this.inboundBlock : !this.outboundBlock) {
            e.packetAgeMillis = e.packetAgeMillis - (e.type == 2 ? (long)this.inboundAmount : (long)this.outboundAmount);
            if (e.packetAgeMillis < 0L) {
               e.packetAgeMillis = 0L;
               e.dispatchMode = 1;
            }
         }
      }

      public strictfp void clear() {
         this.lastState = this.pulseTimer = this.inboundValue = this.outboundValue = this.inboundAmount = this.outboundAmount = 0;
         this.realtime = this.inboundBlock = this.outboundBlock = false;
      }
   }
}
