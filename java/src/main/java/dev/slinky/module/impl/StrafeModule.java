package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final strictfp class StrafeModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private boolean sampledSprint;

   public StrafeModule(WeaponsModule weapons) {
      super("strafe", "Strafe", ModuleCategory.MOVEMENT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 21 || type == 22;
   }

   public static strictfp double[] redirect(double x, double z, float yaw, float forward, float sideways, float amount, boolean boost) {
      double speed = RotationMath.hypot(x, z);
      if (boost) {
         speed += 0.2;
      }

      double turn = sideways < 0.0F ? -1.0 : 0.0;
      if (sideways > 0.0F) {
         turn = 1.0;
      }

      double heading = (double)yaw;
      if (forward > 0.0F) {
         turn *= -45.0;
      } else if (forward < 0.0F) {
         heading += 180.0;
         turn *= 45.0;
      } else {
         turn *= -90.0;
      }

      float radians = (float)((turn + heading) * Math.PI / 180.0);
      float sine = Float.intBitsToFloat(Float.floatToRawIntBits(RotationMath.sine(radians)) ^ Integer.MIN_VALUE);
      double nextX = x + ((double)sine * speed - x) * (double)amount;
      double nextZ = z + ((double)RotationMath.cosine(radians) * speed - z) * (double)amount;
      return new double[]{nextX, nextZ};
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready()) {
         if (event.type == 21) {
            this.sampledSprint = context.player().isSprinting();
         } else if (event.type == 22 && (event.forward != 0.0F || event.sideways != 0.0F)) {
            if (!this.flag("require_weapon") || this.weapons.isAllowed(context.player().getHeldItem(), context.player().inventory.currentItem)) {
               if (!((float)context.player().hurtTime > this.number("max_hurt_time"))) {
                  boolean ground = context.player().onGround;
                  float amount = this.number(!ground ? "strength_air" : (event.jump ? "strength_jump" : "strength_ground"));
                  if (amount != 0.0F) {
                     boolean var10006 = ground && event.jump && !this.sampledSprint;
                     double[] motion = redirect(
                        context.player().motionX, context.player().motionZ, context.player().rotationYaw, event.forward, event.sideways, amount, var10006
                     );
                     context.player().motionX = motion[0];
                     context.player().motionZ = motion[1];
                  }
               }
            }
         }
      }
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.sampledSprint = false;
   }
}
