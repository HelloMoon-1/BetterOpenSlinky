package dev.slinky.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class GlCanvasBackend implements GuiCanvasState.Backend {
   private static final GlCanvasBackend.Driver OPENGL = new GlCanvasBackend.Driver() {
      @Override
      public void blend(int[] composite) {
         GlStateManager.tryBlendFuncSeparate(composite[0], composite[1], composite[2], composite[3]);
      }

      @Override
      public void draw(int mode, List<float[]> vertices) {
         GlStateManager.disableTexture2D();
         GlStateManager.disableAlpha();
         GlStateManager.shadeModel(7425);
         GL11.glBegin(mode);

         try {
            for (float[] vertex : vertices) {
               GlStateManager.color(vertex[2], vertex[3], vertex[4], vertex[5]);
               GL11.glVertex2f(vertex[0], vertex[1]);
            }
         } finally {
            GL11.glEnd();
         }
      }
   };
   private final GlCanvasBackend.Driver driver;

   public GlCanvasBackend() {
      this(OPENGL);
   }

   public GlCanvasBackend(GlCanvasBackend.Driver driver) {
      this.driver = Objects.requireNonNull(driver);
   }

   @Override
   public void fill(GuiCanvasState.Paint paint, int[] composite, float[] scissor, float[] extent, float fringe, List<CanvasPath.Path> paths, float[] bounds) {
      this.driver.blend(composite);

      for (CanvasPath.Path path : paths) {
         this.draw(6, path.fill, paint, 1.0F, false);
         if (!path.stroke.isEmpty()) {
            this.draw(5, path.stroke, paint, 1.0F, true);
         }
      }
   }

   @Override
   public void stroke(GuiCanvasState.Paint paint, int[] composite, float[] scissor, float[] extent, float fringe, float width, List<CanvasPath.Path> paths) {
      this.driver.blend(composite);
      float multiplier = fringe == 0.0F ? 1.0F : (width * 0.5F + fringe * 0.5F) / fringe;

      for (CanvasPath.Path path : paths) {
         this.draw(5, path.stroke, paint, multiplier, true);
      }
   }

   private void draw(int mode, List<float[]> vertices, GuiCanvasState.Paint paint, float multiplier, boolean fringe) {
      if (!vertices.isEmpty()) {
         if (this.driver == OPENGL && CanvasShader.available()) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableAlpha();
            CanvasShader.draw(mode, vertices, paint, multiplier, fringe);
         } else {
            List<float[]> emitted = fringe ? coverageTriangles(mode, vertices, multiplier) : vertices;
            List<float[]> rgba = new ArrayList<>();

            for (float[] vertex : emitted) {
               float coverage = fringe ? vertex[4] : 1.0F;
               float[] color = paint(paint, vertex[0], vertex[1]);
               rgba.add(new float[]{vertex[0], vertex[1], color[0], color[1], color[2], color[3] * coverage});
            }

            this.driver.draw(fringe ? 4 : mode, rgba);
         }
      }
   }

   public static List<float[]> coverageTriangles(int mode, List<float[]> vertices, float multiplier) {
      List<float[]> result = new ArrayList<>();
      float[] bands = multiplier > 1.0F ? new float[]{0.0F, 0.5F / multiplier, 1.0F - 0.5F / multiplier, 1.0F} : new float[]{0.0F, 0.5F, 1.0F};

      for (int index = 2; index < vertices.size(); index++) {
         float[] b = vertices.get(index - 1);
         float[] c = vertices.get(index);
         float[] a;
         if (mode == 6) {
            a = vertices.get(0);
         } else {
            if (mode != 5) {
               throw new IllegalArgumentException("Unsupported canvas fringe primitive");
            }

            a = vertices.get(index - 2);
            if ((index & 1) != 0) {
               float[] swap = a;
               a = b;
               b = swap;
            }
         }

         float low = Math.min(a[2], Math.min(b[2], c[2]));
         float high = Math.max(a[2], Math.max(b[2], c[2]));
         if (low == high) {
            triangle(result, a, b, c, multiplier, 0);
         } else {
            for (int band = 1; band < bands.length; band++) {
               List<float[]> polygon = clip(Arrays.asList(a, b, c), bands[band - 1], true);
               polygon = clip(polygon, bands[band], false);

               for (int point = 2; point < polygon.size(); point++) {
                  triangle(result, polygon.get(0), polygon.get(point - 1), polygon.get(point), multiplier, 0);
               }
            }
         }
      }

      return result;
   }

   private static List<float[]> clip(List<float[]> input, float boundary, boolean above) {
      List<float[]> output = new ArrayList<>();
      if (input.isEmpty()) {
         return output;
      } else {
         float[] previous = input.get(input.size() - 1);
         boolean wasInside = above ? previous[2] >= boundary : previous[2] <= boundary;

         for (float[] current : input) {
            boolean inside = above ? current[2] >= boundary : current[2] <= boundary;
            if (wasInside != inside) {
               float fraction = (boundary - previous[2]) / (current[2] - previous[2]);
               float[] point = interpolate(previous, current, fraction);
               point[2] = boundary;
               output.add(point);
            }

            if (inside) {
               output.add(current);
            }

            previous = current;
            wasInside = inside;
         }

         return output;
      }
   }

   private static float[] interpolate(float[] a, float[] b, float fraction) {
      float[] result = new float[4];

      for (int index = 0; index < 4; index++) {
         result[index] = a[index] + (b[index] - a[index]) * fraction;
      }

      return result;
   }

   public static float coverage(float u, float v, float multiplier) {
      return Math.min(1.0F, Math.max(0.0F, (1.0F - Math.abs(u * 2.0F - 1.0F)) * multiplier)) * Math.min(1.0F, v);
   }

   private static void triangle(List<float[]> output, float[] a, float[] b, float[] c, float multiplier, int depth) {
      float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (area != 0.0F) {
         float ac = coverage(a[2], a[3], multiplier);
         float bc = coverage(b[2], b[3], multiplier);
         float cc = coverage(c[2], c[3], multiplier);
         float[] ab = interpolate(a, b, 0.5F);
         float[] ca = interpolate(c, a, 0.5F);
         float[] cb = interpolate(c, b, 0.5F);
         float error = Math.max(
            Math.abs(coverage(ab[2], ab[3], multiplier) - (ac + bc) * 0.5F),
            Math.max(Math.abs(coverage(ca[2], ca[3], multiplier) - (cc + ac) * 0.5F), Math.abs(coverage(cb[2], cb[3], multiplier) - (cc + bc) * 0.5F))
         );
         if (error > 9.765625E-4F && depth < 6) {
            triangle(output, a, ab, ca, multiplier, depth + 1);
            triangle(output, ab, b, cb, multiplier, depth + 1);
            triangle(output, ca, cb, c, multiplier, depth + 1);
            triangle(output, ab, cb, ca, multiplier, depth + 1);
         } else {
            output.add(new float[]{a[0], a[1], a[2], a[3], ac});
            output.add(new float[]{b[0], b[1], b[2], b[3], bc});
            output.add(new float[]{c[0], c[1], c[2], c[3], cc});
         }
      }
   }

   public static float[] paint(GuiCanvasState.Paint paint, float x, float y) {
      float[] m = paint.transform;
      float determinant = m[0] * m[3] - m[1] * m[2];
      float amount = 0.0F;
      if (determinant != 0.0F && paint.feather != 0.0F) {
         float px = ((x - m[4]) * m[3] - (y - m[5]) * m[2]) / determinant;
         float py = ((y - m[5]) * m[0] - (x - m[4]) * m[1]) / determinant;
         float dx = Math.abs(px) - paint.extent[0] + paint.radius;
         float dy = Math.abs(py) - paint.extent[1] + paint.radius;
         float distance = Math.min(Math.max(dx, dy), 0.0F)
            + (float)StrictMath.sqrt((double)(Math.max(dx, 0.0F) * Math.max(dx, 0.0F) + Math.max(dy, 0.0F) * Math.max(dy, 0.0F)))
            - paint.radius;
         amount = Math.max(0.0F, Math.min(1.0F, (distance + paint.feather * 0.5F) / paint.feather));
      }

      float[] result = new float[4];

      for (int i = 0; i < 4; i++) {
         result[i] = paint.inner[i] + (paint.outer[i] - paint.inner[i]) * amount;
      }

      return result;
   }

   public interface Driver {
      void blend(int[] var1);

      void draw(int var1, List<float[]> var2);
   }
}
