package dev.slinky.client;

import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Iterator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class BindingInput {
   private final byte[] keyBits = new byte[104];
   private final Map<Module, Integer> requested = new IdentityHashMap<>();

   public boolean update(int nativeId, boolean down, boolean toggle) {
      return this.update(nativeId, down ? 1 : 0, toggle ? 1 : 0);
   }

   public boolean update(int nativeId, int requestedByte, int toggleByte) {
      if (nativeId >= 0 && nativeId < this.keyBits.length) {
         int old = this.keyBits[nativeId] & 255;
         int bit = old & 1;
         int requested = requestedByte & 0xFF;
         int next = requested;
         boolean unchanged = true;
         if ((toggleByte & 0xFF) != 1 || bit != requested) {
            if ((toggleByte & 0xFF) == 1) {
               next = (requested | old & 2) ^ 2;
            }

            this.keyBits[nativeId] = (byte)next;
            unchanged = bit == requested;
         }

         return unchanged;
      } else {
         throw new IllegalArgumentException("Binding ID outside the recovered key table");
      }
   }

   public boolean dispatch(ModuleManager manager, int nativeId, boolean down) {
      if (nativeId == 0) {
         return false;
      }

      boolean accepted = false;
      if (!down) {
         Iterator<Map.Entry<Module, Integer>> held = this.requested.entrySet().iterator();
         while (held.hasNext()) {
            Map.Entry<Module, Integer> entry = held.next();
            if (entry.getValue() == nativeId) {
               manager.requestEnabled(entry.getKey(), false);
               held.remove();
               accepted = true;
            }
         }
         return accepted;
      }

      for (Module module : manager.all()) {
         if (module.getBinding() == nativeId) {
            if (module.isHoldBinding()) {
               manager.requestEnabled(module, true);
               this.requested.put(module, nativeId);
            } else {
               manager.requestEnabled(module, !manager.requestedEnabled(module));
            }
            accepted = true;
         }
      }
      return accepted;
   }

   private static boolean physical(int nativeId) {
      int code = NativeKeyMap.gameCode(nativeId);
      return code < 0
         ? Mouse.isCreated() && Mouse.isButtonDown(code + 100)
         : code > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(code);
   }

   public void releaseLostHolds(ModuleManager manager) {
      // A lost key-up must not leave the next physical press marked unchanged,
      // even if its held module was already released while the window was inactive.
      for (int nativeId = 1; nativeId < this.keyBits.length; nativeId++) {
         if ((this.keyBits[nativeId] & 1) != 0 && !physical(nativeId)) {
            this.keyBits[nativeId] &= 2;
         }
      }
      boolean focused = manager.context().inputReady() && Display.isActive();
      boolean changed = false;
      Iterator<Map.Entry<Module, Integer>> held = this.requested.entrySet().iterator();
      while (held.hasNext()) {
         Map.Entry<Module, Integer> entry = held.next();
         Module module = entry.getKey();
         if (!focused || !physical(entry.getValue()) || !module.isHoldBinding() || module.getBinding() != entry.getValue()) {
            manager.requestEnabled(module, false);
            held.remove();
            changed = true;
         }
      }
      if (changed) {
         manager.persist();
      }
   }

   public boolean lastRequested(Module module) {
      return this.requested.containsKey(module);
   }

   public int bits(int nativeId) {
      return this.keyBits[nativeId] & 0xFF;
   }

   public void releaseAll(ModuleManager manager) {
      boolean changed = !this.requested.isEmpty();
      for (Module module : this.requested.keySet()) {
         manager.requestEnabled(module, false);
      }
      this.requested.clear();
      if (changed) {
         manager.persist();
      }
   }

   public void clear() {
      Arrays.fill(this.keyBits, (byte)0);
      this.requested.clear();
   }
}
