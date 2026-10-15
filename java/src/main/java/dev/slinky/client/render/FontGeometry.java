package dev.slinky.client.render;

import java.util.List;

public final strictfp class FontGeometry {
   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   public static strictfp void quadratic(List<float[]> points, float x0, float y0, float x1, float y1, float x2, float y2, float flatness, int depth) {
      if (depth <= 16) {
         float quarter = c(2841456);
         float half = c(2841472);
         float packed = c(2841488);
         depth++;

         do {
            float mx = (x1 + x1 + x0 + x2) * quarter;
            float my = (y1 + y1 + y0 + y2) * quarter;
            float dx = (x2 + x0) * half - mx;
            float dy = (y2 + y0) * half - my;
            float distance = dy * dy + dx * dx;
            if (!(distance > flatness)) {
               points.add(new float[]{x2, y2});
               return;
            }

            float lx = (x0 + x1) * half;
            float ly = (y1 + y0) * half;
            quadratic(points, x0, y0, lx, ly, mx, my, flatness, depth);
            x1 = (x1 + x2) * packed;
            y1 = (y1 + y2) * packed;
            x0 = mx;
            y0 = my;
         } while (++depth != 18);
      }
   }

   public static strictfp void cubic(
      List<float[]> points, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float flatness, int depth
   ) {
      if (depth <= 16) {
         float half = c(2841504);
         float packed = c(2841520);
         int terminal = depth >= 18 ? depth : 17;
         depth++;

         int old;
         do {
            float dx = x3 - x0;
            float dy = y3 - y0;
            float longLength = (float)StrictMath.sqrt((double)(dx * dx + dy * dy));
            dx = x3 - x2;
            dy = y3 - y2;
            double l23 = StrictMath.sqrt((double)(dx * dx + dy * dy));
            float dx01 = x1 - x0;
            float dy01 = y1 - y0;
            float dx12 = x2 - x1;
            float dy12 = y2 - y1;
            double l01 = StrictMath.sqrt((double)(dx01 * dx01 + dy01 * dy01));
            double l12 = StrictMath.sqrt((double)(dx12 * dx12 + dy12 * dy12));
            float length = (float)(l01 + l12 + l23);
            if (!(length * length - longLength * longLength > flatness)) {
               points.add(new float[]{x3, y3});
               return;
            }

            float x01 = (x0 + x1) * packed;
            float x23 = (x3 + x2) * packed;
            float y01 = (y0 + y1) * packed;
            float y23 = (y3 + y2) * packed;
            float x12 = (x1 + x2) * half;
            float y12 = (y1 + y2) * half;
            float x012 = (x12 + x01) * packed;
            float x123 = (x12 + x23) * packed;
            float y012 = (y12 + y01) * packed;
            float y123 = (y12 + y23) * packed;
            float mx = (x123 + x012) * half;
            float my = (y123 + y012) * half;
            cubic(points, x0, y0, x01, y01, x012, y012, mx, my, flatness, depth);
            x0 = mx;
            y0 = my;
            x1 = x123;
            y1 = y123;
            x2 = x23;
            y2 = y23;
            old = depth++;
         } while (old - terminal + 1 != 1);
      }
   }

   private static strictfp void swap(FontGeometry.Edge[] edges, int a, int b) {
      FontGeometry.Edge p = edges[a];
      edges[a] = edges[b];
      edges[b] = p;
   }

   public static strictfp void quickSort(FontGeometry.Edge[] edges, int offset, int count) {
      while (count > 12) {
         int middle = count >>> 1;
         float a = edges[offset].y0;
         float b = edges[offset + middle].y0;
         float c = edges[offset + count - 1].y0;
         if (!(a < b) == b < c) {
            int pivot = !(a < c) != b < c ? 0 : count - 1;
            swap(edges, offset + pivot, offset + middle);
         }

         swap(edges, offset, offset + middle);
         int i = 1;
         int j = count - 1;

         while (true) {
            float pivot = edges[offset].y0;

            while (pivot > edges[offset + i].y0) {
               i++;
            }

            while (edges[offset + j].y0 > pivot) {
               j--;
            }

            if (i >= j) {
               if (count - i <= j) {
                  quickSort(edges, offset + i, count - i);
                  count = j;
                  break;
               }

               quickSort(edges, offset, j);
               offset += i;
               count -= i;
               break;
            }

            swap(edges, offset + i, offset + j);
            i++;
            j--;
         }
      }
   }

   public static strictfp void sort(FontGeometry.Edge[] edges) {
      quickSort(edges, 0, edges.length);

      for (int i = 1; i < edges.length; i++) {
         FontGeometry.Edge value = edges[i];

         int j;
         for (j = i; j > 0 && edges[j - 1].y0 > value.y0; j--) {
            edges[j] = edges[j - 1];
         }

         if (j != i) {
            edges[j] = value;
         }
      }
   }

   private FontGeometry() {
   }

   public static final strictfp class Edge {
      public float x0;
      public float y0;
      public float x1;
      public float y1;
      public int direction;

      public Edge(float x0, float y0, float x1, float y1, int direction) {
         this.x0 = x0;
         this.y0 = y0;
         this.x1 = x1;
         this.y1 = y1;
         this.direction = direction;
      }
   }
}
