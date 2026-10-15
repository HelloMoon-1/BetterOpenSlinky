package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.inventory.ConsumableAccess;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.inventory.ItemActionHelper.Result;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;

public final strictfp class AutoPotModule extends EvidenceModule {
   private final AutoPotModule.State state = new AutoPotModule.State();
   private final ItemActionHelper access;

   public AutoPotModule(ModuleManager modules) {
      super("autopot", "Auto Pot", ModuleCategory.UTILITY);
      this.access = new ItemActionHelper(this, modules);
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.access.context = c;
      this.access.cleanup();
      this.state.enable(this.access, this.flag("prevent_waste"), this.flag("double_splash"));
   }

   @Override
   public strictfp void onClientInputTick(ClientContext c) {
      // Once per tick, after manual input and before world/movement updates.
      // The vanilla click uses the player's ordinary input-phase rotation.
      this.access.context = c;
      this.state.tick(this.access, () -> this.number("delay"), this.flag("use_non_health"));
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.access.context = c;
      this.access.cleanup();
      this.state.reset();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.access.forget();
      this.state.reset();
   }

   public interface Delay {
      float sample();
   }

   public static final class State {
      public int phase;
      public int uses;
      public int pending;
      public int selected = -1;
      public int previous = -1;
      public boolean continuing;
      public float timer;
      private boolean acquired;
      private boolean preventWaste;
      private float healthBefore;
      private int waiting;

      public strictfp void reset() {
         this.phase = 0;
         this.uses = 0;
         this.pending = 0;
         this.selected = -1;
         this.previous = -1;
         this.continuing = false;
         this.timer = 0.0F;
         this.acquired = false;
         this.preventWaste = false;
         this.healthBefore = 0.0F;
         this.waiting = 0;
      }

      public strictfp void enable(ConsumableAccess a, boolean prevent, boolean multiple) {
         this.reset();
         this.preventWaste = prevent;
         if (!a.ready()) {
            this.phase = 4;
            return;
         }
         int count = InventoryActions.truncateDouble(((double)(a.maximumHealth() - a.health()) + 0.5) * 0.125);
         if (count <= 0 && prevent) {
            this.phase = 4;
         } else {
            this.uses = multiple ? Math.max(1, count) : 1;
         }
      }

      public strictfp boolean cancelDisable() {
         // Compatibility for state fixtures. A disable inquiry is reversible:
         // do not clear uses/pending until the lifecycle actually disables us.
         return false;
      }

      private strictfp void finish(ConsumableAccess a) {
         this.phase = 4;
         this.uses = 0;
         this.pending = 0;
         this.continuing = false;
         if (this.acquired) {
            ItemActionHelper.finish(a, this.previous);
            this.acquired = false;
         } else {
            a.release();
         }
         a.disable();
      }

      private static strictfp boolean potion(ConsumableAccess a, int slot, boolean nonHealth) {
         return a.type(slot) == 373 && (a.damage(slot) & 16384) != 0 && ((a.damage(slot) & 15) == 5 || nonHealth);
      }

      public strictfp void tick(ConsumableAccess a, AutoPotModule.Delay delay, boolean nonHealth) {
         if (this.phase == 4 || !a.ready()) {
            this.finish(a);
            return;
         }
         // Validate even during delays; a manual input/slot change wins immediately.
         if (this.acquired && (!ItemActionHelper.intact(a) || a.current() != this.selected)) {
            ItemActionHelper.abandon(a);
            this.acquired = false;
            this.finish(a);
            return;
         }
         if (this.timer > 0.0F) {
            this.timer = Math.max(0.0F, this.timer - 1.0F);
            if (this.timer > 0.0F) {
               return;
            }
         }
         // Zero-delay bookkeeping may advance in this callback. A dispatched
         // click always returns, so the loop cannot emit several uses in a tick.
         while (true) {
            switch (this.phase) {
               case 0:
                  if (this.uses <= 0 || this.acquired && this.preventWaste && a.health() >= a.maximumHealth()) {
                     this.finish(a);
                     return;
                  }
                  int slot = 0;
                  while (slot < 9 && !potion(a, slot, nonHealth)) {
                     slot++;
                  }
                  if (slot == 9 || !a.claim()) {
                     this.finish(a);
                     return;
                  }
                  if (!this.acquired) {
                     this.previous = a.current();
                     a.remember();
                     this.acquired = true;
                  }
                  this.selected = slot;
                  a.select(slot);
                  this.phase = 2;
                  this.continuing = false;
                  this.waiting = 0;
                  this.timer = Math.max(0.0F, delay.sample());
                  if (this.timer > 0.0F) {
                     return;
                  }
                  continue;
               case 2:
                  if (!potion(a, this.selected, nonHealth) || !ItemActionHelper.unchanged(a)) {
                     this.finish(a);
                     return;
                  }
                  if (a.usingItem()) {
                     if (++this.waiting >= 20) {
                        this.finish(a);
                     }
                     return;
                  }
                  this.healthBefore = a.health();
                  Result result = ItemActionHelper.useOnce(a);
                  if (result == Result.WAIT) {
                     if (++this.waiting >= 20) {
                        this.finish(a);
                     }
                     return;
                  }
                  if (result != Result.USED) {
                     this.finish(a);
                     return;
                  }
                  this.uses--;
                  this.pending = 1;
                  this.waiting = 0;
                  this.phase = 3;
                  this.timer = Math.max(0.0F, delay.sample());
                  if (this.uses == 0 && this.timer == 0.0F && ItemActionHelper.consumed(a)) {
                     // The synchronous vanilla click already consumed the last
                     // potion locally; no queued input can hit the restored slot.
                     this.finish(a);
                  }
                  return;
               case 3:
                  // An entity/block interaction or rejected click is not a
                  // reason to throw again. Require consumption/health feedback
                  // before continuing a requested multi-potion sequence.
                  if (!ItemActionHelper.consumed(a) && a.health() <= this.healthBefore) {
                     if (++this.waiting >= 20) {
                        this.finish(a);
                     }
                     return;
                  }
                  this.pending = 0;
                  if (this.uses > 0) {
                     this.continuing = true;
                     this.phase = 0;
                     continue;
                  }
                  // Restore and synchronize before releasing the shared claim.
                  this.finish(a);
                  return;
               default:
                  this.finish(a);
                  return;
            }
         }
      }
   }
}
