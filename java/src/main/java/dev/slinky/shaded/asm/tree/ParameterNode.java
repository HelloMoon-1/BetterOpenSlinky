package dev.slinky.shaded.asm.tree;

import dev.slinky.shaded.asm.MethodVisitor;

public class ParameterNode {
   public String name;
   public int access;

   public ParameterNode(String name, int access) {
      this.name = name;
      this.access = access;
   }

   public void accept(MethodVisitor methodVisitor) {
      methodVisitor.visitParameter(this.name, this.access);
   }
}
