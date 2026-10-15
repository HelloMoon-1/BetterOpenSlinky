package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final strictfp class FastAccelModule extends EvidenceModule {
   private final WeaponsModule weapons;

   public FastAccelModule(WeaponsModule weapons) {
      super("fastaccel", "Fast Accel", ModuleCategory.MOVEMENT);
      this.weapons = weapons;
   }

   public static strictfp double limit(float sideways, float forward, float factor, boolean sprintJump) {
      float length = RecoveredMath.floatDistance(sideways, forward);
      float denominator = !Float.isNaN(length) && !(length >= 1.0F) ? 1.0F : length;
      float scale = factor / denominator;
      float x = sideways * scale;
      float z = scale * forward;
      double step = (double)RecoveredMath.floatDistance(x, z);
      double limit = 0.0 + step;

      for (int i = 0; i < 19; i++) {
         limit = limit * 0.546 + step;
      }

      if (sprintJump) {
         limit += 0.2;
      }

      return limit;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 22;
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready() && (event.forward != 0.0F || event.sideways != 0.0F)) {
         if (!this.flag("require_weapon") || this.weapons.isAllowed(context.player().getHeldItem(), context.player().inventory.currentItem)) {
            if (!this.flag("require_not_damaged") || context.player().hurtTime <= 0) {
               double speed = RotationMath.hypot(context.player().motionX, context.player().motionZ);
               if (speed != 0.0 && context.player().onGround) {
                  double limit = limit(event.sideways, event.forward, event.factor, event.jump && context.player().isSprinting());
                  if (!(speed > limit)) {
                     double accelerated = speed + (limit - speed) * (double)this.number("acceleration");
                     context.player().motionX = context.player().motionX / speed * accelerated;
                     context.player().motionZ = context.player().motionZ / speed * accelerated;
                  }
               }
            }
         }
      }
   }
}
