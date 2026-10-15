package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.inventory.ItemActionHelper.Result;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class AutoRodModule extends EvidenceModule {
   private final ModuleManager modules;
   private final WeaponsModule weapons;
   private final ItemActionHelper action;
   private int phase;
   private int previous = -1;
   private int selected = -1;
   private int retract;
   private int waiting;
   private float timer;
   private boolean rod;
   private boolean acquired;
   private boolean interrupted;
   private boolean hookSeen;
   private Entity hook;

   public AutoRodModule(ModuleManager modules, WeaponsModule weapons) {
      super("autorod", "Auto Rod", ModuleCategory.UTILITY);
      this.modules = modules;
      this.weapons = weapons;
      this.action = new ItemActionHelper(this, modules);
      // Compatibility-only setting: an inert hidden option must not block
      // enabling or resetting a profile that has no saved value for it.
      this.findSetting("randomize_delays").parseAndSet("false");
      this.findSetting("randomize_delays").saveCurrent();
   }

   @Override
   public boolean settingVisible(String key) {
      return !"randomize_delays".equals(key);
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.action.context = c;
      this.action.cleanup();
      this.reset();
   }

   private strictfp void reset() {
      this.phase = 0;
      this.previous = -1;
      this.selected = -1;
      this.retract = 0;
      this.waiting = 0;
      this.timer = 0.0F;
      this.rod = false;
      this.acquired = false;
      this.interrupted = false;
      this.hookSeen = false;
      this.hook = null;
   }

   private strictfp void finish(boolean interrupted) {
      // Restore only while this exact player/world/inventory/slot is owned.
      // Manual input or selection must never be overwritten by cleanup.
      this.action.cleanup();
      this.acquired = false;
      this.phase = 6;
      this.interrupted |= interrupted;
      this.waiting = 0;
      this.hook = null;
      this.timer = Math.max(0.0F, this.rod ? this.delay() : this.throwingDelay());
      if (interrupted || !this.isHoldBinding()) {
         this.modules.requestEnabled(this, false);
      }
   }

   private strictfp float delay() {
      return this.number("delay");
   }

   public static strictfp int choose(int[] types, boolean throwables) {
      int candidate = -1;
      for (int i = 0; i < 9; i++) {
         if ((types[i] == 332 || types[i] == 344) && throwables) {
            return i;
         }
         if (types[i] == 346) {
            candidate = i;
            if (!throwables) {
               return i;
            }
         }
      }
      return candidate;
   }

   private strictfp int scan(InventoryPlayer inventory) {
      int[] types = new int[9];
      for (int i = 0; i < 9; i++) {
         types[i] = InventoryActions.type(inventory.getStackInSlot(i));
      }
      int slot = choose(types, this.flag("allow_throwables"));
      this.rod = slot >= 0 && types[slot] == 346;
      return slot;
   }

   public static strictfp int retractTicks(Vec3 origin, Vec3 target, float speed) {
      if (!(speed > 0.0F)) {
         return 1;
      }
      float delay;
      if (target == null) {
         delay = 10.0F / speed;
      } else {
         Vec3 closest = new Vec3(
            RotationMath.clamp(target.xCoord - 0.3, target.xCoord + 0.3, origin.xCoord),
            RotationMath.clamp(target.yCoord, target.yCoord + 1.8, origin.yCoord),
            RotationMath.clamp(target.zCoord - 0.3, target.zCoord + 0.3, origin.zCoord)
         );
         delay = (float)(RotationMath.distance(origin, closest) / (double)speed);
      }
      return Math.max(1, RecoveredMath.truncateInt(RecoveredMath.ceiling(delay)));
   }

   private strictfp float throwingDelay() {
      float speed = this.number("throw_speed");
      return speed > 0.0F ? 20.0F / speed : 20.0F;
   }

   private strictfp boolean castBlocked(ClientContext c) {
      // Melee range restricts new casts/throws, not reeling a deployed hook.
      if (this.rod && c.player().fishEntity != null) {
         return false;
      }
      MovingObjectPosition hit = c.minecraft().objectMouseOver;
      return this.flag("require_not_mouse_over") && hit != null && hit.typeOfHit == MovingObjectType.ENTITY;
   }

   @Override
   public strictfp void onClientInputTick(ClientContext c) {
      this.action.context = c;
      if (!this.action.ready()) {
         this.finish(true);
         return;
      }
      if (this.acquired && (!this.action.valid() || this.action.current() != this.selected)) {
         this.finish(true);
         return;
      }
      if (this.interrupted) {
         return;
      }
      if (this.phase == 3 && this.rod && c.player().fishEntity != null && !this.hookSeen) {
         this.hookSeen = true;
         this.hook = c.player().fishEntity;
         this.waiting = 0;
      }
      if (this.retract > 0) {
         this.retract--;
      }
      if (this.timer > 0.0F) {
         this.timer = Math.max(0.0F, this.timer - 1.0F);
         if (this.timer > 0.0F) {
            return;
         }
      }
      // Selection/bookkeeping can complete without an artificial warmup tick.
      // Every actual vanilla click returns; its ordinary cooldown still applies.
      while (true) {
         switch (this.phase) {
            case 0:
               int slot = this.scan(c.player().inventory);
               if (slot < 0 || this.castBlocked(c)) {
                  this.finish(true);
                  return;
               }
               if (!this.action.claim()) {
                  this.timer = 4.0F;
                  return;
               }
               this.action.remember();
               this.acquired = true;
               this.previous = this.action.current();
               this.selected = slot;
               if (this.flag("always_switch_to_weapon")) {
                  int weapon = this.weapons.firstAllowedSlot(c.player().inventory);
                  if (weapon != -1) {
                     this.previous = weapon;
                     this.action.restoreTo(weapon);
                  }
               }
               this.action.select(slot);
               this.phase = 2;
               this.waiting = 0;
               this.timer = Math.max(0.0F, this.delay());
               if (this.timer > 0.0F) {
                  return;
               }
               continue;
            case 1:
               if (!this.action.unchanged()) {
                  this.finish(true);
                  return;
               }
               int next = this.scan(c.player().inventory);
               if (next < 0 || this.castBlocked(c)) {
                  this.finish(true);
                  return;
               }
               boolean switched = next != this.selected;
               this.selected = next;
               this.action.select(next);
               this.phase = 2;
               this.waiting = 0;
               this.timer = switched ? Math.max(0.0F, this.delay()) : 0.0F;
               if (this.timer > 0.0F) {
                  return;
               }
               continue;
            case 2:
               int type = this.action.type(this.selected);
               if (!this.action.unchanged() || !(type == 346 || this.flag("allow_throwables") && (type == 332 || type == 344))
                  || this.castBlocked(c)) {
                  this.finish(true);
                  return;
               }
               Entity existingHook = c.player().fishEntity;
               Result result = ItemActionHelper.useOnce(this.action);
               if (result == Result.WAIT) {
                  if (++this.waiting >= 20) {
                     this.finish(true);
                  }
                  return;
               }
               if (result != Result.USED) {
                  this.finish(true);
                  return;
               }
               if (!this.rod && this.isHoldBinding()) {
                  if (!ItemActionHelper.consumed(this.action)) {
                     // A chest/entity interaction is not a throwable burst.
                     this.finish(true);
                     return;
                  }
                  // Preserve the original restoration slot across a held burst;
                  // resnapshot the locally consumed stack before the next shot.
                  this.action.select(this.selected);
                  this.phase = 1;
                  this.timer = Math.max(0.0F, this.throwingDelay());
                  return;
               }
               this.waiting = 0;
               // Rod feedback must not wait on an extra timer. The reel still
               // goes through useOnce and its ordinary vanilla click cooldown.
               this.timer = this.rod ? 0.0F : Math.max(0.0F, this.delay());
               if (this.rod) {
                  this.action.select(this.selected);
                  if (existingHook != null) {
                     // Vanilla right click reels an already-deployed rod. Wait
                     // for that removal instead of accidentally casting again.
                     this.hook = existingHook;
                     this.phase = 4;
                  } else {
                     this.hook = c.player().fishEntity;
                     this.hookSeen = this.hook != null;
                     this.phase = 3;
                     Entity target = GameHooks.target();
                     Vec3 targetPos = target == null ? null : new Vec3(target.posX, target.posY, target.posZ);
                     this.retract = retractTicks(
                        new Vec3(c.player().posX, c.player().posY + (double)c.player().getEyeHeight(), c.player().posZ),
                        targetPos, this.number("retract_speed")
                     );
                  }
               } else {
                  this.phase = 3;
                  if (this.timer == 0.0F && ItemActionHelper.consumed(this.action)) {
                     this.finish(false);
                  }
               }
               return;
            case 3:
               if (!this.rod) {
                  this.finish(false);
                  return;
               }
               if (!this.hookSeen) {
                  // A declined/lost cast gets no retry for this activation.
                  if (++this.waiting >= 20) {
                     this.finish(true);
                  }
                  return;
               }
               if (c.player().fishEntity == null) {
                  this.finish(false);
                  return;
               }
               if (c.player().fishEntity != this.hook || !this.action.unchanged()) {
                  this.finish(true);
                  return;
               }
               if (this.retract > 0) {
                  return;
               }
               Result reel = ItemActionHelper.useOnce(this.action);
               if (reel == Result.WAIT) {
                  if (++this.waiting >= 20) {
                     this.finish(true);
                  }
                  return;
               }
               if (reel != Result.USED) {
                  this.finish(true);
                  return;
               }
               // Retract with the same ordinary vanilla interaction, not an
               // invented switch-away/switch-back pair when the rod was held.
               this.phase = 4;
               this.waiting = 0;
               this.timer = 0.0F;
               return;
            case 4:
               if (c.player().fishEntity == null) {
                  this.finish(false);
               } else if (c.player().fishEntity != this.hook || ++this.waiting >= 20) {
                  this.finish(true);
               }
               return;
            case 6:
               if (this.isHoldBinding()) {
                  this.phase = 0;
                  this.hookSeen = false;
                  continue;
               }
               this.modules.requestEnabled(this, false);
               return;
            default:
               this.finish(true);
               return;
         }
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.action.context = c;
      this.action.cleanup();
      this.reset();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.action.forget();
      this.reset();
   }
}
