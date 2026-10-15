package dev.slinky.client.render;

/** Reference proportions in GUI units. Shared exclusively by the Drip Lite HUD and its editors. */
public final class DripLiteStyle {
   public static final float FONT = 12.5F;
   public static final float ROW = 15.5F;
   public static final float RADIUS = 4.5F;
   public static final float PAD = 5.0F;
   public static final int ACCENT = 0xFFD541EC;
   public static final int LIGHT = 0xFFE49AF1;
   public static final int SECONDARY = 0xFFBDBDC2;
   public static final int SURFACE = 0xDB29292F;

   private DripLiteStyle() { }

   public static int density() { return SmoothHudStyle.pixelRatio(); }
   public static CanvasFonts.GuiFaceScope face() { return CanvasFonts.guiFace(density()); }
   public static float width(String text) { return CanvasFonts.width(text, 1, FONT); }

   public static int accent(int row) {
      double phase = (System.nanoTime() % 4000000000L) / 4000000000.0 * Math.PI * 2.0;
      float t = (float)(0.5 - 0.5 * Math.cos(phase - row * 0.42));
      int r = Math.round((ACCENT >> 16 & 255) * (1 - t) + (LIGHT >> 16 & 255) * t);
      int g = Math.round((ACCENT >> 8 & 255) * (1 - t) + (LIGHT >> 8 & 255) * t);
      int b = Math.round((ACCENT & 255) * (1 - t) + (LIGHT & 255) * t);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static void color(DrawPlan p, int color, float opacity) {
      p.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
         (color & 255) / 255.0F, (color >>> 24) / 255.0F * opacity);
   }

   public static void text(DrawPlan p, String text, float x, float centerY, int color) {
      p.canvasText(text, x, centerY, color, FONT, 17, 1);
   }

   public static void button(DrawPlan p, net.minecraft.client.gui.GuiButton button, int width, int mx, int my) {
      if (!button.visible) return;
      boolean hover = button.enabled && mx >= button.xPosition && mx < button.xPosition + width
         && my >= button.yPosition && my < button.yPosition + 20;
      color(p, SURFACE, hover ? 1 : 0.9F);
      p.rect(button.xPosition, button.yPosition, width, 20, RADIUS);
      p.canvasText(button.displayString, button.xPosition + width / 2.0F, button.yPosition + 10,
         button.enabled ? ACCENT : SECONDARY, 11, 18, 1);
   }

   public static void halo(DrawPlan p, float x, float y, float w, float h, float radius, float strength) {
      for (int spread = 4; spread >= 1; spread--) {
         color(p, ACCENT, strength * (5 - spread) * 0.009F);
         p.rect(x - spread, y - spread, w + spread * 2, h + spread * 2, radius + spread);
      }
   }

   public static void shadow(DrawPlan p, float x, float y, float w, float h, float radius) {
      for (int spread = 3; spread >= 1; spread--) {
         p.color(0, 0, 0, (4 - spread) * 0.035F)
            .rect(x - spread, y - spread, w + spread * 2, h + spread * 2, radius + spread);
      }
   }
}
