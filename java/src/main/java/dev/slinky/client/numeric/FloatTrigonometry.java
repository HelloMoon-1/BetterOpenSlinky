package dev.slinky.client.numeric;

import dev.slinky.client.RecoveredMath;

public final strictfp class FloatTrigonometry {
   private FloatTrigonometry() {
   }

   private static strictfp double c(int address) {
      return NumericConstants.doubleAt(address);
   }

   private static strictfp float sign(float value, int address) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(value) ^ NumericConstants.intAt(address));
   }

   private static strictfp float sineKernel(double x, int coefficients, int sign, boolean negative) {
      double z = x * x;
      double squared = z * z;
      double signedX = negative ? Double.longBitsToDouble(Double.doubleToRawLongBits(x) ^ NumericConstants.longAt(sign)) : x;
      double cube = negative ? signedX * z : z * x;
      double low = z * c(coefficients) + c(coefficients + 16);
      double high = z * c(coefficients + 8) + c(coefficients + 24);
      double lowTerm = squared * cube * low;
      double highTerm = cube * high;
      highTerm = negative ? highTerm - x : highTerm + x;
      return (float)(highTerm + lowTerm);
   }

   private static strictfp float cosineKernel(double x, int coefficients, int sign, boolean negative) {
      double z = x * x;
      double fourth = z * z;
      double first = c(coefficients) * z + c(coefficients + 8);
      double second = c(coefficients + 16) * z + c(coefficients + 24);
      double sixth = z * fourth;
      double fourthTerm = fourth * c(coefficients + 32) + second;
      double sixthTerm = sixth * first;
      float value = (float)(sixthTerm + fourthTerm);
      return negative ? sign(value, sign) : value;
   }

   public static strictfp int reduce(float input, double[] output) {
      int bits = Float.floatToRawIntBits(input);
      int absolute = bits & 2147483647;
      if (absolute <= 1305022426) {
         double x = (double)input;
         double multiple = c(2864832) * x;
         multiple += c(2864840);
         multiple += c(2864848);
         int quadrant = RecoveredMath.truncateInt(multiple);
         double first = c(2864856) * multiple + x;
         double remainder = c(2864864) * multiple + first;
         output[0] = remainder;
         if (c(2864872) > remainder) {
            multiple += c(2864896);
            quadrant--;
         } else {
            if (!(remainder > c(2864880))) {
               return quadrant;
            }

            multiple += c(2864888);
            quadrant++;
         }

         double correction = c(2864856) * multiple;
         x += correction;
         multiple *= c(2864864);
         output[0] = multiple + x;
         return quadrant;
      } else if (absolute > 2139095039) {
         output[0] = (double)FloatNumeric.invalid(input);
         return 0;
      } else {
         int exponent = (absolute >>> 23) - 150;
         int scaledBits = absolute - (exponent << 23);
         double[] reduced = new double[1];
         int quadrant = LargeReduction.reduce(new double[]{(double)Float.intBitsToFloat(scaledBits)}, reduced, exponent, 1, 0);
         if (bits < 0) {
            output[0] = Double.longBitsToDouble(Double.doubleToRawLongBits(reduced[0]) ^ NumericConstants.longAt(2864816));
            quadrant = -quadrant;
         } else {
            output[0] = reduced[0];
         }

         return quadrant;
      }
   }

   public static strictfp float sine(float input) {
      int bits = Float.floatToRawIntBits(input);
      int absolute = bits & 2147483647;
      boolean negative = bits < 0;
      double x = (double)input;
      if (absolute <= 1061752794) {
         return absolute < 964689920 ? input : sineKernel(x, 2867120, 2867104, false);
      } else if (absolute < 1081824210) {
         if (absolute >= 1075235812) {
            x += c(negative ? 2867184 : 2867192);
            return sineKernel(x, 2867120, 2867104, true);
         } else {
            x += c(negative ? 2867200 : 2867208);
            return cosineKernel(x, 2867040, 2867088, negative);
         }
      } else if (absolute < 1088565718) {
         if (absolute >= 1085271520) {
            x += c(negative ? 2867152 : 2867160);
            return sineKernel(x, 2867120, 2867104, false);
         } else {
            x += c(negative ? 2867168 : 2867176);
            return cosineKernel(x, 2867040, 2867088, !negative);
         }
      } else if (absolute > 2139095039) {
         return FloatNumeric.invalid(input);
      } else {
         double[] remainder = new double[1];
         int quadrant = reduce(input, remainder) & 3;
         x = remainder[0];
         if (quadrant == 0) {
            return sineKernel(x, 2867120, 2867104, false);
         } else if (quadrant == 1) {
            return cosineKernel(x, 2867040, 2867088, false);
         } else {
            return quadrant == 2 ? sineKernel(x, 2867120, 2867104, true) : cosineKernel(x, 2867040, 2867088, true);
         }
      }
   }

   public static strictfp float cosine(float input) {
      int bits = Float.floatToRawIntBits(input);
      int absolute = bits & 2147483647;
      boolean negative = bits < 0;
      double x = (double)input;
      if (absolute <= 1061752794) {
         return absolute < 964689920 ? NumericConstants.floatAt(2870084) : cosineKernel(x, 2869952, 2870000, false);
      } else if (absolute < 1081824210) {
         if (absolute > 1075235811) {
            x += c(negative ? 2870064 : 2870072);
            return cosineKernel(x, 2869952, 2870000, true);
         } else {
            x = negative ? x + c(2870056) : c(2870056) - x;
            return sineKernel(x, 2869920, 2870016, false);
         }
      } else if (absolute < 1088565718) {
         if (absolute > 1085271519) {
            x += c(negative ? 2870040 : 2870048);
            return cosineKernel(x, 2869952, 2870000, false);
         } else {
            x = negative ? c(2870032) - x : x + c(2870032);
            return sineKernel(x, 2869920, 2870016, false);
         }
      } else if (absolute > 2139095039) {
         return FloatNumeric.invalid(input);
      } else {
         double[] remainder = new double[1];
         int quadrant = reduce(input, remainder) & 3;
         x = remainder[0];
         if (quadrant == 0) {
            return cosineKernel(x, 2869952, 2870000, false);
         } else if (quadrant == 1) {
            return sineKernel(x, 2869920, 2870016, true);
         } else {
            return quadrant == 2 ? cosineKernel(x, 2869952, 2870000, true) : sineKernel(x, 2869920, 2870016, false);
         }
      }
   }
}
