package dev.slinky.client.packet;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

public final class NetworkSendAdapter {
   private static final ThreadLocal<NetworkSendAdapter.Invocation> DIRECT = new ThreadLocal<>();

   private NetworkSendAdapter() {
   }

   public static void sendDirect(NetworkManager receiver, Packet<?> packet) {
      if (receiver != null && packet != null) {
         NetworkSendAdapter.Invocation previous = DIRECT.get();
         DIRECT.set(new NetworkSendAdapter.Invocation(receiver, packet));

         try {
            receiver.sendPacket(packet);
         } finally {
            if (previous == null) {
               DIRECT.remove();
            } else {
               DIRECT.set(previous);
            }
         }
      } else {
         throw new IllegalArgumentException("A direct send needs its receiver and packet");
      }
   }

   public static boolean consumeDirect(NetworkManager receiver, Packet<?> packet) {
      NetworkSendAdapter.Invocation invocation = DIRECT.get();
      if (invocation != null && !invocation.consumed && invocation.receiver == receiver && invocation.packet == packet) {
         invocation.consumed = true;
         return true;
      } else {
         return false;
      }
   }

   private static final class Invocation {
      final NetworkManager receiver;
      final Packet<?> packet;
      boolean consumed;

      Invocation(NetworkManager receiver, Packet<?> packet) {
         this.receiver = receiver;
         this.packet = packet;
      }
   }
}
