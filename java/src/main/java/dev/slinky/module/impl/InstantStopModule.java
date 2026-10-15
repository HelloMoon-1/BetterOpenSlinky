package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.settings.GameSettings;

public final strictfp class InstantStopModule extends EvidenceModule {
   private int phase;

   public InstantStopModule() {
      super("instantstop", "Instant Stop", ModuleCategory.MOVEMENT);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 16;
   }

   public static strictfp int choose(double origin, double right, double straight, double left) {
      double minimum = RotationMath.min(right, origin);
      int index = origin > right ? -1 : 999;
      double previousMinimum = minimum;
      minimum = RotationMath.min(straight, previousMinimum);
      if (previousMinimum > straight) {
         index = 0;
      }

      if (minimum > left) {
         index = 1;
      }

      return index;
   }

   public static strictfp int stoppingDirection(double x, double z, float yaw, float speed) {
      double[] scores = new double[3];

      for (int i = 0; i < 3; i++) {
         double[] movement = RotationMath.movement(yaw, -1.0F, (float)(i - 1), speed);
         double sumX = x + movement[0];
         double sumZ = movement[1] + z;
         scores[i] = sumX * sumX + sumZ * sumZ;
      }

      return choose(x * x + z * z, scores[0], scores[1], scores[2]);
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 16) {
         if (context.ready() && context.minecraft().inGameHasFocus) {
            GameSettings keys = context.minecraft().gameSettings;
            if (this.phase == 2) {
               this.restore(context);
            } else if (this.phase != 1) {
               if (keys.keyBindForward.isKeyDown()) {
                  this.phase = 1;
               }
            } else if (!keys.keyBindForward.isKeyDown() && context.player().onGround) {
               float speed = context.player().getAIMoveSpeed();
               if (context.player().isSprinting()) {
                  speed = (float)((double)speed / 1.3);
               }

               int direction = stoppingDirection(context.player().motionX, context.player().motionZ, context.player().rotationYaw, speed);
               this.phase = 0;
               if (direction != 999) {
                  KeyState.set(keys.keyBindBack, true);
                  KeyState.set(keys.keyBindLeft, direction == 1);
                  KeyState.set(keys.keyBindRight, direction == -1);
                  this.phase = 2;
               }
            }
         } else {
            this.restore(context);
         }
      }
   }

   private strictfp void restore(ClientContext context) {
      if (this.phase == 2) {
         GameSettings keys = context.minecraft().gameSettings;
         KeyState.restore(keys.keyBindBack);
         KeyState.restore(keys.keyBindLeft);
         KeyState.restore(keys.keyBindRight);
      }

      this.phase = 0;
   }

   @Override
   protected strictfp void onDisable(ClientContext context) {
      this.restore(context);
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.restore(context);
   }
}
