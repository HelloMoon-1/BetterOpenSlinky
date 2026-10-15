package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;

public final class PiercingModule extends EvidenceModule {
   private final WeaponsModule weapons;

   public PiercingModule(WeaponsModule weapons) {
      super("piercing", "Piercing", ModuleCategory.COMBAT);
      this.weapons = weapons;
      this.reorderSettings(new String[]{"sort_by", "non_players", "friendlies", "bots", "enemies", "blocks", "require_weapon", "require_inside_bb"});
   }

   @Override
   public boolean listens(int type) {
      return type == 8;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      event.queryOverride = true;
   }

   public boolean pierceBlocks(ClientContext context) {
      return this.isEnabled() && this.flag("blocks") && (!this.flag("require_weapon") || this.weapons.isWeapon(context.player()));
   }

   public int rank(ClientContext context, Entity first, Entity second, boolean inside) {
      if (this.isEnabled() && (inside || !this.flag("require_inside_bb"))) {
         if (this.flag("require_weapon") && !this.weapons.isWeapon(context.player())) {
            return 2;
         } else if (!(second instanceof EntityOtherPlayerMP)) {
            return this.flag("non_players") ? 1 : 2;
         } else {
            int flags = RelationshipService.flags(second);
            if ((!this.flag("friendlies") || (flags & 5) == 0) && (!this.flag("bots") || (flags & 2) == 0)) {
               if (!(first instanceof EntityOtherPlayerMP)) {
                  return 0;
               } else if (!this.flag("enemies")) {
                  return 2;
               } else {
                  EntityOtherPlayerMP a = (EntityOtherPlayerMP)first;
                  EntityOtherPlayerMP b = (EntityOtherPlayerMP)second;
                  return compare(
                     this.choice("sort_by"),
                     health(a.getHealth()),
                     health(b.getHealth()),
                     a.hurtTime,
                     b.hurtTime,
                     a.ticksExisted + a.getLastAttackerTime(),
                     b.ticksExisted + b.getLastAttackerTime()
                  );
               }
            } else {
               return 1;
            }
         }
      } else {
         return 2;
      }
   }

   private static float health(float value) {
      return Float.isNaN(value) ? 20.0F : value;
   }

   public static int compare(int mode, float firstHealth, float secondHealth, int firstHurt, int secondHurt, int firstCounter, int secondCounter) {
      if (mode == 1) {
         if (secondHealth > firstHealth) {
            return 1;
         }

         if (firstHealth > secondHealth) {
            return 0;
         }
      } else if (mode == 0) {
         if (secondHurt > firstHurt) {
            return 1;
         }

         if (secondHurt < firstHurt) {
            return 0;
         }
      }

      return mix(secondCounter) <= mix(firstCounter) ? 1 : 0;
   }

   public static int mix(int counter) {
      counter = (counter ^ counter >>> 16) * 73244475;
      counter = (counter ^ counter >>> 16) * 73244475;
      return counter ^ counter >>> 16;
   }
}
