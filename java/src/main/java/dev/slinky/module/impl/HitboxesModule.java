package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final class HitboxesModule extends EvidenceModule {
   public HitboxesModule() {
      super("hitboxes", "Hitboxes", ModuleCategory.COMBAT);
   }

   @Override
   public boolean listens(int type) {
      return type == 8;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      event.enabled = true;
      event.expand = (double)this.number("expand");
   }

   @Override
   public String statusText() {
      return !this.findSetting("expand").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("expand"));
   }
}
