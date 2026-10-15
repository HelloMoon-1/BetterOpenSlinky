package dev.slinky.inject.transform;

import dev.slinky.inject.NativeBridge;
import dev.slinky.inject.Platform;
import dev.slinky.shaded.asm.ClassReader;
import dev.slinky.shaded.asm.ClassWriter;
import dev.slinky.shaded.asm.tree.ClassNode;
import dev.slinky.shaded.asm.tree.MethodNode;
import java.util.List;

public final class TransformerEngine {
   private int applied;
   private int failed;
   private int warnings;

   public void applyAll(List<SlinkyPatches.ClassEdits> patches) {
      for (SlinkyPatches.ClassEdits patch : patches) {
         try {
            this.apply(patch);
         } catch (Throwable var5) {
            this.failed++;
            Platform.log("Failed to patch " + patch.target, var5);
         }
      }

      Platform.log(String.format("Patched %d classes (%d failed, %d warnings)", this.applied, this.failed, this.warnings));
   }

   public int getFailed() {
      return this.failed;
   }

   private void warn(String message) {
      this.warnings++;
      Platform.log("WARN " + message);
   }

   private void apply(SlinkyPatches.ClassEdits patch) {
      String runtimeName = patch.getRuntimeTarget();

      Class<?> target;
      try {
         target = Class.forName(runtimeName.replace('/', '.'), false, Platform.getGameClassLoader());
      } catch (LinkageError | ClassNotFoundException var11) {
         this.failed++;
         Platform.log("Missing target class " + patch.target + " (" + runtimeName + ")");
         return;
      }

      byte[] original = NativeBridge.getClassBytes(target);
      if (original == null) {
         throw new IllegalStateException("Unable to capture bytecode of " + runtimeName);
      } else {
         ClassNode node = new ClassNode();
         new ClassReader(original).accept(node, 4);
         int changes = 0;

         for (SlinkyPatches.MethodEdit edit : patch.edits) {
            MethodNode method = patch.find(node, edit);
            if (method == null) {
               this.warn(patch.target + ": method not found for " + edit.describe());
            } else {
               int points = edit.apply(method);
               if (points == 0) {
                  this.warn(patch.target + ": no injection point for " + edit.describe());
               } else if (edit.expected >= 0 && points != edit.expected) {
                  this.warn(patch.target + ": " + points + " injection points (expected " + edit.expected + ") for " + edit.describe());
               }

               changes += points;
            }
         }

         if (patch.hasEveryMethodEdits()) {
            int points = patch.applyEveryMethod(node);
            if (points != patch.getEveryMethodExpected()) {
               this.warn(
                  patch.target + ": " + points + " injection points (expected " + patch.getEveryMethodExpected() + ") for " + patch.getEveryMethodLabel()
               );
            }

            changes += points;
         }

         if (changes != 0) {
            ClassWriter writer = new TransformerEngine.GameClassWriter(3);
            node.accept(writer);
            int error = NativeBridge.retransformClass(target, writer.toByteArray());
            if (error != 0) {
               throw new IllegalStateException(
                  "RetransformClasses returned " + error + (error == -1 ? " (load hook never matched the target)" : " (JVMTI error)")
               );
            } else {
               this.applied++;
            }
         }
      }
   }

   static final class GameClassWriter extends ClassWriter {
      GameClassWriter(int flags) {
         super(flags);
      }

      @Override
      protected String getCommonSuperClass(String first, String second) {
         ClassLoader loader = Platform.getGameClassLoader();

         try {
            Class<?> a = Class.forName(first.replace('/', '.'), false, loader);
            Class<?> b = Class.forName(second.replace('/', '.'), false, loader);
            if (a.isAssignableFrom(b)) {
               return first;
            } else if (b.isAssignableFrom(a)) {
               return second;
            } else if (!a.isInterface() && !b.isInterface()) {
               do {
                  a = a.getSuperclass();
               } while (!a.isAssignableFrom(b));

               return a.getName().replace('.', '/');
            } else {
               return "java/lang/Object";
            }
         } catch (Throwable var6) {
            return "java/lang/Object";
         }
      }
   }
}
