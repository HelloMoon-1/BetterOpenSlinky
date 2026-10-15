package dev.slinky.client.rotation;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatNumeric;
import dev.slinky.client.numeric.NumericConstants;
import java.security.SecureRandom;

public final strictfp class RecoveredRandom {
   public static final RecoveredRandom SHARED = fresh();
   private int a;
   private int b;
   private int c;
   private int d;

   public RecoveredRandom(int a, int b, int c, int d) {
      if ((a | b | c | d) == 0) {
         throw new IllegalArgumentException("zero random state");
      } else {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
      }
   }

   private static strictfp RecoveredRandom fresh() {
      SecureRandom seed = new SecureRandom();
      int a = seed.nextInt();
      int b = seed.nextInt();
      int c = seed.nextInt();
      int d = seed.nextInt();
      if ((a | b | c | d) == 0) {
         d = 1;
      }

      return new RecoveredRandom(a, b, c, d);
   }

   public static strictfp double now() {
      return (double)System.nanoTime() / 1.0E9;
   }

   private strictfp int next() {
      int sum = this.d + this.a;
      int rotated = Integer.rotateLeft(this.b ^ this.d, 11);
      int nextB = this.c ^ this.a ^ this.b;
      int nextA = this.a ^ this.b ^ this.d;
      int nextC = this.c ^ this.a ^ this.b << 9;
      this.a = nextA;
      this.b = nextB;
      this.c = nextC;
      this.d = rotated;
      return sum;
   }

   public strictfp float interval(float low, float high) {
      float width = high - low;
      float value = (float)(this.next() >>> 8);
      value *= 5.9604645E-8F;
      value *= width;
      return value + low;
   }

   public strictfp boolean chance(float threshold) {
      return threshold > this.interval(0.0F, 1.0F);
   }

   public strictfp int[] state() {
      return new int[]{this.a, this.b, this.c, this.d};
   }

   public static final class Gaussian {
      private boolean valid;
      private float cached;

      public strictfp float sample(RecoveredRandom random) {
         if (this.valid) {
            this.valid = false;
            return this.cached;
         } else {
            float x;
            float y;
            float radius;
            do {
               float value = (float)(random.next() >>> 8) * 5.9604645E-8F;
               x = value + value + -1.0F;
               value = (float)(random.next() >>> 8) * 5.9604645E-8F;
               y = value + value + -1.0F;
               radius = RecoveredMath.floatDistance(x, y);
            } while (radius >= 1.0F || radius == 0.0F);

            float ratio = FloatNumeric.log(radius) * -2.0F / radius;
            float factor = FloatNumeric.sqrt(ratio);
            this.cached = y * factor;
            this.valid = true;
            return x * factor;
         }
      }
   }

   public static final class Segment {
      private double elapsed;
      private float left;
      private float right;

      public strictfp float sample(float low, float high, double duration, double delta, RecoveredRandom random) {
         this.elapsed += delta;
         if (this.elapsed > duration) {
            this.elapsed = 0.0;
            if (this.left == 0.0F && this.right == 0.0F) {
               do {
                  this.left = draw(random, 1304864, 1304868);
                  this.right = draw(random, 1304864, 1304868);
               } while (NumericConstants.doubleAt(1304896) > this.maskedDifference(1304880));
            } else {
               this.left = this.right;

               do {
                  this.right = draw(random, 1304912, 1304916);
               } while (NumericConstants.doubleAt(1304944) > this.maskedDifference(1304928));
            }
         }

         double phase = this.elapsed / duration;
         double curve = (double)(this.right - this.left) * phase + (double)this.left;
         return (float)((double)(high - low) * curve + (double)low);
      }

      private strictfp double maskedDifference(int mask) {
         return (double)Float.intBitsToFloat(Float.floatToRawIntBits(this.left - this.right) & NumericConstants.intAt(mask));
      }

      private static strictfp float draw(RecoveredRandom random, int upper, int scale) {
         float value = (float)(random.next() >>> 8);
         value *= NumericConstants.floatAt(scale);
         value *= NumericConstants.floatAt(upper);
         return value + 0.0F;
      }
   }

   public static final class Smooth {
      private double timestamp;
      private float left;
      private float right;

      public strictfp float sample(float low, float high, double duration, double now, RecoveredRandom random) {
         if (now > this.timestamp + duration) {
            this.timestamp = now;
            if (this.left == 0.0F && this.right == 0.0F) {
               do {
                  this.left = random.interval(0.0F, 1.0F);
                  this.right = random.interval(0.0F, 1.0F);
               } while (0.2 > (double)RotationMath.abs(this.left - this.right));
            } else {
               this.left = this.right;

               do {
                  this.right = random.interval(0.0F, 1.0F);
               } while (0.2 > (double)RotationMath.abs(this.left - this.right));
            }
         }

         double phase = (now - this.timestamp) / duration;
         double curve = (double)(this.right - this.left) * phase + (double)this.left;
         return (float)((double)(high - low) * curve + (double)low);
      }

      public strictfp boolean gate(float low, float high, double duration, double now, RecoveredRandom random) {
         float comparison = random.interval(0.0F, 1.0F);
         return this.sample(low, high, duration, now, random) > comparison;
      }
   }
}
