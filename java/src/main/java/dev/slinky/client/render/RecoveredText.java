package dev.slinky.client.render;

import java.nio.charset.StandardCharsets;

public final class RecoveredText {
   private final byte[] bytes;
   private int count;

   public RecoveredText(int capacity) {
      this.bytes = new byte[capacity];
   }

   public boolean append(String value) {
      byte[] source = value.getBytes(StandardCharsets.UTF_8);
      if (this.count < this.bytes.length && source.length < this.bytes.length - this.count) {
         System.arraycopy(source, 0, this.bytes, this.count, source.length);
         this.count += source.length;
         return true;
      } else {
         return false;
      }
   }

   public boolean formatResult(String value) {
      byte[] source = value.getBytes(StandardCharsets.UTF_8);
      if (this.count >= this.bytes.length) {
         return false;
      } else if (source.length >= this.bytes.length - this.count) {
         this.count = this.bytes.length;
         return false;
      } else {
         System.arraycopy(source, 0, this.bytes, this.count, source.length);
         this.count += source.length;
         return true;
      }
   }

   public boolean appendByte(int value) {
      if (this.count >= this.bytes.length) {
         return false;
      } else {
         this.bytes[this.count++] = (byte)value;
         return true;
      }
   }

   public String finish() {
      if (this.count >= this.bytes.length) {
         return null;
      } else {
         this.bytes[this.count++] = 0;
         return new String(this.bytes, 0, this.count - 1, StandardCharsets.UTF_8);
      }
   }

   public int count() {
      return this.count;
   }
}
