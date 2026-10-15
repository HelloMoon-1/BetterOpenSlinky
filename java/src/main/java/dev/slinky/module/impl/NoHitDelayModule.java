package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.util.MovingObjectPosition;

public final class NoHitDelayModule extends EvidenceModule {
   public NoHitDelayModule() {
      super("nohitdelay", "No Hit Delay", ModuleCategory.UTILITY);
   }

   @Override
   public boolean listens(int type) {
      return type == 14;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (event.active) {
         MovingObjectPosition hit = context.minecraft().objectMouseOver;
         int kind = hit == null ? -1 : hit.typeOfHit.ordinal();
         int action = decide(
            this.choice("mode"), true, MinecraftFields.leftClickCounter(context.minecraft()), kind, context.player() != null && context.player().isUsingItem()
         );
         if (action == 1) {
            MinecraftFields.setLeftClickCounter(context.minecraft(), 0);
         } else if (action == 2 && context.player() != null) {
            GameHooks.baseSwing(context.player());
         }
      }
   }

   public static int decide(int mode, boolean attack, int delay, int hitKind, boolean using) {
      if (attack && delay > 0) {
         if (mode == 1 && (hitKind == -1 || hitKind == 0)) {
            return using ? 0 : 2;
         } else {
            return 1;
         }
      } else {
         return 0;
      }
   }

   @Override
   public String statusText() {
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         return StatusText.label(this.getName(), index);
      }
   }
}
