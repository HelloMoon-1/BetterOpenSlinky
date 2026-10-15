package dev.slinky.client;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;

public final class RelationshipService {
   private static final Map<Entity, Integer> FLAGS = new WeakHashMap<>();

   private RelationshipService() {
   }

   public static int flags(Entity entity) {
      Integer value = FLAGS.get(entity);
      return value == null ? 0 : value;
   }

   public static void set(Entity entity, int mask, boolean value) {
      int next = value ? flags(entity) | mask : flags(entity) & ~mask;
      if (next == 0) {
         FLAGS.remove(entity);
      } else {
         FLAGS.put(entity, next);
      }
   }

   public static void clear(int mask) {
      for (Entity entity : new ArrayList<>(FLAGS.keySet())) {
         set(entity, mask, false);
      }
   }

   public static void clear() {
      FLAGS.clear();
   }

   public static boolean hostile(Entity entity) {
      return (flags(entity) & 7) == 0;
   }
}
