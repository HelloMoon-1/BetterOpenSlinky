package dev.slinky.client.numeric;

import dev.slinky.client.RecoveredMath;

public final strictfp class FloatNumeric {
   private FloatNumeric() {
   }

   private static strictfp float c(int address) {
      return NumericConstants.floatAt(address);
   }

   private static strictfp long unsigned(int value) {
      return (long)value & 4294967295L;
   }

   public static strictfp float log(float input) {
      int bits = Float.floatToRawIntBits(input);
      int offset;
      if (bits < 8388608) {
         if (input == 0.0F) {
            return c(2868468) / (input * input);
         }

         if (bits < 0) {
            return invalid(input);
         }

         float value = input * c(2868464);
         bits = Float.floatToRawIntBits(value);
         offset = -152;
      } else {
         if (Integer.compareUnsigned(bits, 2139095039) > 0) {
            return input;
         }

         if (bits == 1065353216) {
            return 0.0F;
         }

         offset = -127;
      }

      bits += 4913933;
      int exponent = (bits >>> 23) + offset;
      bits = (bits & 8388607) + 1060439283;
      float reduced = Float.intBitsToFloat(bits) + c(2868468);
      float denominator = c(2868472) + reduced;
      float ratio = reduced / denominator;
      float square = ratio * ratio;
      float fourth = square * square;
      float low = fourth * c(2868480);
      low += c(2868496);
      float high = fourth * c(2868484);
      high += c(2868500);
      low = square * low;
      high = fourth * high;
      float sum = high + low;
      float halfSquare = c(2868512) * reduced;
      halfSquare *= reduced;
      sum += halfSquare;
      sum *= ratio;
      float exponentFloat = (float)exponent;
      float correction = c(2868516) * exponentFloat;
      correction += sum;
      correction -= halfSquare;
      correction += reduced;
      float result = exponentFloat * c(2868520);
      return result + correction;
   }

   public static strictfp float invalid(float input) {
      int bits = Float.floatToRawIntBits(input);
      return Float.intBitsToFloat((bits & 2147483647) > 2139095040 ? bits | 4194304 : -4194304);
   }

   public static strictfp float sqrt(float input) {
      int bits = Float.floatToRawIntBits(input);
      if (Integer.compareUnsigned(bits - 2139095040, -2130706432) < 0) {
         if (input == 0.0F || bits == 2139095040) {
            return input;
         }

         if (Integer.compareUnsigned(bits, 2139095041) >= 0) {
            return invalid(input);
         }

         input *= c(2864960);
         bits = Float.floatToRawIntBits(input) + -192937984;
      }

      int significand = bits << 8 | -2147483648;
      if ((bits & 8388608) != 0) {
         significand = bits << 7 & 2147483520;
      }

      int exponent = (bits >>> 1) + 532676608 & 2139095040;
      long reciprocal = (long)NumericConstants.shortAt(2875200 + (bits >>> 16 & 254)) << 16;
      long root = unsigned(significand) * reciprocal >>> 32;
      long square = root * reciprocal >>> 32;
      int correction = -1073741824 - (int)square;
      reciprocal = unsigned((int)(reciprocal * unsigned(correction) >>> 31) & -2);
      root = unsigned((int)(root * unsigned(correction) >>> 31) & -2);
      correction = -1073741824 - (int)(reciprocal * root >>> 32);
      int integer = (int)(unsigned(correction) * root >>> 32) - 1 >>> 6;
      significand <<= 16;
      int residual = integer * integer - significand + integer;
      int rounded = integer + (residual >>> 31);
      int test = integer + residual;
      float value = Float.intBitsToFloat(exponent | rounded & 8388607);
      test++;
      int adjustment = (test == 0 ? 0 : 16777216) | (test ^ residual) & -2147483648;
      return value + Float.intBitsToFloat(adjustment);
   }

   public static strictfp float scale(float value, int power) {
      int bits = Float.floatToRawIntBits(value);
      int magnitude = bits & 2147483647;
      if (magnitude >= 2139095040) {
         return value;
      } else {
         int sign = bits & -2147483648;
         int doubled = bits << 1;
         int exponent = doubled >>> 24;
         if (Integer.compareUnsigned(doubled, 16777216) < 0) {
            int highest = doubled != 0 ? 31 - Integer.numberOfLeadingZeros(doubled) : 63;
            exponent = (highest ^ -32) + 9;
         }

         if (power >= 0) {
            if (power > 254 - exponent) {
               return Float.intBitsToFloat(sign | NumericConstants.intAt(2869408));
            }
         } else if (power <= -exponent) {
            if (power < -23 - exponent) {
               return Float.intBitsToFloat(sign);
            }

            int shift = Math.min(-power, 1 - (power + exponent)) - 1 & 31;
            int tie = Integer.numberOfTrailingZeros(bits) == shift ? 1 : 0;
            int significand = bits & 8388607 | (exponent > 0 ? 8388608 : 0);
            int rounded = (significand >>> shift) + 1 >>> 1;
            return Float.intBitsToFloat(sign | rounded & ~tie & 8388607);
         }

         int nextExponent = power + exponent;
         if (nextExponent <= 0) {
            return Float.intBitsToFloat(bits << power | sign);
         } else if (exponent > 0) {
            return Float.intBitsToFloat(bits + (power << 23));
         } else {
            int fraction = bits << 1 - exponent & 8388607;
            return Float.intBitsToFloat(nextExponent << 23 | fraction | sign);
         }
      }
   }

   public static strictfp float exp(float input) {
      int bits = Float.floatToRawIntBits(input);
      int magnitude = bits & 2147483647;
      int negative = bits >>> 31;
      if (magnitude > 2139095040) {
         return input;
      } else {
         if (magnitude > 1118743631) {
            if (negative == 0 && magnitude >= 1118925336) {
               return input * c(2869328);
            }

            if (negative != 0 && magnitude > 1120924084) {
               return 0.0F;
            }
         }

         int power = 0;
         float high = input;
         float low = 0.0F;
         float reduced = input;
         if (magnitude > 1051816472) {
            if (magnitude <= 1065686418) {
               power = negative != 0 ? -1 : 1;
            } else {
               float multiple = c(2869340) * input;
               multiple += c(2875456 + negative * 4);
               power = RecoveredMath.truncateInt(multiple);
            }

            float converted = (float)power;
            high = input + c(2869344) * converted;
            low = converted * c(2869348);
            reduced = high - low;
         } else if (magnitude <= 956301312) {
            return input + c(2869332);
         }

         float square = reduced * reduced;
         float correction = c(2869352) * square;
         correction += c(2869356);
         correction *= square;
         float denominatorCorrection = reduced - correction;
         float result = reduced * denominatorCorrection;
         result /= c(2869360) - denominatorCorrection;
         result -= low;
         result += high;
         result += c(2869332);
         return power != 0 ? scale(result, power) : result;
      }
   }

   public static strictfp float round(float input) {
      int bits = Float.floatToRawIntBits(input);
      int exponent = bits >>> 23 & 0xFF;
      if (exponent >= 150) {
         return input;
      } else {
         float magnitude = Float.intBitsToFloat(bits & NumericConstants.intAt(2867584));
         float delta = c(2867600) + magnitude;
         if (exponent <= 125) {
            return input * 0.0F;
         } else {
            delta += c(2867604);
            delta -= magnitude;
            if (delta > c(2867608)) {
               magnitude += delta;
               magnitude += c(2867620);
            } else {
               magnitude += delta;
               if (c(2867612) >= delta) {
                  magnitude += c(2867616);
               }
            }

            return Float.intBitsToFloat(Float.floatToRawIntBits(magnitude) ^ (bits < 0 ? NumericConstants.intAt(2867632) : 0));
         }
      }
   }

   public static strictfp float truncate(float input) {
      int bits = Float.floatToRawIntBits(input);
      int exponent = bits >>> 23 & 0xFF;
      if (exponent >= 150) {
         return input;
      } else {
         int shift = exponent >= 127 ? exponent - 118 : 1;
         int mask = -1 >>> (shift & 31);
         return Float.intBitsToFloat(bits & ~mask);
      }
   }

   public static strictfp float remainder(float first, float second) {
      int x = Float.floatToRawIntBits(first);
      int y = Float.floatToRawIntBits(second);
      int sign = x & -2147483648;
      int dividend = x & 2147483647;
      int divisor = y & 2147483647;
      int dividendExponent = dividend >>> 23;
      int divisorExponent = divisor >>> 23;
      if (divisor != 0 && divisor <= 2139095040 && dividendExponent != 255) {
         if (dividend <= divisor) {
            return dividend == divisor ? Float.intBitsToFloat(sign) : first;
         } else {
            if (dividendExponent != 0) {
               dividend = dividend & 8388607 | 8388608;
            } else {
               for (dividendExponent = 1; dividend < 8388608; dividendExponent--) {
                  dividend <<= 1;
               }
            }

            if (divisorExponent != 0) {
               divisor = divisor & 8388607 | 8388608;
            } else {
               for (divisorExponent = 1; divisor < 8388608; divisorExponent--) {
                  divisor <<= 1;
               }
            }

            while (dividendExponent > divisorExponent) {
               if (dividend >= divisor) {
                  dividend -= divisor;
                  if (dividend == 0) {
                     return Float.intBitsToFloat(sign);
                  }
               }

               dividend <<= 1;
               dividendExponent--;
            }

            if (dividend >= divisor) {
               dividend -= divisor;
               if (dividend == 0) {
                  return Float.intBitsToFloat(sign);
               }
            }

            while (dividend < 8388608) {
               dividend <<= 1;
               dividendExponent--;
            }

            if (dividendExponent > 0) {
               dividend = dividend - 8388608 | dividendExponent << 23;
            } else {
               dividend >>>= 1 - dividendExponent;
            }

            return Float.intBitsToFloat(dividend | sign);
         }
      } else if (dividend > 2139095040) {
         return invalid(first);
      } else {
         return divisor > 2139095040 ? invalid(second) : Float.intBitsToFloat(-4194304);
      }
   }
}
