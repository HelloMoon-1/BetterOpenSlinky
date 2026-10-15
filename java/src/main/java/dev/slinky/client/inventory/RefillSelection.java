package dev.slinky.client.inventory;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.rotation.RotationMath;

public final strictfp class RefillSelection {
   private RefillSelection() {
   }

   public static strictfp int classify(int type, int damage, boolean soups, boolean pots, boolean nonHealth) {
      if (type == 0) {
         return 0;
      } else if (type == 282) {
         return soups ? 2 : 1;
      } else if (type == 373 && pots && (damage & 16384) != 0) {
         return (damage & 15) != 5 && !nonHealth ? 1 : 2;
      } else {
         return 1;
      }
   }

   public static strictfp RefillSelection.Result choose(byte[] mask, int previous, float angle, RefillSelection.Gates gates) {
      float x = (float)(previous % 9);
      float y = (float)(previous / 9);
      float bestScore = Float.MAX_VALUE;
      float secondScore = Float.MAX_VALUE;
      float emptyScore = Float.MAX_VALUE;
      float bestBearing = 0.0F;
      float secondBearing = 0.0F;
      float emptyBearing = 0.0F;
      int best = -1;
      int second = -1;
      int empty = -1;

      for (int slot = 0; slot < 27; slot++) {
         int mark = mask[slot] & 255;
         if (mark == 2 || mark == 0 && !gates.skipEmpty()) {
            float dx = (float)(slot % 9) - x;
            float dy = (float)(slot / 9) - y;
            float distance = RecoveredMath.floatDistance(dx, dy);
            float bearing = RotationMath.atan2(dy, dx);
            float angular = RotationMath.abs(RotationMath.wrap(bearing - angle)) * 0.5F;
            float score = distance * 1.2F + angular;
            if (mark == 0) {
               if (!(emptyScore < score)) {
                  emptyBearing = bearing;
               }

               if (!(score > emptyScore)) {
                  empty = slot;
               }

               emptyScore = RotationMath.min(emptyScore, score);
            } else {
               boolean replaces = !(score > bestScore);
               if (replaces) {
                  secondScore = bestScore;
                  second = best;
               }

               if (!(bestScore < score)) {
                  secondBearing = bestBearing;
               }

               if (replaces) {
                  bestBearing = bearing;
                  best = slot;
               }

               bestScore = RotationMath.min(bestScore, score);
            }
         }
      }

      if (bestScore > emptyScore && gates.chooseEmpty()) {
         return new RefillSelection.Result(empty, emptyScore, emptyBearing);
      } else {
         return second != -1 && (double)bestScore * 1.5 > (double)secondScore && gates.chooseSecond()
            ? new RefillSelection.Result(second, secondScore, secondBearing)
            : new RefillSelection.Result(best, bestScore, bestBearing);
      }
   }

   public interface Gates {
      boolean skipEmpty();

      boolean chooseEmpty();

      boolean chooseSecond();
   }

   public static final strictfp class Result {
      public final int slot;
      public final float score;
      public final float bearing;

      Result(int slot, float score, float bearing) {
         this.slot = slot;
         this.score = score;
         this.bearing = bearing;
      }
   }
}
