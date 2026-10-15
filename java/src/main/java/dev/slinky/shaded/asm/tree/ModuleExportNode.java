package dev.slinky.shaded.asm.tree;

import dev.slinky.shaded.asm.ModuleVisitor;
import java.util.List;

public class ModuleExportNode {
   public String packaze;
   public int access;
   public List<String> modules;

   public ModuleExportNode(String packaze, int access, List<String> modules) {
      this.packaze = packaze;
      this.access = access;
      this.modules = modules;
   }

   public void accept(ModuleVisitor moduleVisitor) {
      moduleVisitor.visitExport(this.packaze, this.access, this.modules == null ? null : this.modules.toArray(new String[0]));
   }
}
