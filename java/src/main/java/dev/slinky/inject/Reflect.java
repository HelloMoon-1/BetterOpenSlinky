package dev.slinky.inject;

import java.lang.reflect.Method;

public final class Reflect {
   private Reflect() {
   }

   public static String descriptor(Method method) {
      StringBuilder builder = new StringBuilder("(");

      for (Class<?> parameter : method.getParameterTypes()) {
         builder.append(descriptor(parameter));
      }

      return builder.append(')').append(descriptor(method.getReturnType())).toString();
   }

   public static String descriptor(Class<?> type) {
      if (type.isArray()) {
         return type.getName().replace('.', '/');
      } else if (type.isPrimitive()) {
         if (type == int.class) {
            return "I";
         } else if (type == void.class) {
            return "V";
         } else if (type == boolean.class) {
            return "Z";
         } else if (type == byte.class) {
            return "B";
         } else if (type == char.class) {
            return "C";
         } else if (type == short.class) {
            return "S";
         } else if (type == double.class) {
            return "D";
         } else {
            return type == float.class ? "F" : "J";
         }
      } else {
         return "L" + type.getName().replace('.', '/') + ";";
      }
   }
}
