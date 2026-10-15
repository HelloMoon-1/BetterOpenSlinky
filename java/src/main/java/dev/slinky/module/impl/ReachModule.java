package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final class ReachModule extends EvidenceModule {
   private final ReachModule.State state = new ReachModule.State();

   public ReachModule() {
      super("reach", "Reach", ModuleCategory.COMBAT);
   }

   @Override
   public boolean listens(int type) {
      return type == 8;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready()
         && this.state
            .update(this.flag("require_sprint"), context.player().isSprinting(), context.player().getLastAttackerTime(), this.number("activate_time"))) {
         event.enabled = true;
         event.distance = (double)this.number("distance");
      }
   }

   private void reset() {
      this.state.countdown = 0;
      this.state.previousAttackerTime = 0;
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.reset();
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.reset();
   }

   @Override
   public String statusText() {
      return !this.findSetting("distance").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("distance"));
   }

   public static final class State {
      public int countdown;
      public int previousAttackerTime;

      public boolean update(boolean requireSprint, boolean sprinting, int attackerTime, float activation) {
         int count = this.countdown;
         if (count > 0) {
            this.countdown = --count;
         }

         if (requireSprint) {
            if (!sprinting) {
               return false;
            }

            count = this.countdown;
         }

         if (count == 0) {
            if (attackerTime == this.previousAttackerTime) {
               count = this.countdown;
            } else {
               this.previousAttackerTime = attackerTime;
               this.countdown = 9;
               count = 9;
            }
         }

         return activation > (float)count;
      }
   }
}
