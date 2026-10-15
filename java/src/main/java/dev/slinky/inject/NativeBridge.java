package dev.slinky.inject;

public final class NativeBridge {
   private NativeBridge() {
   }

   public static native byte[] getClassBytes(Class<?> var0);

   public static native int retransformClass(Class<?> var0, byte[] var1);

   public static native void log(String var0);
}
