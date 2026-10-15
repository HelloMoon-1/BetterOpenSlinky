package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final strictfp class KeepSprintModule extends EvidenceModule {
   private final KeepSprintModule.State state = new KeepSprintModule.State();
   private final RecoveredRandom random;

   public KeepSprintModule() {
      this(RecoveredRandom.SHARED);
   }

   public KeepSprintModule(RecoveredRandom random) {
      super("keepsprint", "Keep Sprint", ModuleCategory.COMBAT);
      this.random = random;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 14 || type == 15 || type == 17;
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 17) {
         if (this.state.finish() && context.player() != null) {
            context.player().setSprinting(true);
         }
      } else if (event.active) {
         if (event.type == 14) {
            boolean accept = context.inputReady() && context.player().isSprinting() && this.random.chance(this.number("chance"));
            this.state
               .sample(true, accept, accept ? context.player().motionX : 0.0, accept ? context.player().motionY : 0.0, accept ? context.player().motionZ : 0.0);
         } else if (event.type == 15 && this.state.armed && context.player() != null) {
            if (this.flag("require_on_ground") && !context.player().onGround) {
               return;
            }

            if (context.player().ticksExisted != context.player().getLastAttackerTime()) {
               return;
            }

            if (!this.state.applyReductionPolicy(this.flag("allow_reduce"), event.reducing, this.number("motion_mult"))) {
               return;
            }

            context.player().motionX = this.state.x;
            context.player().motionY = this.state.y;
            context.player().motionZ = this.state.z;
         }
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext context) {
      this.state.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.state.clear();
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("motion_mult").isConfigured()) {
         return null;
      } else {
         float percent = this.number("motion_mult") * 100.0F;
         return StatusText.number(this.getName(), (double)percent);
      }
   }

   public static final class State {
      public boolean armed;
      public boolean pending;
      public double x;
      public double y;
      public double z;

      public strictfp void sample(boolean active, boolean accept, double x, double y, double z) {
         if (active) {
            this.armed = accept;
            if (accept) {
               this.x = x;
               this.y = y;
               this.z = z;
            }
         }
      }

      public strictfp void scale(float factor) {
         this.x *= (double)factor;
         this.z = (double)factor * this.z;
         this.pending = true;
      }

      public strictfp boolean applyReductionPolicy(boolean allowReduce, boolean reducing, float factor) {
         if (allowReduce && reducing) {
            return false;
         } else {
            this.scale(factor);
            return true;
         }
      }

      public strictfp boolean finish() {
         boolean result = this.pending;
         this.pending = false;
         return result;
      }

      public strictfp void clear() {
         this.armed = this.pending = false;
         this.x = this.y = this.z = 0.0;
      }
   }
}
