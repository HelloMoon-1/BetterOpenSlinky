package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.item.ItemBlock;

public final class SafeWalkModule extends EvidenceModule {
   private boolean allowed;

   public SafeWalkModule() {
      super("safewalk", "Safe Walk", ModuleCategory.WORLD);
   }

   @Override
   public boolean listens(int type) {
      return type == 0;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.inputReady()) {
         if ((!this.flag("require_looking_down") || !(65.0F > context.player().rotationPitch))
            && (!this.flag("require_not_forward") || !(context.player().moveForward > 0.0F))
            && (!this.flag("require_holding_blocks") || context.player().getHeldItem() != null && context.player().getHeldItem().getItem() instanceof ItemBlock)
            )
          {
            this.allowed = true;
            float factor = this.number("motion_multiplier");
            if (context.player().onGround && factor != 1.0F) {
               context.player().motionX *= (double)factor;
               context.player().motionZ = (double)factor * context.player().motionZ;
            }
         } else {
            this.allowed = false;
         }
      }
   }

   public boolean allowed() {
      return this.isEnabled() && this.allowed;
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.allowed = false;
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.allowed = false;
   }
}
