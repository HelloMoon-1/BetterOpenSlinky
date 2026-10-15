package dev.slinky.client.render;

/** Drip Lite's compact badge + title composition, with Slinky branding and no bitmap upscaling. */
public final class WatermarkRenderer {
   public static final int HEIGHT = 16;
   private static final float GAP = 5;
   private static final String TITLE = "Slinky";
   private static final String VERSION = " b4";
   private static long generation = -1;
   private static int density;
   private static float titleWidth, textWidth;
   private static DrawPlan cached;
   private static int cachedX, cachedY;
   private static float cachedScale, cachedOpacity;

   private WatermarkRenderer() { }

   private static void metrics() {
      int pixels = DripLiteStyle.density();
      if (generation == CanvasFonts.generation() && density == pixels) return;
      try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
         titleWidth = DripLiteStyle.width(TITLE);
         textWidth = titleWidth + DripLiteStyle.width(VERSION) + DripLiteStyle.PAD * 2;
      }
      density = pixels;
      generation = CanvasFonts.generation();
      cached = null;
   }

   public static float width() { metrics(); return HEIGHT + GAP + textWidth; }

   public static DrawPlan plan(int x, int y, float scale, float backgroundOpacity) {
      metrics();
      scale = Float.isFinite(scale) ? Math.max(0.1F, scale) : 1;
      backgroundOpacity = Float.isFinite(backgroundOpacity) ? Math.max(0, Math.min(1, backgroundOpacity)) : 0.85F;
      if (cached != null && cachedX == x && cachedY == y && cachedScale == scale && cachedOpacity == backgroundOpacity) return cached;
      DrawPlan p = new DrawPlan().canvas();
      try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
         float card = HEIGHT + GAP;
         p.push().translate(x, y, 0).scale(scale, scale, 1);
         DripLiteStyle.shadow(p, 0, 0, HEIGHT, HEIGHT, 5);
         DripLiteStyle.shadow(p, card, 0, textWidth, HEIGHT, 5);
         DripLiteStyle.halo(p, 0, 0, HEIGHT, HEIGHT, 5, 0.9F);
         DripLiteStyle.halo(p, card, 0, textWidth, HEIGHT, 5, 0.4F);
         DripLiteStyle.color(p, 0xFF29292F, backgroundOpacity);
         p.rect(0, 0, HEIGHT, HEIGHT, 5).rect(card, 0, textWidth, HEIGHT, 5);
         // Small outlined crest, matching the reference's icon footprint rather than a large text initial.
         emblem(p, 2.3F, 0.07F);
         emblem(p, 1.0F, 1.0F);
         DripLiteStyle.text(p, TITLE, card + DripLiteStyle.PAD, HEIGHT / 2.0F, DripLiteStyle.ACCENT);
         DripLiteStyle.text(p, VERSION, card + DripLiteStyle.PAD + titleWidth, HEIGHT / 2.0F, DripLiteStyle.SECONDARY);
         p.pop();
      }
      cached = p; cachedX = x; cachedY = y; cachedScale = scale; cachedOpacity = backgroundOpacity;
      generation = CanvasFonts.generation();
      return p;
   }

   private static void emblem(DrawPlan p, float lineWidth, float opacity) {
      DripLiteStyle.color(p, DripLiteStyle.ACCENT, opacity);
      p.lineWidth(lineWidth);
      float[] outline = {3.8F, 4.2F, 5.8F, 4.7F, 8, 4.0F, 10.2F, 4.7F, 12.2F, 4.2F,
         11.8F, 9.2F, 10, 11.8F, 8, 12.4F, 6, 11.8F, 4.2F, 9.2F, 3.8F, 4.2F};
      for (int i = 2; i < outline.length; i += 2) {
         p.line(outline[i - 2], outline[i - 1], outline[i], outline[i + 1]);
      }
      p.line(5.5F, 7.0F, 6.8F, 7.5F).line(9.2F, 7.5F, 10.5F, 7.0F)
         .line(6.5F, 9.8F, 8, 10.4F).line(8, 10.4F, 9.5F, 9.8F);
   }
}
