package dev.slinky.shaded.asm;

public final class ClassTooLargeException extends IndexOutOfBoundsException {
   private static final long serialVersionUID = 160715609518896765L;
   private final String className;
   private final int constantPoolCount;

   public ClassTooLargeException(String className, int constantPoolCount) {
      super(stringConcat$0(className));
      this.className = className;
      this.constantPoolCount = constantPoolCount;
   }

   public String getClassName() {
      return this.className;
   }

   public int getConstantPoolCount() {
      return this.constantPoolCount;
   }

   // Synthetic string-concatenation helpers recovered with CFR.
   private static String stringConcat$0(String string) {
        return "Class too large: " + string;
    }
}
