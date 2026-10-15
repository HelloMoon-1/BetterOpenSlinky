package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public final class TeamsModule extends EvidenceModule {
   public static final String[] COLORS = new String[]{
      "Black",
      "Dark blue",
      "Dark green",
      "Dark aqua",
      "Dark red",
      "Dark purple",
      "Gold",
      "Light gray",
      "Dark gray",
      "Light blue",
      "Light green",
      "Light aqua",
      "Light red",
      "Light purple",
      "Yellow",
      "White"
   };

   public TeamsModule() {
      super("teams", "Teams", ModuleCategory.UTILITY);
   }

   private static boolean colorCode(char c) {
      return (c - '0' & 65535) < 10 || (c - 'a' & 65535) < 6 || c == 'r';
   }

   private static boolean nameCharacter(char c) {
      return c == '_' || (c - '0' & 65535) < 10 || ((c & '\uffdf') - 65 & 65535) < 26;
   }

   public static char prefixColor(String text) {
      char result = 0;
      boolean section = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         boolean escaped = section;
         section = c == 167;
         if (c != 167) {
            if (escaped) {
               if (colorCode(c)) {
                  result = c;
               }
            } else if (nameCharacter(c)) {
               return result;
            }
         }
      }

      return '\u0000';
   }

   public static char trailingColor(String text) {
      char selected = 0;
      boolean valid = false;

      for (int i = text.length() - 1; i >= 0; i--) {
         char c = text.charAt(i);
         if (c == 167) {
            if (valid) {
               return selected;
            }

            valid = false;
         } else {
            valid = colorCode(c);
            if (valid) {
               selected = c;
            }
         }
      }

      return 'f';
   }

   public static char entityColor(Entity entity) {
      char initial = prefixColor(entity.getName());
      if (initial != 0) {
         return initial;
      } else {
         IChatComponent component = entity.getDisplayName();
         return component instanceof ChatComponentText ? trailingColor(((ChatComponentText)component).getChatComponentText_TextValue()) : 'f';
      }
   }

   @Override
   public boolean listens(int type) {
      return type == 0;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready()) {
         char owner = entityColor(context.player());
         NetworkPlayerInfo info = context.minecraft().getNetHandler().getPlayerInfo(context.player().getUniqueID());
         if (info != null) {
            if (info.getPlayerTeam() != null) {
               owner = prefixColor(info.getPlayerTeam().getColorPrefix());
               if (owner == 0) {
                  owner = 'r';
               }
            } else if (info.getDisplayName() instanceof ChatComponentText) {
               owner = trailingColor(((ChatComponentText)info.getDisplayName()).getChatComponentText_TextValue());
            }
         }

         for (EntityPlayer player : context.world().playerEntities) {
            if (player != context.player()) {
               char code = entityColor(player);
               boolean member = this.flag("match_self_color") && code == owner;
               int low = code & 255;
               int index = ((byte)low >= 97 ? 169 : 208) + low & 0xFF;
               if (!member && index < COLORS.length) {
                  member = this.names("team_colors").contains(COLORS[index]);
               }

               RelationshipService.set(player, 1, member);
            }
         }
      }
   }

   @Override
   protected void onDisable(ClientContext context) {
      RelationshipService.clear(1);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      RelationshipService.clear(1);
   }
}
