package dev.slinky.client.hook;

import org.lwjgl.opengl.GL11;

public final class GlStateScope implements AutoCloseable {
   private static final GlStateScope.Attributes OPENGL = new GlStateScope.Attributes() {
      @Override
      public void pushServer() {
         GL11.glPushAttrib(1048575);
      }

      @Override
      public void pushClient() {
         GL11.glPushClientAttrib(3);
      }

      @Override
      public void popClient() {
         GL11.glPopClientAttrib();
      }

      @Override
      public void popServer() {
         GL11.glPopAttrib();
      }
   };
   private static int depth;
   private final GlStateScope.Attributes attributes;
   private final Runnable restoreCache;
   private boolean server;
   private boolean client;
   private boolean closed;
   private boolean counted;

   GlStateScope(GlStateScope.Attributes attributes, Runnable restoreCache) {
      this.attributes = attributes;
      this.restoreCache = restoreCache;

      try {
         attributes.pushServer();
         this.server = true;
         attributes.pushClient();
         this.client = true;
      } catch (Error | RuntimeException var6) {
         try {
            this.close();
         } catch (Error | RuntimeException var5) {
            var6.addSuppressed(var5);
         }

         throw var6;
      }
   }

   public static GlStateScope open() {
      GlStateCacheSnapshot cache = GlStateCacheSnapshot.capture();
      GlStateScope scope = new GlStateScope(OPENGL, cache::restore);
      depth++;
      scope.counted = true;
      return scope;
   }

   public static boolean active() {
      return depth > 0;
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
         if (this.counted) {
            depth--;
         }

         Throwable failure = null;
         if (this.client) {
            failure = restore(this.attributes::popClient, failure);
         }

         if (this.server) {
            failure = restore(this.attributes::popServer, failure);
         }

         failure = restore(this.restoreCache, failure);
         if (this.counted && depth == 0 && failure == null) {
            failure = restore(GlCacheResync::run, failure);
         }

         if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
         } else if (failure instanceof Error) {
            throw (Error)failure;
         }
      }
   }

   private static Throwable restore(Runnable action, Throwable failure) {
      try {
         action.run();
      } catch (Error | RuntimeException var3) {
         if (failure == null) {
            return var3;
         }

         failure.addSuppressed(var3);
      }

      return failure;
   }

   interface Attributes {
      void pushServer();

      void pushClient();

      void popClient();

      void popServer();
   }
}
