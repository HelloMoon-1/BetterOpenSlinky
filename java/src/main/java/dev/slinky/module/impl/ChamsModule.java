package dev.slinky.module.impl;

import dev.slinky.client.RelationshipService;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.Objects;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public final class ChamsModule extends EvidenceModule {
   private static ChamsModule.Graphics graphics = new ChamsModule.Graphics() {
      @Override
      public void enable(int c) {
         GL11.glEnable(c);
      }

      @Override
      public void offset(float a, float b) {
         GL11.glPolygonOffset(a, b);
      }

      @Override
      public void disable(int c) {
         GL11.glDisable(c);
      }
   };

   public ChamsModule() {
      super("chams", "Chams", ModuleCategory.RENDER);
   }

   public static ChamsModule.Graphics bindGraphics(ChamsModule.Graphics next) {
      ChamsModule.Graphics old = graphics;
      graphics = Objects.requireNonNull(next);
      return old;
   }

   public boolean eligible(Entity entity) {
      return this.isEnabled() && (RelationshipService.flags(entity) & (this.flag("hide_friendlies") ? 5 : 0) + 2) == 0;
   }

   public boolean begin(Entity entity) {
      boolean selected = this.eligible(entity);
      if (selected) {
         graphics.enable(32823);
         graphics.offset(RenderConstants.f(1392420), RenderConstants.f(1392424));
      }

      return selected;
   }

   public void end(boolean selected) {
      if (selected) {
         graphics.disable(32823);
      }
   }

   public void render(Entity entity, Runnable original) {
      boolean selected = this.begin(entity);

      try {
         original.run();
      } finally {
         this.end(selected);
      }
   }

   public interface Graphics {
      void enable(int var1);

      void offset(float var1, float var2);

      void disable(int var1);
   }
}
