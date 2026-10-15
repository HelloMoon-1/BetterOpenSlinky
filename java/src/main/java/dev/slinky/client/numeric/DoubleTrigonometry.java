package dev.slinky.client.numeric;

import dev.slinky.client.RecoveredMath;

public final strictfp class DoubleTrigonometry {
   private DoubleTrigonometry() {
   }

   private static strictfp double c(int address) {
      return NumericConstants.doubleAt(address);
   }

   private static strictfp int exponent(double value) {
      return (int)(Double.doubleToRawLongBits(value) >>> 52 & 2047L);
   }

   private static strictfp double negate(double value, int address) {
      return Double.longBitsToDouble(Double.doubleToRawLongBits(value) ^ NumericConstants.longAt(address));
   }

   private static strictfp void direct(double value, double[] output, int head, int tail) {
      double first = value + c(head);
      double correction = c(tail);
      output[0] = first + correction;
      output[1] = first - output[0] + correction;
   }

   public static strictfp int reduce(double value, double[] output) {
      long bits = Double.doubleToRawLongBits(value);
      int high = (int)(bits >>> 32) & 2147483647;
      boolean negative = bits < 0L;
      boolean nearbyCancellation = false;
      if (high <= 1074752122) {
         if (((int)(bits >>> 32) & 1048575) != 598523) {
            if (high <= 1073928572) {
               direct(value, output, negative ? 2864800 : 2864616, negative ? 2864624 : 2864792);
               return negative ? -1 : 1;
            }

            direct(value, output, negative ? 2864776 : 2864760, negative ? 2864784 : 2864768);
            return negative ? -2 : 2;
         }
      } else if (high < 1075594812) {
         if (high < 1075183037) {
            if (high != 1074977148) {
               direct(value, output, negative ? 2864744 : 2864728, negative ? 2864752 : 2864736);
               return negative ? -3 : 3;
            }
         } else if (high != 1075388923) {
            direct(value, output, negative ? 2864712 : 2864696, negative ? 2864720 : 2864704);
            return negative ? -4 : 4;
         }

         nearbyCancellation = true;
      } else if (high >= 1094263291) {
         if (high >= 2146435072) {
            output[0] = DoubleNumeric.invalid(value);
            output[1] = output[0];
            return 0;
         }

         double normalized = Double.longBitsToDouble(bits & 4503599627370495L | 4710765210229538816L);
         double[] chunks = new double[]{(double)RecoveredMath.truncateInt(normalized), 0.0, 0.0};
         double fraction = (normalized - chunks[0]) * c(2864560);
         chunks[1] = (double)RecoveredMath.truncateInt(fraction);
         chunks[2] = (fraction - chunks[1]) * c(2864560);
         int count = 3;

         while (chunks[count - 1] == 0.0) {
            count--;
         }

         double[] residual = new double[2];
         int quadrant = LargeReduction.reduce(chunks, residual, (high >>> 20) - 1046, count, 1);
         if (negative) {
            output[0] = negate(residual[0], 2864576);
            output[1] = negate(residual[1], 2864584);
            return -quadrant;
         }

         output[0] = residual[0];
         output[1] = residual[1];
         return quadrant;
      }

      double multiple = c(2864592) * value;
      multiple += c(2864600);
      multiple += c(2864608);
      int quadrant = RecoveredMath.truncateInt(multiple);
      double remainder = c(2864616) * multiple + value;
      double tail = c(2864624) * multiple;
      double first = remainder - tail;
      boolean adjust = false;
      if (c(2864632) > first) {
         quadrant--;
         multiple += c(2864656);
         adjust = true;
      } else if (first > c(2864640)) {
         quadrant++;
         multiple += c(2864648);
         adjust = true;
      }

      if (adjust) {
         remainder = value + c(2864616) * multiple;
         tail = c(2864624) * multiple;
      }

      output[0] = remainder - tail;
      int inputExponent = high >>> 20;
      boolean secondStage = nearbyCancellation ? exponent(output[0]) < 1009 : inputExponent - exponent(output[0]) > 16;
      if (secondStage) {
         double second = c(2864664) * multiple;
         double reduced = remainder - second;
         tail = c(2864672) * multiple - (remainder - reduced - second);
         output[0] = reduced - tail;
         boolean thirdStage = nearbyCancellation ? (Double.doubleToRawLongBits(output[0]) >>> 56 & 127L) <= 60L : inputExponent - exponent(output[0]) >= 50;
         remainder = reduced;
         if (thirdStage) {
            double third = c(2864680) * multiple;
            double reducedThird = reduced - third;
            tail = multiple * c(2864688) - (reduced - reducedThird - third);
            remainder = reducedThird;
            output[0] = reducedThird - tail;
         }
      }

      output[1] = remainder - output[0] - tail;
      return quadrant;
   }

   private static strictfp double sineKernel(double x, double y) {
      double z = x * x;
      double cube = z * z;
      double polynomial = c(2866896) * z + c(2866904);
      double low = c(2866912) * z + c(2866928);
      double high = polynomial * z + c(2866936);
      cube *= z;
      cube *= low;
      double sum = high + cube;
      double xz = x * z;
      double correction = c(2866864) * y;
      sum *= xz;
      correction -= sum;
      correction *= z;
      correction -= y;
      xz *= c(2866944);
      xz += correction;
      return x - xz;
   }

   private static strictfp double cosineKernel(double x, double tail) {
      double z = x * x;
      double fourth = z * z;
      double pLow = c(2866800) * z;
      double pHigh = c(2866808) * z;
      double lower = c(2866816) + pLow;
      double upper = c(2866840) - pHigh;
      lower = lower * z + c(2866848);
      upper = upper * z + c(2866856);
      fourth *= fourth;
      double lowProduct = z * lower;
      double highProduct = fourth * upper;
      double polynomial = highProduct + lowProduct;
      double halfZ = c(2866864) * z;
      double one = c(2866872);
      double head = one - halfZ;
      double correction = one - head - halfZ;
      polynomial *= z;
      x *= tail;
      polynomial -= x;
      polynomial += correction;
      return polynomial + head;
   }

   public static strictfp double sine(double value) {
      int high = (int)(Double.doubleToRawLongBits(value) >>> 32) & 2147483647;
      if (high <= 1072243195) {
         if (high < 1045430272) {
            return value;
         } else {
            double z = value * value;
            double cube = z * z * z;
            double low = z * c(2866960) + c(2866976);
            double highTerm = z * c(2866968) + c(2866984);
            double lowTerm = cube * low;
            double upper = z * highTerm;
            double sum = upper + c(2866992);
            sum += lowTerm;
            double xz = value * z;
            sum *= z;
            sum += c(2867000);
            sum *= xz;
            return value + sum;
         }
      } else if (high > 2146435071) {
         return DoubleNumeric.invalid(value);
      } else {
         double[] residual = new double[2];
         int quadrant = reduce(value, residual) & 3;
         if ((quadrant & 1) == 0) {
            double result = sineKernel(residual[0], residual[1]);
            return quadrant == 2 ? negate(result, 2866880) : result;
         } else {
            double result = cosineKernel(residual[0], residual[1]);
            return quadrant == 3 ? negate(result, 2866880) : result;
         }
      }
   }
}
