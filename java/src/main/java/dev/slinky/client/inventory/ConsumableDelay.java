package dev.slinky.client.inventory;

import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;

public final strictfp class ConsumableDelay {
   private final RecoveredRandom.Smooth first = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth gate = new RecoveredRandom.Smooth();

   public strictfp float switching(float base, double now, RecoveredRandom random) {
      float result = RotationMath.max(0.0F, base - this.first.sample(0.0F, 0.5F, 5.0, now, random));
      if (this.gate.gate(0.01F, 0.03F, 30.0, now, random)) {
         result += random.interval(0.5F, 1.0F);
      }

      return result;
   }

   public strictfp float soup(float base, float scale, double now, RecoveredRandom random) {
      float a = this.first.sample(0.0F, 0.5F, 60.0, now, random);
      float b = this.first.sample(0.0F, 0.5F, 5.0, now, random);
      float sum = b + a;
      if (this.gate.gate(0.03F, 0.06F, 60.0, now, random)) {
         sum += random.interval(0.5F, 2.5F);
      }

      return sum * scale + base;
   }
}
