package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;

public final class FreeLookModule extends EvidenceModule {
   private boolean saved;
   private GameSettings savedSettings;
   private EntityPlayerSP savedPlayer;
   private int perspective;
   private float yaw;
   private float pitch;

   public FreeLookModule() {
      super("freelook", "Free Look", ModuleCategory.RENDER);
   }

   @Override
   protected void onEnable(ClientContext context) {
      this.updateContext(context);
   }

   @Override
   public void onClientTickStart(ClientContext context) {
      this.updateContext(context);
   }

   @Override
   public void onRenderTickStart(ClientContext context) {
      this.updateContext(context);
   }

   private void updateContext(ClientContext context) {
      boolean playerChanged = this.saved && this.savedPlayer != context.player();
      if (this.saved && (!context.ready() || playerChanged || this.savedSettings != context.minecraft().gameSettings)) {
         this.restore(context);
      }

      // A repeated enable notification must not replace the original perspective.
      if (!this.saved && context.ready() && context.minecraft().gameSettings != null) {
         this.savedSettings = context.minecraft().gameSettings;
         this.savedPlayer = context.player();
         boolean storedRotation = !playerChanged && RotationService.storedRotationValid();
         this.yaw = storedRotation ? RotationService.cameraYaw() : this.savedPlayer.rotationYaw;
         this.pitch = storedRotation ? RotationService.cameraPitch() : this.savedPlayer.rotationPitch;
         this.perspective = this.savedSettings.thirdPersonView;
         this.saved = true;
         this.savedSettings.thirdPersonView = this.choice("target_perspective");
      }
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.restore(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.restore(context);
   }

   private void restore(ClientContext context) {
      if (this.saved) {
         this.saved = false;
         this.savedSettings.thirdPersonView = this.perspective;
         if (context.ready() && context.player() == this.savedPlayer) {
            RotationService.restoreCamera(this.yaw, this.pitch);
         }

         this.savedSettings = null;
         this.savedPlayer = null;
      }
   }

   @Override
   public boolean listens(int type) {
      return type == 13;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 13) {
         this.updateContext(context);
         if (this.saved) {
            event.active = true;
            event.yaw = this.yaw;
            event.pitch = this.pitch;
         }
      }
   }
}
