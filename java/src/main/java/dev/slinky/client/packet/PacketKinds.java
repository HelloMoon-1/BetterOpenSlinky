package dev.slinky.client.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;

public final class PacketKinds {
   private PacketKinds() {
   }

   public static boolean realtimeDamage(Packet<?> p) {
      return p instanceof S19PacketEntityStatus || p instanceof S06PacketUpdateHealth || p instanceof S29PacketSoundEffect;
   }

   public static boolean blinkImmediate(Packet<?> p) {
      return p instanceof S2APacketParticles;
   }
}
