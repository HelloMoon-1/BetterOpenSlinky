package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.inventory.RefillSelection;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import java.util.Arrays;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemStack;

public final strictfp class RefillModule extends EvidenceModule {
   private final RefillModule.State state = new RefillModule.State();
   private final ModuleManager modules;
   private final RecoveredRandom.Smooth openRandom = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth closeRandom = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth speedRandom = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth speedGate = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth alternateGate = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth emptyGate = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth secondGate = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Gaussian wave = new RecoveredRandom.Gaussian();

   public RefillModule(ModuleManager modules) {
      super("refill", "Refill", ModuleCategory.UTILITY);
      this.modules = modules;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 5 || type == 25;
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.state.phase = 0;
   }

   private static strictfp double subtractDelay(float setting, float sample) {
      double extra = (double)setting - (double)sample;
      return extra > 0.0 ? extra : 0.0;
   }

   @Override
   public strictfp void onEvent(final ClientContext c, SemanticEvent e) {
      if (e.type == 25) {
         RefillModule.Access access = new RefillModule.Access() {
            @Override
            public strictfp boolean screen() {
               return c.minecraft().currentScreen != null;
            }

            @Override
            public strictfp boolean inventoryScreen() {
               return c.minecraft().currentScreen instanceof GuiInventory;
            }

            @Override
            public strictfp boolean player() {
               return c.player() != null;
            }

            @Override
            public strictfp int type(int slot) {
               return InventoryActions.type(c.player().inventory.getStackInSlot(slot));
            }

            @Override
            public strictfp int damage(int slot) {
               ItemStack s = c.player().inventory.getStackInSlot(slot);
               return s == null ? 0 : s.getItemDamage();
            }

            @Override
            public strictfp void open() {
               InventoryActions.press(c.minecraft().gameSettings.keyBindInventory);
            }

            @Override
            public strictfp void click(int slot, int button) {
               InventoryActions.click((GuiInventory)c.minecraft().currentScreen, slot, button, 1);
            }

            @Override
            public strictfp void finish() {
               RefillModule.this.modules.requestEnabled(RefillModule.this, false);
               GuiScreen screen = c.minecraft().currentScreen;
               if (screen != null) {
                  InventoryActions.escape(screen);
               }
            }
         };
         if (this.state
            .tick(
               access,
               new RefillModule.Policy(),
               this.flag("refill_soup"),
               this.flag("refill_potion"),
               this.flag("refill_potion_non_health"),
               this.number("delay_after_open"),
               this.number("delay_before_close"),
               this.number("speed"),
               this.flag("smart_speed"),
               this.choice("simulate_button")
            )) {
            e.cancelled = true;
         }
      }
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.state.clear();
   }

   public interface Access {
      boolean screen();

      boolean inventoryScreen();

      boolean player();

      int type(int var1);

      int damage(int var1);

      void open();

      void click(int var1, int var2);

      void finish();
   }

   private final strictfp class Policy implements RefillModule.RandomPolicy {
      private final double now = RecoveredRandom.now();
      private final RecoveredRandom r = RecoveredRandom.SHARED;

      private Policy() {
      }

      @Override
      public strictfp boolean skipEmpty() {
         return this.r.chance(0.5F);
      }

      @Override
      public strictfp boolean chooseEmpty() {
         return RefillModule.this.emptyGate.gate(0.05F, 0.1F, 20.0, this.now, this.r);
      }

      @Override
      public strictfp boolean chooseSecond() {
         return RefillModule.this.secondGate.gate(0.05F, 0.1F, 10.0, this.now, this.r);
      }

      @Override
      public strictfp float index() {
         return this.r.interval(0.0F, 27.0F);
      }

      @Override
      public strictfp float angle() {
         return this.r.interval(0.0F, (float) Math.PI);
      }

      @Override
      public strictfp double openDelay(float setting) {
         return RefillModule.subtractDelay(setting, RefillModule.this.openRandom.sample(0.0F, 2.0F, 30.0, this.now, this.r));
      }

      @Override
      public strictfp double closeDelay(float setting) {
         return RefillModule.subtractDelay(setting, RefillModule.this.closeRandom.sample(0.0F, 2.0F, 30.0, this.now, this.r));
      }

      @Override
      public strictfp float multiplier(float speed) {
         float result = RefillModule.this.speedRandom.sample(0.8F, 1.2F, 30.0, this.now, this.r) * speed;
         double factor = (double)RefillModule.this.wave.sample(this.r) * 0.3;
         result = (float)((double)result * ++factor);
         if (RefillModule.this.speedGate.gate(0.04F, 0.08F, 30.0, this.now, this.r)) {
            result /= this.r.interval(1.4F, 1.8F);
         }

         return result;
      }

      @Override
      public strictfp boolean alternate() {
         return RefillModule.this.alternateGate.gate(0.9F, 1.0F, 10.0, this.now, this.r);
      }
   }

   public interface RandomPolicy extends RefillSelection.Gates {
      float index();

      float angle();

      double openDelay(float var1);

      double closeDelay(float var1);

      float multiplier(float var1);

      boolean alternate();
   }

   public static final class State {
      public final byte[] mask = new byte[27];
      public int phase;
      public int previous;
      public int selected;
      public int alternate;
      public float timer;
      public float angle;

      public strictfp boolean tick(
         RefillModule.Access a,
         RefillModule.RandomPolicy random,
         boolean soups,
         boolean pots,
         boolean nonHealth,
         float openDelay,
         float closeDelay,
         float speed,
         boolean smart,
         int simulate
      ) {
         boolean cancel = this.phase != 0;
         if (!cancel || a.screen() && a.inventoryScreen()) {
            if (this.timer > 0.0F) {
               this.timer += -1.0F;
               return cancel;
            } else if (!(this.timer <= 0.0F)) {
               return cancel;
            } else {
               int incomingButton = 0;

               while (this.phase != 4) {
                  int empty = 0;
                  if (a.player()) {
                     while (empty < 9 && a.type(empty) != 0) {
                        empty++;
                     }
                  }

                  if (!a.player() || empty == 9) {
                     this.timer = (float)((double)this.timer + random.closeDelay(closeDelay));
                     this.phase = 4;
                     if (!(this.timer <= 0.0F)) {
                        return cancel;
                     } else {
                        a.finish();
                        return cancel;
                     }
                  }

                  switch (this.phase) {
                     case 0:
                        this.phase = 1;
                        if (a.screen()) {
                           return cancel;
                        }

                        for (int slot = 0; slot < 27; slot++) {
                           this.mask[slot] = (byte)RefillSelection.classify(a.type(slot + 9), a.damage(slot + 9), soups, pots, nonHealth);
                        }

                        RefillSelection.Result candidate = RefillSelection.choose(this.mask, this.previous, this.angle, random);
                        this.angle = candidate.bearing;
                        if (candidate.slot == -1) {
                           a.finish();
                           return cancel;
                        }

                        a.open();
                        return cancel;
                     case 1:
                        this.previous = RecoveredMath.truncateInt(random.index());
                        this.angle = random.angle();
                        this.phase = 2;
                        this.timer = (float)((double)this.timer + random.openDelay(openDelay));
                        break;
                     case 2:
                        RefillSelection.Result result = RefillSelection.choose(this.mask, this.previous, this.angle, random);
                        this.selected = result.slot;
                        this.angle = result.bearing;
                        if (this.selected == -1) {
                           this.timer = (float)((double)this.timer + random.closeDelay(closeDelay));
                           this.phase = 4;
                           return cancel;
                        }

                        float multiplier = random.multiplier(speed);
                        float distance = smart ? result.score : 1.0F;
                        this.timer += distance / multiplier;
                        this.previous = this.selected;
                        this.phase = 3;
                        break;
                     case 3:
                        int button = simulate;
                        if (simulate >= 2) {
                           if (simulate == 2) {
                              button = this.alternate;
                              if (random.alternate()) {
                                 this.alternate = this.alternate == 0 ? 1 : 0;
                              }
                           } else {
                              button = incomingButton;
                           }
                        }

                        a.click(this.selected + 9, button);
                        this.mask[this.selected] = 0;
                        this.phase = 2;
                        incomingButton = button;
                        break;
                     default:
                        throw new IllegalStateException("Invalid refill phase");
                  }

                  if (!(this.timer <= 0.0F)) {
                     return cancel;
                  }
               }

               a.finish();
               return cancel;
            }
         } else {
            a.finish();
            return false;
         }
      }

      public strictfp void clear() {
         this.phase = 0;
         this.previous = 0;
         this.selected = 0;
         this.alternate = 0;
         this.timer = 0.0F;
         this.angle = 0.0F;
         Arrays.fill(this.mask, (byte)0);
      }
   }
}
