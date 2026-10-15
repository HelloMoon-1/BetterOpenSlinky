package dev.slinky.client.render;

import dev.slinky.client.KeyState;
import dev.slinky.module.Module;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

public final class RenderModuleAccess {
   private static volatile IntFunction<String> keys = KeyState::displayName;
   private static volatile BooleanSupplier guiInteraction = () -> false;

   public static int category(Module module) {
      return module.metadataCategory();
   }

   public static int observedFlags(Module module) {
      return module.descriptorFlags();
   }

   public static String status(Module module) {
      return module.statusText();
   }

   public static String keyName(int key) {
      return keys.apply(key);
   }

   public static IntFunction<String> bindKeyNames(IntFunction<String> adapter) {
      IntFunction<String> previous = keys;
      keys = Objects.requireNonNull(adapter);
      return previous;
   }

   public static boolean guiInteractionRequested() {
      return guiInteraction.getAsBoolean();
   }

   public static void bindGuiInteraction(BooleanSupplier query) {
      guiInteraction = Objects.requireNonNull(query);
   }

   private RenderModuleAccess() {
   }
}
