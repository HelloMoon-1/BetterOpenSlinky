package dev.slinky.client.packet;

import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S19PacketEntityStatus;

public final class PacketAccess {
   private static final Field STATUS_ID = ReflectionHelper.findField(S19PacketEntityStatus.class, "entityId", "field_149164_a");
   private static final Field RELATIVE_ID = ReflectionHelper.findField(S14PacketEntity.class, "entityId", "field_149074_a");

   private PacketAccess() {
   }

   public static int statusId(S19PacketEntityStatus p) {
      return integer(STATUS_ID, p);
   }

   public static int relativeId(S14PacketEntity p) {
      return integer(RELATIVE_ID, p);
   }

   private static int integer(Field field, Object p) {
      try {
         return field.getInt(p);
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException(var3);
      }
   }
}
