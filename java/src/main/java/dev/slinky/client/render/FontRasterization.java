package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final strictfp class FontRasterization {
   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp float neg(float value) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(value) ^ RenderConstants.i(2837712));
   }

   private static strictfp List<List<float[]>> contours(List<FontRasterization.Vertex> vertices, float flatness) {
      List<List<float[]>> result = new ArrayList<>();
      List<float[]> current = null;
      float x = 0.0F;
      float y = 0.0F;

      for (FontRasterization.Vertex vertex : vertices) {
         if (vertex.type == 1) {
            current = new ArrayList<>();
            result.add(current);
            x = (float)vertex.x;
            y = (float)vertex.y;
            current.add(new float[]{x, y});
         } else if (vertex.type == 2) {
            x = (float)vertex.x;
            y = (float)vertex.y;
            current.add(new float[]{x, y});
         } else if (vertex.type == 3) {
            FontGeometry.quadratic(current, x, y, (float)vertex.cx, (float)vertex.cy, (float)vertex.x, (float)vertex.y, flatness, 0);
            x = (float)vertex.x;
            y = (float)vertex.y;
         } else if (vertex.type == 4) {
            FontGeometry.cubic(
               current, x, y, (float)vertex.cx, (float)vertex.cy, (float)vertex.cx1, (float)vertex.cy1, (float)vertex.x, (float)vertex.y, flatness, 0
            );
            x = (float)vertex.x;
            y = (float)vertex.y;
         }
      }

      return result;
   }

   private static strictfp void clipped(float[] scan, int x, FontRasterization.Active edge, float x0, float y0, float x1, float y1) {
      if (y0 != y1 && !(y0 > edge.end) && !(y1 < edge.start)) {
         if (y0 < edge.start) {
            x0 += (x1 - x0) * (edge.start - y0) / (y1 - y0);
            y0 = edge.start;
         }

         if (y1 > edge.end) {
            x1 += (x1 - x0) * (edge.end - y1) / (y1 - y0);
            y1 = edge.end;
         }

         float left = (float)x;
         float right = (float)(x + 1);
         float height = (y1 - y0) * edge.direction;
         if (x0 <= left && x1 <= left) {
            scan[x] += height;
         } else if (!(x0 >= right) || !(x1 >= right)) {
            float fraction = (x1 - left + (x0 - left)) * c(2837740) + c(2837728);
            scan[x] += fraction * height;
         }
      }
   }

   private static strictfp void clippedFill(float[] scan, float[] fill, int x, FontRasterization.Active edge, float x0, float y0, float x1, float y1) {
      clipped(fill, x, edge, x0, y0, x1, y1);
   }

   private static strictfp void fill(float[] scan, float[] values, List<FontRasterization.Active> active, float top, float bottom) {
      int width = scan.length;
      float one = c(2837728);
      float negativeHalf = c(2837740);
      float half = c(2837744);

      for (FontRasterization.Active edge : active) {
         float x0 = edge.x;
         float dx = edge.dx;
         if (dx != 0.0F) {
            float unclip = dx + x0;
            float xTop = edge.start > top ? (edge.start - top) * dx + x0 : x0;
            float xBottom = edge.end < bottom ? (edge.end - top) * dx + x0 : unclip;
            if (xTop >= 0.0F && xBottom >= 0.0F && xTop < (float)width && xBottom < (float)width) {
               float sy0 = RenderMath.max(edge.start, top);
               float sy1 = RenderMath.min(edge.end, bottom);
               int first = RecoveredMath.truncateInt(xTop);
               int last = RecoveredMath.truncateInt(xBottom);
               if (first == last) {
                  float fraction = ((xBottom - (float)first + (xTop - (float)first)) * negativeHalf + one) * edge.direction;
                  scan[first] += fraction * (sy1 - sy0);
                  values[first + 1] = (sy1 - sy0) * edge.direction + values[first + 1];
               } else {
                  float left = xTop;
                  float right = xBottom;
                  float dy = edge.dy;
                  float origin = x0;
                  if (xTop > xBottom) {
                     float flipped0 = top - sy0 + bottom;
                     float flipped1 = top - sy1 + bottom;
                     sy1 = flipped0;
                     sy0 = flipped1;
                     dy = neg(dy);
                     left = xBottom;
                     right = xTop;
                     origin = unclip;
                     first = RecoveredMath.truncateInt(xBottom);
                     last = RecoveredMath.truncateInt(xTop);
                  }

                  float cross = ((float)(first + 1) - origin) * dy + top;
                  float area = (cross - sy0) * edge.direction;
                  float fraction = (left - (float)first + one) * negativeHalf + one;
                  scan[first] += fraction * area;
                  int span = last - (first + 1);
                  if (span > 0) {
                     float step = dy * edge.direction;
                     float halfStep = step * half;

                     for (int x = first + 1; x < last; x++) {
                        scan[x] += halfStep + area;
                        area += step;
                     }
                  }

                  float finalY = dy * (float)span + cross;
                  float lastFraction = ((right - (float)last) * negativeHalf + one) * edge.direction;
                  scan[last] += (sy1 - finalY) * lastFraction + area;
                  values[last + 1] = (sy1 - sy0) * edge.direction + values[last + 1];
               }
            } else {
               for (int x = 0; x < width; x++) {
                  float leftx = (float)x;
                  float rightx = (float)(x + 1);
                  float yLeft = (leftx - x0) / dx + top;
                  float yRight = (rightx - x0) / dx + top;
                  if (x0 < leftx && unclip > rightx) {
                     clipped(scan, x, edge, x0, top, leftx, yLeft);
                     clipped(scan, x, edge, leftx, yLeft, rightx, yRight);
                     clipped(scan, x, edge, rightx, yRight, unclip, bottom);
                  } else if (unclip < leftx && x0 > rightx) {
                     clipped(scan, x, edge, x0, top, rightx, yRight);
                     clipped(scan, x, edge, rightx, yRight, leftx, yLeft);
                     clipped(scan, x, edge, leftx, yLeft, unclip, bottom);
                  } else if ((!(x0 < leftx) || !(unclip > leftx)) && (!(unclip < leftx) || !(x0 > leftx))) {
                     if ((!(x0 < rightx) || !(unclip > rightx)) && (!(unclip < rightx) || !(x0 > rightx))) {
                        clipped(scan, x, edge, x0, top, unclip, bottom);
                     } else {
                        clipped(scan, x, edge, x0, top, rightx, yRight);
                        clipped(scan, x, edge, rightx, yRight, unclip, bottom);
                     }
                  } else {
                     clipped(scan, x, edge, x0, top, leftx, yLeft);
                     clipped(scan, x, edge, leftx, yLeft, unclip, bottom);
                  }
               }
            }
         } else if (x0 < (float)width) {
            if (x0 >= 0.0F) {
               int xx = RecoveredMath.truncateInt(x0);
               clipped(scan, xx, edge, x0, top, x0, bottom);
               clippedFill(scan, values, xx + 1, edge, x0, top, x0, bottom);
            } else {
               clippedFill(scan, values, 0, edge, x0, top, x0, bottom);
            }
         }
      }
   }

   public static strictfp void rasterize(
      byte[] output,
      int offset,
      int width,
      int height,
      int stride,
      float flatness,
      List<FontRasterization.Vertex> vertices,
      float scaleX,
      float scaleY,
      float shiftX,
      float shiftY,
      int offsetX,
      int offsetY,
      boolean invert,
      FontRasterization.Scratch scratch
   ) {
      if (!vertices.isEmpty()) {
         int contourCount = 0;

         for (FontRasterization.Vertex v : vertices) {
            if (v.type == 1) {
               contourCount++;
            }
         }

         if (contourCount != 0 && scratch.reserve(contourCount * 4)) {
            float tolerance = flatness / RenderMath.min(scaleY, scaleX);
            flatness = tolerance * tolerance;
            List<List<float[]>> curves = contours(vertices, flatness);
            int total = 0;

            for (List<float[]> curve : curves) {
               total += curve.size();
            }

            if (scratch.reserve(total * 8)) {
               curves = contours(vertices, flatness);
               if (invert) {
                  scaleY = neg(scaleY);
               }

               if (scratch.reserve(total * 20 + 20)) {
                  List<FontGeometry.Edge> edges = new ArrayList<>();

                  for (List<float[]> curve : curves) {
                     float[] previous = curve.get(curve.size() - 1);

                     for (float[] point : curve) {
                        if (previous[1] != point[1]) {
                           boolean forward = invert ? previous[1] > point[1] : point[1] > previous[1];
                           float[] first = forward ? previous : point;
                           float[] last = forward ? point : previous;
                           edges.add(
                              new FontGeometry.Edge(
                                 first[0] * scaleX + shiftX, first[1] * scaleY + shiftY, last[0] * scaleX + shiftX, last[1] * scaleY + shiftY, forward ? 1 : 0
                              )
                           );
                        }

                        previous = point;
                     }
                  }

                  FontGeometry.Edge[] sorted = edges.toArray(new FontGeometry.Edge[0]);
                  FontGeometry.sort(sorted);
                  if (width < 65 || scratch.reserve(width * 8 + 4)) {
                     if (height > 0) {
                        float[] scan = new float[width];
                        float[] fill = new float[width + 1];
                        List<FontRasterization.Active> active = new ArrayList<>();
                        FontRasterization.Pool pool = new FontRasterization.Pool(scratch);
                        int edgeIndex = 0;
                        float one = c(2837728);
                        int row = 0;

                        for (int y = offsetY; row < height; y++) {
                           float top = (float)y;
                           float bottom = top + one;
                           Arrays.fill(scan, 0.0F);
                           Arrays.fill(fill, 0.0F);
                           Iterator<FontRasterization.Active> it = active.iterator();

                           while (it.hasNext()) {
                              FontRasterization.Active a = it.next();
                              if (top >= a.end) {
                                 it.remove();
                                 pool.release();
                              }
                           }

                           while (edgeIndex < sorted.length && bottom >= sorted[edgeIndex].y0) {
                              FontGeometry.Edge e = sorted[edgeIndex++];
                              if (e.y0 != e.y1 && pool.allocate()) {
                                 FontRasterization.Active a = new FontRasterization.Active();
                                 a.dx = (e.x1 - e.x0) / (e.y1 - e.y0);
                                 a.dy = a.dx == 0.0F ? 0.0F : one / a.dx;
                                 a.x = a.dx * (top - e.y0) + e.x0 - (float)offsetX;
                                 a.direction = c(e.direction != 0 ? 2837732 : 2837736);
                                 a.start = e.y0;
                                 a.end = e.y1;
                                 if (row == 0 && offsetY != 0 && top > a.end) {
                                    a.end = top;
                                 }

                                 active.add(0, a);
                              }
                           }

                           fill(scan, fill, active, top, bottom);
                           float sum = 0.0F;

                           for (int x = 0; x < width; x++) {
                              sum += fill[x];
                              float coverage = Float.intBitsToFloat(Float.floatToRawIntBits(scan[x] + sum) & RenderConstants.i(2837760));
                              int value = RecoveredMath.truncateInt(coverage * c(2837776) + c(2837744));
                              if (value >= 255) {
                                 value = 255;
                              }

                              output[offset + stride * row + x] = (byte)value;
                           }

                           for (FontRasterization.Active a : active) {
                              a.x = a.dx + a.x;
                           }

                           row++;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private FontRasterization() {
   }

   private static final strictfp class Active {
      float x;
      float dx;
      float dy;
      float direction;
      float start;
      float end;

      private Active() {
      }
   }

   private static final strictfp class Pool {
      final FontRasterization.Scratch scratch;
      int remaining;
      int free;

      Pool(FontRasterization.Scratch s) {
         this.scratch = s;
      }

      strictfp boolean allocate() {
         if (this.free > 0) {
            this.free--;
            return true;
         } else if (this.remaining > 0) {
            this.remaining--;
            return true;
         } else if (this.scratch.used >= 70385) {
            this.scratch.error.run();
            return false;
         } else {
            this.scratch.used += 25616;
            if (!this.scratch.available) {
               return false;
            } else {
               this.remaining = 799;
               return true;
            }
         }
      }

      strictfp void release() {
         this.free++;
      }
   }

   public static final strictfp class Scratch {
      public int used;
      public boolean available = true;
      public final Runnable error;

      public Scratch(Runnable error) {
         this.error = Objects.requireNonNull(error);
      }

      public strictfp boolean reserve(int bytes) {
         int next = this.used + (bytes + 15 & -16);
         if (next >= 96001) {
            this.error.run();
            return false;
         } else {
            this.used = next;
            return this.available;
         }
      }
   }

   public static final strictfp class Vertex {
      public short x;
      public short y;
      public short cx;
      public short cy;
      public short cx1;
      public short cy1;
      public int type;

      public Vertex(int type, int x, int y, int cx, int cy, int cx1, int cy1) {
         this.type = type;
         this.x = (short)x;
         this.y = (short)y;
         this.cx = (short)cx;
         this.cy = (short)cy;
         this.cx1 = (short)cx1;
         this.cy1 = (short)cy1;
      }
   }
}
