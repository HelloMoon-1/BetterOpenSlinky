package dev.slinky.client.render;

import dev.slinky.setting.ColorSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class DrawPlan {
   private final List<DrawPlan.Op> operations = new ArrayList<>();
   private boolean canvas;
   private float[] canvasTransform = new float[]{1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
   private final Deque<float[]> canvasTransforms = new ArrayDeque<>();

   public DrawPlan canvas() {
      this.canvas = true;
      return this;
   }

   public List<DrawPlan.Op> operations() {
      return Collections.unmodifiableList(this.operations);
   }

   public DrawPlan op(String kind, float... values) {
      this.operations.add(new DrawPlan.Op(kind, null, values));
      return this;
   }

   /** Append a balanced sub-plan without regenerating its cached glyph vertices. */
   public DrawPlan append(DrawPlan plan) {
      if (plan == this) throw new IllegalArgumentException("Cannot append a plan to itself");
      this.operations.addAll(plan.operations);
      return this;
   }

   public DrawPlan push() {
      this.canvasTransforms.push((float[])this.canvasTransform.clone());
      return this.op("push");
   }

   public DrawPlan pop() {
      this.canvasTransform = this.canvasTransforms.pop();
      return this.op("pop");
   }

   public DrawPlan translate(float x, float y, float z) {
      this.canvasTransform[4] = x * this.canvasTransform[0] + y * this.canvasTransform[2] + this.canvasTransform[4];
      this.canvasTransform[5] = x * this.canvasTransform[1] + y * this.canvasTransform[3] + this.canvasTransform[5];
      return this.op("translate", x, y, z);
   }

   public DrawPlan scale(float x, float y, float z) {
      this.canvasTransform[0] = this.canvasTransform[0] * x;
      this.canvasTransform[1] = this.canvasTransform[1] * x;
      this.canvasTransform[2] = this.canvasTransform[2] * y;
      this.canvasTransform[3] = this.canvasTransform[3] * y;
      return this.op("scale", x, y, z);
   }

   public DrawPlan rotate(float angle, float x, float y, float z) {
      return this.op("rotate", angle, x, y, z);
   }

   public DrawPlan color(ColorSetting.Value c, float alpha) {
      return this.op("color", c.red, c.green, c.blue, alpha);
   }

   public DrawPlan color(float r, float g, float b, float a) {
      return this.op("color", r, g, b, a);
   }

   public DrawPlan lineWidth(float width) {
      return this.op("lineWidth", width);
   }

   public DrawPlan indexed(int mode, int dimensions, int vertices, int indices, int count) {
      float[] values = new float[1 + count * dimensions];
      values[0] = (float)mode;

      for (int i = 0; i < count; i++) {
         int v = RenderConstants.u16(indices + i * 2);

         for (int j = 0; j < dimensions; j++) {
            values[1 + i * dimensions + j] = RenderConstants.f(vertices + (v * dimensions + j) * 4);
         }
      }

      this.operations.add(new DrawPlan.Op("vertices" + dimensions, null, values));
      return this;
   }

   public DrawPlan graphics(int address) {
      int mode;
      int count;
      int vertex;
      int index;
      int dim;
      switch (address) {
         case 168960:
            mode = 5;
            count = 14;
            vertex = 1375248;
            index = 1375344;
            dim = 3;
            break;
         case 169008:
            mode = 1;
            count = 24;
            vertex = 1375248;
            index = 1375360;
            dim = 3;
            break;
         case 169120:
            mode = 5;
            count = 4;
            vertex = 1375392;
            index = 1375424;
            dim = 2;
            break;
         case 169168:
            mode = 2;
            count = 4;
            vertex = 1375392;
            index = 1375428;
            dim = 2;
            break;
         case 169280:
            mode = 6;
            count = 4;
            vertex = 1375440;
            index = 1375472;
            dim = 2;
            break;
         case 169328:
            mode = 2;
            count = 4;
            vertex = 1375440;
            index = 1375476;
            dim = 2;
            break;
         default:
            throw new IllegalArgumentException("Unknown recovered draw helper");
      }

      float[] values = new float[1 + count * dim];
      values[0] = (float)mode;

      for (int i = 0; i < count; i++) {
         int v = RenderConstants.i(index + (i & -4)) >>> (i & 3) * 8 & 0xFF;

         for (int j = 0; j < dim; j++) {
            values[1 + i * dim + j] = RenderConstants.f(vertex + (v * dim + j) * 4);
         }
      }

      this.operations.add(new DrawPlan.Op("vertices" + dim, null, values));
      return this;
   }

   public DrawPlan vertices(int mode, int dimensions, float... vertices) {
      float[] v = new float[vertices.length + 1];
      v[0] = (float)mode;
      System.arraycopy(vertices, 0, v, 1, vertices.length);
      this.operations.add(new DrawPlan.Op("vertices" + dimensions, null, v));
      return this;
   }

   public DrawPlan line(float x, float y, float ex, float ey) {
      if (this.canvas) {
         this.operations.add(new DrawPlan.Op("vertices2", Boolean.TRUE, new float[]{1.0F, x, y, ex, ey}));
         return this;
      } else {
         return this.vertices(1, 2, x, y, ex, ey);
      }
   }

   public DrawPlan text(String text, float x, float y, int color, boolean shadow, float size) {
      this.operations.add(new DrawPlan.Op("text", text, new float[]{x, y, Float.intBitsToFloat(color), shadow ? 1.0F : 0.0F, size}));
      return this;
   }

   public float canvasText(String text, float x, float y, int color, float size, int alignment) {
      return this.canvasText(text, x, y, color, size, alignment, 0);
   }

   public float canvasText(String text, float x, float y, int color, float size, int alignment, int style) {
      CanvasFonts.Text rendered = CanvasFonts.text(text, x, y, size, alignment, this.canvasTransform, style);
      this.operations.add(new DrawPlan.Op("text", rendered, new float[]{x, y, Float.intBitsToFloat(color), 0.0F, size}));
      return rendered.end;
   }

   /** Recolors retained glyphs at draw time; color flow must not rebuild the font atlas. */
   public DrawPlan dripText(String text, float x, float y, float size, int row) {
      CanvasFonts.Text rendered = CanvasFonts.text(text, x, y, size, 17, this.canvasTransform, 1);
      this.operations.add(new DrawPlan.Op("dripText", rendered, new float[]{row}));
      return this;
   }

   public DrawPlan alphaSprite(int texture, int atlasWidth, int atlasHeight, byte[] pixels, List<float[]> vertices, int color) {
      CanvasFonts.Batch batch = new CanvasFonts.Batch(texture, atlasWidth, atlasHeight, pixels, vertices);
      CanvasFonts.Text sprite = new CanvasFonts.Text("", Collections.singletonList(batch), 0.0F);
      this.operations.add(new DrawPlan.Op("text", sprite, new float[]{0.0F, 0.0F, Float.intBitsToFloat(color), 0.0F, 0.0F}));
      return this;
   }

   public DrawPlan item(ItemStack stack, float x, float y) {
      this.operations.add(new DrawPlan.Op("item", stack, new float[]{x, y}));
      return this;
   }

   public DrawPlan donorItem(ItemStack stack, float x, float y) {
      this.operations.add(new DrawPlan.Op("donorItem", stack, new float[]{x, y}));
      return this;
   }

   public DrawPlan rect(float x, float y, float width, float height, float radius) {
      return this.op("roundedRect", x, y, width, height, radius);
   }

   public DrawPlan clip(float x, float y, float width, float height) {
      return this.op("screenClip", x, y, width, height);
   }

   public DrawPlan unclip() {
      return this.op("unclip");
   }

   public DrawPlan roundedStroke(float x, float y, float width, float height, float radius) {
      return this.op("roundedStroke", x, y, width, height, radius);
   }

   public DrawPlan opacity(float alpha) {
      return this.op("opacity", alpha);
   }

   public DrawPlan gradient(RenderMath.Rect rect, float[] first, float[] second) {
      return this.op("gradient", rect.x, rect.y, rect.width, rect.height, first[0], first[1], first[2], first[3], second[0], second[1], second[2], second[3]);
   }

   public DrawPlan pillGradient(RenderMath.Rect rect, float radius, float[]... stops) {
      float[] values = new float[5 + stops.length * 4];
      values[0] = rect.x;
      values[1] = rect.y;
      values[2] = rect.width;
      values[3] = rect.height;
      values[4] = radius;

      for (int i = 0; i < stops.length; i++) {
         System.arraycopy(stops[i], 0, values, 5 + i * 4, 4);
      }

      return this.op("pillGradient", values);
   }

   public DrawPlan circleStroke(float x, float y, float radius) {
      return this.op("circleStroke", x, y, radius);
   }

   public DrawPlan rectVarying(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft) {
      return this.op("roundedRectVarying", x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
   }

   public DrawPlan enable(int capability) {
      return this.op("enable", (float)capability);
   }

   public DrawPlan disable(int capability) {
      return this.op("disable", (float)capability);
   }

   public static final class Op {
      public final String kind;
      public final float[] values;
      public final Object value;

      private Op(String kind, Object value, float... values) {
         this.kind = kind;
         this.value = value;
         this.values = (float[])values.clone();
      }
   }
}
