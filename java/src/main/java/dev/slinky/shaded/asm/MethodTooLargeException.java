package dev.slinky.shaded.asm;

public final class MethodTooLargeException extends IndexOutOfBoundsException {
   private static final long serialVersionUID = 6807380416709738314L;
   private final String className;
   private final String methodName;
   private final String descriptor;
   private final int codeSize;

   public MethodTooLargeException(String className, String methodName, String descriptor, int codeSize) {
      super(stringConcat$0(className, methodName, descriptor));
      this.className = className;
      this.methodName = methodName;
      this.descriptor = descriptor;
      this.codeSize = codeSize;
   }

   public String getClassName() {
      return this.className;
   }

   public String getMethodName() {
      return this.methodName;
   }

   public String getDescriptor() {
      return this.descriptor;
   }

   public int getCodeSize() {
      return this.codeSize;
   }

   // Synthetic string-concatenation helpers recovered with CFR.
   private static String stringConcat$0(String string, String string2, String string3) {
        return "Method too large: " + string + "." + string2 + " " + string3;
    }
}
