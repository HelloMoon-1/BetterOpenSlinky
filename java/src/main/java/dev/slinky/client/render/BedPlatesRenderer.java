package dev.slinky.client.render;

import dev.slinky.client.ClientContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;

/** Screen-facing world anchors, using the same captured camera projection as Name Tags. */
public final class BedPlatesRenderer {
   private static final int MAX_PLATES = 64;
   private static final float HALO = 6.0F;
   private static final float DISTANCE_SIZE = 16.0F;

   private BedPlatesRenderer() { }

   public static DrawPlan plan(ClientContext c, RenderAccess.Frame frame,
      List<BedPlatesWorld.Bed> beds, boolean showDistance, float scale, double range) {
      try (CanvasFonts.GuiFaceScope face = CanvasFonts.guiFace(1.0F)) {
         return planWithFace(c, frame, beds, showDistance, scale, range);
      }
   }

   private static DrawPlan planWithFace(ClientContext c, RenderAccess.Frame frame,
      List<BedPlatesWorld.Bed> beds, boolean showDistance, float scale, double range) {
      DrawPlan p = new DrawPlan().canvas();
      if (!c.ready() || frame == null || !Float.isFinite(frame.width) || !Float.isFinite(frame.height)
         || frame.width <= 0.0F || frame.height <= 0.0F || !Float.isFinite(scale) || scale <= 0.0F
         || !Double.isFinite(range) || range <= 0.0) return p;
      scale *= projectionZoom(frame.projection, c.minecraft().gameSettings.fovSetting);
      // The world helper caps this at 64. Keep collision work bounded independently as well.
      List<BedPlatesWorld.Bed> sorted = new ArrayList<>(beds.subList(0, Math.min(MAX_PLATES, beds.size())));
      Collections.sort(sorted, (a, b) -> Double.compare(BedPlatesWorld.distanceSquared(c, a), BedPlatesWorld.distanceSquared(c, b)));
      List<Plate> visible = new ArrayList<>();
      for (BedPlatesWorld.Bed bed : sorted) {
         double distanceSquared = BedPlatesWorld.distanceSquared(c, bed);
         if (!bed.scanned() || !Double.isFinite(distanceSquared) || distanceSquared > range * range) continue;
         RenderMath.Projection projected = frame.project(new double[]{bed.x() - frame.camera[0],
            bed.head.getY() + 1.15 - frame.camera[1], bed.z() - frame.camera[2]});
         // Explicitly reject behind-camera/non-finite projections, not just the shared depth clipping.
         if (projected == null || !Float.isFinite(projected.x) || !Float.isFinite(projected.y)
            || !Float.isFinite(projected.reciprocal) || projected.reciprocal <= 0.0F) continue;
         String label = (int)Math.sqrt(distanceSquared) + "m";
         float width = Math.max(28.0F, bed.items().size() * 18.0F + 8.0F);
         if (showDistance) width = Math.max(width, CanvasFonts.width(label, SmoothHudStyle.FONT_STYLE, DISTANCE_SIZE) + 10.0F);
         float height = showDistance ? 42.0F : 23.0F;
         float halfWidth = width * scale * 0.5F;
         float left = projected.x - halfWidth, right = projected.x + halfWidth;
         float top = projected.y - height * scale, bottom = projected.y;
         if (!Float.isFinite(left) || !Float.isFinite(right) || !Float.isFinite(top)
            || right <= 0.0F || left >= frame.width || bottom <= 0.0F || top >= frame.height) continue;
         // Reserve only visible pixels, including the halo and a small AA margin. An offscreen
         // portion must not suppress an unrelated on-screen plate. Partial edge plates remain anchored.
         float padding = HALO * scale + 1.0F;
         Plate candidate = new Plate(bed, label, projected.x, projected.y, width, height,
            Math.max(0.0F, left - padding), Math.max(0.0F, top - padding),
            Math.min(frame.width, right + padding), Math.min(frame.height, bottom + padding));
         boolean obscured = false;
         for (Plate admitted : visible) {
            if (candidate.left < admitted.right && candidate.right > admitted.left
               && candidate.top < admitted.bottom && candidate.bottom > admitted.top) {
               obscured = true;
               break;
            }
         }
         // Nearer beds win. Do not move a label away from its true world anchor or invent a distance.
         if (!obscured) visible.add(candidate);
      }
      p.op("itemDepth", 0.0F).disable(2929).disable(2896).disable(3008);
      for (int i = visible.size() - 1; i >= 0; i--) {
         Plate plate = visible.get(i);
         List<ItemStack> items = plate.bed.items();
         p.push().translate(plate.x, plate.y, 0.0F).scale(scale, scale, 1.0F);
         float x = -plate.width * 0.5F, y = -plate.height;
         // Layered neutral halo: inexpensive soft falloff, no framebuffer/shader dependency.
         for (int spread = (int)HALO; spread >= 1; spread -= 2) {
            float alpha = 0.04F + (HALO - spread) * 0.012F;
            p.color(0.02F, 0.02F, 0.025F, alpha).rect(x - spread, y - spread,
               plate.width + spread * 2.0F, plate.height + spread * 2.0F, 6.0F + spread);
         }
         p.color(0.12F, 0.12F, 0.13F, 0.93F).rect(x, y, plate.width, plate.height, 6.0F);
         p.color(0.58F, 0.58F, 0.60F, 0.23F).lineWidth(0.7F).roundedStroke(x + 0.4F, y + 0.4F,
            plate.width - 0.8F, plate.height - 0.8F, 5.6F);
         if (showDistance) {
            p.canvasText(plate.label, 0.0F, y + 12.0F, 0xFFF2F2F5, DISTANCE_SIZE, 18, SmoothHudStyle.FONT_STYLE);
         }
         float itemX = -items.size() * 9.0F + 1.0F;
         for (ItemStack item : items) {
            // Existing native-compatible baked-item renderer; preserves colored wool/clay metadata.
            p.color(1.0F, 1.0F, 1.0F, 1.0F).disable(2929).donorItem(item, itemX, -20.0F).disable(2929).disable(3008);
            itemX += 18.0F;
         }
         p.pop();
      }
      return p;
   }

   /** Read actual world projection, including Lunar/OptiFine zoom without depending on their keybinds. */
   public static float projectionZoom(float[] projection, float configuredFov) {
      if (projection == null || projection.length != 16 || !Float.isFinite(configuredFov)
         || configuredFov <= 0 || configuredFov >= 180 || Math.abs(projection[11]) < 0.5F) return 1;
      double zoom = Math.abs(projection[5]) * Math.tan(Math.toRadians(configuredFov) * 0.5);
      return Double.isFinite(zoom) && zoom > 0 ? (float)Math.max(0.25, Math.min(8.0, zoom)) : 1;
   }

   private static final class Plate {
      final BedPlatesWorld.Bed bed;
      final String label;
      final float x, y, width, height, left, top, right, bottom;

      Plate(BedPlatesWorld.Bed bed, String label, float x, float y, float width, float height,
         float left, float top, float right, float bottom) {
         this.bed = bed;
         this.label = label;
         this.x = x; this.y = y; this.width = width; this.height = height;
         this.left = left; this.top = top; this.right = right; this.bottom = bottom;
      }
   }
}
