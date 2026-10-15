package dev.slinky.client;

import java.math.BigInteger;

public final class FixedWidth {
   private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

   private FixedWidth() {
   }

   private static BigInteger unsigned(long value) {
      BigInteger integer = BigInteger.valueOf(value);
      return value < 0L ? integer.add(TWO_64) : integer;
   }

   public static long unsignedMultiplyHigh(long left, long right) {
      return unsigned(left).multiply(unsigned(right)).shiftRight(64).longValue();
   }

   public static long signedMultiplyHigh(long left, long right) {
      return BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)).shiftRight(64).longValue();
   }
}
