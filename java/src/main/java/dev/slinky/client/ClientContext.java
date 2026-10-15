package dev.slinky.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;

public final class ClientContext {
   private final Minecraft minecraft;
   private BooleanSupplier guiEnabled = () -> false;

   public ClientContext(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public Minecraft minecraft() {
      return this.minecraft;
   }

   public EntityPlayerSP player() {
      return this.minecraft.thePlayer;
   }

   public WorldClient world() {
      return this.minecraft.theWorld;
   }

   public boolean ready() {
      return this.player() != null && this.world() != null;
   }

   public void setGuiEnabled(BooleanSupplier guiEnabled) {
      this.guiEnabled = guiEnabled;
   }

   public boolean inputReady() {
      return this.ready() && this.minecraft.inGameHasFocus && !this.guiEnabled.getAsBoolean();
   }
}
