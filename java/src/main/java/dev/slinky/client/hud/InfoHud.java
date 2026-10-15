package dev.slinky.client.hud;

import dev.slinky.client.render.DrawPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;

/** OpenB4's per-frame information-row collector. Producers must supply real observations. */
public final class InfoHud {
   public static final class Line {
      private boolean columns;
      private String left, right;
      private ItemStack item;
      private int leftWidth, rightWidth, width;

      private void set(String left, String right, ItemStack item) {
         this.columns = true;
         this.left = color(left);
         this.right = color(right);
         this.item = item;
         this.leftWidth = mc().fontRendererObj.getStringWidth(this.left);
         this.rightWidth = mc().fontRendererObj.getStringWidth(this.right);
      }

      private void set(String text) {
         this.columns = false;
         this.left = color(text);
         this.right = null;
         this.item = null;
         this.width = this.leftWidth = mc().fontRendererObj.getStringWidth(this.left);
      }
   }

   private static final List<Line> lines = new ArrayList<>();
   private static final List<Line> pool = new ArrayList<>();
   private static int poolUsed, width, leftColumn, rightColumn, gap;
   private static boolean hasItems;
   private static int targetWidth = -1, targetLeft, targetRight;
   private static float curWidth, curLeft, curRight, fromWidth, fromLeft, fromRight;
   private static long animStart;

   private InfoHud() { }

   private static Minecraft mc() {
      return Minecraft.getMinecraft();
   }

   public static String color(String text) {
      return text.replace('&', '§');
   }

   public static String strip(String text) {
      if (text == null || text.isEmpty()) return "";
      StringBuilder result = new StringBuilder(text.length());
      boolean skip = false;
      for (int i = 0; i < text.length(); ++i) {
         char c = text.charAt(i);
         if (skip) { skip = false; continue; }
         if (c == '§') { skip = true; continue; }
         if (c <= '\u0014' || c >= '\u007f') continue;
         result.append(c);
      }
      return result.toString();
   }

   public static void clear() {
      lines.clear();
      rightColumn = leftColumn = poolUsed = width = 0;
      hasItems = false;
   }

   public static void add(Line line) {
      if (line == null) return;
      if (line.columns) {
         gap = mc().fontRendererObj.getCharWidth(' ') * 2;
         leftColumn = Math.max(leftColumn, line.leftWidth);
         rightColumn = Math.max(rightColumn, line.rightWidth);
         if (line.item != null) hasItems = true;
         width = Math.max(width, leftColumn + gap + rightColumn + (hasItems ? gap + 10 : 0));
      } else if (line.width > width) {
         width = line.width;
      }
      lines.add(line);
   }

   public static void add(String text) {
      Line line;
      if (poolUsed < pool.size()) line = pool.get(poolUsed);
      else {
         line = new Line();
         pool.add(line);
      }
      ++poolUsed;
      line.set(text);
      add(line);
   }

   public static Line line(String left, String right, ItemStack item) {
      Line line = new Line();
      line.set(left, right, item);
      return line;
   }

   private static void animate(int w, int left, int right, boolean animate, long now) {
      if (!animate || targetWidth == -1) {
         targetWidth = w;
         curWidth = fromWidth = targetWidth;
         targetLeft = left;
         curLeft = fromLeft = targetLeft;
         targetRight = right;
         curRight = fromRight = targetRight;
         animStart = now;
         return;
      }
      float f = Math.min(1.0F, (float)(now - animStart) / 100.0F);
      f = f * f * (3.0F - 2.0F * f);
      curWidth = fromWidth + (targetWidth - fromWidth) * f;
      curLeft = fromLeft + (targetLeft - fromLeft) * f;
      curRight = fromRight + (targetRight - fromRight) * f;
      if (w != targetWidth || left != targetLeft || right != targetRight) {
         fromWidth = curWidth;
         fromLeft = curLeft;
         fromRight = curRight;
         targetWidth = w;
         targetLeft = left;
         targetRight = right;
         animStart = now;
      }
   }

   /** Background values match donor None, Rect, Round. Always drains the frame, even when hidden. */
   public static DrawPlan plan(boolean visible, int x, int y, int background, long now) {
      DrawPlan p = new DrawPlan();
      int n = lines.size();
      if (n == 0) return p;
      try {
         if (!visible) return p;
         boolean backdrop = background != 0;
         animate(width, leftColumn, rightColumn, backdrop, now);
         float x1 = x - 5, x2 = x + curWidth + 4.0F;
         float y1 = y - 5, y2 = y + (n - 1) * 12 + mc().fontRendererObj.FONT_HEIGHT + 5;
         if (backdrop) {
            ScaledResolution sr = new ScaledResolution(mc());
            if (background == 2) {
               if (x1 <= 0.0F) x1 = -3.0F;
               if (x2 > sr.getScaledWidth()) x2 = sr.getScaledWidth() + 3;
            }
            background(p, x1, y1, x2 - 0.5F, y2 - 1.5F, background);
            scissor(p, x1, y1, x2 - 0.5F, y2 - 1.5F);
         }
         int fontHeight = mc().fontRendererObj.FONT_HEIGHT;
         for (int i = 0; i < n; ++i) {
            Line line = lines.get(i);
            if (!line.columns) p.text(line.left, x, y + i * 12, -1, !backdrop, fontHeight);
         }
         if (backdrop) scissor(p, x, y1, x + curLeft, y2 - 1.5F);
         for (int i = 0; i < n; ++i) {
            Line line = lines.get(i);
            if (line.columns) p.text(line.left, x, y + i * 12, -1, !backdrop, fontHeight);
         }
         if (backdrop) scissor(p, x1, y1, x2 - 0.5F, y2 - 1.5F);
         float rx = x + curLeft + gap;
         for (int i = 0; i < n; ++i) {
            Line line = lines.get(i);
            if (line.columns) p.text(line.right, rx + curRight - line.rightWidth, y + i * 12, -1, !backdrop, fontHeight);
         }
         if (hasItems) {
            float ix = (rx + curRight + gap) * 1.6F;
            p.push().scale(0.625F, 0.625F, 0.625F);
            for (int i = 0; i < n; ++i) {
               Line line = lines.get(i);
               if (!line.columns || line.item == null) continue;
               float iy = (y + i * 12 - 0.5F) * 1.6F;
               p.op("depthFunc", 519).donorItem(line.item, ix, iy);
            }
            p.op("depthFunc", 515).pop();
         }
         if (backdrop) p.unclip();
         return p.color(1, 1, 1, 1);
      } finally {
         clear();
      }
   }

   private static void scissor(DrawPlan p, float x1, float y1, float x2, float y2) {
      int scale = new ScaledResolution(mc()).getScaleFactor();
      int a = Math.max(0, (int)Math.floor(x1 * scale));
      int b = Math.min(mc().displayWidth, (int)Math.ceil(x2 * scale));
      int c = Math.max(0, (int)Math.floor(mc().displayHeight - y2 * scale));
      int d = Math.min(mc().displayHeight, (int)Math.ceil(mc().displayHeight - y1 * scale));
      p.clip(a, mc().displayHeight - d, Math.max(0, b - a), Math.max(0, d - c));
   }

   /** Donor uses a doubled-coordinate 3-degree polygon: radius 8 means four GUI pixels. */
   public static void background(DrawPlan p, float x, float y, float x2, float y2, int mode) {
      if (mode == 0) return;
      p.color(0, 0, 0, 125.0F / 255.0F);
      if (mode == 1) {
         p.vertices(7, 2, x2, y, x, y, x, y2, x2, y2);
      } else {
         float radius = Math.min(8.0F, Math.min(x2 - x, y2 - y)) * 0.5F;
         float[] vertices = new float[124 * 2];
         int at = 0;
         for (int corner = 0; corner < 4; ++corner) {
            for (int degrees = 0; degrees <= 90; degrees += 3) {
               double angle = (corner % 2 == 0 ? degrees : degrees + 90) * ((float)Math.PI / 180);
               float sx = corner < 2 ? -radius : radius;
               float sy = corner < 2 ? -radius : radius;
               float cx = corner < 2 ? x + radius : x2 - radius;
               float cy = corner == 0 || corner == 3 ? y + radius : y2 - radius;
               vertices[at++] = cx + (float)Math.sin(angle) * sx;
               vertices[at++] = cy + (float)Math.cos(angle) * sy;
            }
         }
         p.enable(2848).vertices(9, 2, vertices).disable(2848);
      }
      p.color(1, 1, 1, 1);
   }
}
