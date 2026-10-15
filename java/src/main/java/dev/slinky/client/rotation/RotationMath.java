package dev.slinky.client.rotation;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatNumeric;
import dev.slinky.client.numeric.FloatTrigonometry;
import dev.slinky.client.numeric.NumericServices;
import net.minecraft.util.Vec3;

public final strictfp class RotationMath {
   private RotationMath() {
   }

   public static strictfp float sine(float radians) {
      return FloatTrigonometry.sine(radians);
   }

   public static strictfp float cosine(float radians) {
      return FloatTrigonometry.cosine(radians);
   }

   public static strictfp double atan2(double first, double second) {
      return NumericServices.atan2(first, second);
   }

   public static strictfp float atan2(float first, float second) {
      return NumericServices.atan2f(first, second);
   }

   public static strictfp float atan2f(float first, float second) {
      return NumericServices.atan2f(first, second);
   }

   public static strictfp double hypot(double first, double second) {
      return NumericServices.hypot(first, second);
   }

   public static strictfp float abs(float value) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(value) & 2147483647);
   }

   public static strictfp float min(float a, float b) {
      return a < b ? a : b;
   }

   public static strictfp float max(float a, float b) {
      return a > b ? a : b;
   }

   public static strictfp double min(double a, double b) {
      return a < b ? a : b;
   }

   public static strictfp double max(double a, double b) {
      return a > b ? a : b;
   }

   public static strictfp float clamp(float low, float high, float value) {
      return value < low ? low : min(high, value);
   }

   public static strictfp double clamp(double low, double high, double value) {
      return value < low ? low : min(high, value);
   }

   public static strictfp float wrap(float angle) {
      float result = FloatNumeric.remainder(angle, 360.0F);
      if (result >= 180.0F) {
         result += -360.0F;
      } else if (result < -180.0F) {
         result += 360.0F;
      }

      return result;
   }

   public static strictfp float[] angles(Vec3 from, Vec3 to) {
      double x = to.xCoord - from.xCoord;
      double y = to.yCoord - from.yCoord;
      double z = to.zCoord - from.zCoord;
      double horizontal = Math.sqrt(x * x + z * z);
      float yaw = (float)(atan2(z, x) * 180.0 / Math.PI + -90.0);
      float pitch = (float)(atan2(y, horizontal) * -180.0 / Math.PI);
      return new float[]{yaw, pitch};
   }

   public static strictfp Vec3 interpolate(Vec3 a, Vec3 b, double fraction) {
      return new Vec3(fraction * (b.xCoord - a.xCoord) + a.xCoord, fraction * (b.yCoord - a.yCoord) + a.yCoord, (b.zCoord - a.zCoord) * fraction + a.zCoord);
   }

   public static strictfp double distance(Vec3 a, Vec3 b) {
      double x = a.xCoord - b.xCoord;
      double y = a.yCoord - b.yCoord;
      double z = a.zCoord - b.zCoord;
      return Math.sqrt(z * z + (x * x + y * y));
   }

   public static strictfp double[] movement(float yaw, float forward, float sideways, float speed) {
      float f = forward * 0.98F;
      float s = sideways * 0.98F;
      float squared = s * s + f * f;
      if (1.0E-4F > squared) {
         return new double[]{0.0, 0.0};
      } else {
         float scale = speed * Float.intBitsToFloat(1065353213);
         float length = max(1.0F, (float)Math.sqrt((double)squared));
         scale /= length;
         s *= scale;
         f *= scale;
         float angle = (float)((double)yaw * Math.PI / 180.0);
         float sin = sine(angle);
         float cos = cosine(angle);
         return new double[]{(double)(s * cos - f * sin), (double)(f * cos + s * sin)};
      }
   }

   public static strictfp float magnitude(float yaw, float pitch) {
      return RecoveredMath.floatDistance(yaw, pitch);
   }

   public static strictfp int tieHash(int entityId, long counter) {
      int x = entityId + (int)counter;
      x ^= x >>> 16;
      x *= 73244475;
      x ^= x >>> 16;
      x *= 73244475;
      return x ^ x >>> 16;
   }
}
