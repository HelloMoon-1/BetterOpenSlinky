package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class AntiBotModule extends EvidenceModule {
   private final Map<UUID, AntiBotModule.History> history = new HashMap<>();
   private Object world;

   public AntiBotModule() {
      super("antibot", "Anti Bot", ModuleCategory.UTILITY);
   }

   public static boolean validName(String text) {
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167) {
            i++;
         } else {
            int upper = c & '\uffdf';
            if ((upper - 65 & 65535) < 26 || c == '_' || (c - '0' & 65535) < 10) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public boolean listens(int type) {
      return type == 0;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (this.world != context.world()) {
         this.history.clear();
         this.world = context.world();
      }

      if (context.ready()) {
         for (EntityPlayer player : context.world().playerEntities) {
            if (player != context.player()) {
               AntiBotModule.History entry = this.history.get(player.getUniqueID());
               if (entry == null) {
                  entry = new AntiBotModule.History();
                  this.history.put(player.getUniqueID(), entry);
               }

               entry.movement(player.prevPosX, player.prevPosZ, player.posX, player.posZ);
               boolean rejected = this.reject(context, player, entry);
               RelationshipService.set(player, 2, rejected);
            }
         }
      }
   }

   private boolean reject(ClientContext context, EntityPlayer player, AntiBotModule.History entry) {
      if (this.flag("always_stationary") && 1.0 > entry.travelledSquared) {
         return true;
      } else {
         int mode = this.choice("invalid_uuid");
         if (mode == 0 || ((int)player.getUniqueID().getMostSignificantBits() & 61440) != 8192 || mode != 2 && !(1.0 > entry.travelledSquared)) {
            if (this.flag("sleeping") && player.isPlayerSleeping()) {
               return true;
            } else {
               int age = player.ticksExisted;
               if (this.flag("always_invis") && !entry.armorSeen) {
                  if (player.isInvisibleToPlayer(context.player())) {
                     boolean found = false;

                     for (int i = 0; i < 4; i++) {
                        ItemStack armor = player.inventory.armorInventory[i];
                        if (armor != null && armor.getItem() != null && Item.getIdFromItem(armor.getItem()) != 0) {
                           found = true;
                           break;
                        }
                     }

                     if (!found) {
                        return true;
                     }
                  }

                  if (age >= 21) {
                     entry.armorSeen = true;
                  }
               }

               float threshold = this.number("min_age");
               if (0.0F < threshold && !entry.ageAccepted) {
                  if ((float)age < threshold) {
                     return true;
                  }

                  entry.ageAccepted = true;
               }

               if (this.flag("invalid_username") && !entry.nameAccepted) {
                  entry.nameAccepted = validName(player.getName());
                  if (!entry.nameAccepted) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return true;
         }
      }
   }

   private void clear() {
      this.history.clear();
      this.world = null;
      RelationshipService.clear(2);
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.clear();
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.clear();
   }

   public static final class History {
      public double travelledSquared;
      public boolean armorSeen;
      public boolean ageAccepted;
      public boolean nameAccepted;

      public void movement(double previousX, double previousZ, double x, double z) {
         double dx = previousX - x;
         double dz = previousZ - z;
         double zSquared = dz * dz;
         double xSquared = dx * dx;
         this.travelledSquared += xSquared + zSquared;
      }
   }
}
