package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public final class NoItemReleaseModule extends EvidenceModule {
   private final NoItemReleaseModule.State state = new NoItemReleaseModule.State();

   public NoItemReleaseModule() {
      super("noitemrelease", "No Item Release", ModuleCategory.COMBAT);
   }

   @Override
   public boolean listens(int type) {
      return type == 3;
   }

   @Override
   public synchronized void onEvent(ClientContext context, SemanticEvent event) {
      if (event.packet instanceof C08PacketPlayerBlockPlacement) {
         ItemStack stack = ((C08PacketPlayerBlockPlacement)event.packet).getStack();
         this.state.use(this.choice("mode"), stack != null && stack.getItem() != null ? Item.getIdFromItem(stack.getItem()) : null);
      } else if (event.packet instanceof C07PacketPlayerDigging && this.state.release(((C07PacketPlayerDigging)event.packet).getStatus().ordinal())) {
         event.dispatchMode = 2;
      }
   }

   @Override
   protected synchronized void onDisable(ClientContext context) {
      this.state.active = false;
   }

   @Override
   public synchronized void onWorldUnload(ClientContext context) {
      this.state.active = false;
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

   public static final class State {
      public boolean active;

      public void use(int mode, Integer identifier) {
         if (identifier == null) {
            this.active = false;
         } else {
            int id = identifier;
            int bit = id - 261;
            boolean sword = Integer.compareUnsigned(bit, 23) < 0 && (4229312 >>> bit & 1) != 0;
            if (sword) {
               if (mode == 1) {
                  this.active = false;
                  return;
               }
            } else if (id == 261 || id == 346 || mode == 0) {
               this.active = false;
               return;
            }

            this.active = id != 261 && id != 346;
         }
      }

      public boolean release(int action) {
         if (!this.active) {
            return false;
         } else {
            boolean cancelled = action == 5;
            this.active = false;
            return cancelled;
         }
      }
   }
}
