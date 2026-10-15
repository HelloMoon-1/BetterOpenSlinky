package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatTrigonometry;
import dev.slinky.client.numeric.NumericServices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final strictfp class CanvasPath {
   private final List<Float> commands = new ArrayList<>();
   private final List<CanvasPath.Path> paths = new ArrayList<>();
   public final float[] bounds = new float[4];
   public final float tolerance;
   public final float distanceTolerance;
   public final float fringe;

   public CanvasPath(float tolerance, float distanceTolerance, float fringe) {
      this.tolerance = tolerance;
      this.distanceTolerance = distanceTolerance;
      this.fringe = fringe;
   }

   private static strictfp float c(int r) {
      return RenderConstants.f(r);
   }

   private static strictfp float neg(float x) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(x) ^ Integer.MIN_VALUE);
   }

   private static strictfp float abs(float x) {
      float n = neg(x);
      return n <= x ? x : n;
   }

   public strictfp CanvasPath command(float... values) {
      for (float f : values) {
         this.commands.add(f);
      }

      this.paths.clear();
      return this;
   }

   public strictfp CanvasPath transform(float[] matrix) {
      int i = 0;

      while (i < this.commands.size()) {
         int kind = (int)this.commands.get(i).floatValue();
         int pairs = kind != 0 && kind != 1 ? (kind == 2 ? 3 : 0) : 1;

         for (int j = 0; j < pairs; j++) {
            int at = i + 1 + j * 2;
            float x = this.commands.get(at);
            float y = this.commands.get(at + 1);
            this.commands.set(at, x * matrix[0] + y * matrix[2] + matrix[4]);
            this.commands.set(at + 1, x * matrix[1] + y * matrix[3] + matrix[5]);
         }

         i += kind != 0 && kind != 1 ? (kind == 2 ? 7 : (kind == 4 ? 2 : 1)) : 3;
      }

      this.paths.clear();
      return this;
   }

   public strictfp CanvasPath rectangle(float x, float y, float w, float h) {
      return this.command(0.0F, x, y, 1.0F, x, h + y, 1.0F, w + x, h + y, 1.0F, w + x, y, 3.0F);
   }

   public strictfp CanvasPath rounded(float x, float y, float w, float h, float tl, float tr, float br, float bl) {
      float threshold = c(2839776);
      if (threshold > tl && threshold > tr && threshold > br && threshold > bl) {
         return this.rectangle(x, y, w, h);
      } else {
         float nw = neg(w);
         float nh = neg(h);
         float hw = (w >= nw ? w : nw) * c(2839808);
         float hh = (h >= nh ? h : nh) * c(2839808);
         float sx = c(nw <= w ? 2839812 : 2839816);
         float sy = c(nh <= h ? 2839812 : 2839816);
         float xb = RenderMath.min(bl, hw) * sx;
         float yb = RenderMath.min(bl, hh) * sy;
         float xr = RenderMath.min(br, hw) * sx;
         float yr = RenderMath.min(br, hh) * sy;
         float xt = RenderMath.min(tr, hw) * sx;
         float yt = RenderMath.min(tr, hh) * sy;
         float xl = RenderMath.min(tl, hw) * sx;
         float yl = RenderMath.min(tl, hh) * sy;
         float bottom = h + y;
         float right = w + x;
         float k = c(2839840);
         float k0 = c(2839824);
         float k1 = c(2839828);
         return this.command(
            0.0F,
            x,
            y + yl,
            1.0F,
            x,
            bottom - yb,
            2.0F,
            x,
            neg(yb) * k0 + bottom,
            xb * k1 + x,
            bottom,
            xb + x,
            bottom,
            1.0F,
            right - xr,
            bottom,
            2.0F,
            right - xr * k,
            bottom,
            right,
            bottom - yr * k,
            right,
            bottom - yr,
            1.0F,
            right,
            yt + y,
            2.0F,
            right,
            yt * k0 + y,
            neg(xt) * k1 + right,
            y,
            right - xt,
            y,
            1.0F,
            x + xl,
            y,
            2.0F,
            xl * k + x,
            y,
            x,
            yl * k + y,
            x,
            y + yl,
            3.0F
         );
      }
   }

   public strictfp CanvasPath ellipse(float x, float y, float rx, float ry) {
      float a = c(2839872);
      float b = c(2839876);
      float left = x - rx;
      float right = x + rx;
      float bottom = y + ry;
      float top = y - ry;
      float bc = ry * a + y;
      float lc = neg(rx) * b + x;
      float tc = neg(ry) * a + y;
      float rc = rx * b + x;
      return this.command(
         0.0F,
         left,
         y,
         2.0F,
         left,
         bc,
         lc,
         bottom,
         x,
         bottom,
         2.0F,
         rc,
         bottom,
         right,
         bc,
         right,
         y,
         2.0F,
         right,
         tc,
         rc,
         top,
         x,
         top,
         2.0F,
         lc,
         top,
         left,
         tc,
         left,
         y,
         3.0F
      );
   }

   public strictfp List<Float> commands() {
      return Collections.unmodifiableList(this.commands);
   }

   public strictfp List<CanvasPath.Path> paths() {
      this.flatten();
      return Collections.unmodifiableList(this.paths);
   }

   private strictfp void point(CanvasPath.Path path, float x, float y, int flags) {
      if (!path.points.isEmpty()) {
         CanvasPath.Point p = path.points.get(path.points.size() - 1);
         float dx = x - p.x;
         float dy = y - p.y;
         if (this.distanceTolerance * this.distanceTolerance > dx * dx + dy * dy) {
            p.flags |= flags;
            return;
         }
      }

      path.points.add(new CanvasPath.Point(x, y, flags));
   }

   private strictfp void bezier(CanvasPath.Path p, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int level, int flags) {
      if (level <= 10) {
         float dx = x4 - x1;
         float dy = y4 - y1;
         float d2 = abs((x2 - x4) * dy - (y2 - y4) * dx);
         float d3 = abs((x3 - x4) * dy - (y3 - y4) * dx);
         float distance = d3 + d2;
         if ((dx * dx + dy * dy) * this.tolerance > distance * distance) {
            this.point(p, x4, y4, flags);
         } else {
            float half = c(2842720);
            float x12 = (x1 + x2) * half;
            float y12 = (y1 + y2) * half;
            float x23 = (x2 + x3) * half;
            float y23 = (y2 + y3) * half;
            float x34 = (x4 + x3) * half;
            float y34 = (y4 + y3) * half;
            float x123 = (x23 + x12) * half;
            float y123 = (y23 + y12) * half;
            float x234 = (x23 + x34) * half;
            float y234 = (y23 + y34) * half;
            float x = (x234 + x123) * half;
            float y = (y234 + y123) * half;
            this.bezier(p, x1, y1, x12, y12, x123, y123, x, y, level + 1, 0);
            this.bezier(p, x, y, x234, y234, x34, y34, x4, y4, level + 1, flags);
         }
      }
   }

   public strictfp void flatten() {
      if (this.paths.isEmpty()) {
         CanvasPath.Path p = null;
         int i = 0;

         while (i < this.commands.size()) {
            int kind = RecoveredMath.truncateInt(this.commands.get(i));
            if (kind == 0) {
               p = new CanvasPath.Path();
               this.paths.add(p);
            }

            if (kind != 0 && kind != 1) {
               if (kind == 2) {
                  if (p != null && !p.points.isEmpty()) {
                     CanvasPath.Point previous = p.points.get(p.points.size() - 1);
                     this.bezier(
                        p,
                        previous.x,
                        previous.y,
                        this.commands.get(i + 1),
                        this.commands.get(i + 2),
                        this.commands.get(i + 3),
                        this.commands.get(i + 4),
                        this.commands.get(i + 5),
                        this.commands.get(i + 6),
                        0,
                        1
                     );
                  }

                  i += 7;
               } else if (kind == 3) {
                  if (p != null) {
                     p.closed = true;
                  }

                  i++;
               } else if (kind == 4) {
                  if (p != null) {
                     p.winding = RecoveredMath.truncateInt(this.commands.get(i + 1));
                  }

                  i += 2;
               } else {
                  i++;
               }
            } else {
               if (p != null) {
                  this.point(p, this.commands.get(i + 1), this.commands.get(i + 2), 1);
               }

               i += 3;
            }
         }

         for (int j = 0; j < 4; j++) {
            this.bounds[j] = c(2839936 + 4 * j);
         }

         float half = c(2839952);
         float threshold = c(2839956);
         float one = c(2839960);

         for (CanvasPath.Path path : this.paths) {
            List<CanvasPath.Point> ps = path.points;
            if (!ps.isEmpty()) {
               CanvasPath.Point first = ps.get(0);
               CanvasPath.Point last = ps.get(ps.size() - 1);
               float dx = first.x - last.x;
               float dy = first.y - last.y;
               if (this.distanceTolerance * this.distanceTolerance > dx * dx + dy * dy) {
                  ps.remove(ps.size() - 1);
                  path.closed = true;
               }

               if (ps.size() > 2) {
                  float area = 0.0F;

                  for (int ix = 2; ix < ps.size(); ix++) {
                     CanvasPath.Point a = ps.get(ix - 1);
                     CanvasPath.Point b = ps.get(ix);
                     area += (b.x - first.x) * (a.y - first.y) - (b.y - first.y) * (a.x - first.x);
                  }

                  area *= half;
                  if (path.winding == 1 && 0.0F > area || path.winding == 2 && area > 0.0F) {
                     Collections.reverse(ps);
                  }
               }

               if (!ps.isEmpty()) {
                  CanvasPath.Point previous = ps.get(ps.size() - 1);

                  for (CanvasPath.Point next : ps) {
                     dx = next.x - previous.x;
                     dy = next.y - previous.y;
                     float length = (float)StrictMath.sqrt((double)(dx * dx + dy * dy));
                     if (length > threshold) {
                        float inverse = one / length;
                        dx *= inverse;
                        dy *= inverse;
                     }

                     previous.dx = dx;
                     previous.dy = dy;
                     previous.length = length;
                     this.bounds[0] = RenderMath.min(this.bounds[0], previous.x);
                     this.bounds[1] = RenderMath.min(this.bounds[1], previous.y);
                     this.bounds[2] = RenderMath.max(this.bounds[2], previous.x);
                     this.bounds[3] = RenderMath.max(this.bounds[3], previous.y);
                     previous = next;
                  }
               }
            }
         }
      }
   }

   public strictfp void joins(float width, int join, float miter) {
      this.flatten();
      float one = c(2842752);
      float half = c(2842784);
      float threshold = c(2842788);
      float maximum = c(2842792);
      float minimum = c(2842796);
      float inverse = width > 0.0F ? one / width : 0.0F;

      for (CanvasPath.Path path : this.paths) {
         path.bevels = 0;
         int left = 0;
         if (!path.points.isEmpty()) {
            CanvasPath.Point previous = path.points.get(path.points.size() - 1);

            for (CanvasPath.Point p : path.points) {
               p.mx = (previous.dy + p.dy) * half;
               p.my = (neg(p.dx) - previous.dx) * half;
               float square = p.mx * p.mx + p.my * p.my;
               if (square > threshold) {
                  float scale = RenderMath.min(maximum, one / square);
                  p.mx *= scale;
                  p.my *= scale;
               }

               p.flags &= 1;
               float cross = previous.dy * p.dx - previous.dx * p.dy;
               if (cross > 0.0F) {
                  left++;
                  p.flags |= 2;
               }

               float limit = RenderMath.max(minimum, RenderMath.min(previous.length, p.length) * inverse);
               if (one > square * limit * limit) {
                  p.flags |= 8;
               }

               if ((p.flags & 1) != 0 && ((join & -3) == 1 || one > square * miter * miter)) {
                  p.flags |= 4;
               }

               if ((p.flags & 12) != 0) {
                  path.bevels++;
               }

               previous = p;
            }

            path.convex = left == path.points.size();
         }
      }
   }

   private static strictfp void vertex(List<float[]> out, float x, float y, float u) {
      vertex(out, x, y, u, 1.0F);
   }

   private static strictfp void vertex(List<float[]> out, float x, float y, float u, float v) {
      out.add(new float[]{x, y, u, v});
   }

   private static strictfp float[][] bevel(boolean bevel, CanvasPath.Point p0, CanvasPath.Point p, float width) {
      float[] a = bevel ? new float[]{p0.dy * width + p.x, p.y - p0.dx * width} : new float[]{p.mx * width + p.x, p.my * width + p.y};
      float[] b = bevel ? new float[]{p.dy * width + p.x, p.y - p.dx * width} : (float[])a.clone();
      return new float[][]{a, b};
   }

   public static strictfp void bevelJoin(List<float[]> out, CanvasPath.Point previous, CanvasPath.Point p, float lw, float rw, float lu, float ru) {
      boolean left = (p.flags & 2) != 0;
      float[][] ab = bevel((p.flags & 8) != 0, previous, p, left ? lw : neg(rw));
      float sign = left ? neg(rw) : lw;
      float ax = previous.dy * sign + p.x;
      float ay = neg(previous.dx) * sign + p.y;
      float bx = p.dy * sign + p.x;
      float by = neg(p.dx) * sign + p.y;
      float[] a = ab[0];
      float[] b = ab[1];
      if (left) {
         vertex(out, a[0], a[1], lu);
         vertex(out, ax, ay, ru);
      } else {
         vertex(out, ax, ay, lu);
         vertex(out, a[0], a[1], ru);
      }

      if ((p.flags & 4) != 0) {
         if (left) {
            vertex(out, a[0], a[1], lu);
            vertex(out, ax, ay, ru);
            vertex(out, b[0], b[1], lu);
            vertex(out, bx, by, ru);
         } else {
            vertex(out, ax, ay, lu);
            vertex(out, a[0], a[1], ru);
            vertex(out, bx, by, lu);
            vertex(out, b[0], b[1], ru);
         }
      } else {
         float cx = p.mx * sign + p.x;
         float cy = p.my * sign + p.y;
         if (left) {
            vertex(out, p.x, p.y, 0.5F);
            vertex(out, ax, ay, ru);
            vertex(out, cx, cy, ru);
            vertex(out, cx, cy, ru);
            vertex(out, p.x, p.y, 0.5F);
            vertex(out, bx, by, ru);
         } else {
            vertex(out, ax, ay, lu);
            vertex(out, p.x, p.y, 0.5F);
            vertex(out, cx, cy, lu);
            vertex(out, cx, cy, lu);
            vertex(out, bx, by, lu);
            vertex(out, p.x, p.y, 0.5F);
         }
      }

      if (left) {
         vertex(out, b[0], b[1], lu);
         vertex(out, bx, by, ru);
      } else {
         vertex(out, bx, by, lu);
         vertex(out, b[0], b[1], ru);
      }
   }

   public strictfp void expandFill(float width) {
      this.joins(width, 4, c(2839984));
      boolean convex = this.paths.size() == 1 && this.paths.get(0).convex;
      float offset = this.fringe * c(2839988);
      float left = convex ? offset : width + offset;
      float right = width - offset;
      float lu = convex ? c(2839988) : 0.0F;

      for (CanvasPath.Path path : this.paths) {
         path.fill.clear();
         path.stroke.clear();
         if (!path.points.isEmpty()) {
            CanvasPath.Point previous = path.points.get(path.points.size() - 1);

            for (CanvasPath.Point p : path.points) {
               if (width > 0.0F) {
                  if ((p.flags & 4) != 0 && (p.flags & 2) == 0) {
                     vertex(path.fill, previous.dy * offset + p.x, neg(previous.dx) * offset + p.y, 0.5F);
                     vertex(path.fill, p.dy * offset + p.x, p.y - p.dx * offset, 0.5F);
                  } else {
                     vertex(path.fill, p.mx * offset + p.x, p.my * offset + p.y, 0.5F);
                  }
               } else {
                  vertex(path.fill, p.x, p.y, 0.5F);
               }

               previous = p;
            }

            if (width > 0.0F) {
               previous = path.points.get(path.points.size() - 1);

               for (CanvasPath.Point p : path.points) {
                  if ((p.flags & 12) != 0) {
                     bevelJoin(path.stroke, previous, p, left, right, lu, 1.0F);
                  } else {
                     vertex(path.stroke, p.mx * left + p.x, p.my * left + p.y, lu);
                     vertex(path.stroke, p.x - p.mx * right, p.y - p.my * right, 1.0F);
                  }

                  previous = p;
               }

               float[] a = path.stroke.get(0);
               float[] b = path.stroke.get(1);
               vertex(path.stroke, a[0], a[1], lu);
               vertex(path.stroke, b[0], b[1], 1.0F);
            }
         }
      }
   }

   private static strictfp float[] direction(CanvasPath.Point a, CanvasPath.Point b) {
      float x = b.x - a.x;
      float y = b.y - a.y;
      float length = (float)StrictMath.sqrt((double)(x * x + y * y));
      float inverse = c(2840148) / length;
      float dx = inverse * x;
      float dy = inverse * y;
      return length > c(2840144) ? new float[]{dx, dy} : new float[]{x, y};
   }

   private static strictfp void flatCap(
      List<float[]> out, CanvasPath.Point p, float dx, float dy, float width, float distance, float aa, float u0, float u1, boolean end
   ) {
      float px = end ? dx * distance + p.x : p.x - dx * distance;
      float py = end ? dy * distance + p.y : p.y - dy * distance;
      float ax = dy * width + px;
      float ay = neg(dx) * width + py;
      float bx = neg(dy) * width + px;
      float by = dx * width + py;
      float ex = (end ? dx : neg(dx)) * aa;
      float ey = (end ? dy : neg(dy)) * aa;
      if (end) {
         vertex(out, ax, ay, u0);
         vertex(out, bx, by, u1);
      }

      vertex(out, ex + ax, ey + ay, u0, 0.0F);
      vertex(out, ex + bx, ey + by, u1, 0.0F);
      if (!end) {
         vertex(out, ax, ay, u0);
         vertex(out, bx, by, u1);
      }
   }

   private static strictfp void roundCap(List<float[]> out, CanvasPath.Point p, float dx, float dy, float width, int divisions, float u0, float u1, boolean end) {
      if (end) {
         vertex(out, dy * width + p.x, neg(dx) * width + p.y, u0);
         vertex(out, neg(dy) * width + p.x, dx * width + p.y, u1);
      }

      float denominator = (float)(divisions - 1);

      for (int i = 0; i < divisions; i++) {
         float angle = (float)i / denominator * c(2840096);
         float ax = FloatTrigonometry.cosine(angle) * width;
         float ay = FloatTrigonometry.sine(angle) * width;
         float x = (end ? dx : neg(dx)) * ay + (neg(dy) * ax + p.x);
         float y = (end ? dy : neg(dy)) * ay + (dx * ax + p.y);
         if (end) {
            vertex(out, p.x, p.y, 0.5F);
         }

         vertex(out, x, y, u0);
         if (!end) {
            vertex(out, p.x, p.y, 0.5F);
         }
      }

      if (!end) {
         vertex(out, dy * width + p.x, neg(dx) * width + p.y, u0);
         vertex(out, neg(dy) * width + p.x, dx * width + p.y, u1);
      }
   }

   private static strictfp void roundJoin(List<float[]> out, CanvasPath.Point previous, CanvasPath.Point p, float width, float lu, float ru, int divisions) {
      boolean left = (p.flags & 2) != 0;
      float[][] ab = bevel((p.flags & 8) != 0, previous, p, left ? width : neg(width));
      float a0 = NumericServices.atan2f(left ? previous.dx : neg(previous.dx), left ? neg(previous.dy) : previous.dy);
      float a1 = NumericServices.atan2f(left ? p.dx : neg(p.dx), left ? neg(p.dy) : p.dy);
      if (left ? a0 < a1 : a1 < a0) {
         a1 += c(left ? 2840176 : 2840180);
      }

      float signed = left ? neg(width) : width;
      float px = previous.dy * signed + p.x;
      float py = neg(previous.dx) * signed + p.y;
      float qx = p.dy * signed + p.x;
      float qy = neg(p.dx) * signed + p.y;
      float[] a = ab[0];
      float[] b = ab[1];
      if (left) {
         vertex(out, a[0], a[1], lu);
         vertex(out, px, py, ru);
      } else {
         vertex(out, px, py, lu);
         vertex(out, a[0], a[1], ru);
      }

      float arc = left ? a0 - a1 : a1 - a0;
      int count = RecoveredMath.truncateInt(RecoveredMath.ceiling(arc / c(2840096) * (float)divisions));
      int n = count < 2 ? 2 : (count < divisions ? count : divisions);

      for (int i = 0; i < n; i++) {
         float angle = (float)i / (float)(n - 1) * (a1 - a0) + a0;
         float x = FloatTrigonometry.cosine(angle) * width + p.x;
         float y = FloatTrigonometry.sine(angle) * width + p.y;
         if (left) {
            vertex(out, p.x, p.y, 0.5F);
         }

         vertex(out, x, y, left ? ru : lu);
         if (!left) {
            vertex(out, p.x, p.y, 0.5F);
         }
      }

      if (left) {
         vertex(out, b[0], b[1], lu);
         vertex(out, qx, qy, ru);
      } else {
         vertex(out, qx, qy, lu);
         vertex(out, b[0], b[1], ru);
      }
   }

   public strictfp void expandStroke(float width, float aa, int cap, int join, float miter) {
      this.flatten();
      float arc = NumericServices.acosf(width / (this.tolerance + width));
      arc += arc;
      int steps = RecoveredMath.truncateInt(RecoveredMath.ceiling(c(2840096) / arc));
      int divisions = steps >= 3 ? steps : 2;
      width = aa * c(2840100) + width;
      float u0 = aa == 0.0F ? c(2840100) : 0.0F;
      float u1 = c(aa == 0.0F ? 2840108 : 2840104);
      float butt = aa * c(2840112);
      float square = width - aa;
      this.joins(width, join, miter);

      for (CanvasPath.Path path : this.paths) {
         path.fill.clear();
         path.stroke.clear();
         int count = path.points.size();
         if (count >= 2) {
            CanvasPath.Point previous = path.closed ? path.points.get(count - 1) : path.points.get(0);
            CanvasPath.Point p = path.closed ? path.points.get(0) : path.points.get(1);
            if (!path.closed) {
               float[] dir = direction(previous, p);
               if (cap == 0 || cap == 2) {
                  flatCap(path.stroke, previous, dir[0], dir[1], width, cap == 0 ? butt : square, aa, u0, u1, false);
               } else if (cap == 1) {
                  roundCap(path.stroke, previous, dir[0], dir[1], width, divisions, u0, u1, false);
               }
            }

            int start = path.closed ? 0 : 1;
            int end = path.closed ? count : count - 1;

            for (int i = start; i < end; i++) {
               p = path.points.get(i);
               if ((p.flags & 12) != 0) {
                  if (join == 1) {
                     roundJoin(path.stroke, previous, p, width, u0, u1, divisions);
                  } else {
                     bevelJoin(path.stroke, previous, p, width, width, u0, u1);
                  }
               } else {
                  vertex(path.stroke, p.mx * width + p.x, p.my * width + p.y, u0);
                  vertex(path.stroke, p.x - p.mx * width, p.y - p.my * width, u1);
               }

               previous = p;
            }

            if (path.closed) {
               float[] a = path.stroke.get(0);
               float[] b = path.stroke.get(1);
               vertex(path.stroke, a[0], a[1], u0);
               vertex(path.stroke, b[0], b[1], u1);
            } else {
               p = path.points.get(count - 1);
               float[] dir = direction(previous, p);
               if (cap == 0 || cap == 2) {
                  flatCap(path.stroke, p, dir[0], dir[1], width, cap == 0 ? butt : square, aa, u0, u1, true);
               } else if (cap == 1) {
                  roundCap(path.stroke, p, dir[0], dir[1], width, divisions, u0, u1, true);
               }
            }
         }
      }
   }

   public static final class Path {
      public final List<CanvasPath.Point> points = new ArrayList<>();
      public boolean closed;
      public boolean convex;
      public int winding = 1;
      public int bevels;
      public final List<float[]> fill = new ArrayList<>();
      public final List<float[]> stroke = new ArrayList<>();
   }

   public static final strictfp class Point {
      public float x;
      public float y;
      public float dx;
      public float dy;
      public float length;
      public float mx;
      public float my;
      public int flags;

      Point(float x, float y, int f) {
         this.x = x;
         this.y = y;
         this.flags = f;
      }
   }
}
