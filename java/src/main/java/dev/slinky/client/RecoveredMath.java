package dev.slinky.client;

public final strictfp class RecoveredMath {
   private RecoveredMath() {
   }

   public static strictfp float floatDistance(float first, float second) {
      int a = Float.floatToRawIntBits(first) & 2147483647;
      int b = Float.floatToRawIntBits(second) & 2147483647;
      if (a == 2139095040 || b == 2139095040) {
         return Float.POSITIVE_INFINITY;
      } else if (a <= 2139095040 && b <= 2139095040) {
         double x = (double)first;
         double y = (double)second;
         return (float)Math.sqrt(x * x + y * y);
      } else {
         return Float.NaN;
      }
   }

   public static strictfp int truncateInt(float value) {
      return !Float.isNaN(value) && !(value >= 2.1474836E9F) && !(value < -2.1474836E9F) ? (int)value : Integer.MIN_VALUE;
   }

   public static strictfp int truncateInt(double value) {
      return !Double.isNaN(value) && !(value >= 2.1474836E9F) && !(value < -2.1474836E9F) ? (int)value : Integer.MIN_VALUE;
   }

   public static strictfp long truncateLong(float value) {
      return !Float.isNaN(value) && !((double)value >= 9.223372E18F) && !((double)value < -9.223372E18F) ? (long)value : Long.MIN_VALUE;
   }

   public static strictfp long truncateLong(double value) {
      return !Double.isNaN(value) && !(value >= 9.223372E18F) && !(value < -9.223372E18F) ? (long)value : Long.MIN_VALUE;
   }

   public static strictfp float ceiling(float value) {
      int bits = Float.floatToRawIntBits(value);
      int exponent = bits >>> 23 & 0xFF;
      if (exponent >= 150) {
         return value;
      } else if (exponent >= 127) {
         int mask = -1 << (22 - (bits >>> 23) & 31);
         int fraction = ~mask;
         return (bits & fraction) == 0 ? value : Float.intBitsToFloat(bits + (bits < 0 ? 0 : fraction) & mask);
      } else {
         return Float.intBitsToFloat(bits < 0 ? Integer.MIN_VALUE : (bits == 0 ? 0 : 1065353216));
      }
   }
}
