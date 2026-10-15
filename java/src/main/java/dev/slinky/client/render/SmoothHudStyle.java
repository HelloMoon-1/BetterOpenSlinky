package dev.slinky.client.render;

/** Shared smooth face and palette for the screenshot HUDs. Caller owns a GuiFaceScope. */
public final class SmoothHudStyle {
   public static final float FONT_SIZE = 12.0F;
   public static final float ROW_HEIGHT = 16.0F;
   public static final float RADIUS = 4.0F;
   public static final int FONT_STYLE = 1;
   public static final int ACCENT = 0xFFCF46EA;
   public static final int MUTED = 0xFFB9BAC0;
   public static final int BACKGROUND = 0xCC1A1B21;

   private SmoothHudStyle() { }

   public static CanvasFonts.GuiFaceScope face() {
      return CanvasFonts.guiFace(pixelRatio());
   }

   public static int pixelRatio() {
      return new net.minecraft.client.gui.ScaledResolution(
         net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
   }

   public static void background(DrawPlan plan, float opacity) {
      plan.color((BACKGROUND >> 16 & 255) / 255.0F, (BACKGROUND >> 8 & 255) / 255.0F,
         (BACKGROUND & 255) / 255.0F, opacity);
   }

   public static float width(String text) {
      return CanvasFonts.width(text, FONT_STYLE, FONT_SIZE);
   }

   public static void text(DrawPlan plan, String text, float x, float centerY, int color) {
      plan.canvasText(text, x, centerY, color, FONT_SIZE, 17, FONT_STYLE);
   }
}
