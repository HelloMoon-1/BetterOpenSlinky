package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final strictfp class FontServices {
   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp int byteAt(int address) {
      return RenderConstants.i(address & -4) >>> (address & 3) * 8 & 0xFF;
   }

   public static strictfp FontServices.Quad quad(
      FontServices.Face face,
      FontServices.Glyph glyph,
      int previous,
      float scale,
      float spacing,
      FontServices.Cursor cursor,
      boolean downward,
      float inverseWidth,
      float inverseHeight
   ) {
      if (previous != -1) {
         int kern = face.kern(previous, glyph.index);
         float delta = (float)RecoveredMath.truncateInt((float)kern * scale + spacing + c(2838228));
         cursor.x += delta;
      }

      float xoff = (float)((short)(glyph.xOffset + 1));
      float yoff = (float)((short)(glyph.yOffset + 1));
      float top = (float)(glyph.y0 + 1);
      float bottom = (float)(glyph.y1 - 1);
      float left = (float)(glyph.x0 + 1);
      float right = (float)(glyph.x1 - 1);
      FontServices.Quad q = new FontServices.Quad();
      q.x0 = RecoveredGui.floor(xoff + cursor.x);
      q.y0 = RecoveredGui.floor(downward ? cursor.y + yoff : cursor.y - yoff);
      q.x1 = q.x0 + right - left;
      q.y1 = downward ? q.y0 + bottom - top : q.y0 - bottom + top;
      q.s0 = left * inverseWidth;
      q.t0 = top * inverseHeight;
      q.s1 = right * inverseWidth;
      q.t1 = bottom * inverseHeight;
      float advance = (float)RecoveredMath.truncateInt((float)glyph.advance / c(2838232) + c(2838228));
      cursor.x += advance;
      return q;
   }

   private static strictfp float vertical(
      FontServices.Face face, boolean downward, int alignment, float size, int ten, int half, int negativeTen, int negativeHalf, boolean sizeFirst
   ) {
      boolean negate = false;
      float metric;
      if ((alignment & 8) != 0) {
         metric = face.ascender();
         negate = !downward;
      } else if ((alignment & 16) != 0) {
         metric = (face.ascender() + face.descender()) * c(downward ? half : negativeHalf);
      } else {
         if ((alignment & 96) != 32) {
            return 0.0F;
         }

         metric = face.descender();
         negate = !downward;
      }

      return (sizeFirst ? size * metric : metric * size) / c(negate ? negativeTen : ten);
   }

   public static strictfp float bounds(
      FontServices.Face face,
      float x,
      float y,
      byte[] text,
      int end,
      float fontSize,
      float blur,
      float spacing,
      int alignment,
      boolean downward,
      float inverseWidth,
      float inverseHeight,
      float[] bounds
   ) {
      int sizeBits = RecoveredMath.truncateInt(fontSize * c(2838192));
      float size = (float)((short)sizeBits);
      y += vertical(face, downward, alignment, size, 2838192, 2838196, 2838200, 2838204, false);
      float minX = x;
      float maxX = x;
      float minY = y;
      float maxY = y;
      float scale = size / c(2838192) / (float)face.unitsPerEm();
      FontServices.Cursor cursor = new FontServices.Cursor(x, y);
      FontServices.Utf8 utf8 = new FontServices.Utf8();
      int previous = -1;

      for (int i = 0; i < end; i++) {
         if (utf8.next(text[i])) {
            FontServices.Glyph glyph = face.glyph(utf8.codepoint, sizeBits, (short)RecoveredMath.truncateInt(blur), 1);
            if (glyph == null) {
               previous = -1;
            } else {
               FontServices.Quad q = quad(face, glyph, previous, scale, spacing, cursor, downward, inverseWidth, inverseHeight);
               float low = downward ? q.y0 : q.y1;
               float high = downward ? q.y1 : q.y0;
               minY = RenderMath.min(low, minY);
               maxY = high > maxY ? high : maxY;
               minX = RenderMath.min(q.x0, minX);
               maxX = RenderMath.max(q.x1, maxX);
               previous = glyph.index;
            }
         }
      }

      float advance = cursor.x - x;
      if ((alignment & 1) == 0) {
         if ((alignment & 4) != 0) {
            minX -= advance;
            maxX -= advance;
         } else if ((alignment & 2) != 0) {
            float offset = c(2838204) * advance;
            minX += offset;
            maxX += offset;
         }
      }

      if (bounds != null) {
         bounds[0] = minX;
         bounds[1] = minY;
         bounds[2] = maxX;
         bounds[3] = maxY;
      }

      return advance;
   }

   public static strictfp float[] lineBounds(FontServices.Face face, float y, float fontSize, int alignment, boolean downward) {
      float size = (float)((short)RecoveredMath.truncateInt(fontSize * c(2838324)));
      y += vertical(face, downward, alignment, size, 2838324, 2838328, 2838332, 2838336, false);
      if (downward) {
         y -= face.ascender() * size / c(2838324);
         return new float[]{y, size * face.lineHeight() / c(2838324) + y};
      } else {
         y = face.descender() * size / c(2838324) + y;
         return new float[]{y - size * face.lineHeight() / c(2838324), y};
      }
   }

   public static strictfp int hash(int codepoint) {
      int h = ~(codepoint << 15) + codepoint;
      h ^= h >>> 10;
      h *= 9;
      h ^= h >>> 6;
      h = ~(h << 11) + h;
      h ^= h >>> 16;
      return h & 0xFF;
   }

   private static strictfp int blurStep(byte[] pixels, int at, int accumulator, int coefficient) {
      int difference = ((pixels[at] & 255) << 7) - accumulator;
      accumulator += coefficient * difference >> 16;
      pixels[at] = (byte)(accumulator >>> 7);
      return accumulator;
   }

   public static strictfp void blur(byte[] pixels, int offset, int width, int height, int stride, int coefficient) {
      for (int pass = 0; pass < 2; pass++) {
         for (int x = 0; x < width; x++) {
            int base = offset + x;
            int a = 0;

            for (int y = 1; y < height; y++) {
               a = blurStep(pixels, base + y * stride, a, coefficient);
            }

            pixels[base + (height - 1) * stride] = 0;
            a = 0;

            for (int y = height - 2; y >= 0; y--) {
               a = blurStep(pixels, base + y * stride, a, coefficient);
            }

            pixels[base] = 0;
         }

         for (int y = 0; y < height; y++) {
            int base = offset + y * stride;
            int a = 0;

            for (int x = 1; x < width; x++) {
               a = blurStep(pixels, base + x, a, coefficient);
            }

            pixels[base + width - 1] = 0;
            a = 0;

            for (int x = width - 2; x >= 0; x--) {
               a = blurStep(pixels, base + x, a, coefficient);
            }

            pixels[base] = 0;
         }
      }
   }

   public static strictfp float transformScale(float[] matrix, float bias, float quantum, float max, float deviceRatio) {
      float x = (float)StrictMath.sqrt((double)(matrix[0] * matrix[0] + matrix[2] * matrix[2]));
      float y = (float)StrictMath.sqrt((double)(matrix[1] * matrix[1] + matrix[3] * matrix[3]));
      float average = y + x;
      average = average * bias / quantum + bias;
      float quantized = (float)RecoveredMath.truncateInt(average);
      return RenderMath.min(quantized * quantum, max) * deviceRatio;
   }

   public static strictfp float canvasBounds(
      FontServices.Face face,
      int font,
      float x,
      float y,
      byte[] text,
      int end,
      float fontSize,
      float blur,
      float spacing,
      int alignment,
      boolean downward,
      float inverseWidth,
      float inverseHeight,
      float[] transform,
      float deviceRatio,
      float[] output
   ) {
      if (font == -1) {
         return 0.0F;
      } else {
         float scale = transformScale(transform, c(2840336), c(2840340), c(2840344), deviceRatio);
         float inverse = c(2840348) / scale;
         float scaledY = y * scale;
         float result = bounds(
            face, x * scale, scaledY, text, end, fontSize * scale, blur * scale, spacing * scale, alignment, downward, inverseWidth, inverseHeight, output
         );
         if (output != null) {
            float[] line = lineBounds(face, scaledY, fontSize * scale, alignment, downward);
            output[1] = line[0];
            output[3] = line[1];

            for (int i = 0; i < 4; i++) {
               output[i] = inverse * output[i];
            }
         }

         return inverse * result;
      }
   }

   public static strictfp float[][] textVertices(FontServices.Quad q, float inverse, float[] transform) {
      float y0 = q.y0;
      float y1 = q.y1;
      float t0 = q.t0;
      float t1 = q.t1;
      float determinant = transform[3] * transform[0] - transform[1] * transform[2];
      if (0.0F > determinant) {
         float swap = y0;
         y0 = y1;
         y1 = swap;
         swap = t0;
         t0 = t1;
         t1 = swap;
      }

      float x0 = q.x0 * inverse;
      float x1 = q.x1 * inverse;
      y0 *= inverse;
      y1 *= inverse;
      float[] p0 = transform(transform, x0, y0);
      float[] p1 = transform(transform, x1, y0);
      float[] p2 = transform(transform, x1, y1);
      float[] p3 = transform(transform, x0, y1);
      return new float[][]{
         {p0[0], p0[1], q.s0, t0},
         {p2[0], p2[1], q.s1, t1},
         {p1[0], p1[1], q.s1, t0},
         {p0[0], p0[1], q.s0, t0},
         {p3[0], p3[1], q.s0, t1},
         {p2[0], p2[1], q.s1, t1}
      };
   }

   private static strictfp float[] transform(float[] matrix, float x, float y) {
      return new float[]{x * matrix[0] + y * matrix[2] + matrix[4], x * matrix[1] + y * matrix[3] + matrix[5]};
   }

   public static strictfp float drawText(
      FontServices.Iterator iterator, float originalX, float scale, float[] transform, FontServices.TextBackend backend, FontServices.TextStats stats
   ) {
      int length = iterator.bytes.length;
      int capacity = (length >= 3 ? length : 2) * 6;
      if (!backend.reserve(capacity + 255 & 2147483392)) {
         return originalX;
      } else {
         List<float[]> vertices = new ArrayList<>();
         FontServices.Quad quad = new FontServices.Quad();
         int[] previous = iteratorState(iterator);
         float[] previousPosition = new float[]{iterator.cursor.x, iterator.cursor.y};
         float inverse = c(2840212) / scale;

         while (iterator.next(quad)) {
            if (iterator.previous == -1) {
               if (!vertices.isEmpty()) {
                  backend.draw(vertices);
                  stats.drawCalls++;
                  stats.triangles = stats.triangles + vertices.size() / 3;
               }

               if (!backend.allocateTextAtlas()) {
                  vertices.clear();
                  break;
               }

               restoreIterator(iterator, previous, previousPosition);
               iterator.next(quad);
               vertices.clear();
               if (iterator.previous == -1) {
                  break;
               }
            }

            previous = iteratorState(iterator);
            previousPosition = new float[]{iterator.cursor.x, iterator.cursor.y};
            if (vertices.size() + 6 <= capacity) {
               Collections.addAll(vertices, textVertices(quad, inverse, transform));
            }
         }

         backend.uploadDirtyTexture();
         backend.draw(vertices);
         stats.drawCalls++;
         stats.triangles = stats.triangles + vertices.size() / 3;
         return iterator.cursor.x / scale;
      }
   }

   private static strictfp int[] iteratorState(FontServices.Iterator i) {
      return new int[]{i.next, i.start, i.previous, i.utf8.state, i.utf8.codepoint, Float.floatToRawIntBits(i.x), Float.floatToRawIntBits(i.y)};
   }

   private static strictfp void restoreIterator(FontServices.Iterator i, int[] state, float[] cursor) {
      i.next = state[0];
      i.start = state[1];
      i.previous = state[2];
      i.utf8.state = state[3];
      i.utf8.codepoint = state[4];
      i.x = Float.intBitsToFloat(state[5]);
      i.y = Float.intBitsToFloat(state[6]);
      i.cursor.x = cursor[0];
      i.cursor.y = cursor[1];
   }

   public static final strictfp class Atlas {
      public final int width;
      public final int height;
      public final List<FontServices.Atlas.Node> nodes = new ArrayList<>();

      public Atlas(int width, int height) {
         this.width = width;
         this.height = height;
         this.nodes.add(new FontServices.Atlas.Node(0, 0, width));
      }

      public strictfp int[] add(int w, int h) {
         int count = this.nodes.size();
         int best = -1;
         int bestX = -1;
         int bestY = -1;
         int bestHeight = this.height;
         int bestWidth = this.width;

         for (int index = 0; index < count; index++) {
            FontServices.Atlas.Node first = this.nodes.get(index);
            int x = first.x;
            if (w + x <= this.width) {
               int y = first.y;
               int remaining = w;
               int scan = index;
               boolean fit = true;

               while (remaining > 0) {
                  if (scan >= count) {
                     fit = false;
                     break;
                  }

                  FontServices.Atlas.Node node = this.nodes.get(scan++);
                  y = Math.max(y, node.y);
                  if (y + h > this.height) {
                     fit = false;
                     break;
                  }

                  remaining -= node.width;
               }

               if (fit && y != -1) {
                  int bottom = y + h;
                  if (bottom < bestHeight || bottom == bestHeight && first.width < bestWidth) {
                     best = index;
                     bestX = x;
                     bestY = y;
                     bestHeight = bottom;
                     bestWidth = first.width;
                  }
               }
            }
         }

         if (best == -1) {
            return null;
         } else {
            this.nodes.add(best, new FontServices.Atlas.Node(bestX, bestY + h, w));
            int indexx = best + 1;

            while (indexx < this.nodes.size()) {
               FontServices.Atlas.Node previous = this.nodes.get(indexx - 1);
               FontServices.Atlas.Node current = this.nodes.get(indexx);
               int end = previous.x + previous.width;
               int x = current.x;
               if (end <= x) {
                  break;
               }

               current.x = (short)end;
               current.width = (short)(x - end + current.width);
               if (current.width > 0) {
                  break;
               }

               this.nodes.remove(indexx);
            }

            int i = 0;

            while (i < this.nodes.size() - 1) {
               FontServices.Atlas.Node a = this.nodes.get(i);
               FontServices.Atlas.Node b = this.nodes.get(i + 1);
               if (a.y != b.y) {
                  i++;
               } else {
                  a.width = (short)(a.width + b.width);
                  this.nodes.remove(i + 1);
               }
            }

            return new int[]{bestX, bestY};
         }
      }

      public static final strictfp class Node {
         public short x;
         public short y;
         public short width;

         Node(int x, int y, int width) {
            this.x = (short)x;
            this.y = (short)y;
            this.width = (short)width;
         }
      }
   }

   public static final strictfp class Cursor {
      public float x;
      public float y;

      public Cursor(float x, float y) {
         this.x = x;
         this.y = y;
      }
   }

   public interface Face {
      FontServices.Glyph glyph(int var1, int var2, short var3, int var4);

      int kern(int var1, int var2);

      float ascender();

      float descender();

      float lineHeight();

      int unitsPerEm();
   }

   public static final strictfp class Glyph {
      public int index;
      public short x0;
      public short y0;
      public short x1;
      public short y1;
      public short advance;
      public short xOffset;
      public short yOffset;
   }

   public static final strictfp class Iterator {
      public final FontServices.Face face;
      public final FontServices.Cursor cursor;
      public final byte[] bytes;
      public final FontServices.Utf8 utf8 = new FontServices.Utf8();
      public int next;
      public int start;
      public int previous = -1;
      public short size;
      public short blur;
      public float scale;
      public float spacing;
      public final int bitmapRequired;
      public float x;
      public float y;
      public final boolean downward;
      public float inverseWidth;
      public float inverseHeight;

      public Iterator(
         FontServices.Face face,
         float x,
         float y,
         byte[] text,
         float fontSize,
         float blur,
         float spacing,
         int alignment,
         int bitmapRequired,
         boolean downward,
         float inverseWidth,
         float inverseHeight
      ) {
         this.face = face;
         this.bytes = (byte[])text.clone();
         int raw = RecoveredMath.truncateInt(fontSize * FontServices.c(2838236));
         this.size = (short)raw;
         this.blur = (short)RecoveredMath.truncateInt(blur);
         this.scale = (float)raw / FontServices.c(2838236) / (float)face.unitsPerEm();
         this.spacing = spacing;
         this.bitmapRequired = bitmapRequired;
         this.downward = downward;
         this.inverseWidth = inverseWidth;
         this.inverseHeight = inverseHeight;
         if ((alignment & 1) == 0) {
            if ((alignment & 4) != 0) {
               x -= FontServices.bounds(face, x, y, text, text.length, fontSize, blur, spacing, alignment, downward, inverseWidth, inverseHeight, null);
            } else if ((alignment & 2) != 0) {
               x += FontServices.bounds(face, x, y, text, text.length, fontSize, blur, spacing, alignment, downward, inverseWidth, inverseHeight, null)
                  * FontServices.c(2838240);
            }
         }

         y += FontServices.vertical(face, downward, alignment, (float)this.size, 2838236, 2838244, 2838248, 2838240, true);
         this.cursor = new FontServices.Cursor(x, y);
         this.x = x;
         this.y = y;
      }

      public strictfp boolean next(FontServices.Quad output) {
         this.start = this.next;
         if (this.next == this.bytes.length) {
            return false;
         } else {
            do {
               int index = this.next++;
               if (this.utf8.next(this.bytes[index])) {
                  this.x = this.cursor.x;
                  this.y = this.cursor.y;
                  FontServices.Glyph glyph = this.face.glyph(this.utf8.codepoint, this.size & '\uffff', this.blur, this.bitmapRequired);
                  if (glyph == null) {
                     this.previous = -1;
                  } else {
                     FontServices.Quad q = FontServices.quad(
                        this.face, glyph, this.previous, this.scale, this.spacing, this.cursor, this.downward, this.inverseWidth, this.inverseHeight
                     );
                     output.x0 = q.x0;
                     output.y0 = q.y0;
                     output.x1 = q.x1;
                     output.y1 = q.y1;
                     output.s0 = q.s0;
                     output.t0 = q.t0;
                     output.s1 = q.s1;
                     output.t1 = q.t1;
                     this.previous = glyph.index;
                  }
                  break;
               }
            } while (this.next != this.bytes.length);

            return true;
         }
      }
   }

   public static final strictfp class Quad {
      public float x0;
      public float y0;
      public float x1;
      public float y1;
      public float s0;
      public float t0;
      public float s1;
      public float t1;
   }

   public interface TextBackend {
      boolean reserve(int var1);

      boolean allocateTextAtlas();

      void draw(List<float[]> var1);

      void uploadDirtyTexture();
   }

   public static final strictfp class TextStats {
      public int drawCalls;
      public int triangles;
   }

   public static final strictfp class Utf8 {
      public int state;
      public int codepoint;

      public strictfp boolean next(int b) {
         b &= 255;
         int type = FontServices.byteAt(2842960 + b);
         this.codepoint = this.state != 0 ? this.codepoint << 6 | b & 63 : 255 >>> (type & 31) & b;
         this.state = FontServices.byteAt(2843216 + this.state + type);
         return this.state == 0;
      }
   }
}
