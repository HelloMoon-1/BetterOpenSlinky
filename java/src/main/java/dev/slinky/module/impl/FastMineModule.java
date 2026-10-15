package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.multiplayer.PlayerControllerMP;

public final strictfp class FastMineModule extends EvidenceModule {
   private final FastMineModule.State state = new FastMineModule.State();

   public FastMineModule() {
      super("fastmine", "Fast Mine", ModuleCategory.WORLD);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 18;
   }

   public static strictfp float acceleratedProgress(float progress, float speed) {
      float firstArgument = 1.0F / progress;
      float secondArgument = (float)(1.0 / (double)speed / (double)progress);
      long first = RecoveredMath.truncateLong(RecoveredMath.ceiling(firstArgument));
      long second = RecoveredMath.truncateLong(RecoveredMath.ceiling(secondArgument));
      long steps = first - second + 1L & 4294967295L;
      return (float)steps * progress;
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      PlayerControllerMP controller = context.minecraft().playerController;
      if (event.type == 0) {
         if (controller == null) {
            this.state.update(false, 0, 0.0F, 0.0F, false);
            return;
         }

         int before = MinecraftFields.blockHitDelay(controller);
         int after = this.state.update(true, before, MinecraftFields.blockDamage(controller), this.number("break_delay"), this.flag("decr_block_hit_delay"));
         if (after != before) {
            MinecraftFields.setBlockHitDelay(controller, after);
         }
      } else if (controller != null) {
         float progress = MinecraftFields.blockDamage(controller);
         if (progress != 0.0F && this.state.savedProgress == 0.0F) {
            MinecraftFields.setBlockDamage(controller, acceleratedProgress(progress, this.number("speed")));
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
      return !this.findSetting("speed").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("speed"));
   }

   public static final class State {
      public int lastDelay;
      public float savedProgress;

      public strictfp int update(boolean controllerPresent, int current, float progress, float limit, boolean decrement) {
         int result = current;
         if (controllerPresent) {
            this.savedProgress = progress;
            if ((float)current > limit) {
               result = RecoveredMath.truncateInt(limit);
            } else if (current > 0 && decrement && current == this.lastDelay) {
               result = current - 1;
            }
         }

         this.lastDelay = controllerPresent ? current : 0;
         return result;
      }

      public strictfp void clear() {
         this.lastDelay = 0;
         this.savedProgress = 0.0F;
      }
   }
}
