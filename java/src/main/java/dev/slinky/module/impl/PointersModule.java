package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.numeric.NumericServices;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.ColorSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;

public final strictfp class PointersModule extends EvidenceModule {
   private final List<PointersModule.Row> rows = new ArrayList<>();

   public strictfp List<PointersModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   public PointersModule() {
      super("pointers", "Pointers", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 7 || t == 18;
   }

   public static strictfp float angle(double x, double z, float yaw, int degrees, int pi, int adjust) {
      return RotationMath.wrap((float)(NumericServices.atan2(z, x) * RenderConstants.d(degrees) / RenderConstants.d(pi) + RenderConstants.d(adjust)) - yaw);
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 18) {
         if (!c.ready()) {
            return;
         }

         this.rows.clear();
         double[] self = RenderAccess.position(c.player());
         double[] previous = RenderAccess.previous(c.player());
         float range = this.number("range");
         float yaw = c.minecraft().getRenderViewEntity().rotationYaw;
         int mask = (this.flag("hide_friendlies") ? 5 : 0) + 2;

         for (EntityPlayer player : c.world().playerEntities) {
            int flags = RelationshipService.flags(player);
            if (player != c.player() && !player.isDead && (flags & mask) == 0) {
               double distance = RenderMath.distanceSquared(RenderAccess.position(player), self);
               if (!(distance > (double)(range * range))
                  && !(this.number("ignore_fov") > Math.abs(angle(player.posX - self[0], player.posZ - self[2], yaw, 1391808, 1391816, 1391824)))) {
                  ColorSetting.Value color = this.choice("color_mode") == 1
                     ? RenderAccess.nameColor(player)
                     : (this.choice("color_mode") == 2 ? this.color((flags & 5) != 0 ? "color_friendlies" : "color_enemies") : null);
                  this.rows.add(new PointersModule.Row(player, self, previous, color));
                  if (this.rows.size() == 100) {
                     break;
                  }
               }
            }
         }

         Collections.sort(this.rows, (a, b) -> RenderMath.compareDistance(a.distance, b.distance));
      } else if (c.ready()) {
         RenderAccess.Frame frame = RenderAccess.frame(c, e);
         if (frame != null) {
            RenderAccess.submit(
               e,
               this.plan(frame, e.timerRenderPartialTicks, c.minecraft().getRenderViewEntity().rotationYaw, c.minecraft().getRenderViewEntity().rotationPitch)
            );
         }
      }
   }

   public strictfp DrawPlan plan(RenderAccess.Frame frame, float partial, float yaw, float pitch) {
      DrawPlan p = new DrawPlan().push().translate(frame.width * RenderConstants.f(1391728), frame.height * RenderConstants.f(1391728), 0.0F);
      if (this.choice("style") != 0) {
         p.rotate(RenderConstants.f(1391736) - RenderMath.max(RenderConstants.f(1391732), pitch), RenderConstants.f(1391740), 0.0F, 0.0F);
      }

      float one = RenderConstants.f(1391740);
      float span = (float)this.range("near_far_range").upper - (float)this.range("near_far_range").lower;
      if (span == 0.0F) {
         span = RenderConstants.f(1391744);
      }

      p.lineWidth(one).enable(2848);

      for (PointersModule.Row row : this.rows) {
         double x = (double)RenderMath.lerp((float)row.px, (float)row.x, partial) - frame.camera[0];
         double z = (double)RenderMath.lerp((float)row.pz, (float)row.z, partial) - frame.camera[2];
         float angle = angle(x, z, yaw, 1391752, 1391760, 1391768);
         float size = this.number("scale") * RenderConstants.f(1391776);
         float distance = RenderMath.lerp(row.previousDistance, row.distance, partial);
         float factor = RenderMath.clamp(0.0F, one, (distance - (float)this.range("near_far_range").lower) / span);
         float radius = this.number("radius");
         if (this.flag("scale_radius")) {
            radius *= factor + one;
         }

         ColorSetting.Value color = this.choice("color_mode") == 0 ? RenderMath.blend(this.color("color_near"), this.color("color_far"), factor) : row.color;
         p.push()
            .rotate(angle, 0.0F, 0.0F, one)
            .scale(size, size, size)
            .translate(0.0F, Float.intBitsToFloat(Float.floatToRawIntBits(radius) ^ RenderConstants.i(1391792)), 0.0F)
            .color(color, 1.0F)
            .graphics(169280)
            .color(0.0F, 0.0F, 0.0F, 1.0F)
            .graphics(169328)
            .pop();
      }

      return p.pop();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.rows.clear();
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("style").isConfigured()) {
         return null;
      } else {
         int index = this.choice("style");
         return StatusText.label(this.getName(), index);
      }
   }

   public static final strictfp class Row {
      public final double x;
      public final double z;
      public final double px;
      public final double pz;
      public final float distance;
      public final float previousDistance;
      public final ColorSetting.Value color;

      public Row(EntityPlayer e, double[] self, double[] previous, ColorSetting.Value color) {
         this(RenderAccess.position(e), RenderAccess.previous(e), self, previous, color);
      }

      public Row(double[] position, double[] old, double[] self, double[] previous, ColorSetting.Value color) {
         this.x = position[0];
         this.z = position[2];
         this.px = old[0];
         this.pz = old[2];
         this.distance = (float)StrictMath.sqrt((double)((float)RenderMath.distanceSquared(position, self)));
         this.previousDistance = (float)StrictMath.sqrt(RenderMath.distanceSquared(old, previous));
         this.color = color;
      }
   }
}
