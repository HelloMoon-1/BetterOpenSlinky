package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.packet.PacketKinds;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final class BlinkModule extends EvidenceModule {
   private final ModuleManager modules;
   private int previous;
   private int remaining;
   private int direction;

   public BlinkModule(ModuleManager modules) {
      super("blink", "Blink", ModuleCategory.UTILITY);
      this.modules = modules;
   }

   @Override
   public boolean listens(int type) {
      return type == 0 || type == 2 || type == 3 || type == 14;
   }

   @Override
   protected synchronized void onEnable(ClientContext c) {
      this.remaining = (int)RecoveredMath.truncateLong(this.number("max_duration"));
      this.previous = Integer.MAX_VALUE;
      this.direction = this.choice("direction");
   }

   @Override
   public synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 2 && this.direction != 1) {
         e.dispatchMode = !PacketKinds.realtimeDamage(e.packet) && !PacketKinds.blinkImmediate(e.packet) ? 1 : 3;
      } else if (e.type == 3 && this.direction != 0) {
         e.dispatchMode = 1;
      } else if (e.type == 0 && c.inputReady()) {
         int current = c.player().getLastAttackerTime();
         boolean rising = current > this.previous && this.flag("disable_on_attack");
         this.previous = current;
         if (--this.remaining == 0 || rising) {
            this.modules.requestEnabled(this, false);
         }
      } else if (e.type == 14 && this.flag(e.active ? "disable_on_block_interact" : "disable_on_block_dig")) {
         MovingObjectPosition hit = c.minecraft().objectMouseOver;
         if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            this.modules.requestEnabled(this, false);
         }
      }
   }

   @Override
   public synchronized void onWorldUnload(ClientContext c) {
      this.remaining = 0;
      this.previous = Integer.MAX_VALUE;
      this.modules.requestEnabled(this, false);
   }

   @Override
   public String statusText() {
      if (!this.findSetting("direction").isConfigured()) {
         return null;
      } else {
         int index = this.choice("direction");
         return StatusText.label(this.getName(), index);
      }
   }
}
