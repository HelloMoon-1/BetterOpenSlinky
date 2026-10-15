package dev.slinky.client.numeric;

public final strictfp class DoubleNumeric {
   private DoubleNumeric() {
   }

   private static strictfp long c(int address) {
      return NumericConstants.longAt(address);
   }

   private static strictfp double d(int address) {
      return NumericConstants.doubleAt(address);
   }

   public static strictfp long upperProduct(long a, long b) {
      long al = a & 4294967295L;
      long bl = b & 4294967295L;
      long ah = a >>> 32;
      long bh = b >>> 32;
      long low = al * bl;
      long left = ah * bl;
      long right = al * bh;
      long middle = (low >>> 32) + (left & 4294967295L) + (right & 4294967295L);
      return ah * bh + (left >>> 32) + (right >>> 32) + (middle >>> 32);
   }

   public static strictfp double invalid(double input) {
      long bits = Double.doubleToRawLongBits(input);
      return Double.longBitsToDouble(Long.compareUnsigned(bits & Long.MAX_VALUE, 9218868437227405312L) > 0 ? bits | 2251799813685248L : -2251799813685248L);
   }

   public static strictfp double sqrt(double input) {
      long bits = Double.doubleToRawLongBits(input);
      long exponent = bits >>> 52;
      if (Long.compareUnsigned(exponent - 2047L, -2046L) < 0) {
         if (input == 0.0 || bits == 9218868437227405312L) {
            return input;
         }

         if (Long.compareUnsigned(bits, 9218868437227405312L) > 0) {
            return invalid(input);
         }

         input *= d(2864952);
         bits = Double.doubleToRawLongBits(input);
         exponent = (bits >>> 52) - 52L;
      }

      long significand = bits << 11 | Long.MIN_VALUE;
      if ((exponent & 1L) != 0L) {
         significand = bits << 10 & 9223372036854774784L;
      }

      long reciprocal = (long)NumericConstants.shortAt(2875200 + 2 * (int)(bits >>> 46 & 127L)) << 16;
      long root = (significand >>> 32) * reciprocal >>> 32;
      long square = root * reciprocal >>> 32;
      int correction = -1073741824 - (int)square;
      reciprocal = reciprocal * ((long)correction & 4294967295L) >>> 31 & 4294967294L;
      root = root * ((long)correction & 4294967295L) >>> 31 & 4294967294L;
      correction = -1073741824 - (int)(root * reciprocal >>> 32);
      long inverse = 2L * ((long)correction & 4294967295L) * reciprocal & -8589934592L;
      root = upperProduct(inverse, significand);
      root = upperProduct(root, -4611686018427387904L - upperProduct(root, inverse));
      root = root - 2L >>> 9;
      significand <<= 42;
      long residual = root * root - significand + root;
      long rounded = root + (residual >>> 63);
      long test = root + residual;
      long result = 2303591209400008704L + (exponent << 51) & -4503599627370496L | rounded & 4503599627370495L;
      double value = Double.longBitsToDouble(result);
      if (++test != 0L) {
         long adjustment = 4503599627370496L | (test ^ residual) & Long.MIN_VALUE;
         value += Double.longBitsToDouble(adjustment);
      }

      return value;
   }

   public static strictfp double scale(double value, int amount) {
      long bits = Double.doubleToRawLongBits(value);
      int stored = (int)(bits >>> 52 & 2047L);
      if (stored == 2047) {
         return value;
      } else {
         long sign = bits & Long.MIN_VALUE;
         long doubled = bits * 2L;
         int high = doubled != 0L ? 63 - Long.numberOfLeadingZeros(doubled) : 127;
         int effective = stored != 0 ? stored : (doubled != 0L ? high - 52 : -53);
         if (amount >= 0) {
            if (amount > 2046 - effective) {
               return Double.longBitsToDouble(bits & c(2863232) | c(2863248));
            } else {
               int adjusted = effective + amount;
               if (adjusted <= 0) {
                  return Double.longBitsToDouble(bits << amount | sign);
               } else if (effective > 0) {
                  return Double.longBitsToDouble(bits + ((long)amount << 52));
               } else {
                  long fraction = bits << 1 - effective & 4503599627370495L;
                  return Double.longBitsToDouble(fraction | (long)adjusted << 52 | sign);
               }
            }
         } else {
            int positive = -amount;
            if (amount > -effective) {
               return Double.longBitsToDouble(bits - (((long)positive & 4294967295L) << 52));
            } else if (amount < -52 - effective) {
               return Double.longBitsToDouble(sign);
            } else {
               int sum = amount + effective;
               int shift = (Integer.compareUnsigned(positive, 1 - sum) < 0 ? positive : 1 - sum) - 1 & 63;
               boolean tie = Long.numberOfTrailingZeros(bits) == shift;
               long significand = bits & 4503599627370495L | (effective > 0 ? 4503599627370496L : 0L);
               long rounded = (significand >>> shift) + 1L >>> 1;
               rounded &= ~(tie ? 1L : 0L);
               return Double.longBitsToDouble(bits & c(2863232) | rounded & c(2863216));
            }
         }
      }
   }

   public static strictfp double floor(double value) {
      long bits = Double.doubleToRawLongBits(value);
      long exponent = bits >>> 52 & 2047L;
      if (exponent < 1075L && value != 0.0) {
         double positive = d(2868888);
         double negative = d(2868896);
         double minusOne = d(2868904);
         boolean below = bits < 0L;
         double correction = below ? positive + (negative + value) - value : positive + value + negative - value;
         if (exponent <= 1022L) {
            return below ? minusOne : 0.0;
         } else {
            double integer = value + correction;
            return correction > 0.0 ? integer + minusOne : integer;
         }
      } else {
         return value;
      }
   }

   public static strictfp double remainder(double first, double second) {
      long x = Double.doubleToRawLongBits(first);
      long y = Double.doubleToRawLongBits(second);
      long sign = x & Long.MIN_VALUE;
      long dividend = x & Long.MAX_VALUE;
      long divisor = y & Long.MAX_VALUE;
      int dividendExponent = (int)(dividend >>> 52);
      int divisorExponent = (int)(divisor >>> 52);
      if (divisor != 0L && divisor <= 9218868437227405312L && dividendExponent != 2047) {
         if (dividend <= divisor) {
            return dividend == divisor ? Double.longBitsToDouble(sign) : first;
         } else {
            if (dividendExponent != 0) {
               dividend = dividend & 4503599627370495L | 4503599627370496L;
            } else {
               for (dividendExponent = 1; dividend < 4503599627370496L; dividendExponent--) {
                  dividend <<= 1;
               }
            }

            if (divisorExponent != 0) {
               divisor = divisor & 4503599627370495L | 4503599627370496L;
            } else {
               for (divisorExponent = 1; divisor < 4503599627370496L; divisorExponent--) {
                  divisor <<= 1;
               }
            }

            while (dividendExponent > divisorExponent) {
               if (dividend >= divisor) {
                  dividend -= divisor;
                  if (dividend == 0L) {
                     return Double.longBitsToDouble(sign);
                  }
               }

               dividend <<= 1;
               dividendExponent--;
            }

            if (dividend >= divisor) {
               dividend -= divisor;
               if (dividend == 0L) {
                  return Double.longBitsToDouble(sign);
               }
            }

            while (dividend < 4503599627370496L) {
               dividend <<= 1;
               dividendExponent--;
            }

            if (dividendExponent > 0) {
               dividend = dividend - 4503599627370496L | (long)dividendExponent << 52;
            } else {
               dividend >>>= 1 - dividendExponent;
            }

            return Double.longBitsToDouble(dividend | sign);
         }
      } else if (dividend > 9218868437227405312L) {
         return invalid(first);
      } else {
         return divisor > 9218868437227405312L ? invalid(second) : Double.longBitsToDouble(-2251799813685248L);
      }
   }
}
