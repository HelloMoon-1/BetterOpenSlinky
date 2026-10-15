package dev.slinky.client.render;

import dev.slinky.setting.ColorSetting;

public final strictfp class RenderMath {
   private RenderMath() {
   }

   private static strictfp float model(float[] m, int i, float x, float y, float z) {
      return m[i + 8] * z + (m[i] * x + m[i + 4] * y) + m[i + 12];
   }

   private static strictfp float row(float[] m, int i, float x, float y, float z, float w) {
      return m[i + 12] * w + (m[i + 8] * z + (m[i] * x + m[i + 4] * y));
   }

   public static strictfp RenderMath.Projection project(double[] p, float[] model, float[] projection, float width, float height) {
      float x = (float)p[0];
      float y = (float)p[1];
      float z = (float)p[2];
      float mx = model(model, 0, x, y, z);
      float my = model(model, 1, x, y, z);
      float mz = model(model, 2, x, y, z);
      float mw = z * model[11] + (x * model[3] + y * model[7]) + model[15];
      float w = row(projection, 3, mx, my, mz, mw);
      if (w == 0.0F) {
         return null;
      } else {
         float one = RenderConstants.f(1375232);
         float half = RenderConstants.f(1375236);
         float reciprocal = one / w * half;
         float depth = row(projection, 2, mx, my, mz, mw) * reciprocal + half;
         if (!(0.0F >= depth) && !(depth >= one)) {
            float py = row(projection, 1, mx, my, mz, mw) * reciprocal + half;
            float px = (mw * projection[12] + (mz * projection[8] + (mx * projection[0] + my * projection[4]))) * reciprocal + half;
            return new RenderMath.Projection(px * width, height - py * height, reciprocal);
         } else {
            return null;
         }
      }
   }

   public static strictfp float lerp(float a, float b, float t) {
      return a + t * (b - a);
   }

   public static strictfp double[] lerp(double[] a, double[] b, double t) {
      return new double[]{a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), a[2] + t * (b[2] - a[2])};
   }

   public static strictfp double[] subtract(double[] a, double[] b) {
      return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
   }

   public static strictfp double[] add(double[] a, double[] b) {
      return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
   }

   public static strictfp double[] scale(double[] a, double n) {
      return new double[]{a[0] * n, a[1] * n, a[2] * n};
   }

   public static strictfp double distanceSquared(double[] a, double[] b) {
      double x = a[0] - b[0];
      double y = a[1] - b[1];
      double z = a[2] - b[2];
      return z * z + (x * x + y * y);
   }

   public static strictfp float min(float a, float b) {
      return a < b ? a : b;
   }

   public static strictfp float max(float a, float b) {
      return a > b ? a : b;
   }

   public static strictfp float clamp(float lo, float hi, float value) {
      return value < lo ? lo : min(hi, value);
   }

   public static strictfp float smooth(float value) {
      float square = value * value;
      return (RenderConstants.f(1304788) - (value + value)) * square;
   }

   public static strictfp int compareDistance(float a, float b) {
      return Float.compare(b, a);
   }

   public static strictfp ColorSetting.Value blend(ColorSetting.Value a, ColorSetting.Value b, float t) {
      return new ColorSetting.Value(lerp(a.red, b.red, t), lerp(a.green, b.green, t), lerp(a.blue, b.blue, t));
   }

   public static strictfp boolean visibleBounds(float minx, float miny, float maxx, float maxy, float width, float height) {
      return !(0.0F > minx) && !(0.0F > miny) && !(minx > width) && !(miny > height) || !(0.0F > maxx) && !(maxx > width) && !(0.0F > maxy) && !(maxy > height);
   }

   public static strictfp boolean catalogCharacter(int character) {
      int b = character & 0xFF;
      return ((b & 223) + 191 & 0xFF) < 26 || b == 32 || (b - 48 & 0xFF) < 10;
   }

   public static final strictfp class Projection {
      public final float x;
      public final float y;
      public final float reciprocal;

      public Projection(float x, float y, float reciprocal) {
         this.x = x;
         this.y = y;
         this.reciprocal = reciprocal;
      }
   }

   public static final strictfp class Rect {
      public final float x;
      public final float y;
      public final float width;
      public final float height;

      public Rect(float x, float y, float w, float h) {
         this.x = x;
         this.y = y;
         this.width = w;
         this.height = h;
      }

      public strictfp boolean contains(float px, float py) {
         return px >= this.x && py >= this.y && this.x + this.width > px && this.y + this.height > py;
      }

      public strictfp RenderMath.Rect inset(float amount) {
         return new RenderMath.Rect(amount + this.x, amount + this.y, this.width - (amount + amount), this.height - (amount + amount));
      }
   }
}
