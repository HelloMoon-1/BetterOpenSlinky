package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final class NoJumpDelayModule extends EvidenceModule {
   public NoJumpDelayModule() {
      super("nojumpdelay", "No Jump Delay", ModuleCategory.MOVEMENT);
   }

   @Override
   public boolean listens(int type) {
      return type == 17;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.inputReady()) {
         MinecraftFields.setJumpTicks(context.player(), 0);
      }
   }
}
