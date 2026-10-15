package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final strictfp class GuiWidgets {
   private final GuiPresentation view;
   private final GuiPresentation.Tooltip tooltip;
   private final GuiWidgets.Formatter formatter;

   public GuiWidgets(GuiPresentation view, GuiPresentation.Tooltip tooltip, GuiWidgets.Formatter formatter) {
      this.view = Objects.requireNonNull(view);
      this.tooltip = Objects.requireNonNull(tooltip);
      this.formatter = Objects.requireNonNull(formatter);
   }

   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp double d(int at) {
      return RenderConstants.d(at);
   }

   private static strictfp int gray(float value) {
      int v = (int)(value * 255.0F);
      return 0xFF000000 | (v & 0xFF) << 16 | (v & 0xFF) << 8 | v & 0xFF;
   }

   private static strictfp int rgba(float[] color) {
      return (int)(color[3] * 255.0F) << 24
         | ((int)(color[0] * 255.0F) & 0xFF) << 16
         | ((int)(color[1] * 255.0F) & 0xFF) << 8
         | (int)(color[2] * 255.0F) & 0xFF;
   }

   private static strictfp RenderMath.Rect height(RenderMath.Rect rect, float h) {
      return new RenderMath.Rect(rect.x, rect.y, rect.width, h);
   }

   private strictfp void label(DrawPlan plan, String text, RenderMath.Rect rect, int shade, int size) {
      this.view.text(plan, text, rect, 0, 0, c(size), gray(c(shade)));
   }

   private static strictfp void solid(DrawPlan plan, float[] color) {
      plan.color(color[0], color[1], color[2], color[3]);
   }

   private static strictfp void stroke(DrawPlan plan, RenderMath.Rect rect, int shade, int width, int radius) {
      float v = c(shade);
      plan.color(v, v, v, 1.0F).lineWidth(c(width)).roundedStroke(rect.x, rect.y, rect.width, rect.height, c(radius));
   }

   public static strictfp float[] blend(float[] a, float[] b, float fraction) {
      float amount = fraction < 0.0F ? 0.0F : RenderMath.min(c(2838932), fraction);
      float complement = c(2838932) - amount;
      float[] out = new float[4];

      for (int i = 0; i < 4; i++) {
         out[i] = complement * a[i] + amount * b[i];
      }

      return out;
   }

   private static strictfp void circle(DrawPlan plan, float x, float y, float radius) {
      plan.op("circle", x, y, radius);
   }

   private static strictfp void close(DrawPlan plan, RenderMath.Rect rect, boolean hover, float normal, int red, int other, int inset) {
      float r = hover ? c(red) : normal;
      float g = hover ? c(other) : normal;
      float amount = (float)((double)rect.height * d(inset));
      float left = rect.x + amount;
      float top = rect.y + amount;
      float right = rect.height + rect.x - amount;
      float bottom = rect.y + rect.height - amount;
      plan.color(r, g, g, 1.0F).line(left, top, right, bottom).line(right, top, left, bottom);
   }

   private strictfp String formatted(String format, int defaultAddress, float scale, float... values) {
      double[] args = new double[values.length];

      for (int i = 0; i < args.length; i++) {
         args[i] = (double)(scale != 0.0F ? scale * values[i] : values[i]);
      }

      return this.formatter.format(format == null ? RenderConstants.text(defaultAddress) : format, args);
   }

   private strictfp void tip(String text, float x, float y) {
      this.tooltip.request(text, RecoveredMath.truncateInt(x), RecoveredMath.truncateInt(y));
   }

   public static strictfp boolean toggle(
      RecoveredGui.Runtime state, DrawPlan plan, RecoveredGui.Toggle animation, RenderMath.Rect rectangle, boolean value, float[] accent, double now
   ) {
      animation.animate(value, now);
      float amount = animation.smooth();
      float shade = c(state.hover(rectangle) ? 1313880 : 1313876);
      float[] color = blend(new float[]{shade, shade, shade, 1.0F}, accent, amount);
      solid(plan, color);
      plan.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, c(1313884));
      if (state.click(rectangle)) {
         value = !value;
      }

      RenderMath.Rect inset = rectangle.inset(c(1313888));
      float white = c(1313892);
      float integerHeight = (float)RecoveredMath.truncateInt(inset.height);
      float half = integerHeight * c(1313896);
      float x = RenderMath.lerp(half + inset.x, inset.x + inset.width - half, amount);
      float y = half + inset.y;
      plan.color(white, white, white, 1.0F);
      circle(plan, x, y, integerHeight);
      return value;
   }

   public strictfp boolean checkbox(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      RecoveredGui.Toggle animation,
      boolean value,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1313900));
      this.label(plan, text, rect, 1313904, 1313908);
      return toggle(state, plan, animation, new RenderMath.Rect(rect.x + rect.width + c(1313912), rect.y, c(1313916), rect.height), value, accent, now);
   }

   public strictfp void section(DrawPlan plan, RecoveredGui.Layout layout, String text) {
      RenderMath.Rect rect = layout.next(c(1313920));
      this.label(plan, text, new RenderMath.Rect(rect.x, rect.y + c(1313924), rect.width, rect.height), 1313928, 1313932);
   }

   public strictfp float number(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      String format,
      Object id,
      RecoveredGui.Slider animation,
      float minimum,
      float maximum,
      float step,
      float displayScale,
      float value,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1313936));
      this.label(plan, text, rect, 1313940, 1313944);
      double half = (double)rect.width * d(1313952);
      RenderMath.Rect track = new RenderMath.Rect((float)((double)rect.x + half), rect.y + c(1313948) + c(1313960), (float)(half + d(1313968)), 4.0F);
      float shade = c(state.hover(rect) ? 1313980 : 1313976);
      float radius = c(1313984);
      plan.color(shade, shade, shade, 1.0F).rect(track.x, track.y, track.width, track.height, radius);
      float fraction = RenderMath.clamp(0.0F, c(1313988), (value - minimum) / (maximum - minimum));
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1313992));
      animation.animation = RecoveredGui.limited(animation.animation, fraction, ratio);
      animation.stamp = now;
      float end = RenderMath.lerp(track.x, track.x + track.width, animation.animation);
      solid(plan, accent);
      plan.rect(track.x, track.y, end - track.x, 4.0F, radius);
      circle(plan, end, radius + track.y, c(1314000));
      value = animation.action(state, id, track, rect, value, minimum, maximum, step, displayScale);
      if (animation.active || state.hover(rect) && !state.down) {
         this.tip(state.textActive(id) ? state.text.toString() : this.formatted(format, 1314387, displayScale, value), end, track.y);
      }

      return value;
   }

   public strictfp float[] range(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      String format,
      String pairFormat,
      Object lowId,
      Object highId,
      GuiWidgets.Range animation,
      float minimum,
      float maximum,
      float step,
      float displayScale,
      float lower,
      float upper,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1314024));
      this.label(plan, text, rect, 1314028, 1314032);
      double half = (double)rect.width * d(1314040);
      RenderMath.Rect track = new RenderMath.Rect((float)((double)rect.x + half), rect.y + c(1314036) + c(1314048), (float)(half + d(1314056)), 4.0F);
      float shade = c(state.hover(rect) ? 1314068 : 1314064);
      float radius = c(1314072);
      plan.color(shade, shade, shade, 1.0F).rect(track.x, track.y, track.width, 4.0F, radius);
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1314080));
      float difference = maximum - minimum;
      animation.lower.animation = RecoveredGui.limited(animation.lower.animation, RenderMath.clamp(0.0F, c(1314076), (lower - minimum) / difference), ratio);
      animation.upper.animation = RecoveredGui.limited(animation.upper.animation, RenderMath.clamp(0.0F, c(1314076), (upper - minimum) / difference), ratio);
      animation.stamp = now;
      float lowX = RenderMath.lerp(track.x, track.x + track.width, animation.lower.animation);
      float highX = RenderMath.lerp(track.x, track.x + track.width, animation.upper.animation);
      solid(plan, accent);
      plan.rect(lowX, track.y, highX - lowX, 4.0F, radius);
      circle(plan, lowX, radius + track.y, c(1314088));
      circle(plan, highX, radius + track.y, c(1314088));
      RenderMath.Rect[] hits = rangeHits(track, rect, lowX, highX);
      lower = animation.lower.action(state, lowId, track, hits[0], lower, minimum, maximum, step, displayScale);
      if (animation.lower.active) {
         if (lower > upper) {
            upper = lower;
         }

         this.tip(state.textActive(lowId) ? state.text.toString() : this.formatted(format, 1314387, displayScale, lower), lowX, track.y);
      } else {
         upper = animation.upper.action(state, highId, track, hits[1], upper, minimum, maximum, step, displayScale);
         if (animation.upper.active) {
            if (lower > upper) {
               lower = upper;
            }

            this.tip(state.textActive(highId) ? state.text.toString() : this.formatted(format, 1314387, displayScale, upper), highX, track.y);
         } else if (state.hover(rect) && (!state.down || animation.lower.active || animation.upper.active)) {
            float sum = hits[0].x + hits[0].width + hits[1].x;
            this.tooltip
               .request(
                  this.formatted(pairFormat, 1314348, displayScale, lower, upper),
                  RecoveredMath.truncateInt((double)sum * d(1314040)),
                  RecoveredMath.truncateInt(track.y)
               );
         }
      }

      return new float[]{lower, upper};
   }

   public static strictfp RenderMath.Rect[] rangeHits(RenderMath.Rect track, RenderMath.Rect row, float lowX, float highX) {
      float center = (float)RecoveredMath.truncateInt((double)(lowX + highX) * d(1314040));
      float distance = center - track.x;
      float extra = c(1314096);
      return new RenderMath.Rect[]{
         new RenderMath.Rect(c(1314092) + track.x, row.y, distance + extra, row.height),
         new RenderMath.Rect(center, row.y, track.width - distance + extra, row.height)
      };
   }

   public strictfp int choice(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      GuiWidgets.Choice animation,
      List<String> options,
      int[] current,
      int flag,
      float[] accent,
      double now
   ) {
      return this.choice(state, plan, layout, text, animation, options, current, flag, accent, now, index -> current[0] = index);
   }

   public strictfp int choice(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      GuiWidgets.Choice animation,
      List<String> options,
      int[] current,
      int flag,
      float[] accent,
      double now,
      IntConsumer accept
   ) {
      int count = options.size();
      float target = animation.open ? (float)Integer.toUnsignedLong(count * 28) : c(1314104);
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1314112));
      animation.height = RecoveredGui.limited(animation.height, target, ratio);
      animation.stamp = now;
      RenderMath.Rect rect = layout.next(animation.height);
      this.label(plan, text, height(rect, 20.0F), 1314120, 1314124);
      RenderMath.Rect selection = new RenderMath.Rect((float)((double)rect.width * d(1314128) + (double)rect.x), rect.y, rect.width * c(1314136), rect.height);
      stroke(plan, selection, 1314140, 1314144, 1314148);
      float shade = c(1314140);
      if (animation.open) {
         if (count == 0) {
            return 0;
         } else {
            float rowHeight = selection.height / (float)Integer.toUnsignedLong(count);
            boolean selected = false;

            for (int index = 0; index < count; index++) {
               RenderMath.Rect row = new RenderMath.Rect(selection.x, (float)index * rowHeight + selection.y, selection.width, rowHeight);
               boolean hover = state.hover(row);
               int inverted = (flag ^ 1) & 0xFF;
               float[] color;
               if (index == current[0]) {
                  color = accent;
               } else {
                  int at = hover ? (((index == 0 ? 1 : 0) | inverted) != 0 ? 1314168 : 1314176) : (((index != 0 ? 1 : 0) | inverted) != 0 ? 1314120 : 1314172);
                  float value = c(at);
                  color = new float[]{value, value, value, 1.0F};
               }

               this.view.text(plan, options.get(index), row, 1, 0, c(1314124), rgba(color));
               if (!selected && state.click(row)) {
                  current[0] = index;
                  animation.open = false;
                  selected = true;
               }

               if (index != 0) {
                  plan.color(shade, shade, shade, c(1314136)).line(selection.x, row.y, selection.x + selection.width, row.y);
               }
            }

            return selected ? 1 : 0;
         }
      } else {
         boolean hoverx = state.hover(selection);
         if (((flag ^ 1 | (count < 6 ? 1 : 0)) & 0xFF) == 0 && current[0] != 0) {
            RenderMath.Rect clear = new RenderMath.Rect(selection.width - selection.height + selection.x, selection.y, selection.height, selection.height);
            boolean clearHover = state.hover(clear);
            close(plan, clear, clearHover, shade, 1314152, 1314156, 1314160);
            if (state.click(clear)) {
               current[0] = 0;
            }

            hoverx = hoverx && !clearHover;
         }

         int at = flag != 0 && current[0] == 0 ? (hoverx ? 1314176 : 1314172) : (hoverx ? 1314168 : 1314120);
         this.view.text(plan, options.get(current[0]), selection, 1, 0, c(1314124), gray(c(at)));
         if (state.click(selection)) {
            if (count < 6) {
               animation.open = true;
            } else {
               state.beginOptions(RenderConstants.text(1314362), options.subList(1, count), index -> {
                  if (index != null) {
                     current[0] = index + 1;
                     accept.accept(current[0]);
                  }
               }, now);
            }
         }

         return 0;
      }
   }

   public strictfp boolean color(
      RecoveredGui.Runtime state, DrawPlan plan, RecoveredGui.Layout layout, String text, float[] current, Consumer<float[]> accept, double now
   ) {
      RenderMath.Rect rect = layout.next(c(1314184));
      this.label(plan, text, height(rect, 20.0F), 1314188, 1314192);
      RenderMath.Rect preview = new RenderMath.Rect(rect.x + rect.width + c(1314196), rect.y, c(1314200), 20.0F);
      float radius = preview.height * 0.5F;
      plan.color(current[0], current[1], current[2], 1.0F).rect(preview.x, preview.y, preview.width, preview.height, radius);
      plan.color(0.33F, 0.33F, 0.33F, 1.0F).lineWidth(1.0F).roundedStroke(preview.x, preview.y, preview.width, preview.height, radius);
      RenderMath.Rect hit = new RenderMath.Rect((float)((double)rect.width * d(1314216) + (double)rect.x), rect.y, rect.width * c(1314224), rect.height);
      boolean clicked = state.click(hit);
      if (clicked) {
         state.beginColor(text, current, accept, now);
      }

      return clicked;
   }

   public strictfp boolean list(
      RecoveredGui.Runtime state,
      DrawPlan plan,
      RecoveredGui.Layout layout,
      String text,
      String title,
      RecoveredGui.Filter filter,
      int kind,
      GuiWidgets.ListData list,
      List<String> suggestions,
      double now
   ) {
      long count = list.size();
      long rows = count + 1L;
      float rowCount = GuiPresentation.unsignedFloat(rows);
      RenderMath.Rect rect = layout.next(GuiPresentation.unsignedFloat(rows * 28L));
      this.label(plan, text, height(rect, 20.0F), 1314232, 1314236);
      RenderMath.Rect selection = new RenderMath.Rect((float)((double)rect.width * d(1314240) + (double)rect.x), rect.y, rect.width * c(1314248), rect.height);
      stroke(plan, selection, 1314252, 1314256, 1314260);
      float rowHeight = selection.height / rowCount;
      float shade = c(1314252);
      long removed = -1L;
      boolean result = false;

      for (long index = 0L; Long.compareUnsigned(index, count) < 0; index++) {
         RenderMath.Rect row = new RenderMath.Rect(selection.x, GuiPresentation.unsignedFloat(index) * rowHeight + selection.y, selection.width, rowHeight);
         String name = list.name(index);
         byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
         String shown = new String(bytes, 0, Math.min(bytes.length, 20), StandardCharsets.UTF_8);
         this.view.text(plan, shown, row, 1, 0, c(1314236), gray(c(1314232)));
         if (kind == 1) {
            RenderMath.Rect preview = new RenderMath.Rect(row.x, row.y, row.height, row.height).inset(c(1314264));
            float[] rgb = list.color(index);
            plan.color(rgb[0], rgb[1], rgb[2], 1.0F).rect(preview.x, preview.y, preview.width, preview.height, c(1314268));
            if (state.click(preview)) {
               long selected = index;
               state.beginColor(name, rgb, value -> list.color(selected, value), now);
               result = true;
            }
         }

         RenderMath.Rect remove = new RenderMath.Rect(row.width - row.height + row.x, row.y, row.height, row.height);
         close(plan, remove, state.hover(remove), shade, 1314272, 1314276, 1314280);
         if (state.click(remove)) {
            removed = index;
         }

         plan.color(shade, shade, shade, c(1314248)).line(row.x, row.y + row.height, row.x + row.width, row.y + row.height);
      }

      if (removed != -1L) {
         list.remove(removed);
         result = true;
      }

      RenderMath.Rect add = new RenderMath.Rect(selection.x, GuiPresentation.unsignedFloat(count) * rowHeight + selection.y, selection.width, rowHeight);
      this.view.text(plan, RenderConstants.text(1314374), add, 1, 0, c(1314236), gray(c(state.hover(add) ? 1314292 : 1314288)));
      if (state.click(add)) {
         if (suggestions != null) {
            state.beginOptions(title, suggestions, index -> {
               if (index != null) {
                  list.add(suggestions.get(index), new float[]{c(1314296), 0.0F, 0.0F});
               }
            }, now);
         } else {
            state.beginText(title, list, filter, value -> {
               if (value != null) {
                  list.add(value, new float[]{c(1314300), 0.0F, 0.0F});
               }
            }, now);
         }

         result = true;
      }

      return result;
   }

   public static final class Choice {
      public boolean open;
      public float height;
      public double stamp;
   }

   public interface Formatter {
      String format(String var1, double... var2);
   }

   public interface ListData {
      long size();

      String name(long var1);

      float[] color(long var1);

      void color(long var1, float[] var3);

      void add(String var1, float[] var2);

      void remove(long var1);
   }

   public static final class Range {
      public final RecoveredGui.Slider lower = new RecoveredGui.Slider();
      public final RecoveredGui.Slider upper = new RecoveredGui.Slider();
      public double stamp;
   }
}
