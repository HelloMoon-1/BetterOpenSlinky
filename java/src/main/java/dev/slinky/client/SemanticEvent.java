package dev.slinky.client;

import dev.slinky.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

public final class SemanticEvent {
   public final int type;
   public boolean cancelled;
   public boolean active = true;
   public boolean enabled;
   public boolean jump;
   public boolean sneaking;
   public boolean overrideRotation;
   public boolean remapMovement;
   public boolean reducing;
   public boolean queryOverride;
   public float sideways;
   public float forward;
   public float factor;
   public float scale = 1.0F;
   public float yaw;
   public float pitch;
   public float timerRenderPartialTicks;
   public double elapsedSeconds;
   public double distance;
   public double expand;
   public Entity entity;
   public Packet<?> packet;
   public NetworkManager receiver;
   public int dispatchMode;
   public long packetAgeMillis;
   public int packetSequence;
   public Module module;
   public double cameraX;
   public double cameraY;
   public double cameraZ;
   public float renderWidth;
   public float renderHeight;

   public SemanticEvent(int type) {
      this.type = type;
   }
}
