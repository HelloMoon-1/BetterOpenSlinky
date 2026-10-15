package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final class ItemUseFixModule extends EvidenceModule {
   private boolean restoreBreaking;

   public ItemUseFixModule() {
      super("itemusefix", "Item Use Fix", ModuleCategory.WORLD);
   }

   @Override
   public boolean listens(int type) {
      return type == 14 || type == 15;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (!event.active && !event.cancelled && context.minecraft().playerController != null) {
         if (event.type == 14) {
            this.restoreBreaking = MinecraftFields.breaking(context.minecraft().playerController);
            if (this.restoreBreaking) {
               MinecraftFields.setBreaking(context.minecraft().playerController, false);
            }
         } else if (this.restoreBreaking) {
            MinecraftFields.setBreaking(context.minecraft().playerController, true);
         }
      }
   }

   private void restore(ClientContext context) {
      if (this.restoreBreaking && context.minecraft().playerController != null) {
         MinecraftFields.setBreaking(context.minecraft().playerController, true);
      }

      this.restoreBreaking = false;
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.restore(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.restore(context);
   }
}
