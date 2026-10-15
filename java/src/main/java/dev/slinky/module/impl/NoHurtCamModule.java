package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.entity.EntityLivingBase;

public final class NoHurtCamModule extends EvidenceModule {
   private EntityLivingBase changedEntity;
   private final NoHurtCamModule.State state = new NoHurtCamModule.State();

   public NoHurtCamModule() {
      super("nohurtcam", "No Hurt Cam", ModuleCategory.RENDER);
   }

   public void applyPreRender(ClientContext context) {
      if (context.ready()) {
         if (this.changedEntity != context.player()) {
            this.restore();
            this.changedEntity = context.player();
         }

         this.changedEntity.maxHurtTime = this.state.apply(this.changedEntity.maxHurtTime);
      }
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.restore();
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.restore();
   }

   private void restore() {
      if (this.changedEntity != null) {
         this.changedEntity.maxHurtTime = this.state.restore(this.changedEntity.maxHurtTime);
         this.changedEntity = null;
      }
   }

   public static final class State {
      private int saved;
      private boolean held;

      public int apply(int current) {
         if (!this.held || current != Integer.MAX_VALUE) {
            this.saved = current;
         }

         this.held = true;
         return Integer.MAX_VALUE;
      }

      public int restore(int current) {
         int result = this.held && current == Integer.MAX_VALUE ? this.saved : current;
         this.held = false;
         return result;
      }
   }
}
