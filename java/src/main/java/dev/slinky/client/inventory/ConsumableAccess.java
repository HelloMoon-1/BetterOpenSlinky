package dev.slinky.client.inventory;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ModuleActionService;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public interface ConsumableAccess {
   boolean ready();

   float health();

   float maximumHealth();

   boolean usingItem();

   int current();

   int type(int var1);

   int damage(int var1);

   void remember();

   void select(int var1);

   void use();

   void drop();

   boolean claim();

   void release();

   void disable();

   public static final class Live implements ConsumableAccess {
      private final Module module;
      private final ModuleManager manager;
      private InventoryPlayer savedInventory;
      private int savedSlot;
      public ClientContext context;

      public Live(Module module, ModuleManager manager) {
         this.module = module;
         this.manager = manager;
      }

      @Override
      public boolean ready() {
         return this.context.inputReady();
      }

      @Override
      public float health() {
         return this.context.player().getHealth();
      }

      @Override
      public float maximumHealth() {
         return this.context.player().getMaxHealth();
      }

      @Override
      public boolean usingItem() {
         return this.context.player().isUsingItem();
      }

      @Override
      public int current() {
         return this.context.player().inventory.currentItem;
      }

      @Override
      public int type(int slot) {
         return InventoryActions.type(this.context.player().inventory.getStackInSlot(slot));
      }

      @Override
      public int damage(int slot) {
         ItemStack s = this.context.player().inventory.getStackInSlot(slot);
         return s == null ? 0 : s.getItemDamage();
      }

      @Override
      public void remember() {
         this.savedInventory = this.context.player().inventory;
         this.savedSlot = this.savedInventory.currentItem;
      }

      @Override
      public void select(int slot) {
         this.context.player().inventory.currentItem = slot;
         if (this.context.player().inventory == this.savedInventory && slot == this.savedSlot) {
            this.savedInventory = null;
         }
      }

      @Override
      public void use() {
         InventoryActions.press(this.context.minecraft().gameSettings.keyBindUseItem);
      }

      @Override
      public void drop() {
         InventoryActions.press(this.context.minecraft().gameSettings.keyBindDrop);
      }

      @Override
      public boolean claim() {
         return ModuleActionService.claim(this.module);
      }

      @Override
      public void release() {
         ModuleActionService.release(this.module);
      }

      @Override
      public void disable() {
         this.manager.requestEnabled(this.module, false);
      }

      public void cleanup() {
         if (this.savedInventory != null) {
            this.savedInventory.currentItem = this.savedSlot;
            this.savedInventory = null;
         }

         this.release();
         this.context = null;
      }

      public void forget() {
         this.savedInventory = null;
      }
   }
}
