package dev.slinky.config;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class RecoveredJsonJvmServices
   implements RecoveredJsonRuntime.Allocator,
   RecoveredJsonRuntime.Resizer,
   RecoveredJsonRuntime.NumberServices,
   RecoveredJsonRuntime.ByteComparisons {
   private final int decimal;
   private final long allocationLimit;

   public RecoveredJsonJvmServices() {
      this(46, 2147483639L);
   }

   public RecoveredJsonJvmServices(int decimal, long allocationLimit) {
      if (decimal >= 1 && decimal <= 127 && allocationLimit >= 0L) {
         this.decimal = decimal;
         this.allocationLimit = allocationLimit;
      } else {
         throw new IllegalArgumentException("JVM JSON services");
      }
   }

   public RecoveredJsonRuntime.Context context() {
      return new RecoveredJsonRuntime.Context(this, this, this, this);
   }

   @Override
   public RecoveredJsonRuntime.Node allocateNode() {
      return new RecoveredJsonRuntime.Node();
   }

   @Override
   public byte[] allocateBytes(long size) {
      return size >= 0L && size <= this.allocationLimit && size <= 2147483639L ? new byte[(int)size] : null;
   }

   @Override
   public void release(Object allocation) {
   }

   @Override
   public byte[] resize(byte[] old, long size) {
      byte[] next = this.allocateBytes(size);
      if (next != null) {
         System.arraycopy(old, 0, next, 0, Math.min(old.length, next.length));
      }

      return next;
   }

   @Override
   public int decimalPoint() {
      return this.decimal;
   }

   @Override
   public int compareCString(byte[] left, byte[] right) {
      int i = 0;

      while (true) {
         int a = left[i] & 255;
         int b = right[i] & 255;
         if (a != b) {
            return a - b;
         }

         if (a == 0) {
            return 0;
         }

         i++;
      }
   }

   @Override
   public int compareBytes(byte[] left, int offset, byte[] right, int width) {
      for (int i = 0; i < width; i++) {
         int difference = (left[offset + i] & 255) - (right[i] & 255);
         if (difference != 0) {
            return difference;
         }
      }

      return 0;
   }

   @Override
   public RecoveredJsonRuntime.FormattedNumber format(String format, long bits) {
      String text;
      if ("%d".equals(format)) {
         text = Integer.toString((int)bits);
      } else if ("u%04x".equals(format)) {
         String hex = Long.toHexString(bits & 4294967295L);
         StringBuilder value = new StringBuilder("u");

         for (int i = hex.length(); i < 4; i++) {
            value.append('0');
         }

         text = value.append(hex).toString();
      } else {
         if (!"%1.15g".equals(format) && !"%1.17g".equals(format)) {
            throw new IllegalArgumentException("Unbound JSON format " + format);
         }

         text = significant(Double.longBitsToDouble(bits), "%1.15g".equals(format) ? 15 : 17);
         if (this.decimal != 46) {
            text = text.replace('.', (char)this.decimal);
         }
      }

      return new RecoveredJsonRuntime.FormattedNumber(RecoveredJsonRuntime.cString(text), text.length());
   }

   private static String significant(double value, int precision) {
      if (Double.isNaN(value)) {
         return "nan";
      } else if (Double.isInfinite(value)) {
         return value < 0.0 ? "-inf" : "inf";
      } else if (value == 0.0) {
         return Double.doubleToRawLongBits(value) < 0L ? "-0" : "0";
      } else {
         BigDecimal rounded = new BigDecimal(value).round(new MathContext(precision, RoundingMode.HALF_EVEN)).stripTrailingZeros();
         int exponent = rounded.precision() - rounded.scale() - 1;
         if (exponent >= -4 && exponent < precision) {
            return rounded.toPlainString();
         } else {
            String mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString();
            String digits = Integer.toString(Math.abs(exponent));
            if (digits.length() < 2) {
               digits = "0" + digits;
            }

            return mantissa + "e" + (exponent < 0 ? "-" : "+") + digits;
         }
      }
   }

   @Override
   public RecoveredJsonRuntime.ScannedNumber scan(byte[] text, String format) {
      if (!"%lg".equals(format)) {
         throw new IllegalArgumentException("Unbound JSON scan " + format);
      } else {
         RecoveredJsonRuntime.ParsedNumber parsed = this.parse(text);
         return new RecoveredJsonRuntime.ScannedNumber(parsed.consumed == 0 ? 0 : 1, parsed.value);
      }
   }

   @Override
   public RecoveredJsonRuntime.ParsedNumber parse(byte[] text) {
      int length = RecoveredJsonRuntime.stringLength(text, 0);
      int offset = 0;
      if (offset < length && (text[offset] == 43 || text[offset] == 45)) {
         offset++;
      }

      int first = offset;

      while (offset < length && digit(text[offset])) {
         offset++;
      }

      int digits = offset - first;
      if (offset < length && (text[offset] & 255) == this.decimal) {
         int fraction = ++offset;

         while (offset < length && digit(text[offset])) {
            offset++;
         }

         digits += offset - fraction;
      }

      if (digits == 0) {
         return new RecoveredJsonRuntime.ParsedNumber(0.0, 0);
      } else {
         int consumed = offset;
         if (offset < length && (text[offset] == 101 || text[offset] == 69)) {
            offset++;
            if (offset < length && (text[offset] == 43 || text[offset] == 45)) {
               offset++;
            }

            int exponent = offset;

            while (offset < length && digit(text[offset])) {
               offset++;
            }

            if (offset != exponent) {
               consumed = offset;
            }
         }

         byte[] prefix = Arrays.copyOf(text, consumed);

         for (int i = 0; i < prefix.length; i++) {
            if ((prefix[i] & 255) == this.decimal) {
               prefix[i] = 46;
            }
         }

         return new RecoveredJsonRuntime.ParsedNumber(Double.parseDouble(new String(prefix, StandardCharsets.US_ASCII)), consumed);
      }
   }

   private static boolean digit(byte value) {
      return value >= 48 && value <= 57;
   }
}
