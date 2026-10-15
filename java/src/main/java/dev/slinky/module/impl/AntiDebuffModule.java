package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public final class AntiDebuffModule extends EvidenceModule {
   public AntiDebuffModule() {
      super("antidebuff", "Anti Debuff", ModuleCategory.RENDER);
   }

   @Override
   public boolean listens(int type) {
      return type == 18;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready()) {
         if (this.flag("blindness")) {
            PotionEffect effect = context.player().getActivePotionEffect(Potion.blindness);
            if (effect != null) {
               MinecraftFields.setPotionDuration(effect, 0);
            }
         }

         if (this.flag("nausea")) {
            context.player().timeInPortal = 0.0F;
            context.player().prevTimeInPortal = 0.0F;
         }
      }
   }
}
