package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.util.StatCollector;

public final strictfp class ChestStealerModule extends EvidenceModule {
   private float remainingDelay;
   private float previousX;
   private float previousY;

   public ChestStealerModule() {
      super("cheststealer", "Chest Stealer", ModuleCategory.UTILITY);
   }

   public static strictfp int compareTitle(String title, String translated) {
      return title.length() != translated.length() ? 0 : title.length() & -256 | (title.equals(translated) ? 1 : 0);
   }

   public strictfp boolean acceptsTitle(String title) {
      return (compareTitle(title, StatCollector.translateToLocal("container.chest")) & 0xFF) != 0
         || (compareTitle(title, StatCollector.translateToLocal("container.chestDouble")) & 0xFF) != 0;
   }

   public strictfp void updateOpenDelay(ClientContext c) {
      if (c.minecraft().currentScreen instanceof GuiChest) {
         if (!this.flag("require_vanilla_title")) {
            return;
         }

         String title = InventoryActions.chestTitle((GuiChest)c.minecraft().currentScreen);
         if (title != null && this.acceptsTitle(title)) {
            return;
         }
      }

      this.remainingDelay = this.number("delay_after_open");
      this.remainingDelay = RecoveredRandom.SHARED.interval(1.0F, 1.5F) * this.remainingDelay;
      this.previousX = 4.0F;
      this.previousY = 4.0F;
   }

   public strictfp float remainingDelay() {
      return this.remainingDelay;
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.remainingDelay = 0.0F;
      this.previousX = 0.0F;
      this.previousY = 0.0F;
   }
}
