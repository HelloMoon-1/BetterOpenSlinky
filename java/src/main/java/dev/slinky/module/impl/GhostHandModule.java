package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.BooleanSetting;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class GhostHandModule extends EvidenceModule {
   public GhostHandModule() {
      super("ghosthand", "Ghost Hand", ModuleCategory.COMBAT);
      this.reorderSettings(
         new String[]{
            "non_players",
            "friendlies",
            "bots",
            "enemies",
            "allow_using_sword",
            "allow_using_tools",
            "allow_using_fists",
            "allow_using_buckets",
            "allow_using_fns",
            "allow_using_cobweb",
            "allow_using_other",
            "require_mouse_pressed"
         }
      );
   }

   @Override
   public boolean listens(int type) {
      return type == 8;
   }

   public boolean swordExcluded() {
      return !this.flag("allow_using_sword");
   }

   public void setSwordExcluded(boolean excluded) {
      ((BooleanSetting)this.findSetting("allow_using_sword")).set(Boolean.valueOf(!excluded));
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      event.queryOverride = true;
   }

   public boolean canInteractThrough(ClientContext context, Entity target) {
      if (!this.isEnabled() || !context.ready()) {
         return false;
      } else if (this.flag("require_mouse_pressed") && !AttackInput.pressed(context)) {
         return false;
      } else {
         ItemStack held = context.player().getHeldItem();
         String key = held != null && held.getItem() != null ? itemGate(Item.getIdFromItem(held.getItem())) : "allow_using_fists";
         if (!this.flag(key)) {
            return false;
         } else {
            return !(target instanceof EntityOtherPlayerMP) ? this.flag("non_players") : this.flag(relationshipGate(RelationshipService.flags(target)));
         }
      }
   }

   public static String relationshipGate(int flags) {
      return (flags & 2) != 0 ? "bots" : ((flags & 5) != 0 ? "friendlies" : "enemies");
   }

   public static String itemGate(int id) {
      switch (id) {
         case 30:
            return "allow_using_cobweb";
         case 256:
         case 257:
         case 258:
         case 269:
         case 270:
         case 271:
         case 273:
         case 274:
         case 275:
         case 277:
         case 278:
         case 279:
         case 284:
         case 285:
         case 286:
         case 359:
            return "allow_using_tools";
         case 259:
            return "allow_using_fns";
         case 267:
         case 268:
         case 272:
         case 276:
         case 283:
            return "allow_using_sword";
         case 325:
         case 326:
         case 327:
            return "allow_using_buckets";
         default:
            return "allow_using_other";
      }
   }
}
