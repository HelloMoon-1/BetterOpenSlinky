package dev.slinky.client;

public final class ModuleActionService {
   private static Object owner;

   private ModuleActionService() {
   }

   public static synchronized boolean claim(Object token) {
      if (owner == token) {
         return true;
      } else if (owner != null) {
         return false;
      } else {
         owner = token;
         return true;
      }
   }

   public static synchronized void release(Object token) {
      if (owner == token) {
         owner = null;
      }
   }

   public static synchronized Object owner() {
      return owner;
   }

   public static synchronized boolean busy() {
      return owner != null;
   }

   public static synchronized void clear() {
      owner = null;
   }
}
