package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

public final strictfp class AutoClickerModule extends EvidenceModule {
   private final AutoClickerModule.State state = new AutoClickerModule.State();
   private final WeaponsModule weapons;
   private final ModuleManager modules;

   public AutoClickerModule(ModuleManager modules, WeaponsModule weapons) {
      super("autoclicker", "Auto Clicker", ModuleCategory.COMBAT);
      this.modules = modules;
      this.weapons = weapons;
   }

   private strictfp AutoClickerModule.Settings settings() {
      AutoClickerModule.Settings s = new AutoClickerModule.Settings();
      s.pattern = this.choice("pattern");
      s.cps = this.number("target_cps");
      s.randomize = this.flag("randomize_cps");
      s.exhaust = this.flag("exhaust");
      s.blockBreak = this.flag("allow_block_break");
      s.inventory = this.flag("allow_inventory");
      s.inventoryRandomize = this.flag("inventory_randomize");
      s.weapon = this.flag("require_weapon");
      s.notUsing = this.flag("require_not_using_item");
      return s;
   }

   private strictfp AutoClickerModule.Input input(final ClientContext c) {
      return new AutoClickerModule.Input() {
         @Override
         public strictfp boolean ready() {
            return c.inputReady();
         }

         @Override
         public strictfp boolean hardware(int button) {
            return ActionAccess.mouse().physical(button);
         }

         @Override
         public strictfp boolean menu() {
            return c.minecraft().currentScreen != null;
         }

         @Override
         public strictfp boolean inventoryPrimary() {
            return this.hardware(1) && (!AutoClickerModule.this.state.modifier || AutoClickerModule.this.state.secondary);
         }

         @Override
         public strictfp boolean shift() {
            return ActionAccess.mouse().shift();
         }

         private strictfp Slot hovered() {
            return c.minecraft().currentScreen instanceof GuiContainer ? ActionAccess.hovered((GuiContainer)c.minecraft().currentScreen) : null;
         }

         @Override
         public strictfp int slot() {
            Slot slot = this.hovered();
            return slot == null ? -1 : slot.slotNumber;
         }

         @Override
         public strictfp boolean cursorStack() {
            return c.player() == null || c.player().inventory.getItemStack() != null;
         }

         @Override
         public strictfp boolean slotStack() {
            Slot slot = this.hovered();
            return slot != null && slot.getHasStack();
         }

         @Override
         public strictfp boolean weapon() {
            return AutoClickerModule.this.weapons.isWeapon(c.player());
         }

         @Override
         public strictfp boolean using() {
            return c.player().isUsingItem();
         }

         @Override
         public strictfp float yaw() {
            return c.player().rotationYaw;
         }

         @Override
         public strictfp float pitch() {
            return c.player().rotationPitch;
         }

         @Override
         public strictfp boolean target() {
            return GameHooks.target() != null;
         }

         @Override
         public strictfp double targetDistance() {
            return GameHooks.targetDistanceSquared();
         }

         @Override
         public strictfp void emit(int button, boolean down) {
            ActionAccess.mouse().emit(c, button, down);
         }
      };
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 1 || type == 17 || type == 26;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      AutoClickerModule.Input i = this.input(c);
      if (e.type == 26) {
         this.state.clear(i);
      } else {
         if (e.type == 0) {
            this.state.secondary = c.minecraft().gameSettings.keyBindAttack.getKeyCode() == -99;
            if (c.inputReady()) {
               this.state.previousYaw = c.player().rotationYaw;
               this.state.previousPitch = c.player().rotationPitch;
               Module delay = this.modules.find("nohitdelay");
               if (delay == null || !delay.isEnabled()) {
                  MinecraftFields.setLeftClickCounter(c.minecraft(), 0);
               }
            }
         } else if (e.type == 17) {
            if (c.inputReady()) {
               this.state.hitKind = ActionAccess.hitKind(c.minecraft().objectMouseOver);
            }
         } else {
            this.state
               .advance(i, this.settings(), MinecraftFields.timer(c.minecraft()).timerSpeed, e.elapsedSeconds, RecoveredRandom.SHARED, RecoveredRandom.now());
         }
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.state.clear(this.input(c));
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.state.clear(this.input(c));
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("pattern").isConfigured()) {
         return null;
      } else {
         int index = this.choice("pattern");
         return StatusText.label(this.getName(), index);
      }
   }

   public interface Input {
      boolean ready();

      boolean hardware(int var1);

      boolean menu();

      boolean inventoryPrimary();

      boolean shift();

      int slot();

      boolean cursorStack();

      boolean slotStack();

      boolean weapon();

      boolean using();

      float yaw();

      float pitch();

      boolean target();

      double targetDistance();

      void emit(int var1, boolean var2);
   }

   public static final class Settings {
      public int pattern;
      public float cps;
      public boolean randomize;
      public boolean exhaust;
      public boolean blockBreak;
      public boolean inventory;
      public boolean inventoryRandomize;
      public boolean weapon;
      public boolean notUsing;
   }

   public static final class State {
      public double remaining;
      public double previousInterval;
      public int adjustmentCount;
      public int hitKind;
      public int previousSlot = -1;
      public boolean secondary;
      public boolean modifier;
      public boolean inventoryWasPressed;
      public boolean quick;
      public boolean lastAdjusted;
      public float previousYaw;
      public float previousPitch;
      private final RecoveredRandom.Smooth inventoryGate = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth inventoryDelay = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth amplitude = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth slow = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth medium = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth fast = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth quickGate = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth exhaustGate = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth exhaustCount = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Gaussian wave = new RecoveredRandom.Gaussian();
      private final RecoveredRandom.Gaussian exhaustWave = new RecoveredRandom.Gaussian();

      private static strictfp float f(int a) {
         return ActionConstants.f(a);
      }

      private static strictfp double d(int a) {
         return ActionConstants.d(a);
      }

      private static strictfp float sample(RecoveredRandom.Smooth s, int lower, int period, RecoveredRandom r, double now) {
         return s.sample(f(lower), f(lower + 4), d(period), now, r);
      }

      private strictfp void cleanup(double idle) {
         this.remaining = idle;
         this.previousSlot = -1;
      }

      public strictfp void restore(AutoClickerModule.Input i) {
         if (this.modifier) {
            i.emit(this.secondary ? 0 : 1, i.hardware(this.secondary ? 0 : 1));
         }

         this.modifier = false;
      }

      public strictfp void clear(AutoClickerModule.Input i) {
         this.restore(i);
         this.cleanup(0.0);
         this.previousInterval = 0.0;
         this.adjustmentCount = this.hitKind = 0;
         this.inventoryWasPressed = this.quick = this.lastAdjusted = false;
      }

      public strictfp float adjust(float interval, float bias, boolean exhaust, RecoveredRandom r, double now) {
         this.lastAdjusted = false;
         int count = this.adjustmentCount;
         if (count < 1) {
            if (!exhaust || !this.exhaustGate.gate((float)(d(1377240) + (double)bias), (float)((double)bias + d(1377248)), d(1377256), now, r)) {
               return interval;
            }

            float threshold = r.interval(0.0F, f(1377264));
            float first = sample(this.exhaustCount, 1377268, 1377280, r, now);
            count = 3;
            if (!(first > threshold)) {
               count = (sample(this.exhaustCount, 1377288, 1377280, r, now) > threshold ? 1 : 0) + 1;
            }
         }

         this.adjustmentCount = count - 1;
         this.lastAdjusted = true;
         double scaled = (double)this.exhaustWave.sample(r) * d(1377296);
         double offset = scaled + d(1377240);
         return (float)((double)interval + offset);
      }

      private strictfp void menu(AutoClickerModule.Input i, AutoClickerModule.Settings s, int physical, int synthetic, RecoveredRandom r, double now) {
         if (synthetic == 1) {
            i.emit(this.secondary ? 0 : 1, (physical & 1) == 0);
            this.modifier = false;
         }

         if (!i.menu()) {
            this.cleanup(d(1377072));
         } else {
            boolean primary = i.inventoryPrimary();
            if (primary && this.inventoryWasPressed) {
               if (s.inventory && i.shift() && i.slot() != -1 && !i.cursorStack() && i.slot() != this.previousSlot) {
                  this.previousSlot = i.slot();
                  if (!i.slotStack() && this.inventoryGate.gate(f(1377080), f(1377084), d(1377088), now, r)) {
                     this.idleMenu(s);
                  } else {
                     i.emit(1, true);
                     if (s.inventoryRandomize) {
                        boolean longer = this.inventoryDelay.gate(f(1377104), f(1377108), d(1377088), now, r);
                        int range = longer ? 1377120 : 1377112;
                        this.remaining = (double)r.interval(f(range), f(range + 4)) + this.remaining;
                     } else {
                        this.remaining = d(1377096) + this.remaining;
                     }
                  }
               } else {
                  this.idleMenu(s);
               }
            } else {
               this.inventoryWasPressed = primary;
               this.idleMenu(s);
            }
         }
      }

      private strictfp void idleMenu(AutoClickerModule.Settings s) {
         this.remaining = d(s.inventoryRandomize ? 1377128 : 1377096) + this.remaining;
      }

      public strictfp void advance(AutoClickerModule.Input i, AutoClickerModule.Settings s, float timerSpeed, double elapsed, RecoveredRandom r, double now) {
         double next = this.remaining - (double)timerSpeed * elapsed;
         this.remaining = d(1377064) > next ? d(1377064) : next;
         if (0.0 >= this.remaining) {
            boolean hardware = i.hardware(this.secondary ? 0 : 1);
            int physical = (hardware ^ this.modifier ? 1 : 0) | (this.modifier ? 2 : 0);
            int synthetic = this.modifier ? 1 : 0;

            while (i.ready()) {
               if (((physical | synthetic) & 1) == 0) {
                  this.cleanup(d(1377072));
                  return;
               }

               boolean reject = this.hitKind == 1 && s.blockBreak || s.weapon && !i.weapon() || s.notUsing && (i.using() || (physical & synthetic & 1) != 0);
               if (reject) {
                  if (synthetic != 1) {
                     this.cleanup(0.0);
                     return;
                  }

                  i.emit(this.secondary ? 0 : 1, (physical & 1) == 0);
                  this.modifier = false;
                  this.cleanup(0.0);
                  return;
               }

               if (!s.notUsing && (physical & synthetic & 1) != 0) {
                  i.emit(this.secondary ? 0 : 1, false);
                  this.modifier = false;
                  this.cleanup(0.0);
                  return;
               }

               if ((physical & 1) != 0) {
                  i.emit(this.secondary ? 0 : 1, false);
                  this.remaining = this.previousInterval + this.remaining;
               } else {
                  i.emit(this.secondary ? 0 : 1, true);
                  float rate = s.cps;
                  if (s.randomize) {
                     float amp = sample(this.amplitude, 1377136, 1377144, r, now);
                     float low = f(1377152);
                     float high = f(1377156);
                     float a = this.slow.sample(low, high, d(1377160), now, r);
                     float b = this.medium.sample(low, high, d(1377144), now, r);
                     float c = sample(this.fast, 1377168, 1377176, r, now);
                     rate *= a + f(1377184) + b + c;
                     rate *= this.wave.sample(r) * amp + f(1377184);
                  }

                  float interval;
                  if (s.pattern == 1 && this.quick) {
                     interval = Float.intBitsToFloat(1013276738);
                     this.quick = false;
                  } else {
                     interval = s.pattern == 1 ? (float)(d(1377216) / (double)rate + d(1377224)) : f(1377188) / rate;
                     float turn = RotationMath.magnitude(i.yaw() - this.previousYaw, i.pitch() - this.previousPitch);
                     float bias = turn / f(1377192) + 0.0F;
                     if (!i.target() || i.targetDistance() > d(1377200)) {
                        bias = (float)((double)bias + d(1377208));
                     }

                     interval = this.adjust(interval, bias, s.exhaust, r, now);
                     if (s.pattern == 1 && !this.lastAdjusted) {
                        boolean gate = this.quickGate.gate(f(1377232), f(1377236), d(1377144), now, r);
                        this.quick = r.chance(gate ? 1.0F : 0.0F);
                     }
                  }

                  this.remaining += (double)interval;
                  this.previousInterval = (double)interval;
               }

               physical ^= 1;
               synthetic ^= 1;
               this.modifier = synthetic != 0;
               if (!(0.0 >= this.remaining)) {
                  return;
               }
            }

            this.menu(i, s, physical, synthetic, r, now);
         }
      }
   }
}
