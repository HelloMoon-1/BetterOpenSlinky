package dev.slinky.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionHelper {
   private ReflectionHelper() {
   }

   public static Field findField(Class<?> type, String... names) {
      for (String name : names) {
         try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException var10) {
         }
      }

      String owner = Mappings.unmapClass(type.getName().replace('.', '/'));

      for (String name : names) {
         String runtime = Mappings.mapField(owner, name);
         if (!runtime.equals(name)) {
            try {
               Field field = type.getDeclaredField(runtime);
               field.setAccessible(true);
               return field;
            } catch (NoSuchFieldException var9) {
            }
         }
      }

      throw new IllegalStateException("Missing field " + owner + "." + String.join("/", names));
   }

   public static <E> Method findMethod(Class<? super E> type, E instance, String[] names, Class<?>... parameterTypes) {
      for (String name : names) {
         try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
         } catch (NoSuchMethodException var14) {
         }
      }

      String owner = Mappings.unmapClass(type.getName().replace('.', '/'));
      StringBuilder runtimeParameters = new StringBuilder("(");

      for (Class<?> parameter : parameterTypes) {
         runtimeParameters.append(Reflect.descriptor(parameter));
      }

      String namedParameters = Mappings.unmapDescriptor(runtimeParameters.append(')').toString());

      for (String name : names) {
         String runtime = Mappings.mapMethodByParameters(owner, name, namedParameters);
         if (!runtime.equals(name)) {
            try {
               Method method = type.getDeclaredMethod(runtime, parameterTypes);
               method.setAccessible(true);
               return method;
            } catch (NoSuchMethodException var13) {
            }
         }
      }

      throw new IllegalStateException("Missing method " + owner + "." + String.join("/", names) + namedParameters);
   }

   public static <T, E> T getPrivateValue(Class<? super E> type, E instance, String... names) {
      try {
         return (T)findField(type, names).get(instance);
      } catch (IllegalAccessException var4) {
         throw new IllegalStateException(var4);
      }
   }
}
