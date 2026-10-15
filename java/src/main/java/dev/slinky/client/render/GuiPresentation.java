package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatTrigonometry;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

public final strictfp class GuiPresentation {
   private final GuiPresentation.Metrics metrics;
   private final boolean canvas;
   private final Map<Integer, Float> keyWidths = new HashMap<>();

   public GuiPresentation(GuiPresentation.Metrics metrics) {
      this(metrics, false);
   }

   public GuiPresentation(GuiPresentation.Metrics metrics, boolean canvas) {
      this.metrics = Objects.requireNonNull(metrics);
      this.canvas = canvas;
   }

   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp double d(int at) {
      return RenderConstants.d(at);
   }

   private static strictfp int rgba(float r, float g, float b, float a) {
      return (int)(a * 255.0F) << 24 | ((int)(r * 255.0F) & 0xFF) << 16 | ((int)(g * 255.0F) & 0xFF) << 8 | (int)(b * 255.0F) & 0xFF;
   }

   private static strictfp int gray(float value) {
      return rgba(value, value, value, 1.0F);
   }

   public static strictfp float[] alignedAnchor(RenderMath.Rect r, int alignment) {
      float x = r.x;
      if (alignment == 2) {
         x += r.width;
      } else if (alignment != 0) {
         x = (float)((double)r.width * d(1312824) + (double)x);
      }

      float y = r.y + c(1312832);
      y = (float)((double)r.height * d(1312824) + (double)y);
      return new float[]{x, y};
   }

   public strictfp float text(DrawPlan plan, String text, RenderMath.Rect rect, int alignment, int style, float size, int color) {
      float[] anchor = alignedAnchor(rect, alignment);
      float advance = this.metrics.width(text, style, size);
      float x = anchor[0];
      if (alignment == 2) {
         x -= advance;
      } else if (alignment != 0) {
         x -= advance * (float)d(1312824);
      }

      if (this.canvas) {
         return plan.canvasText(text, anchor[0], anchor[1], color, size, (alignment == 0 ? 1 : (alignment == 2 ? 4 : 2)) | 16, style);
      } else {
         plan.text(text, x, anchor[1] - size * 0.5F, color, false, size);
         return x + advance;
      }
   }

   public strictfp float moduleText(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      GuiPresentation.Expansion expansion,
      boolean hasCallback,
      String text,
      RenderMath.Rect rect,
      float font,
      int normal,
      int highlight
   ) {
      if (!hasCallback) {
         return this.text(plan, text, rect, 0, 0, font, normal);
      } else {
         RenderMath.Rect expander = new RenderMath.Rect(rect.x, rect.y, rect.height, rect.height);
         float spacing = c(1313312);
         float textX = expander.x + expander.width + spacing;
         float advance = this.metrics.width(text, 0, font);
         RenderMath.Rect label = new RenderMath.Rect(textX, rect.y, advance, rect.height);
         RenderMath.Rect combined = new RenderMath.Rect(rect.x, rect.y, expander.width + label.width + spacing, rect.height);
         if (state.click(combined)) {
            expansion.open = !expansion.open;
         }

         boolean hovering = state.hover(combined);
         this.text(plan, text, label, 0, 0, font, hovering ? highlight : normal);
         float background = c(hovering ? 1313320 : 1313316);
         plan.color(background, background, background, 1.0F).rect(expander.x, expander.y, expander.width, expander.height, c(1313324));
         RenderMath.Rect indicator = expander.inset(c(1313328));
         float foreground = c(hovering ? 1313336 : 1313332);
         float half = c(1313340);
         plan.color(foreground, foreground, foreground, 1.0F)
            .lineWidth(c(1313344))
            .line(indicator.x, indicator.height * half + indicator.y, indicator.x + indicator.width, indicator.height * half + indicator.y);
         if (!expansion.open) {
            float cx = half * indicator.width + indicator.x;
            plan.line(cx, indicator.y, cx, indicator.y + indicator.height);
         }

         return combined.x + combined.width;
      }
   }

   public strictfp void binding(
      RecoveredGui.Runtime state, DrawPlan plan, GuiPresentation.Binding record, RenderMath.Rect rectangle, float[] accent, boolean modeAllowed
   ) {
      float font = c(1313348);
      boolean active = state.mode == 1 && state.identity == record.identity;
      String label = active ? RenderConstants.text(1313462) : RenderModuleAccess.keyName(record.key);
      float width;
      if (active) {
         width = RecoveredMath.ceiling(this.metrics.width(label, 1, font));
      } else {
         Float cached = this.keyWidths.get(record.key);
         width = cached == null ? 0.0F : cached;
         if (width == 0.0F) {
            width = RecoveredMath.ceiling(this.metrics.width(label, 1, font));
            this.keyWidths.put(record.key, width);
         }
      }

      RenderMath.Rect button = new RenderMath.Rect(rectangle.x, rectangle.y, width + font, rectangle.height);
      float idle = c(1313356);
      float[] base = record.key != 0 ? accent : new float[]{idle, idle, idle, 1.0F};
      plan.color(base[0], base[1], base[2], base[3] * c(1313352)).rect(button.x, button.y, button.width, button.height, c(1313360));
      int color = state.hover(button) ? gray(c(1313364)) : rgba(base[0], base[1], base[2], base[3]);
      this.text(plan, label, button, 1, 1, font, color);
      if (state.click(button)) {
         state.activateBinding(record.identity, key -> {
            record.key = key;
            record.changed.accept(key);
         });
      }

      if (record.key == 0) {
         if (modeAllowed) {
            record.hold = false;
         }
      } else if (modeAllowed) {
         String toggleLabel = RenderConstants.text(1313477);
         float toggleWidth = RecoveredMath.ceiling(this.metrics.width(toggleLabel, 1, font));
         RenderMath.Rect toggle = new RenderMath.Rect(button.x + button.width + c(1313360), button.y, toggleWidth + font, button.height);
         if (record.hold || state.hover(rectangle)) {
            float value = c(record.hold ? 1313368 : 1313356);
            plan.color(value, value, value, c(1313352)).rect(toggle.x, toggle.y, toggle.width, toggle.height, c(1313360));
            this.text(plan, toggleLabel, toggle, 1, 1, font, gray(c(state.hover(toggle) ? 1313368 : 1313356)));
            if (state.click(toggle)) {
               record.hold = !record.hold;
            }
         }
      }
   }

   public static strictfp GuiPresentation.Icon icon(RenderMath.Rect rect, float[] atlasOrigins, int id, float textureWidth, float textureHeight) {
      float sx = rect.width / c(1312816);
      float sy = rect.height / c(1312816);
      return new GuiPresentation.Icon(rect.x - atlasOrigins[id * 2] * sx, rect.y - atlasOrigins[id * 2 + 1] * sy, sx * textureWidth, sy * textureHeight);
   }

   public static strictfp float caretAlpha(float frequency, double now) {
      float phase = (float)((double)frequency * now);
      float half = c(1312856);
      return half + FloatTrigonometry.sine(phase) * half;
   }

   public strictfp void caret(DrawPlan plan, RenderMath.Rect r, float[] color, float frequency, float width, double now) {
      plan.color(color[0], color[1], color[2], color[3] * caretAlpha(frequency, now)).lineWidth(width).line(r.x, r.y, r.x, r.y + r.height);
   }

   public strictfp void textEntry(RecoveredGui.Runtime state, DrawPlan plan, RenderMath.Rect r, int alignment, float font, double now) {
      float advance = this.text(plan, state.text.toString(), r, alignment, 0, font, -1) + c(alignment == 1 ? 1314324 : 1314336);
      float gray = c(alignment == 1 ? 1314328 : 1314340);
      this.caret(
         plan, new RenderMath.Rect(advance, r.y, r.width, r.height), new float[]{gray, gray, gray, 1.0F}, c(alignment == 1 ? 1314332 : 1314344), 1.0F, now
      );
   }

   public strictfp void tooltip(DrawPlan plan, GuiPresentation.Tooltip state, float elapsed) {
      if (!state.requested) {
         state.progress = 0.0F;
      } else {
         state.progress = (float)((double)state.progress + (double)elapsed / d(1313136));
         plan.push().translate(state.x, state.y, 0.0F);
         if (state.progress > c(1312976)) {
            state.progress = 1.0F;
         } else {
            float smooth = RenderMath.smooth(state.progress);
            plan.opacity(smooth).scale(smooth, smooth, 1.0F);
         }

         float font = c(1313088);
         float width = this.metrics.width(state.text, 0, font);
         RenderMath.Rect toast = new RenderMath.Rect((float)((double)width * d(1313144) + d(1313152)), c(1313164), width + c(1313160), 20.0F);
         float bg = c(1313168);
         plan.color(bg, bg, bg, 1.0F).rect(toast.x, toast.y, toast.width, toast.height, c(1313076));
         this.text(plan, state.text, toast, 1, 0, font, gray(c(1313072)));
         plan.pop();
         state.requested = false;
      }
   }

   public static strictfp float unsignedFloat(long value) {
      if (value >= 0L) {
         return (float)value;
      } else {
         long half = value >>> 1 | value & 1L;
         float converted = (float)half;
         return converted + converted;
      }
   }

   public static strictfp float categoryHeight(float height) {
      long signed = RecoveredMath.truncateLong(height);
      long adjusted = RecoveredMath.truncateLong(height - c(1313308));
      long sign = signed >> 63;
      return unsignedFloat(signed | adjusted & sign);
   }

   public static final strictfp class Binding {
      public int key;
      public boolean hold;
      public final Object identity;
      public final IntConsumer changed;

      public Binding(int key, boolean hold, Object identity, IntConsumer changed) {
         this.key = key;
         this.hold = hold;
         this.identity = identity;
         this.changed = changed;
      }
   }

   public static final class Expansion {
      public boolean open;
   }

   public static final strictfp class Icon {
      public final float x;
      public final float y;
      public final float width;
      public final float height;

      public Icon(float x, float y, float width, float height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }
   }

   public interface Metrics {
      float width(String var1, int var2, float var3);
   }

   public static final class Tooltip {
      public String text;
      public float x;
      public float y;
      public float progress;
      public boolean requested;

      public strictfp void request(String text, int x, int y) {
         byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
         this.text = new String(bytes, 0, Math.min(bytes.length, 100), StandardCharsets.UTF_8);
         this.x = (float)x;
         this.y = (float)y;
         this.requested = true;
      }
   }
}
