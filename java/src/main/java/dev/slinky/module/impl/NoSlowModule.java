package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.item.Item;

public final class NoSlowModule extends EvidenceModule {
   public NoSlowModule() {
      super("noslow", "No Slow", ModuleCategory.MOVEMENT);
   }

   public static String category(int identifier) {
      int bucket = identifier - 261;
      if (Integer.compareUnsigned(bucket, 23) < 0 && (4229312 >>> (bucket & 31) & 1) != 0) {
         return "mult_sword";
      } else {
         return bucket == 0 ? "mult_bow" : "mult_consumables";
      }
   }

   public static float scale(float input, float retained) {
      return (float)((double)retained / 0.2 * (double)input);
   }

   @Override
   public boolean listens(int type) {
      return type == 21;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready() && context.player().isUsingItem() && context.player().getHeldItem() != null) {
         float retained = this.number(category(Item.getIdFromItem(context.player().getHeldItem().getItem())));
         event.sideways = scale(event.sideways, retained);
         event.forward = scale(event.forward, retained);
      }
   }
}
