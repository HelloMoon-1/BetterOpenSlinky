package dev.slinky.client.render;

import dev.slinky.client.numeric.FloatTrigonometry;
import java.util.List;
import java.util.Objects;

public final strictfp class GuiCanvasState {
   private final GuiCanvasState.State[] states = new GuiCanvasState.State[33];
   private int depth;

   public GuiCanvasState(GuiCanvasState.State initial) {
      this.states[0] = Objects.requireNonNull(initial).copy();
      this.depth = 1;
   }

   public strictfp GuiCanvasState.State selected() {
      return this.states[this.depth - 1];
   }

   public strictfp int depth() {
      return this.depth;
   }

   public strictfp void save() {
      if (this.depth <= 31) {
         this.states[this.depth] = this.selected().copy();
         this.depth++;
      }
   }

   public strictfp void restore() {
      if (this.depth >= 2) {
         this.depth--;
      }
   }

   public strictfp void translate(float x, float y) {
      float[] m = this.selected().transform;
      float a = m[0];
      float b = m[1];
      float c = m[2];
      float d = m[3];
      float tx = m[4];
      float ty = m[5];
      m[0] = c * 0.0F + a;
      m[1] = d * 0.0F + b;
      m[2] = 0.0F * a + c;
      m[3] = 0.0F * b + d;
      m[4] = x * a + y * c + tx;
      m[5] = x * b + y * d + ty;
   }

   public strictfp void scale(float x, float y) {
      float[] m = this.selected().transform;
      float a = m[0];
      float b = m[1];
      float c = m[2];
      float d = m[3];
      float tx = m[4];
      float ty = m[5];
      float zc = c * 0.0F;
      float zd = d * 0.0F;
      m[0] = zc + x * a;
      m[1] = zd + x * b;
      m[2] = y * c + 0.0F * a;
      m[3] = y * d + 0.0F * b;
      m[4] = a * 0.0F + zc + tx;
      m[5] = b * 0.0F + zd + ty;
   }

   public strictfp void scissor(float x, float y, float width, float height) {
      GuiCanvasState.State s = this.selected();
      float[] m = s.transform;
      float[] r = s.scissor;
      float w = RenderMath.max(0.0F, width);
      float h = RenderMath.max(0.0F, height);
      float half = RenderConstants.f(2839408);
      float cx = w * half + x;
      float cy = h * half + y;
      r[0] = m[2] * 0.0F + m[0];
      r[1] = m[3] * 0.0F + m[1];
      r[2] = 0.0F * m[0] + m[2];
      r[3] = 0.0F * m[1] + m[3];
      r[4] = cx * m[0] + cy * m[2] + m[4];
      r[5] = cx * m[1] + cy * m[3] + m[5];
      s.scissorExtent[0] = w * RenderConstants.f(2839432);
      s.scissorExtent[1] = h * RenderConstants.f(2839436);
   }

   private static strictfp GuiCanvasState.Paint color(float[] rgba, int identity) {
      GuiCanvasState.Paint p = new GuiCanvasState.Paint();

      for (int i = 0; i < 4; i++) {
         p.transform[i] = RenderConstants.f(identity + i * 4);
      }

      p.feather = 1.0F;
      System.arraycopy(rgba, 0, p.inner, 0, 4);
      System.arraycopy(rgba, 0, p.outer, 0, 4);
      return p;
   }

   public strictfp void fillColor(float[] rgba) {
      float[] copy = (float[])rgba.clone();
      this.selected().fill = color(copy, 2839184);
   }

   public strictfp void strokeColor(float[] rgba) {
      float[] copy = (float[])rgba.clone();
      this.selected().stroke = color(copy, 2839168);
   }

   public strictfp void fillPaint(GuiCanvasState.Paint incoming) {
      GuiCanvasState.Paint p = incoming.copy();
      float[] a = (float[])incoming.transform.clone();
      float[] m = this.selected().transform;
      float tx = a[4] * m[0] + a[5] * m[2] + m[4];
      float ty = a[4] * m[1] + a[5] * m[3] + m[5];
      p.transform[0] = a[0] * m[0] + a[1] * m[2];
      p.transform[1] = a[0] * m[1] + a[1] * m[3];
      p.transform[2] = a[2] * m[0] + a[3] * m[2];
      p.transform[3] = a[2] * m[1] + a[3] * m[3];
      p.transform[4] = tx;
      p.transform[5] = ty;
      this.selected().fill = p;
   }

   public static strictfp GuiCanvasState.Paint imagePaint(float x, float y, float width, float height, float rotation, int id, float alpha) {
      GuiCanvasState.Paint p = new GuiCanvasState.Paint();
      float cosine = FloatTrigonometry.cosine(rotation);
      float sine = FloatTrigonometry.sine(rotation);
      p.transform[0] = cosine;
      p.transform[1] = sine;
      p.transform[2] = Float.intBitsToFloat(Float.floatToRawIntBits(sine) ^ RenderConstants.i(2839376));
      p.transform[3] = cosine;
      p.transform[4] = x;
      p.transform[5] = y;
      p.extent[0] = width;
      p.extent[1] = height;
      p.image = id;
      p.inner[0] = p.outer[0] = RenderConstants.f(2839392);
      p.inner[1] = p.outer[1] = RenderConstants.f(2839396);
      p.inner[2] = p.outer[2] = 1.0F;
      p.inner[3] = p.outer[3] = alpha;
      return p;
   }

   public static strictfp GuiCanvasState.Paint linearGradient(float sx, float sy, float ex, float ey, float[] inner, float[] outer) {
      GuiCanvasState.Paint p = new GuiCanvasState.Paint();
      float x = ex - sx;
      float y = ey - sy;
      float length = (float)StrictMath.sqrt((double)(x * x + y * y));
      boolean regular = length > RenderConstants.f(2839200);
      float extent = RenderConstants.f(2839264) * length;
      float feather = RenderMath.max(RenderConstants.f(2839272), length);
      float nx = x / length;
      float ny = y / length;
      if (!regular) {
         nx = 0.0F;
         ny = 0.0F;
      }

      float ox = regular ? nx : RenderConstants.f(2839216);
      float oy = regular ? ny : RenderConstants.f(2839220);
      p.transform[0] = oy;
      p.transform[1] = Float.intBitsToFloat(Float.floatToRawIntBits(nx) ^ RenderConstants.i(2839232));
      p.transform[2] = ox;
      p.transform[3] = oy;
      p.transform[4] = RenderConstants.f(2839248) * ox + sx;
      p.transform[5] = RenderConstants.f(2839252) * oy + sy;
      p.extent[0] = Float.intBitsToFloat(1203982336);
      p.extent[1] = extent + RenderConstants.f(2839268);
      p.feather = feather;
      System.arraycopy(inner, 0, p.inner, 0, 4);
      System.arraycopy(outer, 0, p.outer, 0, 4);
      return p;
   }

   public strictfp void fill(CanvasPath path, GuiCanvasState.Backend backend, GuiCanvasState.Stats stats, boolean canvasAntialias) {
      GuiCanvasState.State s = this.selected();
      GuiCanvasState.Paint p = s.fill.copy();
      path.flatten();
      path.expandFill(canvasAntialias && s.shapeAntialias != 0 ? path.fringe : 0.0F);
      p.inner[3] = p.inner[3] * s.alpha;
      p.outer[3] = s.alpha * p.outer[3];
      backend.fill(
         p, (int[])s.composite.clone(), (float[])s.scissor.clone(), (float[])s.scissorExtent.clone(), path.fringe, path.paths(), (float[])path.bounds.clone()
      );

      for (CanvasPath.Path item : path.paths()) {
         stats.fillTriangles = stats.fillTriangles + (item.fill.size() + item.stroke.size() - 4);
         stats.drawCalls += 2;
      }
   }

   public strictfp void stroke(CanvasPath path, GuiCanvasState.Backend backend, GuiCanvasState.Stats stats, boolean canvasAntialias) {
      GuiCanvasState.State s = this.selected();
      float[] m = s.transform;
      float x = (float)StrictMath.sqrt((double)(m[0] * m[0] + m[2] * m[2]));
      float y = (float)StrictMath.sqrt((double)(m[1] * m[1] + m[3] * m[3]));
      float width = (y + x) * RenderConstants.f(2840064) * s.lineWidth;
      float clamped = RenderMath.min(RenderConstants.f(2840068), width);
      width = width < 0.0F ? 0.0F : clamped;
      GuiCanvasState.Paint p = s.stroke.copy();
      float drawn = path.fringe;
      if (drawn > width) {
         float ratio = width / drawn;
         float maximum = RenderMath.min(RenderConstants.f(2840072), ratio);
         ratio = ratio < 0.0F ? 0.0F : maximum;
         ratio *= ratio;
         p.inner[3] = p.inner[3] * ratio;
         p.outer[3] = p.outer[3] * ratio;
      } else {
         drawn = width;
      }

      p.inner[3] = p.inner[3] * s.alpha;
      p.outer[3] = p.outer[3] * s.alpha;
      path.flatten();
      float aa = canvasAntialias && s.shapeAntialias != 0 ? path.fringe : 0.0F;
      path.expandStroke(RenderConstants.f(2840064) * drawn, aa, s.cap, s.join, s.miter);
      backend.stroke(p, (int[])s.composite.clone(), (float[])s.scissor.clone(), (float[])s.scissorExtent.clone(), path.fringe, drawn, path.paths());

      for (CanvasPath.Path item : path.paths()) {
         stats.strokeTriangles = stats.strokeTriangles + (item.stroke.size() - 2);
         stats.drawCalls++;
      }
   }

   public interface Backend {
      void fill(GuiCanvasState.Paint var1, int[] var2, float[] var3, float[] var4, float var5, List<CanvasPath.Path> var6, float[] var7);

      void stroke(GuiCanvasState.Paint var1, int[] var2, float[] var3, float[] var4, float var5, float var6, List<CanvasPath.Path> var7);
   }

   public static final class Paint {
      public final float[] transform = new float[6];
      public final float[] extent = new float[2];
      public final float[] inner = new float[4];
      public final float[] outer = new float[4];
      public float radius;
      public float feather;
      public int image;

      public strictfp GuiCanvasState.Paint copy() {
         GuiCanvasState.Paint p = new GuiCanvasState.Paint();
         System.arraycopy(this.transform, 0, p.transform, 0, 6);
         System.arraycopy(this.extent, 0, p.extent, 0, 2);
         System.arraycopy(this.inner, 0, p.inner, 0, 4);
         System.arraycopy(this.outer, 0, p.outer, 0, 4);
         p.radius = this.radius;
         p.feather = this.feather;
         p.image = this.image;
         return p;
      }
   }

   public static final class State {
      public final float[] transform = new float[6];
      public final float[] scissor = new float[6];
      public final float[] scissorExtent = new float[2];
      public GuiCanvasState.Paint fill = new GuiCanvasState.Paint();
      public GuiCanvasState.Paint stroke = new GuiCanvasState.Paint();
      public float alpha;
      public float lineWidth;
      public float miter;
      public float fontSize;
      public float blur;
      public float spacing;
      public float lineHeight;
      public int alignment;
      public int font;
      public int cap;
      public int join;
      public int shapeAntialias;
      public final int[] composite = new int[4];

      public strictfp GuiCanvasState.State copy() {
         GuiCanvasState.State s = new GuiCanvasState.State();
         System.arraycopy(this.transform, 0, s.transform, 0, 6);
         System.arraycopy(this.scissor, 0, s.scissor, 0, 6);
         System.arraycopy(this.scissorExtent, 0, s.scissorExtent, 0, 2);
         System.arraycopy(this.composite, 0, s.composite, 0, 4);
         s.fill = this.fill.copy();
         s.stroke = this.stroke.copy();
         s.alpha = this.alpha;
         s.lineWidth = this.lineWidth;
         s.miter = this.miter;
         s.fontSize = this.fontSize;
         s.blur = this.blur;
         s.spacing = this.spacing;
         s.lineHeight = this.lineHeight;
         s.alignment = this.alignment;
         s.font = this.font;
         s.cap = this.cap;
         s.join = this.join;
         s.shapeAntialias = this.shapeAntialias;
         return s;
      }
   }

   public static final class Stats {
      public int drawCalls;
      public int fillTriangles;
      public int strokeTriangles;
   }
}
