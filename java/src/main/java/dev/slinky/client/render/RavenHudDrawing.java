package dev.slinky.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Only the immediate-mode primitives used by the donor TargetHUD and Radar. */
public final class RavenHudDrawing {
   private static final double[][] SIN = new double[2][31];
   private static final double[][] COS = new double[2][31];

   static {
      for (int side = 0; side < 2; side++) {
         for (int i = 0; i <= 30; i++) {
            double angle = (float)(side * 90 + i * 3) * ((float)Math.PI / 180.0F);
            SIN[side][i] = Math.sin(angle);
            COS[side][i] = Math.cos(angle);
         }
      }
   }

   private RavenHudDrawing() {
   }

   public static int alpha(int color, int value) {
      return color & 0xFFFFFF | Math.max(0, Math.min(255, value)) << 24;
   }

   /** Donor blendInt intentionally weights the first argument by amount. */
   public static int blend(int first, int second, double amount) {
      int weight = (int)(Math.max(0.0, Math.min(1.0, amount)) * 255.0);
      int other = 255 - weight;
      int a = ((first >>> 24) * weight + (second >>> 24) * other) >> 8;
      int r = ((first >> 16 & 255) * weight + (second >> 16 & 255) * other) >> 8;
      int g = ((first >> 8 & 255) * weight + (second >> 8 & 255) * other) >> 8;
      int b = ((first & 255) * weight + (second & 255) * other) >> 8;
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void color(int color) {
      GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
         (color & 255) / 255.0F, (color >>> 24) / 255.0F);
   }

   public static void begin() {
      GlStateManager.disableTexture2D();
      GlStateManager.disableAlpha();
      GlStateManager.enableBlend();
      // Preserve accumulated alpha when drawing into the runtime's transparent UI FBO.
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
   }

   public static void rect(float x, float y, float right, float bottom, int fill) {
      begin();
      color(fill);
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x, bottom);
      GL11.glVertex2f(right, bottom);
      GL11.glVertex2f(right, y);
      GL11.glEnd();
   }

   public static void rounded(float x, float y, float right, float bottom, float radius, int fill) {
      roundedGradient(x, y, right, bottom, radius, fill, fill);
   }

   public static void roundedGradient(float x, float y, float right, float bottom, float radius, int left, int end) {
      if (right <= x || bottom <= y) {
         return;
      }
      float r = Math.min(radius, Math.min(right - x, bottom - y));
      begin();
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      GlStateManager.shadeModel(GL11.GL_SMOOTH);
      GL11.glPushMatrix();
      try {
         GL11.glScaled(0.5, 0.5, 0.5);
         GL11.glBegin(GL11.GL_POLYGON);
         color(left);
         arc(x * 2.0F + r, y * 2.0F + r, -r, 0);
         arc(x * 2.0F + r, bottom * 2.0F - r, -r, 1);
         color(end);
         arc(right * 2.0F - r, bottom * 2.0F - r, r, 0);
         arc(right * 2.0F - r, y * 2.0F + r, r, 1);
         GL11.glEnd();
      } finally {
         GL11.glPopMatrix();
      }
      GlStateManager.shadeModel(GL11.GL_FLAT);
      GL11.glDisable(GL11.GL_LINE_SMOOTH);
   }

   public static void roundedBorder(float x, float y, float right, float bottom, float radius,
      int fill, int first, int second) {
      rounded(x, y, right, bottom, radius, fill);
      begin();
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      GlStateManager.shadeModel(GL11.GL_SMOOTH);
      GL11.glLineWidth(2.0F);
      GL11.glPushMatrix();
      try {
         GL11.glScaled(0.5, 0.5, 0.5);
         GL11.glBegin(GL11.GL_LINE_LOOP);
         color(first);
         arc(x * 2.0F + radius, y * 2.0F + radius, -radius, 0);
         arc(x * 2.0F + radius, bottom * 2.0F - radius, -radius, 1);
         color(second);
         arc(right * 2.0F - radius, bottom * 2.0F - radius, radius, 0);
         arc(right * 2.0F - radius, y * 2.0F + radius, radius, 1);
         GL11.glEnd();
      } finally {
         GL11.glPopMatrix();
      }
      GL11.glLineWidth(1.0F);
      GlStateManager.shadeModel(GL11.GL_FLAT);
      GL11.glDisable(GL11.GL_LINE_SMOOTH);
   }

   private static void arc(float x, float y, float radius, int side) {
      for (int i = 0; i <= 30; i++) {
         GL11.glVertex2d(x + SIN[side][i] * radius, y + COS[side][i] * radius);
      }
   }

   /** Donor circle(n=3/4) is a triangle/diamond, not a smooth circular marker. */
   public static void polygon(double x, double y, double radius, int sides, int fill) {
      begin();
      color(fill);
      GL11.glBegin(GL11.GL_TRIANGLE_FAN);
      for (int i = 0; i < sides; i++) {
         double angle = Math.PI * 2.0 * i / sides + Math.PI;
         GL11.glVertex2d(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius);
      }
      GL11.glEnd();
   }

   public static void filledBox(AxisAlignedBB box, int fill, float opacity) {
      begin();
      GlStateManager.color((fill >> 16 & 255) / 255.0F, (fill >> 8 & 255) / 255.0F,
         (fill & 255) / 255.0F, opacity);
      double x = box.minX, y = box.minY, z = box.minZ;
      double X = box.maxX, Y = box.maxY, Z = box.maxZ;
      GL11.glBegin(GL11.GL_QUADS);
      face(x, y, z, x, Y, z, X, Y, z, X, y, z);
      face(x, y, Z, X, y, Z, X, Y, Z, x, Y, Z);
      face(x, y, z, x, y, Z, x, Y, Z, x, Y, z);
      face(X, y, z, X, Y, z, X, Y, Z, X, y, Z);
      face(x, y, z, X, y, z, X, y, Z, x, y, Z);
      face(x, Y, z, x, Y, Z, X, Y, Z, X, Y, z);
      GL11.glEnd();
   }

   private static void face(double... points) {
      for (int i = 0; i < points.length; i += 3) {
         GL11.glVertex3d(points[i], points[i + 1], points[i + 2]);
      }
   }
}
