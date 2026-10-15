package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.StringListSetting;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

public final class FriendsModule extends EvidenceModule {
   private final SortedMap<String, byte[]> records = new TreeMap<>();
   private List<String> loaded = Collections.emptyList();

   public FriendsModule() {
      super("friends", "Friends", ModuleCategory.UTILITY);
   }

   @Override
   public boolean settingVisible(String key) {
      return !key.equals("friends");
   }

   private static int digit(int c) {
      int decimal = c - 48 & 0xFF;
      if (decimal < 10) {
         return decimal;
      } else {
         return (c - 97 & 0xFF) < 26 ? c + 169 & 0xFF : -1;
      }
   }

   public static byte[] decodeRecord(String text) {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
      if (bytes.length < 48) {
         return null;
      } else {
         byte[] record = new byte[32];

         for (int end : new int[]{16, 24}) {
            for (int i = end == 16 ? 0 : 16; i < end; i++) {
               int high = digit(bytes[i * 2] & 255);
               int low = digit(bytes[i * 2 + 1] & 255);
               if (high < 0 || low < 0) {
                  break;
               }

               record[i] = (byte)(high << 4 | low);
            }
         }

         return record;
      }
   }

   public static String encodeRecord(byte[] record, int length) {
      StringBuilder result = new StringBuilder();
      String digits = "0123456789abcdef";

      for (int i = 0; i < length; i++) {
         result.append(digits.charAt((record[i] & 255) >>> 4));
         result.append(digits.charAt(record[i] & 15));
      }

      return result.toString();
   }

   private static byte[] record(UUID uuid, long timestamp) {
      return ByteBuffer.allocate(32)
         .order(ByteOrder.LITTLE_ENDIAN)
         .putLong(uuid.getMostSignificantBits())
         .putLong(uuid.getLeastSignificantBits())
         .putLong(timestamp)
         .putLong(0L)
         .array();
   }

   private void load() {
      List<String> values = this.names("friends");
      if (!values.equals(this.loaded)) {
         this.records.clear();

         for (String name : values) {
            byte[] r = decodeRecord(name);
            if (r != null && !this.records.containsKey(encodeRecord(r, 16))) {
               this.records.put(encodeRecord(r, 16), r);
            }
         }

         this.loaded = new ArrayList<>(values);
      }
   }

   private void store() {
      List<String> values = new ArrayList<>();

      for (byte[] r : this.records.values()) {
         values.add(encodeRecord(r, 24));
      }

      ((StringListSetting)this.findSetting("friends")).set(values);
      this.loaded = new ArrayList<>(values);
   }

   public void toggle(EntityPlayer player) {
      this.toggleRecord(player);
   }

   private Boolean toggleRecord(EntityPlayer player) {
      this.load();
      byte[] r = record(player.getUniqueID(), System.currentTimeMillis() / 1000L);
      String key = encodeRecord(r, 16);
      boolean added = !this.records.containsKey(key);
      if (!added) {
         this.records.remove(key);
      } else {
         if ((RelationshipService.flags(player) & 2) != 0) {
            return null;
         }

         this.records.put(key, r);
      }

      this.store();
      return added;
   }

   public void middleClickAction(ClientContext context, ModuleManager manager) {
      if (this.flag("middle_click_add")) {
         MovingObjectPosition hit = context.minecraft().objectMouseOver;
         if (hit != null && hit.entityHit instanceof EntityPlayer) {
            Boolean added = this.toggleRecord((EntityPlayer)hit.entityHit);
            if (added != null) {
               SemanticEvent event = new SemanticEvent(19);
               RenderEventPayload payload = RenderEventPayload.attach(event);
               payload.friendAdded = added;

               try {
                  manager.dispatch(event);
               } finally {
                  RenderEventPayload.detach(event);
               }

               manager.persist();
            }
         }
      }
   }

   public String command(ClientContext context, String action, String argument) {
      this.load();
      if (action.equals("clear")) {
         int count = this.records.size();
         this.records.clear();
         this.store();
         return "§aRemoved " + count + " friend" + (count == 1 ? "" : "s") + ".";
      } else if (action.equals("near")) {
         long radius = Long.parseUnsignedLong(argument);
         double distance = (double)(radius & Long.MAX_VALUE) + (radius < 0L ? 9.223372E18F : 0.0);
         int added = 0;

         for (EntityPlayer player : context.world().playerEntities) {
            if (player != context.player()
               && (RelationshipService.flags(player) & 2) == 0
               && player.getDistanceSqToEntity(context.player()) <= distance * distance) {
               byte[] r = record(player.getUniqueID(), 0L);
               String key = encodeRecord(r, 16);
               boolean fresh = !this.records.containsKey(key);
               if (fresh) {
                  this.records.put(key, r);
               }

               added += fresh ? 1 : 0;
            }
         }

         this.store();
         return "§aAdded " + added + " friend" + (added == 1 ? "" : "s") + ".";
      } else if (argument.getBytes(StandardCharsets.UTF_8).length >= 17) {
         return "§cUsername is too long.";
      } else {
         EntityPlayer found = null;

         for (EntityPlayer playerx : context.world().playerEntities) {
            if (playerx != context.player() && asciiPrefix(playerx.getName(), argument)) {
               if (found != null) {
                  return "§cFound multiple names starting with '" + argument + "'.";
               }

               found = playerx;
            }
         }

         if (found == null) {
            return "§cPlayer not found.";
         } else {
            byte[] r = record(found.getUniqueID(), 0L);
            String key = encodeRecord(r, 16);
            if (action.equals("add")) {
               if (!this.records.containsKey(key)) {
                  this.records.put(key, r);
               }

               this.store();
               return "§aAdded " + found.getName() + " as friend.";
            } else if (action.equals("remove")) {
               this.records.remove(key);
               this.store();
               return "§aRemoved " + found.getName() + " as friend.";
            } else {
               throw new IllegalArgumentException("friend action must be add, remove, near, or clear");
            }
         }
      }
   }

   private static boolean asciiPrefix(String text, String prefix) {
      byte[] a = text.getBytes(StandardCharsets.UTF_8);
      byte[] b = prefix.getBytes(StandardCharsets.UTF_8);
      if (b.length > a.length) {
         return false;
      } else {
         for (int i = 0; i < b.length; i++) {
            int x = a[i] & 255;
            int y = b[i] & 255;
            if (x >= 65 && x <= 90) {
               x += 32;
            }

            if (y >= 65 && y <= 90) {
               y += 32;
            }

            if (x != y) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   public boolean listens(int type) {
      return type == 0;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready()) {
         this.load();
         long time = System.currentTimeMillis() / 1000L;

         for (EntityPlayer player : context.world().playerEntities) {
            if (player != context.player()) {
               byte[] r = this.records.get(encodeRecord(record(player.getUniqueID(), 0L), 16));
               RelationshipService.set(player, 4, r != null);
               if (r != null) {
                  ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN).putLong(16, time);
               }
            }
         }

         this.store();
      }
   }

   @Override
   protected void onDisable(ClientContext context) {
      RelationshipService.clear(4);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      RelationshipService.clear(4);
   }
}
