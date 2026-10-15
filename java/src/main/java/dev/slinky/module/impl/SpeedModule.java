package dev.slinky.module.impl;

import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;

public final class SpeedModule extends EvidenceModule {
   public SpeedModule() {
      super("speed", "Speed", ModuleCategory.MOVEMENT);
      this.reorderSettings(new String[]{"speed", "require_ground"});
   }
}
