package dev.slinky.client.hook;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.GlStateManager;

public final class GlStateCacheSnapshot {
   private final List<GlStateCacheSnapshot.Value> values = new ArrayList<>();
   private static List<Object[]> layout;

   private GlStateCacheSnapshot() {
   }

   public static GlStateCacheSnapshot capture() {
      List<Object[]> fields = layout;
      if (fields == null) {
         layout = fields = discover();
      }

      GlStateCacheSnapshot snapshot = new GlStateCacheSnapshot();

      try {
         for (Object[] entry : fields) {
            snapshot.values.add(new GlStateCacheSnapshot.Value((Field)entry[0], entry[1]));
         }

         return snapshot;
      } catch (ReflectiveOperationException var4) {
         throw new IllegalStateException("Cannot snapshot Minecraft's GL state cache", var4);
      }
   }

   private static List<Object[]> discover() {
      GlStateCacheSnapshot found = new GlStateCacheSnapshot();
      Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

      try {
         for (Field field : GlStateManager.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
               field.setAccessible(true);
               if (field.getType().isPrimitive()) {
                  if (!Modifier.isFinal(field.getModifiers())) {
                     found.values.add(new GlStateCacheSnapshot.Value(field, null));
                  }
               } else {
                  found.captureObject(field.get(null), visited);
               }
            }
         }
      } catch (ReflectiveOperationException var6) {
         throw new IllegalStateException("Cannot snapshot Minecraft's GL state cache", var6);
      }

      if (found.values.isEmpty()) {
         throw new IllegalStateException("Minecraft's GL cache layout is unsupported");
      } else {
         List<Object[]> result = new ArrayList<>(found.values.size());

         for (GlStateCacheSnapshot.Value value : found.values) {
            result.add(new Object[]{value.field, value.owner});
         }

         return result;
      }
   }

   private void captureObject(Object object, Set<Object> visited) throws IllegalAccessException {
      if (object != null && visited.add(object)) {
         Class<?> type = object.getClass();
         if (!type.isArray()) {
            if (type.getName().startsWith(GlStateManager.class.getName() + "$")) {
               for (Field field : type.getDeclaredFields()) {
                  if (!Modifier.isStatic(field.getModifiers())) {
                     field.setAccessible(true);
                     if (field.getType().isPrimitive()) {
                        if (!Modifier.isFinal(field.getModifiers())) {
                           this.values.add(new GlStateCacheSnapshot.Value(field, object));
                        }
                     } else {
                        this.captureObject(field.get(object), visited);
                     }
                  }
               }
            }
         } else if (!type.getComponentType().isPrimitive()) {
            for (int i = 0; i < Array.getLength(object); i++) {
               this.captureObject(Array.get(object, i), visited);
            }
         }
      }
   }

   public int valueCount() {
      return this.values.size();
   }

   public void restore() {
      try {
         for (GlStateCacheSnapshot.Value value : this.values) {
            value.restore();
         }
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException("Cannot restore Minecraft's GL state cache", var3);
      }
   }

   private static final class Value {
      final Field field;
      final Object owner;
      final Object value;

      Value(Field field, Object owner) throws IllegalAccessException {
         this.field = field;
         this.owner = owner;
         this.value = field.get(owner);
      }

      void restore() throws IllegalAccessException {
         this.field.set(this.owner, this.value);
      }
   }
}
