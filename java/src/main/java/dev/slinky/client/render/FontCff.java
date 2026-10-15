package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import java.util.ArrayList;
import java.util.List;

public final strictfp class FontCff {
   public static strictfp FontCff.Buffer element(FontCff.Buffer index, int element) {
      index.seek(0);
      int count = index.bytes(2);
      int width = index.byteValue();
      index.skip(element * width);
      int first = index.bytes(width);
      int end = index.bytes(width);
      int at = 2 + (count + 1) * width + first;
      return index.range(at, end - first);
   }

   public static strictfp int integer(FontCff.Buffer b) {
      int first = b.byteValue();
      if (first >= 32 && first <= 246) {
         return first - 139;
      } else if (first >= 247 && first <= 250) {
         return (first - 247) * 256 + b.byteValue() + 108;
      } else if (first >= 251 && first <= 254) {
         return -(first - 251) * 256 - b.byteValue() - 108;
      } else if (first == 28) {
         return b.bytes(2);
      } else {
         return first == 29 ? b.bytes(4) : 0;
      }
   }

   public static strictfp void dictionary(FontCff.Buffer b, int key, int count, int[] output) {
      b.seek(0);

      while (b.cursor < b.size) {
         int start = b.cursor;

         while (b.cursor < b.size && (b.data[b.offset + b.cursor] & 255) >= 28) {
            if ((b.data[b.offset + b.cursor] & 255) == 30) {
               b.skip(1);

               while (b.cursor < b.size) {
                  int n = b.byteValue();
                  if ((n & 15) == 15 || n >>> 4 == 15) {
                     break;
                  }
               }
            } else {
               integer(b);
            }
         }

         int end = b.cursor;
         int op = b.byteValue();
         if (op == 12) {
            op = b.byteValue() | 256;
         }

         if (op == key) {
            FontCff.Buffer operands = b.range(start, end - start);
            if (operands.cursor < operands.size) {
               int i = 0;

               do {
                  output[i++] = integer(operands);
               } while (Integer.toUnsignedLong(i) < Integer.toUnsignedLong(count) && operands.cursor < operands.size);
            }

            return;
         }
      }
   }

   public static strictfp FontCff.Buffer readIndex(FontCff.Buffer b) {
      int start = b.cursor;
      int count = b.bytes(2);
      if (count != 0) {
         int width = b.byteValue();
         b.skip(width * count);
         b.skip(b.bytes(width) - 1);
      }

      return b.range(start, b.cursor - start);
   }

   public static strictfp FontCff.Buffer privateSubroutines(FontCff.Buffer cff, FontCff.Buffer dict) {
      int[] location = new int[2];
      int[] subrs = new int[1];
      dictionary(dict, 18, 2, location);
      if (location[0] != 0 && location[1] != 0) {
         FontCff.Buffer privateDict = cff.range(location[1], location[0]);
         dictionary(privateDict, 19, 1, subrs);
         if (subrs[0] == 0) {
            return new FontCff.Buffer(new byte[0]);
         } else {
            cff.seek(location[1] + subrs[0]);
            return readIndex(cff);
         }
      } else {
         return new FontCff.Buffer(new byte[0]);
      }
   }

   private static strictfp FontCff.Buffer subroutine(FontCff.Buffer index, int number) {
      index = index.copy();
      index.seek(0);
      int count = index.bytes(2);
      int bias = count >= 33900 ? '耀' : (count >= 1240 ? 1131 : 107);
      int element = number + bias;
      return Integer.compareUnsigned(element, count) >= 0 ? new FontCff.Buffer(new byte[0]) : element(index, element);
   }

   private static strictfp FontCff.Buffer glyphSubroutines(FontCff.Info info, int glyph) {
      FontCff.Buffer selector = info.fdSelect.copy();
      selector.seek(0);
      int format = selector.byteValue();
      int selected = -1;
      if (format == 0) {
         selector.skip(glyph);
         selected = selector.byteValue();
      } else if (format == 3) {
         int count = selector.bytes(2);
         int start = selector.bytes(2);

         for (int i = 0; i < count; i++) {
            int value = selector.byteValue();
            int end = selector.bytes(2);
            if (glyph >= start && glyph < end) {
               selected = value;
               break;
            }

            start = end;
         }
      }

      FontCff.Buffer dictionary = element(info.fdArray.copy(), selected);
      return privateSubroutines(info.cff.copy(), dictionary);
   }

   private static strictfp int trunc(float value) {
      return RecoveredMath.truncateInt(value);
   }

   private static strictfp float neg(float value) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(value) ^ Integer.MIN_VALUE);
   }

   public static strictfp void close(FontCff.Context c) {
      if (c.firstX != c.x || c.firstY != c.y) {
         c.vertex(2, trunc(c.firstX), trunc(c.firstY), 0, 0, 0, 0);
      }
   }

   public static strictfp void move(FontCff.Context c, float dx, float dy) {
      close(c);
      c.firstX = c.x += dx;
      c.firstY = c.y += dy;
      c.vertex(1, trunc(c.x), trunc(c.y), 0, 0, 0, 0);
   }

   public static strictfp void line(FontCff.Context c, float dx, float dy) {
      c.x += dx;
      c.y += dy;
      c.vertex(2, trunc(c.x), trunc(c.y), 0, 0, 0, 0);
   }

   public static strictfp void curve(FontCff.Context c, float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
      float x1 = dx1 + c.x;
      float y1 = dy1 + c.y;
      float x2 = dx2 + x1;
      float y2 = dy2 + y1;
      c.x = dx3 + x2;
      c.y = dy3 + y2;
      c.vertex(4, trunc(c.x), trunc(c.y), trunc(x1), trunc(y1), trunc(x2), trunc(y2));
   }

   public static strictfp boolean run(FontCff.Info font, int glyph, FontCff.Context c) {
      int header = 1;
      int maskBits = 0;
      int height = 0;
      int sp = 0;
      boolean hasSubrs = false;
      float[] s = new float[48];
      FontCff.Buffer[] returns = new FontCff.Buffer[10];
      FontCff.Buffer local = font.localSubrs.copy();
      FontCff.Buffer b = element(font.strings.copy(), glyph);

      while (b.cursor < b.size) {
         int op = b.byteValue();
         int i = 0;
         boolean clear = true;
         switch (op) {
            case 1:
            case 3:
            case 18:
            case 23:
               maskBits += sp / 2;
               break;
            case 2:
            case 9:
            case 13:
            case 15:
            case 16:
            case 17:
            case 28:
            default:
               if (op != 255 && op != 28 && op < 32) {
                  return false;
               }

               float value;
               if (op == 255) {
                  value = (float)b.bytes(4) * 1.5258789E-5F;
               } else {
                  b.skip(-1);
                  value = (float)((short)integer(b));
               }

               if (sp >= 48) {
                  return false;
               }

               s[sp++] = value;
               clear = false;
               break;
            case 4:
               if (sp < 1) {
                  return false;
               }

               move(c, 0.0F, s[sp - 1]);
               header = 0;
               break;
            case 5:
               if (sp < 2) {
                  return false;
               }

               while (i + 1 < sp) {
                  line(c, s[i], s[i + 1]);
                  i += 2;
               }
               break;
            case 6:
            case 7:
               if (sp < 1) {
                  return false;
               }

               for (boolean horizontal = op == 6; i < sp; i++) {
                  line(c, horizontal ? s[i] : 0.0F, horizontal ? 0.0F : s[i]);
                  horizontal = !horizontal;
               }
               break;
            case 8:
               if (sp < 6) {
                  return false;
               }

               while (i + 5 < sp) {
                  curve(c, s[i], s[i + 1], s[i + 2], s[i + 3], s[i + 4], s[i + 5]);
                  i += 6;
               }
               break;
            case 10:
            case 29:
               if (op == 10 && !hasSubrs) {
                  if (font.fdSelect.size != 0) {
                     local = glyphSubroutines(font, glyph);
                  }

                  hasSubrs = true;
               }

               if (sp < 1 || height >= 10) {
                  return false;
               }

               int number = trunc(s[--sp]);
               returns[height] = b;
               b = subroutine(op == 10 ? local : font.globalSubrs, number);
               if (b.size == 0) {
                  return false;
               }

               height++;
               b.cursor = 0;
               clear = false;
               break;
            case 11:
               if (height <= 0) {
                  return false;
               }

               b = returns[--height];
               clear = false;
               break;
            case 12:
               int escape = b.byteValue();
               if (escape == 34) {
                  if (sp < 7) {
                     return false;
                  }

                  curve(c, s[0], 0.0F, s[1], s[2], s[3], 0.0F);
                  curve(c, s[4], 0.0F, s[5], neg(s[2]), s[6], 0.0F);
               } else if (escape == 35) {
                  if (sp < 13) {
                     return false;
                  }

                  curve(c, s[0], s[1], s[2], s[3], s[4], s[5]);
                  curve(c, s[6], s[7], s[8], s[9], s[10], s[11]);
               } else if (escape == 36) {
                  if (sp < 9) {
                     return false;
                  }

                  curve(c, s[0], s[1], s[2], s[3], s[4], 0.0F);
                  curve(c, s[5], 0.0F, s[6], s[7], s[8], neg(s[1] + s[3] + s[7]));
               } else {
                  if (escape != 37) {
                     return false;
                  }

                  if (sp < 11) {
                     return false;
                  }

                  float dx = s[0] + s[2] + s[4] + s[6] + s[8];
                  float dy = s[1] + s[3] + s[5] + s[7] + s[9];
                  float absX = Float.intBitsToFloat(Float.floatToRawIntBits(dx) & 2147483647);
                  float absY = Float.intBitsToFloat(Float.floatToRawIntBits(dy) & 2147483647);
                  float dx6 = absX > absY ? s[10] : neg(dx);
                  float dy6 = absX > absY ? neg(dy) : s[10];
                  curve(c, s[0], s[1], s[2], s[3], s[4], s[5]);
                  curve(c, s[6], s[7], s[8], s[9], dx6, dy6);
               }
               break;
            case 14:
               close(c);
               return true;
            case 19:
            case 20:
               if (header != 0) {
                  maskBits += sp / 2;
               }

               header = 0;
               b.skip((maskBits + 7) / 8);
               break;
            case 21:
               if (sp < 2) {
                  return false;
               }

               move(c, s[sp - 2], s[sp - 1]);
               header = 0;
               break;
            case 22:
               if (sp < 1) {
                  return false;
               }

               move(c, s[sp - 1], 0.0F);
               header = 0;
               break;
            case 24:
               if (sp < 8) {
                  return false;
               }

               while (i + 5 < sp - 2) {
                  curve(c, s[i], s[i + 1], s[i + 2], s[i + 3], s[i + 4], s[i + 5]);
                  i += 6;
               }

               if (i + 1 >= sp) {
                  return false;
               }

               line(c, s[i], s[i + 1]);
               break;
            case 25:
               if (sp < 8) {
                  return false;
               }

               while (i + 1 < sp - 6) {
                  line(c, s[i], s[i + 1]);
                  i += 2;
               }

               if (i + 5 >= sp) {
                  return false;
               }

               curve(c, s[i], s[i + 1], s[i + 2], s[i + 3], s[i + 4], s[i + 5]);
               break;
            case 26:
            case 27:
               if (sp < 4) {
                  return false;
               }

               float first = 0.0F;
               if ((sp & 1) != 0) {
                  first = s[i++];
               }

               while (i + 3 < sp) {
                  if (op == 27) {
                     curve(c, s[i], first, s[i + 1], s[i + 2], s[i + 3], 0.0F);
                  } else {
                     curve(c, first, s[i], s[i + 1], s[i + 2], 0.0F, s[i + 3]);
                  }

                  first = 0.0F;
                  i += 4;
               }
               break;
            case 30:
            case 31:
               if (sp < 4) {
                  return false;
               }

               for (boolean horizontal = op == 31; i + 3 < sp; i += 4) {
                  if (horizontal) {
                     curve(c, s[i], 0.0F, s[i + 1], s[i + 2], sp - i == 5 ? s[i + 4] : 0.0F, s[i + 3]);
                  } else {
                     curve(c, 0.0F, s[i], s[i + 1], s[i + 2], s[i + 3], sp - i == 5 ? s[i + 4] : 0.0F);
                  }

                  horizontal = !horizontal;
               }
         }

         if (clear) {
            sp = 0;
         }
      }

      return false;
   }

   private FontCff() {
   }

   public static final strictfp class Buffer {
      public final byte[] data;
      public final int offset;
      public final int size;
      public int cursor;

      public Buffer(byte[] data) {
         this(data, 0, data.length);
      }

      public Buffer(byte[] data, int offset, int size) {
         this.data = data;
         this.offset = offset;
         this.size = size;
      }

      public strictfp FontCff.Buffer copy() {
         FontCff.Buffer b = new FontCff.Buffer(this.data, this.offset, this.size);
         b.cursor = this.cursor;
         return b;
      }

      public strictfp int byteValue() {
         return this.cursor >= this.size ? 0 : this.data[this.offset + this.cursor++] & 0xFF;
      }

      public strictfp int bytes(int count) {
         int v = 0;

         for (int i = 0; i < count; i++) {
            v = v << 8 | this.byteValue();
         }

         return v;
      }

      public strictfp void seek(int at) {
         this.cursor = at >= 0 && at <= this.size ? at : this.size;
      }

      public strictfp void skip(int count) {
         this.seek(this.cursor + count);
      }

      public strictfp FontCff.Buffer range(int at, int count) {
         return at >= 0 && count >= 0 && at <= this.size && count <= this.size - at
            ? new FontCff.Buffer(this.data, this.offset + at, count)
            : new FontCff.Buffer(new byte[0]);
      }
   }

   public static final strictfp class Context {
      public final boolean bounds;
      public boolean started;
      public float firstX;
      public float firstY;
      public float x;
      public float y;
      public int minX;
      public int maxX;
      public int minY;
      public int maxY;
      public int count;
      public final List<FontRasterization.Vertex> vertices = new ArrayList<>();

      public Context(boolean bounds) {
         this.bounds = bounds;
      }

      private strictfp void track(int x, int y) {
         if (x > this.maxX || !this.started) {
            this.maxX = x;
         }

         if (y > this.maxY || !this.started) {
            this.maxY = y;
         }

         if (x < this.minX || !this.started) {
            this.minX = x;
         }

         if (y < this.minY || !this.started) {
            this.minY = y;
         }

         this.started = true;
      }

      private strictfp void vertex(int type, int x, int y, int cx, int cy, int cx1, int cy1) {
         if (this.bounds) {
            this.track(x, y);
            if (type == 4) {
               this.track(cx, cy);
               this.track(cx1, cy1);
            }
         } else {
            this.vertices.add(new FontRasterization.Vertex(type, x, y, cx, cy, cx1, cy1));
         }

         this.count++;
      }
   }

   public static final strictfp class Info {
      public FontCff.Buffer cff;
      public FontCff.Buffer strings;
      public FontCff.Buffer globalSubrs;
      public FontCff.Buffer localSubrs;
      public FontCff.Buffer fdArray;
      public FontCff.Buffer fdSelect;

      public Info(
         FontCff.Buffer cff, FontCff.Buffer strings, FontCff.Buffer global, FontCff.Buffer local, FontCff.Buffer dictionaries, FontCff.Buffer selectors
      ) {
         this.cff = cff;
         this.strings = strings;
         this.globalSubrs = global;
         this.localSubrs = local;
         this.fdArray = dictionaries;
         this.fdSelect = selectors;
      }
   }
}
