package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.inventory.InventorySelection;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.NumberSetting;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

public final strictfp class InvManagerModule extends EvidenceModule {
   private int phase;
   private int resume;
   private int scan;
   private long processed;
   private InventorySelection.Action action;
   private float timer;
   private float previousX;
   private float previousY;

   public InvManagerModule() {
      super("invmanager", "Inv Manager", ModuleCategory.UTILITY);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 25;
   }

   private strictfp void delay(float x, float y) {
      float amount = (float)((NumberSetting)this.findSetting("speed")).getMaximum() / this.number("speed");
      float distance = RecoveredMath.floatDistance(x - this.previousX, y - this.previousY);
      if (this.flag("smart_speed")) {
         amount *= RotationMath.max(distance * 0.5F, 1.0F);
      }

      if (this.flag("randomize")) {
         amount *= RecoveredRandom.SHARED.interval(1.0F, 1.25F);
      }

      this.timer += amount;
      this.previousX = x;
      this.previousY = y;
   }

   private strictfp void delay(int slot) {
      float[] grid = InventoryActions.grid(slot);
      this.delay(grid[0], grid[1]);
   }

   private strictfp void advance(int next) {
      this.phase = next;
      this.scan = 0;
      this.processed = 0L;
      this.action = null;
      this.resume = 0;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 25) {
         if (!(c.minecraft().currentScreen instanceof GuiInventory)) {
            this.advance(0);
            this.timer = this.number("delay_after_open");
            if (this.flag("randomize")) {
               this.timer = RecoveredRandom.SHARED.interval(1.0F, 1.5F) * this.timer;
            }

            float[] grid = InventoryActions.grid(13);
            this.previousX = grid[0];
            this.previousY = grid[1];
         } else if (this.phase != 2 && c.ready()) {
            if (this.timer > 0.0F) {
               this.timer += -1.0F;
               if (this.timer > 0.0F) {
                  return;
               }
            }

            e.cancelled = true;
            GuiInventory screen = (GuiInventory)c.minecraft().currentScreen;
            InventoryPlayer inventory = c.player().inventory;

            while (true) {
               while (this.phase == 0 || this.phase == 1) {
                  int currentPhase = this.phase;
                  int limit = this.phase == 0 ? 4 : 9;
                  if (this.flag(this.phase == 0 ? "equip_armor" : "sort_hotbar")) {
                     while (this.scan < limit) {
                        InventorySelection.Stack[] main = new InventorySelection.Stack[36];

                        for (int i = 0; i < 36; i++) {
                           main[i] = InventorySelection.Stack.of(inventory.getStackInSlot(i));
                        }

                        if (this.phase == 0) {
                           this.action = InventorySelection.planArmor(main, InventorySelection.Stack.of(inventory.getStackInSlot(39 - this.scan)), this.scan);
                        } else {
                           int category = this.choice(Integer.toString(this.scan + 1));
                           this.action = InventorySelection.planHotbar(main, this.scan, category, this.flag("sort_hotbar_fallback"), this.processed);
                           if (this.action != null
                              || category != 0
                                 && main[this.scan] != null
                                 && Math.max(0, main[this.scan].count)
                                       * InventorySelection.policy(category, main[this.scan], this.flag("sort_hotbar_fallback"))
                                    != 0) {
                              this.processed = this.processed | InventorySelection.processedBit(this.scan);
                           }
                        }

                        if (this.action != null) {
                           this.delay(this.action.source);
                           this.resume = currentPhase;
                           this.phase = 3;
                           return;
                        }

                        this.scan = this.scan + 1 & 0xFF;
                     }
                  }

                  this.advance(currentPhase + 1);
               }

               if (this.phase == 2) {
                  return;
               }

               if (this.phase == 3) {
                  int button = this.action.kind == 1 ? this.action.destination : 0;
                  int mode = this.action.kind == 0 ? 1 : (this.action.kind == 1 ? 2 : 0);
                  InventoryActions.click(screen, this.action.source, button, mode);
                  this.processed = this.processed | InventorySelection.processedBit(this.scan);
                  if (this.resume != 1) {
                     this.scan = this.scan + 1 & 0xFF;
                  }

                  if (this.action.kind == 2) {
                     this.delay(this.action.destination);
                     this.phase = 4;
                     this.action.source = this.action.destination;
                     return;
                  }

                  this.phase = this.resume;
               } else {
                  if (this.phase == 4) {
                     InventoryActions.click(screen, this.action.source, 0, 0);
                     this.delay(this.previousX + -2.0F, this.previousY);
                     this.phase = 5;
                     return;
                  }

                  if (this.phase != 5) {
                     return;
                  }

                  InventoryActions.click(screen, -999, 0, 0);
                  this.phase = this.resume;
               }
            }
         }
      }
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.advance(0);
      this.timer = 0.0F;
      this.previousX = 0.0F;
      this.previousY = 0.0F;
   }
}
