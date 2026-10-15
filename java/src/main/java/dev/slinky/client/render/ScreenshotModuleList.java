package dev.slinky.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.FontRenderer;

/** Drip Lite list. Historical class/position keys remain compatible with existing profiles. */
public final class ScreenshotModuleList {
   private static final float RIGHT_PAD = 3.0F;
   private static final float MARGIN = 4.0F;
   private static final float TEXT_TOP = 3.0F;
   private static final double DURATION = 0.20;

   private ScreenshotModuleList() { }

   public static final class Entry {
      private final String id, name, status;
      public Entry(String name, String status) { this(name, name, status); }
      public Entry(String id, String name, String status) {
         this.name = clean(name);
         this.status = clean(status);
         this.id = id == null ? this.name : id;
      }
      @Override public boolean equals(Object value) {
         if (!(value instanceof Entry)) return false;
         Entry other = (Entry)value;
         return this.id.equals(other.id) && this.name.equals(other.name) && this.status.equals(other.status);
      }
      @Override public int hashCode() { return 31 * (31 * this.id.hashCode() + this.name.hashCode()) + this.status.hashCode(); }
   }

   private static final class Row {
      private Entry entry;
      private float nameWidth, width;
      private float progress;
      private boolean active;
      private DrawPlan glyphs;
      private float glyphScale;
      private int glyphRow;

      private void measure(Entry entry) {
         this.entry = entry;
         this.nameWidth = DripLiteStyle.width(entry.name);
         this.width = this.nameWidth + (entry.status.isEmpty() ? 0 : DripLiteStyle.width(" " + entry.status));
         this.glyphs = null;
      }
      private float visibility() { return this.progress * this.progress * (3 - 2 * this.progress); }

      private DrawPlan text(float scale, int index) {
         if (this.glyphs == null || this.glyphScale != scale || this.glyphRow != index) {
            DrawPlan p = new DrawPlan().canvas().push().scale(scale, scale, 1);
            p.dripText(this.entry.name, -this.width, DripLiteStyle.ROW / 2, DripLiteStyle.FONT, index);
            if (!this.entry.status.isEmpty()) {
               DripLiteStyle.text(p, " " + this.entry.status, -this.width + this.nameWidth,
                  DripLiteStyle.ROW / 2, DripLiteStyle.SECONDARY);
            }
            this.glyphs = p.pop();
            this.glyphScale = scale;
            this.glyphRow = index;
         }
         return this.glyphs;
      }
   }

   /** Actual rendered rectangles are also the editor's drag hit regions. */
   public static final class Layout {
      public final DrawPlan plan;
      public final float left, top, right, bottom, anchorX, anchorY;
      private final float[][] bounds;
      private Layout(DrawPlan plan, float left, float top, float right, float bottom,
                     float anchorX, float anchorY, float[][] bounds) {
         this.plan = plan;
         this.left = left; this.top = top; this.right = right; this.bottom = bottom;
         this.anchorX = anchorX; this.anchorY = anchorY; this.bounds = bounds;
      }
      public boolean empty() { return this.bounds.length == 0; }
      public boolean contains(float x, float y) {
         for (float[] b : this.bounds) {
            if (x >= b[0] && y >= b[1] && x < b[2] && y < b[3]) return true;
         }
         return false;
      }
   }

   /** Only membership/progress lives here; both preview and animation use compose(). */
   public static final class Animator {
      private final Map<String, Row> rows = new LinkedHashMap<>();
      private List<Entry> previous = Collections.emptyList();
      private long lastFrame, generation = -1;
      private int density;
      private float width, height, x, y, scale;
      private boolean background, animate, moving;
      private Layout cached;

      public void clear() {
         this.rows.clear(); this.previous = Collections.emptyList();
         this.lastFrame = 0; this.cached = null; this.moving = false;
      }

      public DrawPlan frame(List<Entry> entries, float width, float height, float x, float y,
                            float scale, boolean background, boolean animate) {
         long now = System.nanoTime();
         double dt = this.lastFrame == 0 ? 0 : Math.max(0, (now - this.lastFrame) * 1.0E-9);
         this.lastFrame = now;
         int density = DripLiteStyle.density();
         boolean fonts = this.generation != CanvasFonts.generation() || this.density != density;
         boolean changed = !entries.equals(this.previous);
         boolean geometry = this.width != width || this.height != height || this.x != x || this.y != y
            || this.scale != scale || this.background != background || this.animate != animate;
         if (this.cached != null && !fonts && !changed && !geometry && !this.moving) return this.cached.plan;
         try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
            if (fonts) for (Row row : this.rows.values()) row.measure(row.entry);
            if (changed || this.cached == null) {
               for (Row row : this.rows.values()) row.active = false;
               for (Entry entry : entries) {
                  if (entry.name.isEmpty()) continue;
                  Row row = this.rows.get(entry.id);
                  if (row == null) {
                     row = new Row(); row.measure(entry);
                     this.rows.put(entry.id, row);
                  } else if (!entry.equals(row.entry)) row.measure(entry);
                  row.active = true;
               }
               this.previous = new ArrayList<>(entries);
            }
            this.moving = false;
            for (Iterator<Row> it = this.rows.values().iterator(); it.hasNext();) {
               Row row = it.next();
               float target = row.active ? 1 : 0;
               float step = (float)Math.min(1, dt / DURATION);
               row.progress = !animate ? target : target > row.progress
                  ? Math.min(target, row.progress + step) : Math.max(target, row.progress - step);
               if (!row.active && row.progress == 0) { it.remove(); continue; }
               this.moving |= row.progress != target;
            }
            this.cached = compose(new ArrayList<>(this.rows.values()), width, height, x, y, scale, background);
         }
         this.width = width; this.height = height; this.x = x; this.y = y; this.scale = scale;
         this.background = background; this.animate = animate;
         this.density = density; this.generation = CanvasFonts.generation();
         return this.cached.plan;
      }
   }

   public static DrawPlan plan(FontRenderer font, List<Entry> entries, float width, float height,
                               float x, float y, float scale, boolean background) {
      return layout(font, entries, width, height, x, y, scale, background).plan;
   }

   public static Layout layout(FontRenderer font, List<Entry> entries, float width, float height,
                               float x, float y, float scale, boolean background) {
      try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
         List<Row> rows = new ArrayList<>();
         for (Entry entry : entries) {
            if (entry.name.isEmpty()) continue;
            Row row = new Row(); row.measure(entry); row.progress = 1; row.active = true;
            rows.add(row);
         }
         return compose(rows, width, height, x, y, scale, background);
      }
   }

   private static Layout compose(List<Row> rows, float width, float height, float x, float y,
                                 float requestedScale, boolean background) {
      DrawPlan p = new DrawPlan().canvas();
      if (rows.isEmpty() || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
         return new Layout(p, x, y, x, y, x, y, new float[0][]);
      }
      Collections.sort(rows, (a, b) -> {
         int order = Float.compare(b.width, a.width);
         return order != 0 ? order : a.entry.id.compareTo(b.entry.id);
      });
      float scale = Float.isFinite(requestedScale) ? Math.max(0.01F, requestedScale) : 1;
      scale = Math.min(scale, width / (rows.get(0).width + DripLiteStyle.PAD + RIGHT_PAD + MARGIN * 2));
      int count = Math.min(rows.size(), Math.max(0, (int)((height / scale - MARGIN * 2) / DripLiteStyle.ROW)));
      if (count == 0) return new Layout(p, x, y, x, y, x, y, new float[0][]);
      float total = 0;
      for (int i = 0; i < count; i++) total += DripLiteStyle.ROW * rows.get(i).visibility();
      float right = clamp(x / scale, rows.get(0).width + DripLiteStyle.PAD + MARGIN, width / scale - RIGHT_PAD - MARGIN);
      float top = clamp(y / scale - TEXT_TOP, MARGIN, height / scale - total - MARGIN);
      float[][] bounds = new float[count][4];
      float offset = 0;
      float left = right;
      // All geometry uses GUI coordinates, with exactly one HUD scale. No raw-pixel scissors.
      for (int i = 0; i < count; i++) {
         Row row = rows.get(i);
         float visible = row.visibility();
         float slide = 10 * (1 - visible);
         bounds[i] = new float[]{(right - row.width - DripLiteStyle.PAD + slide) * scale,
            (top + offset) * scale, (right + RIGHT_PAD + slide) * scale,
            (top + offset + DripLiteStyle.ROW * visible) * scale};
         left = Math.min(left, bounds[i][0] / scale);
         offset += DripLiteStyle.ROW * visible;
      }
      if (background) {
         // Complete the shadow pass before drawing surfaces so adjacent rows do not darken each other.
         for (int layer = 3; layer >= 0; layer--) {
            for (int i = 0; i < count; i++) {
               Row row = rows.get(i);
               float visible = row.visibility();
               if (visible <= 0.001F) continue;
               float[] b = bounds[i];
               float step = i + 1 < count ? Math.max(0, bounds[i + 1][0] - b[0]) / scale : DripLiteStyle.RADIUS;
               float corner = Math.min(DripLiteStyle.RADIUS, step);
               p.push().scale(scale, scale, 1);
               DripLiteStyle.color(p, layer == 0 ? DripLiteStyle.SURFACE : 0xFF000000,
                  visible * (layer == 0 ? 1 : (4 - layer) * 0.035F));
               p.rectVarying(b[0] / scale - layer, b[1] / scale - layer,
                  (b[2] - b[0]) / scale + layer * 2, (b[3] - b[1]) / scale + layer * 2,
                  i == 0 ? DripLiteStyle.RADIUS + layer : 0, i == 0 ? DripLiteStyle.RADIUS + layer : 0,
                  i == count - 1 ? DripLiteStyle.RADIUS + layer : 0, corner > 0 ? corner + layer : 0);
               // The thin violet right edge belongs to the surface, not a bright outline around every row.
               if (layer == 0) {
                  DripLiteStyle.color(p, DripLiteStyle.ACCENT, visible * 0.65F);
                  p.rect(b[2] / scale - 0.65F, b[1] / scale, 0.65F, (b[3] - b[1]) / scale, 0);
               }
               p.pop();
            }
         }
      }
      for (int i = 0; i < count; i++) {
         Row row = rows.get(i);
         float visible = row.visibility();
         float textAlpha = Math.max(0, (visible - 0.55F) / 0.45F);
         if (textAlpha <= 0.001F) continue;
         textAlpha = textAlpha * textAlpha * (3 - 2 * textAlpha);
         float[] b = bounds[i];
         // Fade the full-size label before its row becomes shorter than the glyphs. This avoids
         // overlapping text during exit without scaling the font or mixing GUI/raw-pixel scissors.
         float textY = b[1] + ((b[3] - b[1]) - DripLiteStyle.ROW * scale) * 0.5F;
         p.push().translate(b[2] - RIGHT_PAD * scale, textY, 0).opacity(textAlpha)
            .append(row.text(scale, i)).pop();
      }
      return new Layout(p, left * scale, top * scale, (right + RIGHT_PAD) * scale,
         (top + total) * scale, right * scale, (top + TEXT_TOP) * scale, bounds);
   }

   private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
   private static String clean(String value) {
      return value == null ? "" : value.replaceAll("§.", "").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
   }
}
