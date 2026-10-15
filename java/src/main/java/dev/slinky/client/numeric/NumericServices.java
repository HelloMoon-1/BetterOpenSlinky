package dev.slinky.client.numeric;

import java.util.Objects;

public final class NumericServices {
   public static final String CAPTURED_HYPOT = "0x13cfd0: ucrtbase.dll!hypot";
   public static final String CAPTURED_ATAN2 = "0x13cfb0: ucrtbase.dll!atan2";
   public static final String CAPTURED_ACOS = "0x13cfa0: ucrtbase.dll!acos";
   public static final String CAPTURED_ATAN2F = "0x13cfc0: ucrtbase.dll!atan2f";
   public static final String CAPTURED_ACOSF = "0x13d190: ucrtbase.dll!acosf";
   public static final String CAPTURED_POWF = "0x13cfe0: ucrtbase.dll!powf";
   private static volatile NumericServices.DoublePair pair = StrictMath::hypot;
   private static volatile NumericServices.DoublePair direction = StrictMath::atan2;
   private static volatile NumericServices.DoubleUnary projection = StrictMath::acos;
   private static volatile NumericServices.FloatPair floatDirection = (first, second) -> (float)StrictMath.atan2((double)first, (double)second);
   private static volatile NumericServices.FloatUnary floatProjection = value -> (float)StrictMath.acos((double)value);
   private static volatile NumericServices.FloatPair scalar = (first, second) -> (float)StrictMath.pow((double)first, (double)second);

   private NumericServices() {
   }

   public static double hypot(double first, double second) {
      return pair.call(first, second);
   }

   public static double atan2(double first, double second) {
      return direction.call(first, second);
   }

   public static double acos(double value) {
      return projection.call(value);
   }

   public static float atan2f(float first, float second) {
      return floatDirection.call(first, second);
   }

   public static float acosf(float value) {
      return floatProjection.call(value);
   }

   public static float powf(float first, float second) {
      return scalar.call(first, second);
   }

   public static NumericServices.DoublePair bindHypot(NumericServices.DoublePair endpoint) {
      NumericServices.DoublePair previous = pair;
      pair = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static NumericServices.DoublePair bindAtan2(NumericServices.DoublePair endpoint) {
      NumericServices.DoublePair previous = direction;
      direction = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static NumericServices.DoubleUnary bindAcos(NumericServices.DoubleUnary endpoint) {
      NumericServices.DoubleUnary previous = projection;
      projection = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static NumericServices.FloatPair bindAtan2f(NumericServices.FloatPair endpoint) {
      NumericServices.FloatPair previous = floatDirection;
      floatDirection = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static NumericServices.FloatUnary bindAcosf(NumericServices.FloatUnary endpoint) {
      NumericServices.FloatUnary previous = floatProjection;
      floatProjection = Objects.requireNonNull(endpoint);
      return previous;
   }

   public static NumericServices.FloatPair bindPowf(NumericServices.FloatPair endpoint) {
      NumericServices.FloatPair previous = scalar;
      scalar = Objects.requireNonNull(endpoint);
      return previous;
   }

   public interface DoublePair {
      double call(double var1, double var3);
   }

   public interface DoubleUnary {
      double call(double var1);
   }

   public interface FloatPair {
      float call(float var1, float var2);
   }

   public interface FloatUnary {
      float call(float var1);
   }
}
