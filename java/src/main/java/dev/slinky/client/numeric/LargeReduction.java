package dev.slinky.client.numeric;

import dev.slinky.client.RecoveredMath;

public final strictfp class LargeReduction {
   private LargeReduction() {
   }

   private static strictfp double c(int address) {
      return NumericConstants.doubleAt(address);
   }

   private static strictfp double digit(int index) {
      return (double)NumericConstants.intAt(2872376 + 4 * index);
   }

   private static strictfp double convolution(double[] input, double[] table, int lastInput, int index) {
      double sum = 0.0;

      for (int j = 0; j <= lastInput; j++) {
         sum += input[j] * table[lastInput + index - j];
      }

      return sum;
   }

   private static strictfp double signed(double value, int complement) {
      return complement != 0 ? Double.longBitsToDouble(Double.doubleToRawLongBits(value) ^ NumericConstants.longAt(2863200)) : value;
   }

   public static strictfp int reduce(double[] input, double[] output, int exponent, int count, int precision) {
      int initial = NumericConstants.intAt(2872360 + 4 * precision);
      int lastInput = count - 1;
      int tableStart = Math.max(0, (short)(exponent - 3) / 24);
      int chunkExponent = exponent - 24 * (tableStart + 1);
      double[] table = new double[20];
      double[] products = new double[20];
      double[] weighted = new double[20];
      int[] chunks = new int[20];
      double tiny = c(2863136);
      double radix = c(2863144);
      double negativeRadix = c(2863152);
      double one = c(2863184);
      double half = c(2863176);

      for (int i = 0; i <= lastInput + initial; i++) {
         int index = tableStart - lastInput + i;
         table[i] = index < 0 ? 0.0 : digit(index);
      }

      for (int i = 0; i <= initial; i++) {
         products[i] = convolution(input, table, lastInput, i);
      }

      int last = initial;
      int quadrant = 0;
      int complement = 0;

      double residual;
      while (true) {
         residual = products[last];
         int i = 0;

         for (int j = last; j > 0; j--) {
            double carry = (double)RecoveredMath.truncateInt(residual * tiny);
            chunks[i] = RecoveredMath.truncateInt(carry * negativeRadix + residual);
            residual = products[j - 1] + carry;
            i++;
         }

         residual = DoubleNumeric.scale(residual, chunkExponent);
         double cycles = DoubleNumeric.floor(residual * c(2863160));
         residual = cycles * c(2863168) + residual;
         quadrant = RecoveredMath.truncateInt(residual);
         residual -= (double)quadrant;
         complement = 0;
         if (chunkExponent > 0) {
            int high = chunks[last - 1] >> 24 - chunkExponent;
            quadrant += high;
            chunks[last - 1] = chunks[last - 1] - (high << 24 - chunkExponent);
            complement = chunks[last - 1] >> 23 - chunkExponent;
         } else if (chunkExponent == 0) {
            complement = chunks[last - 1] >> 23;
         } else if (!(residual < half)) {
            complement = 2;
         }

         if (complement > 0) {
            quadrant++;
            boolean carry = false;

            for (int ix = 0; ix < last; ix++) {
               int chunk = chunks[ix];
               if (carry) {
                  chunks[ix] = 16777215 - chunk;
               } else if (chunk != 0) {
                  carry = true;
                  chunks[ix] = 16777216 - chunk;
               }
            }

            if (chunkExponent == 1) {
               chunks[last - 1] = chunks[last - 1] & 8388607;
            } else if (chunkExponent == 2) {
               chunks[last - 1] = chunks[last - 1] & 4194303;
            }

            if (complement == 2) {
               residual = one - residual;
               if (carry) {
                  residual -= DoubleNumeric.scale(one, chunkExponent);
               }
            }
         }

         if (residual != 0.0) {
            break;
         }

         int nonzero = 0;

         for (int ixx = last - 1; ixx >= initial; ixx--) {
            nonzero |= chunks[ixx];
         }

         if (nonzero != 0) {
            break;
         }

         int additional = 1;

         while (chunks[initial - additional] == 0) {
            additional++;
         }

         for (int ixx = last + 1; ixx <= last + additional; ixx++) {
            table[lastInput + ixx] = digit(tableStart + ixx);
            products[ixx] = convolution(input, table, lastInput, ixx);
         }

         last += additional;
      }

      if (residual == 0.0) {
         last--;

         for (chunkExponent -= 24; chunks[last] == 0; chunkExponent -= 24) {
            last--;
         }
      } else {
         residual = DoubleNumeric.scale(residual, -chunkExponent);
         if (!(residual < radix)) {
            double carry = (double)RecoveredMath.truncateInt(residual * tiny);
            chunks[last] = RecoveredMath.truncateInt(carry * negativeRadix + residual);
            last++;
            chunkExponent += 24;
            chunks[last] = RecoveredMath.truncateInt(carry);
         } else {
            chunks[last] = RecoveredMath.truncateInt(residual);
         }
      }

      double factor = DoubleNumeric.scale(one, chunkExponent);

      for (int i = last; i >= 0; i--) {
         products[i] = factor * (double)chunks[i];
         factor *= tiny;
      }

      for (int i = last; i >= 0; i--) {
         double sum = 0.0;

         for (int k = 0; k <= initial && k <= last - i; k++) {
            sum += c(2875136 + 8 * k) * products[i + k];
         }

         weighted[last - i] = sum;
      }

      if (precision < 3) {
         double sum = 0.0;

         for (int i = last; i >= 0; i--) {
            sum += weighted[i];
         }

         output[0] = signed(sum, complement);
         if (precision == 1 || precision == 2) {
            double tail = weighted[0] - sum;

            for (int i = 1; i <= last; i++) {
               tail += weighted[i];
            }

            output[1] = signed(tail, complement);
         }
      } else if (precision == 3) {
         for (int pass = 0; pass < 2; pass++) {
            for (int i = last; i > pass; i--) {
               double sum = weighted[i - 1] + weighted[i];
               weighted[i] += weighted[i - 1] - sum;
               weighted[i - 1] = sum;
            }
         }

         double tail = 0.0;

         for (int i = last; i >= 2; i--) {
            tail += weighted[i];
         }

         output[0] = signed(weighted[0], complement);
         output[1] = signed(weighted[1], complement);
         output[2] = signed(tail, complement);
      }

      return quadrant & 7;
   }
}
