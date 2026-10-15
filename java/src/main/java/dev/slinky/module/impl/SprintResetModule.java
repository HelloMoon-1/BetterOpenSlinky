package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

public final strictfp class SprintResetModule extends EvidenceModule {
   private final SprintResetModule.State state = new SprintResetModule.State();
   private final WeaponsModule weapons;
   private KeyBinding heldKey;

   public SprintResetModule(WeaponsModule weapons) {
      super("sprintreset", "Sprint Reset", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 17;
   }

   private strictfp SprintResetModule.Action action(final ClientContext context) {
      return new SprintResetModule.Action() {
         @Override
         public strictfp void apply(boolean active) {
            int mode = SprintResetModule.this.choice("mode");
            GameSettings keys = context.minecraft().gameSettings;
            KeyBinding key = mode == 2 ? keys.keyBindSprint : (mode == 1 ? keys.keyBindSneak : keys.keyBindForward);
            if (SprintResetModule.this.heldKey != null && SprintResetModule.this.heldKey != key) {
               KeyState.restore(SprintResetModule.this.heldKey);
               SprintResetModule.this.heldKey = null;
            }

            if (mode == 2 && context.player() != null) {
               context.player().setSprinting(!active);
            }

            if (active) {
               KeyState.set(key, mode == 1);
               SprintResetModule.this.heldKey = key;
            } else {
               KeyState.restore(key);
               SprintResetModule.this.heldKey = null;
            }
         }
      };
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 17) {
         boolean ready = context.ready() && context.minecraft().inGameHasFocus && context.minecraft().currentScreen == null;
         boolean weaponAllowed = !this.flag("require_weapon")
            || ready && this.weapons.isAllowed(context.player().getHeldItem(), context.player().inventory.currentItem);
         this.state
            .update(
               ready,
               RecoveredRandom.now(),
               ready && context.player().ticksExisted == context.player().getLastAttackerTime(),
               ready ? context.player().hurtTime : 0,
               this.number("delay"),
               this.number("duration"),
               this.flag("randomize"),
               this.flag("wait_for_damage"),
               weaponAllowed,
               RecoveredRandom.SHARED,
               this.action(context)
            );
      }
   }

   private strictfp void reset(ClientContext context) {
      this.state.reset(this.action(context));
      if (this.heldKey != null) {
         KeyState.restore(this.heldKey);
         this.heldKey = null;
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext context) {
      this.reset(context);
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.reset(context);
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

   public interface Action {
      void apply(boolean var1);
   }

   public static final class State {
      public int phase;
      public int sampleCount;
      public float timer;
      public double lastSampleTime;
      public boolean haveState;
      private final RecoveredRandom.Smooth delayRandom = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth durationRandom = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth gateRandom = new RecoveredRandom.Smooth();

      public strictfp void reset(SprintResetModule.Action action) {
         if (this.phase == 2) {
            action.apply(false);
         }

         this.phase = 0;
         this.haveState = false;
         this.timer = 0.0F;
      }

      public strictfp void update(
         boolean ready,
         double now,
         boolean countersEqual,
         int damage,
         float delay,
         float duration,
         boolean randomize,
         boolean waitDamage,
         boolean weaponAllowed,
         RecoveredRandom random,
         SprintResetModule.Action action
      ) {
         if (!ready) {
            this.reset(action);
         } else {
            if (now - this.lastSampleTime >= 0.5 && countersEqual) {
               this.lastSampleTime = now;
               this.haveState = true;
            }

            this.sampleCount++;
            if (damage == 10) {
               this.sampleCount = 0;
            }

            if (this.timer > 0.0F) {
               this.timer += -1.0F;
               if (this.timer > 0.0F) {
                  if (this.phase == 2) {
                     action.apply(true);
                  }

                  return;
               }
            }

            if (this.phase == 2) {
               action.apply(false);
               this.phase = 0;
            } else if (this.phase == 1) {
               action.apply(true);
               this.phase = 2;
               this.haveState = false;
               if (randomize) {
                  float sample = this.durationRandom.sample(0.0F, 1.0F, 30.0, now, random);
                  duration = RotationMath.max(0.0F, duration - sample);
                  if (this.gateRandom.gate(0.05F, 0.1F, 30.0, now, random)) {
                     duration += random.interval(1.0F, 2.0F);
                  }
               }

               this.timer += duration;
            } else {
               if (randomize) {
                  delay += this.delayRandom.sample(0.0F, 2.0F, 5.0, now, random);
               }

               if (this.haveState && (!waitDamage || this.sampleCount == 0) && weaponAllowed) {
                  this.timer += delay;
                  this.phase = 1;
               }
            }
         }
      }
   }
}
