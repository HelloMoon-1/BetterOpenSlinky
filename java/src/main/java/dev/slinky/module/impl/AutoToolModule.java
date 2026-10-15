package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ItemPolicyService;
import dev.slinky.client.KeyState;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.StringListSetting;
import java.util.Collections;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final class AutoToolModule extends EvidenceModule {
   public final AutoToolModule.State state = new AutoToolModule.State();
   private InventoryPlayer changedInventory;

   public AutoToolModule() {
      super("autotool", "Auto Tool", ModuleCategory.WORLD);
      this.itemPolicy("allowed_blocks");
   }

   @Override
   public boolean listens(int type) {
      return type == 25 || type == 26;
   }

   @Override
   public boolean settingVisible(String key) {
      return !key.equals("allowed_blocks") || this.flag("restrict_allowed_blocks");
   }

   @Override
   public void onEvent(final ClientContext context, SemanticEvent event) {
      if (event.type == 26) {
         this.cleanup(context);
         ((StringListSetting)this.findSetting("allowed_blocks")).set(Collections.emptyList());
      } else if (context.ready()) {
         final InventoryPlayer inventory = context.player().inventory;
         AutoToolModule.Access access = new AutoToolModule.Access() {
            private Block block;

            @Override
            public int current() {
               return inventory.currentItem;
            }

            @Override
            public boolean heldAllowed() {
               ItemStack stack = inventory.getCurrentItem();
               return stack == null
                  ? AutoToolModule.this.flag("allow_using_fists")
                  : AutoToolModule.typeAllowed(
                     Item.getIdFromItem(stack.getItem()),
                     AutoToolModule.this.flag("allow_using_sword"),
                     AutoToolModule.this.flag("allow_using_tools"),
                     AutoToolModule.this.flag("allow_using_other")
                  );
            }

            @Override
            public boolean usingItem() {
               return context.player().isUsingItem();
            }

            @Override
            public boolean attackDown() {
               return KeyState.physical(context.minecraft().gameSettings.keyBindAttack);
            }

            @Override
            public boolean sneakDown() {
               return context.minecraft().gameSettings.keyBindSneak.isKeyDown();
            }

            @Override
            public boolean acquireBlock() {
               MovingObjectPosition hit = context.minecraft().objectMouseOver;
               if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
                  this.block = context.world().getBlockState(hit.getBlockPos()).getBlock();
                  return this.block != null;
               } else {
                  return false;
               }
            }

            @Override
            public boolean blockAllowed() {
               return ItemPolicyService.contains(AutoToolModule.this.names("allowed_blocks"), Block.getIdFromBlock(this.block));
            }

            @Override
            public Float score(int index) {
               ItemStack stack = inventory.getStackInSlot(index);
               return stack == null ? null : stack.getStrVsBlock(this.block);
            }

            @Override
            public boolean claim() {
               return ModuleActionService.claim(AutoToolModule.this);
            }

            @Override
            public void release() {
               ModuleActionService.release(AutoToolModule.this);
            }

            @Override
            public void select(int slot) {
               inventory.currentItem = slot;
               AutoToolModule.this.changedInventory = inventory;
            }
         };
         this.state
            .update(true, this.number("activation_time"), this.flag("switch_back"), this.flag("require_sneak"), this.flag("restrict_allowed_blocks"), access);
         if (this.state.originalSlot == -1) {
            this.changedInventory = null;
         }
      }
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.cleanup(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.cleanup(context);
   }

   private void cleanup(ClientContext context) {
      ModuleActionService.release(this);
      if (this.state.originalSlot != -1 && this.changedInventory != null) {
         this.changedInventory.currentItem = this.state.originalSlot;
      }

      this.state.originalSlot = -1;
      this.state.cooldown = 0;
      this.changedInventory = null;
   }

   public static boolean typeAllowed(int id, boolean sword, boolean tool, boolean other) {
      switch (id - 256) {
         case 0:
         case 1:
         case 2:
         case 13:
         case 14:
         case 15:
         case 17:
         case 18:
         case 19:
         case 21:
         case 22:
         case 23:
         case 28:
         case 29:
         case 30:
         case 103:
            return tool;
         case 11:
         case 12:
         case 16:
         case 20:
         case 27:
            return sword;
         default:
            return other;
      }
   }

   public interface Access {
      int current();

      boolean heldAllowed();

      boolean usingItem();

      boolean attackDown();

      boolean sneakDown();

      boolean acquireBlock();

      boolean blockAllowed();

      Float score(int var1);

      boolean claim();

      void release();

      void select(int var1);
   }

   public static final class State {
      public int originalSlot = -1;
      public int lastSelectedSlot;
      public int cooldown;

      public void update(boolean ready, float delay, boolean switchBack, boolean requireSneak, boolean restrictBlocks, AutoToolModule.Access access) {
         if (ready) {
            int current = access.current();
            if (this.originalSlot != -1 && this.lastSelectedSlot != current) {
               this.originalSlot = -1;
               this.restore(access);
            } else if ((this.originalSlot != -1 || access.heldAllowed())
               && !access.usingItem()
               && access.attackDown()
               && (!requireSneak || access.sneakDown())
               && access.acquireBlock()
               && (!restrictBlocks || access.blockAllowed())) {
               if (this.cooldown >= 1) {
                  this.cooldown--;
                  this.restore(access);
               } else {
                  float best = 1.0F;
                  int bestIndex = current;

                  for (int offset = 0; offset < 9; offset++) {
                     int index = (current + offset) % 9;
                     Float score = access.score(index);
                     if (score != null && score > best) {
                        best = score;
                        bestIndex = index;
                     }
                  }

                  if (bestIndex != current) {
                     if (access.claim()) {
                        access.select(bestIndex);
                        this.lastSelectedSlot = bestIndex;
                        if (this.originalSlot == -1 && switchBack) {
                           this.originalSlot = current;
                        }
                     }

                     if (bestIndex == -1) {
                        this.restore(access);
                     }
                  }
               }
            } else {
               this.cooldown = RecoveredMath.truncateInt(delay);
               this.restore(access);
            }
         }
      }

      public void restore(AutoToolModule.Access access) {
         access.release();
         if (this.originalSlot != -1) {
            access.select(this.originalSlot);
            this.originalSlot = -1;
         }
      }
   }
}
