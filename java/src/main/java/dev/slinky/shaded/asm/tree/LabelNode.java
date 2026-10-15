package dev.slinky.shaded.asm.tree;

import dev.slinky.shaded.asm.Label;
import dev.slinky.shaded.asm.MethodVisitor;
import java.util.Map;

public class LabelNode extends AbstractInsnNode {
   private Label value;

   public LabelNode() {
      super(-1);
   }

   public LabelNode(Label label) {
      super(-1);
      this.value = label;
   }

   @Override
   public int getType() {
      return 8;
   }

   public Label getLabel() {
      if (this.value == null) {
         this.value = new Label();
      }

      return this.value;
   }

   @Override
   public void accept(MethodVisitor methodVisitor) {
      methodVisitor.visitLabel(this.getLabel());
   }

   @Override
   public AbstractInsnNode clone(Map<LabelNode, LabelNode> clonedLabels) {
      return clonedLabels.get(this);
   }

   public void resetLabel() {
      this.value = null;
   }
}
