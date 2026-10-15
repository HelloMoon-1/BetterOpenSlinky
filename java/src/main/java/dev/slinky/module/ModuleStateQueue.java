package dev.slinky.module;

import java.util.ArrayList;
import java.util.List;

public final class ModuleStateQueue<T> {
   private final ModuleStateQueue.Lifecycle<T> lifecycle;
   private final List<T> enables = new ArrayList<>();
   private final List<T> disables = new ArrayList<>();
   private boolean applying;

   public ModuleStateQueue(ModuleStateQueue.Lifecycle<T> lifecycle) {
      this.lifecycle = lifecycle;
   }

   public synchronized boolean request(T module, boolean enabled) {
      if (this.requestedEnabled(module) == enabled) {
         return true;
      }

      if (!enabled && this.lifecycle.enabled(module) && this.lifecycle.cancelDisable(module)) {
         return false;
      }

      this.enables.remove(module);
      this.disables.remove(module);
      if (this.lifecycle.enabled(module) != enabled) {
         (enabled ? this.enables : this.disables).add(module);
      }
      return true;
   }

   public synchronized boolean requestedEnabled(T module) {
      if (this.enables.contains(module)) {
         return true;
      }
      return !this.disables.contains(module) && this.lifecycle.enabled(module);
   }

   public synchronized void apply() {
      if (this.applying) {
         return;
      }

      this.applying = true;
      try {
         List<T> enabling = new ArrayList<>(this.enables);
         this.enables.clear();
         enabling.removeIf(this.lifecycle::enabled);
         for (T module : enabling) {
            this.lifecycle.mark(module, true);
         }
         for (T module : enabling) {
            this.lifecycle.notifyChange(module, true);
         }

         this.lifecycle.beforeDisableBatch();
         List<T> disabling = new ArrayList<>(this.disables);
         this.disables.clear();
         disabling.removeIf(module -> !this.lifecycle.enabled(module));
         for (T module : disabling) {
            this.lifecycle.mark(module, false);
         }
         for (T module : disabling) {
            this.lifecycle.notifyChange(module, false);
         }
      } finally {
         this.applying = false;
      }
   }

   public synchronized boolean pending() {
      return !this.enables.isEmpty() || !this.disables.isEmpty();
   }

   public synchronized void clear() {
      this.enables.clear();
      this.disables.clear();
   }

   public interface Lifecycle<T> {
      boolean enabled(T var1);

      boolean cancelDisable(T var1);

      void mark(T var1, boolean var2);

      void notifyChange(T var1, boolean var2);

      void beforeDisableBatch();
   }
}
