package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.numeric.DoubleNumeric;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.util.BlockPos;

public final strictfp class HitSelectModule extends EvidenceModule {
   private final HitSelectModule.State state = new HitSelectModule.State();
   private final WeaponsModule weapons;

   public HitSelectModule(WeaponsModule weapons) {
      super("hitselect", "Hit Select", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   private strictfp HitSelectModule.Input input(final ClientContext c) {
      return new HitSelectModule.Input() {
         @Override
         public strictfp boolean ready() {
            return c.inputReady();
         }

         @Override
         public strictfp int hurt() {
            return c.player().hurtTime;
         }

         @Override
         public strictfp boolean sprint() {
            return c.player().isSprinting();
         }

         @Override
         public strictfp boolean weapon() {
            return HitSelectModule.this.weapons.isWeapon(c.player());
         }

         @Override
         public strictfp int hitKind() {
            return ActionAccess.hitKind(c.minecraft().objectMouseOver);
         }

         @Override
         public strictfp boolean hasHit() {
            return c.minecraft().objectMouseOver != null;
         }

         @Override
         public strictfp boolean hitEntity() {
            return c.minecraft().objectMouseOver != null && c.minecraft().objectMouseOver.entityHit != null;
         }

         @Override
         public strictfp boolean ground() {
            return c.player().onGround;
         }

         @Override
         public strictfp double vertical() {
            return c.player().motionY;
         }

         @Override
         public strictfp float fallDistance() {
            return c.player().fallDistance;
         }

         @Override
         public strictfp boolean feetAir() {
            return ActionAccess.registeredAir(
               c.world(), new BlockPos(DoubleNumeric.floor(c.player().posX), DoubleNumeric.floor(c.player().posY), DoubleNumeric.floor(c.player().posZ))
            );
         }

         @Override
         public strictfp boolean using() {
            return c.player().isUsingItem();
         }

         @Override
         public strictfp void swing() {
            GameHooks.baseSwing(c.player());
         }

         @Override
         public strictfp boolean target() {
            return GameHooks.target() != null;
         }

         @Override
         public strictfp int targetId() {
            return GameHooks.target().getEntityId();
         }

         @Override
         public strictfp int targetHurt() {
            return GameHooks.target().hurtTime;
         }

         @Override
         public strictfp int ticks() {
            return c.player().ticksExisted;
         }

         @Override
         public strictfp int attackerTime() {
            return c.player().getLastAttackerTime();
         }
      };
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 14 || type == 17 || type == 26;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 26) {
         this.state.clear();
      } else if (!c.ready()) {
         this.state.clear();
      } else {
         HitSelectModule.Input i = this.input(c);
         if (e.type == 0) {
            this.state.tick(i);
         } else if (e.type == 17) {
            this.state.history(i, this.flag("use_server_time"));
         } else if (e.type == 14
            && e.active
            && this.state
               .filter(
                  i,
                  e.reducing,
                  this.choice("mode"),
                  this.flag("require_sprint"),
                  this.flag("require_weapon"),
                  this.number("cancel_rate_air"),
                  this.number("pause_dur"),
                  this.number("wait_for_damage_dur"),
                  this.number("trades_delay"),
                  this.flag("use_server_time"),
                  this.flag("crits_allow_reduce"),
                  this.flag("require_damage"),
                  this.number("cancel_rate"),
                  this.flag("fake_swing"),
                  RecoveredRandom.SHARED
               )) {
            e.cancelled = true;
         }
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.state.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.state.clear();
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         return StatusText.label(this.getName(), index);
      }
   }

   public interface Input {
      boolean ready();

      int hurt();

      boolean sprint();

      boolean weapon();

      boolean hasHit();

      int hitKind();

      boolean hitEntity();

      boolean ground();

      double vertical();

      float fallDistance();

      boolean feetAir();

      boolean using();

      void swing();

      int targetId();

      int targetHurt();

      boolean target();

      int ticks();

      int attackerTime();
   }

   public static final class State {
      public int unhurt;
      public int targetHistory;
      public int consecutive;
      public int previousTarget;
      public boolean ready;
      public boolean cancel;
      public boolean newHurtWindow;

      public strictfp void tick(HitSelectModule.Input i) {
         this.ready = false;
         this.unhurt = i.ready() && i.hurt() == 0 ? this.unhurt + 1 : 0;
      }

      public strictfp void history(HitSelectModule.Input i, boolean server) {
         if (!server) {
            int id = i.target() ? i.targetId() : 0;
            if (id == this.previousTarget) {
               if (Integer.compareUnsigned(++this.targetHistory, 10) < 0) {
                  return;
               }
            } else {
               this.previousTarget = id;
               this.targetHistory = 1001;
            }

            if (i.ticks() == i.attackerTime()) {
               this.targetHistory = 0;
            }
         } else {
            if (i.target() && i.targetHurt() == 10) {
               this.targetHistory = 0;
               this.newHurtWindow = true;
            } else {
               this.targetHistory++;
            }
         }
      }

      private static strictfp int rounded(float value, RecoveredRandom r) {
         long whole = !Double.isNaN((double)value) && !((double)value >= 9.223372E18F) && !((double)value < -9.223372E18F) ? (long)value : Long.MIN_VALUE;
         float fraction = value - (float)Integer.toUnsignedLong((int)whole);
         return (int)whole + (r.chance(fraction) ? 1 : 0);
      }

      public strictfp boolean filter(
         HitSelectModule.Input i,
         boolean reducing,
         int mode,
         boolean requireSprint,
         boolean requireWeapon,
         float airRate,
         float pause,
         float waitDamage,
         float trades,
         boolean server,
         boolean allowReduce,
         boolean requireDamage,
         float combatRate,
         boolean fakeSwing,
         RecoveredRandom r
      ) {
         if (!this.ready) {
            boolean decision;
            if ((!requireSprint || i.sprint()) && (!requireWeapon || i.weapon()) && i.hitKind() != 1) {
               if (i.hasHit() && i.hitKind() == 0 && r.chance(airRate)) {
                  decision = true;
               } else {
                  int threshold = rounded(pause, r);
                  boolean ground = i.ground();
                  boolean rising = (!ground || i.vertical() > 0.0) && reducing;
                  boolean missing = i.hitKind() != 2 || !i.hitEntity();
                  boolean early = false;
                  decision = false;
                  if (mode == 0) {
                     if (rising) {
                        early = true;
                     } else {
                        if (Integer.compareUnsigned(this.targetHistory, 15) < 0) {
                           this.consecutive = 0;
                        } else if (!missing && Integer.compareUnsigned(++this.consecutive, rounded(waitDamage, r)) <= 0) {
                           decision = true;
                           early = true;
                        }

                        if (!early) {
                           if (Integer.compareUnsigned(this.unhurt, 9) <= 0) {
                              threshold += rounded(trades, r);
                           }

                           if (this.newHurtWindow) {
                              this.newHurtWindow = false;
                              early = true;
                           }
                        }
                     }
                  } else if (mode == 1) {
                     boolean window = Integer.compareUnsigned(this.targetHistory - 9, -7) < 0 || server;
                     boolean descending = !ground && i.fallDistance() > 0.0F;
                     if ((!descending || !window) && (!rising || window && !allowReduce) && i.feetAir()) {
                        if (!ground && !descending && (!requireDamage || i.hurt() != 0)) {
                           decision = true;
                           early = true;
                        }
                     } else {
                        early = true;
                     }
                  } else {
                     early = true;
                  }

                  if (!early) {
                     decision = Integer.compareUnsigned(threshold, this.targetHistory + 1) > 0 && r.chance(combatRate);
                  }
               }
            } else {
               decision = false;
            }

            this.cancel = decision;
            this.ready = true;
         }

         if (this.cancel && fakeSwing && !i.using()) {
            i.swing();
         }

         return this.cancel;
      }

      public strictfp void clear() {
         this.unhurt = this.targetHistory = this.consecutive = this.previousTarget = 0;
         this.ready = this.cancel = this.newHurtWindow = false;
      }
   }
}
