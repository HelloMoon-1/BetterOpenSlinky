package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.settings.KeyBinding;

public final class NoUseDelayModule extends EvidenceModule {
   private final NoUseDelayModule.State state = new NoUseDelayModule.State(0);

   public NoUseDelayModule() {
      super("nousedelay", "No Use Delay", ModuleCategory.UTILITY);
   }

   @Override
   public boolean listens(int type) {
      return type == 18;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (!context.inputReady()) {
         this.state.changedSlot(false, 0);
      } else {
         KeyBinding use = context.minecraft().gameSettings.keyBindUseItem;
         boolean active = use.isKeyDown();
         if (this.state.changedSlot(true, context.player().inventory.currentItem)) {
            KeyState.restore(use);
         }

         if (active && this.state.suppress(true, context.player().getItemInUseCount(), this.flag("allow_desync"))) {
            KeyState.set(use, false);
         }
      }
   }

   private void reset(ClientContext context) {
      this.state.clear();
      KeyState.restore(context.minecraft().gameSettings.keyBindUseItem);
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.reset(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.reset(context);
   }

   public static final class State {
      public int countdown;
      public int lastSlot;

      public State(int initialSlot) {
         this.lastSlot = initialSlot;
      }

      public boolean changedSlot(boolean ready, int selected) {
         if (!ready) {
            this.countdown = 0;
            return false;
         } else if (selected == this.lastSlot) {
            return false;
         } else {
            this.lastSlot = selected;
            this.countdown = 0;
            return true;
         }
      }

      public boolean suppress(boolean activeSnapshot, int useCount, boolean allowDesync) {
         if (!activeSnapshot) {
            return false;
         } else if (this.countdown <= 0) {
            this.countdown = useCount;
            return false;
         } else {
            this.countdown--;
            return this.countdown == 0 || useCount == 0 && allowDesync;
         }
      }

      public void clear() {
         this.countdown = 0;
      }
   }
}
