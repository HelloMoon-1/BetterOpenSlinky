package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ItemPolicyService;
import dev.slinky.client.inventory.ConsumableAccess;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.inventory.ItemActionHelper.Result;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.NumberSetting;

public final strictfp class AutoSoupModule extends EvidenceModule {
   private final AutoSoupModule.State state = new AutoSoupModule.State();
   private final ItemActionHelper access;

   public AutoSoupModule(ModuleManager modules) {
      super("autosoup", "Auto Soup", ModuleCategory.UTILITY);
      this.access = new ItemActionHelper(this, modules);
      this.itemPolicy("allowed_items");
      // Retain parsing of old profiles without exposing or requiring a setting
      // that no longer affects deterministic live action timing.
      this.findSetting("randomization").parseAndSet("0");
      this.findSetting("randomization").saveCurrent();
   }

   @Override
   public boolean settingVisible(String key) {
      return !"randomization".equals(key);
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.access.context = c;
      this.access.cleanup();
      this.state.enable();
   }

   @Override
   public strictfp void onClientInputTick(ClientContext c) {
      this.access.context = c;
      this.state.tick(
         this.access,
         new AutoSoupModule.Policy() {
            @Override
            public strictfp boolean matches(int type) {
               return AutoSoupModule.this.flag("override_item")
                  ? ItemPolicyService.contains(AutoSoupModule.this.names("allowed_items"), type)
                  : type == 282 || type == 413;
            }

            @Override
            public strictfp boolean skipCandidate() {
               return false;
            }

            @Override
            public strictfp float delay(float base) {
               return Math.max(0.0F, base);
            }
         },
         this.number("single_soup_max_hp"), this.number("double_soup_max_hp"),
         this.number("delay_before"), this.number("delay_between"), this.number("delay_after"), this.flag("drop_bowls")
      );
   }

   @Override
   public strictfp boolean afterSettingControl(String key, ClientContext c) {
      if (!"single_soup_max_hp".equals(key)) {
         return false;
      } else {
         NumberSetting single = (NumberSetting)this.findSetting(key);
         NumberSetting twice = (NumberSetting)this.findSetting("double_soup_max_hp");
         if (single.isConfigured() && twice.isConfigured() && twice.floatValue() > single.floatValue()) {
            twice.set(Double.valueOf((double)single.floatValue()));
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.access.context = c;
      this.access.cleanup();
      this.state.enable();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.access.forget();
      this.state.enable();
   }

   public interface Policy {
      boolean matches(int type);

      /** Retained for state-fixture compatibility; live selection never skips candidates. */
      boolean skipCandidate();

      float delay(float base);
   }

   public static final class State {
      public int phase;
      public int selected = -1;
      public int previous = -1;
      public boolean cancel;
      public boolean reuse;
      public boolean repeat;
      public boolean soup;
      public float timer;
      private boolean acquired;
      private boolean suspended;
      private boolean confirmed;
      private float healthBefore;
      private int waiting;

      public strictfp void enable() {
         this.phase = 0;
         this.selected = -1;
         this.previous = -1;
         this.cancel = false;
         this.reuse = false;
         this.repeat = false;
         this.soup = false;
         this.timer = 0.0F;
         this.acquired = false;
         this.suspended = false;
         this.confirmed = false;
         this.healthBefore = 0.0F;
         this.waiting = 0;
      }

      public strictfp boolean cancelDisable() {
         // Compatibility for state fixtures. A disable inquiry is reversible:
         // release -> press may remove it before onDisable actually commits.
         return false;
      }

      private strictfp void finish(ConsumableAccess a, float cooldown, boolean suspend) {
         if (this.acquired) {
            ItemActionHelper.finish(a, this.previous);
            this.acquired = false;
         } else {
            a.release();
         }
         this.phase = 0;
         this.reuse = false;
         this.repeat = false;
         this.waiting = 0;
         this.timer = Math.max(0.0F, cooldown);
         this.suspended |= suspend;
      }

      private strictfp boolean confirm(ConsumableAccess a, AutoSoupModule.Policy p, float after) {
         if (this.confirmed) {
            return true;
         }
         if (this.soup && a.type(this.selected) == 281 || a.health() > this.healthBefore || ItemActionHelper.consumed(a)) {
            this.confirmed = true;
            this.waiting = 0;
            return true;
         }
         // Dispatch is not acknowledgement. In vanilla, food still needs its
         // ordinary held-use duration; this module does not fake that hold or
         // repeatedly click when a server declines instant soup consumption.
         if (++this.waiting >= 20) {
            this.finish(a, p.delay(after), true);
         }
         return false;
      }

      public strictfp void tick(ConsumableAccess a, AutoSoupModule.Policy p, float single, float twice, float before, float between, float after, boolean drop) {
         if (this.cancel) {
            this.finish(a, 0.0F, false);
            a.disable();
            return;
         }
         if (!a.ready()) {
            this.finish(a, 0.0F, this.acquired);
            return;
         }
         // Timers cannot hide a user's input/slot change or a lost transaction.
         if (this.acquired && (!ItemActionHelper.intact(a) || a.current() != this.selected)) {
            ItemActionHelper.abandon(a);
            this.acquired = false;
            this.finish(a, 0.0F, true);
            return;
         }
         if (this.suspended) {
            // Do not repeatedly steal the slot/click after a cancelled or failed
            // action while the same low-health trigger is still asserted.
            if (a.health() <= single) {
               return;
            }
            this.suspended = false;
         }
         if (this.timer > 0.0F) {
            this.timer = Math.max(0.0F, this.timer - 1.0F);
            if (this.timer > 0.0F) {
               return;
            }
         }
         // Only bookkeeping falls through. Every dispatched click returns,
         // and the helper still enforces vanilla's right-click cooldown.
         while (true) {
            switch (this.phase) {
               case 0:
                  float hp = a.health();
                  if (hp > single) {
                     return;
                  }
                  this.repeat = hp <= twice;
                  this.phase = 1;
                  this.timer = Math.max(0.0F, p.delay(before));
                  if (this.timer > 0.0F) {
                     return;
                  }
                  continue;
               case 1:
                  if (a.health() > single) {
                     this.finish(a, p.delay(after), false);
                     return;
                  }
                  int start = a.current();
                  int found = -1;
                  for (int i = 0; i < 9; i++) {
                     int candidate = (start + i) % 9;
                     if ((candidate != start || !this.reuse) && p.matches(a.type(candidate))) {
                        found = candidate;
                        break;
                     }
                  }
                  if (found == -1) {
                     this.finish(a, Math.max(4.0F, p.delay(after)), false);
                     return;
                  }
                  // Claim even when the soup is already in the current slot.
                  if (!a.claim()) {
                     this.finish(a, 4.0F, false);
                     return;
                  }
                  if (!this.acquired) {
                     this.previous = a.current();
                     a.remember();
                     this.acquired = true;
                  }
                  this.selected = found;
                  this.soup = a.type(found) == 282 || a.type(found) == 413;
                  this.reuse = false;
                  a.select(found);
                  this.phase = 3;
                  this.waiting = 0;
                  this.timer = Math.max(0.0F, p.delay(between));
                  if (this.timer > 0.0F) {
                     return;
                  }
                  continue;
               case 3:
                  if (!p.matches(a.type(this.selected)) || !ItemActionHelper.unchanged(a)) {
                     this.finish(a, p.delay(after), true);
                     return;
                  }
                  if (a.usingItem()) {
                     if (++this.waiting >= 20) {
                        this.finish(a, p.delay(after), true);
                     }
                     return;
                  }
                  this.healthBefore = a.health();
                  Result result = ItemActionHelper.useOnce(a);
                  if (result == Result.WAIT) {
                     if (++this.waiting >= 20) {
                        this.finish(a, p.delay(after), true);
                     }
                     return;
                  }
                  if (result != Result.USED) {
                     this.finish(a, p.delay(after), true);
                     return;
                  }
                  this.confirmed = false;
                  this.waiting = 0;
                  this.phase = drop && this.soup ? 4 : 5;
                  this.timer = Math.max(0.0F, p.delay(between));
                  return;
               case 4:
                  if (!this.confirm(a, p, after)) {
                     return;
                  }
                  // Drop only the verified bowl, never a queued key that could
                  // later affect the restored weapon or the next soup.
                  if (a.type(this.selected) == 281) {
                     a.drop();
                  }
                  this.phase = 5;
                  this.timer = Math.max(0.0F, p.delay(between));
                  if (this.timer > 0.0F) {
                     return;
                  }
                  continue;
               case 5:
                  if (!this.confirm(a, p, after)) {
                     return;
                  }
                  if (this.repeat && a.health() <= single) {
                     this.repeat = false;
                     this.reuse = true;
                     this.phase = 1;
                     continue;
                  }
                  this.finish(a, p.delay(after), false);
                  return;
               default:
                  this.finish(a, p.delay(after), true);
                  return;
            }
         }
      }
   }
}
