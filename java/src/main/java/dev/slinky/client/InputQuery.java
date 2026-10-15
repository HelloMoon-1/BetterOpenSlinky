package dev.slinky.client;

import java.util.Objects;

public final class InputQuery {
   private final BindingInput keys;

   public InputQuery(BindingInput keys) {
      this.keys = Objects.requireNonNull(keys);
   }

   public int query(InputQuery.Request request, InputQuery.Host host) {
      boolean unchanged = true;
      if (request.type == 2) {
         unchanged = this.keys.update(request.key, request.requested, request.toggle);
      }

      if (!host.environmentAvailable()) {
         return 0;
      } else {
         int result = host.dispatch(request, unchanged);
         // A GUI may consume input, but must never trap an already-held module on key release.
         if (request.type == 2 && request.requested == 0 && !unchanged && request.key != 0) {
            host.moduleBindings(request.key, request.requested);
            return result;
         }
         if ((result & 0xFF) != 0 || request.type != 2 || unchanged || request.key == 0) {
            return result;
         } else if (host.ready() && !host.guiEnabled()) {
            if (host.friendsEnabled() && request.type == 2 && request.requested == 1 && request.key == 3) {
               host.friendAction();
            }

            if (!host.moduleBindings(request.key, request.requested)) {
               host.profileBindings(request.key, request.requested);
            }

            return result;
         } else {
            return result;
         }
      }
   }

   public interface Host {
      boolean environmentAvailable();

      int dispatch(InputQuery.Request var1, boolean var2);

      boolean ready();

      boolean guiEnabled();

      boolean friendsEnabled();

      void friendAction();

      boolean moduleBindings(int var1, byte var2);

      void profileBindings(int var1, byte var2);
   }

   public static final class Request {
      public int type;
      public int key;
      public int x;
      public int y;
      public byte requested;
      public byte toggle;
      public float wheel;
      public byte[] utf8 = new byte[0];

      public Request(int type, int key, boolean down) {
         this.type = type;
         this.key = key;
         this.requested = (byte)(down ? 1 : 0);
      }
   }
}
