package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final strictfp class FontTables {
   public static strictfp int u16(byte[] b, int p) {
      return (b[p] & 0xFF) << 8 | b[p + 1] & 0xFF;
   }

   public static strictfp int s16(byte[] b, int p) {
      return (short)u16(b, p);
   }

   public static strictfp int u32(byte[] b, int p) {
      return (b[p] & 0xFF) << 24 | (b[p + 1] & 0xFF) << 16 | (b[p + 2] & 0xFF) << 8 | b[p + 3] & 0xFF;
   }

   public static strictfp int offset(FontTables.Font f, int glyph) {
      if (glyph < f.glyphCount && f.locaFormat <= 1) {
         int first = f.locaFormat == 0 ? u16(f.data, f.loca + glyph * 2) * 2 : u32(f.data, f.loca + glyph * 4);
         int second = f.locaFormat == 0 ? u16(f.data, f.loca + glyph * 2 + 2) * 2 : u32(f.data, f.loca + glyph * 4 + 4);
         return first == second ? -1 : f.glyf + first;
      } else {
         return -1;
      }
   }

   public static strictfp int map(FontTables.Font f, int cp) {
      byte[] b = f.data;
      int at = f.cmap;
      int format = u16(b, at);
      if (format == 0) {
         return cp >= u16(b, at + 2) - 6 ? 0 : b[at + 6 + cp] & 0xFF;
      } else if (format == 6) {
         int first = u16(b, at + 6);
         int count = u16(b, at + 8);
         return Integer.compareUnsigned(cp, first) >= 0 && Integer.compareUnsigned(cp, first + count) < 0 ? u16(b, at + 10 + (cp - first) * 2) : 0;
      } else if (format == 4) {
         if (cp > 65535) {
            return 0;
         } else {
            int segments = u16(b, at + 6) >>> 1;
            int range = u16(b, at + 8) >>> 1;
            int selector = u16(b, at + 10);
            int shift = u16(b, at + 12) >>> 1;
            int endCounts = at + 14;
            int search = endCounts;
            if (cp >= u16(b, endCounts + shift * 2)) {
               search = endCounts + shift * 2;
            }

            for (search -= 2; selector != 0; selector--) {
               range >>>= 1;
               if (cp > u16(b, search + range * 2)) {
                  search += range * 2;
               }
            }

            search += 2;
            int item = search - endCounts >>> 1 & 65535;
            int first = u16(b, at + 16 + segments * 2 + item * 2);
            if (cp < first) {
               return 0;
            } else {
               int word = at + 16 + segments * 6 + item * 2;
               int relative = u16(b, word);
               return relative == 0 ? cp + u16(b, at + 16 + segments * 4 + item * 2) & 65535 : u16(b, word + relative + (cp - first) * 2);
            }
         }
      } else {
         if (format == 12 || format == 13) {
            int low = 0;
            int high = u32(b, at + 12);

            while (low < high) {
               int mid = low + (high - low >>> 1);
               int group = at + 16 + mid * 12;
               int first = u32(b, group);
               int last = u32(b, group + 4);
               if (Integer.compareUnsigned(cp, first) < 0) {
                  high = mid;
               } else {
                  if (Integer.compareUnsigned(cp, last) <= 0) {
                     return u32(b, group + 8) + (format == 12 ? cp - first : 0);
                  }

                  low = mid + 1;
               }
            }
         }

         return 0;
      }
   }

   public static strictfp int coverage(byte[] b, int at, int glyph) {
      int format = u16(b, at);
      int low = 0;
      int high = u16(b, at + 2) - 1;

      while (low <= high) {
         int mid = low + high >> 1;
         if (format == 1) {
            int value = u16(b, at + 4 + mid * 2);
            if (glyph < value) {
               high = mid - 1;
            } else {
               if (Integer.compareUnsigned(glyph, value) <= 0) {
                  return mid;
               }

               low = mid + 1;
            }
         } else {
            if (format != 2) {
               break;
            }

            int row = at + 4 + mid * 6;
            int first = u16(b, row);
            int last = u16(b, row + 2);
            if (glyph < first) {
               high = mid - 1;
            } else {
               if (Integer.compareUnsigned(glyph, last) <= 0) {
                  return glyph - first + u16(b, row + 4);
               }

               low = mid + 1;
            }
         }
      }

      return -1;
   }

   public static strictfp int glyphClass(byte[] b, int at, int glyph) {
      int format = u16(b, at);
      if (format == 1) {
         int first = u16(b, at + 2);
         int count = u16(b, at + 4);
         return glyph >= first && Integer.compareUnsigned(glyph, first + count) < 0 ? u16(b, at + 6 + (glyph - first) * 2) : -1;
      } else {
         if (format == 2) {
            int low = 0;
            int high = u16(b, at + 2) - 1;

            while (low <= high) {
               int mid = low + high >> 1;
               int row = at + 4 + mid * 6;
               if (glyph < u16(b, row)) {
                  high = mid - 1;
               } else {
                  if (Integer.compareUnsigned(glyph, u16(b, row + 2)) <= 0) {
                     return u16(b, row + 4);
                  }

                  low = mid + 1;
               }
            }
         }

         return -1;
      }
   }

   public static strictfp int kerning(FontTables.Font f, int previous, int current) {
      byte[] b = f.data;
      if (f.gpos == 0) {
         if (f.kern == 0) {
            return 0;
         } else {
            int at = f.kern;
            if ((b[at + 2] | b[at + 3]) != 0 && u16(b, at + 8) == 1) {
               int key = previous << 16 | current;
               int low = 0;
               int high = u16(b, at + 10) - 1;

               while (low <= high) {
                  int mid = low + high >> 1;
                  int row = at + 18 + mid * 6;
                  int value = u32(b, row);
                  if (Integer.compareUnsigned(key, value) < 0) {
                     high = mid - 1;
                  } else {
                     if (Integer.compareUnsigned(key, value) <= 0) {
                        return s16(b, row + 4);
                     }

                     low = mid + 1;
                  }
               }

               return 0;
            } else {
               return 0;
            }
         }
      } else {
         int table = f.gpos;
         if (u16(b, table) == 1 && (b[table + 2] | b[table + 3]) == 0) {
            int lookups = table + u16(b, table + 8);
            int count = u16(b, lookups);

            for (int i = 0; i < count; i++) {
               int lookup = lookups + u16(b, lookups + 2 + i * 2);
               if (u16(b, lookup) == 2) {
                  int subtables = u16(b, lookup + 4);

                  for (int j = 0; j < subtables; j++) {
                     int pair = lookup + u16(b, lookup + 6 + j * 2);
                     int format = u16(b, pair);
                     int covered = coverage(b, pair + u16(b, pair + 2), previous);
                     if (covered != -1) {
                        if (format == 1) {
                           if (u16(b, pair + 4) != 4 || (b[pair + 6] | b[pair + 7]) != 0) {
                              return 0;
                           }

                           int values = pair + u16(b, pair + 10 + covered * 2);
                           int low = 0;
                           int high = u16(b, values) - 1;

                           while (low <= high) {
                              int mid = low + high >> 1;
                              int record = values + 2 + mid * 4;
                              if (current < u16(b, record)) {
                                 high = mid - 1;
                              } else {
                                 if (Integer.compareUnsigned(current, u16(b, record)) <= 0) {
                                    return s16(b, record + 2);
                                 }

                                 low = mid + 1;
                              }
                           }
                        } else if (format == 2) {
                           int a = glyphClass(b, pair + u16(b, pair + 8), previous);
                           int c = glyphClass(b, pair + u16(b, pair + 10), current);
                           if (u16(b, pair + 4) != 4 || (b[pair + 6] | b[pair + 7]) != 0) {
                              return 0;
                           }

                           int ac = u16(b, pair + 12);
                           int cc = u16(b, pair + 14);
                           if (a >= 0 && Integer.compareUnsigned(a, ac) < 0 && c >= 0 && Integer.compareUnsigned(c, cc) < 0) {
                              return s16(b, pair + 16 + a * cc * 2 + c * 2);
                           }
                        }
                     }
                  }
               }
            }

            return 0;
         } else {
            return 0;
         }
      }
   }

   public static strictfp int[] box(FontTables.Font f, int glyph) {
      if (f.cff != null) {
         FontCff.Context context = new FontCff.Context(true);
         boolean success = FontCff.run(f.cff, glyph, context);
         return success ? new int[]{context.minX, context.minY, context.maxX, context.maxY} : new int[4];
      } else {
         int p = offset(f, glyph);
         return p < 0 ? null : new int[]{s16(f.data, p + 2), s16(f.data, p + 4), s16(f.data, p + 6), s16(f.data, p + 8)};
      }
   }

   private static strictfp void vertex(List<FontRasterization.Vertex> out, int type, int x, int y, int cx, int cy) {
      out.add(new FontRasterization.Vertex(type, x, y, cx, cy, 0, 0));
   }

   private static strictfp void close(List<FontRasterization.Vertex> out, boolean off, boolean firstOff, int x, int y, int firstCX, int firstCY, int cx, int cy) {
      if (firstOff) {
         if (off) {
            vertex(out, 3, cx + firstCX >> 1, cy + firstCY >> 1, cx, cy);
         }

         vertex(out, 3, x, y, firstCX, firstCY);
      } else if (off) {
         vertex(out, 3, x, y, cx, cy);
      } else {
         vertex(out, 2, x, y, 0, 0);
      }
   }

   private static strictfp short transformed(float value, boolean saturate) {
      int i = RecoveredMath.truncateInt(value);
      return (short)(saturate ? (i > 32767 ? 32767 : (i < -32768 ? -32768 : i)) : i);
   }

   public static strictfp List<FontRasterization.Vertex> shape(FontTables.Font f, int glyph) {
      if (f.cff != null) {
         FontCff.Context count = new FontCff.Context(true);
         if (FontCff.run(f.cff, glyph, count) && f.scratch.reserve(count.count * 14 + 15 & -16)) {
            FontCff.Context draw = new FontCff.Context(false);
            return FontCff.run(f.cff, glyph, draw) ? draw.vertices : Collections.emptyList();
         } else {
            return Collections.emptyList();
         }
      } else {
         int at = offset(f, glyph);
         if (at < 0) {
            return Collections.emptyList();
         } else {
            byte[] b = f.data;
            int contours = s16(b, at);
            List<FontRasterization.Vertex> output = new ArrayList<>();
            if (contours > 0) {
               int ends = at + 10;
               int last = u16(b, ends + contours * 2 - 2);
               int n = last + 1;
               if (!f.scratch.reserve((last + 2 * contours) * 14 + 29 & 2147483632)) {
                  return output;
               } else {
                  int p = at + 12 + contours * 2 + u16(b, at + 10 + contours * 2);
                  int[] flags = new int[n];
                  int[] xs = new int[n];
                  int[] ys = new int[n];
                  int flag = 0;
                  int repeats = 0;

                  for (int i = 0; i < n; i++) {
                     if (repeats == 0) {
                        flag = b[p++] & 255;
                        if ((flag & 8) != 0) {
                           repeats = b[p++] & 255;
                        }
                     } else {
                        repeats--;
                     }

                     flags[i] = flag;
                  }

                  int coordinate = 0;

                  for (int i = 0; i < n; i++) {
                     flag = flags[i];
                     if ((flag & 2) != 0) {
                        int delta = b[p++] & 255;
                        coordinate += (flag & 16) != 0 ? delta : -delta;
                     } else if ((flag & 16) == 0) {
                        coordinate += s16(b, p);
                        p += 2;
                     }

                     xs[i] = (short)coordinate;
                  }

                  coordinate = 0;

                  for (int i = 0; i < n; i++) {
                     flag = flags[i];
                     if ((flag & 4) != 0) {
                        int delta = b[p++] & 255;
                        coordinate += (flag & 32) != 0 ? delta : -delta;
                     } else if ((flag & 32) == 0) {
                        coordinate += s16(b, p);
                        p += 2;
                     }

                     ys[i] = (short)coordinate;
                  }

                  int nextMove = 0;
                  int contour = 0;
                  int x = 0;
                  int y = 0;
                  int cx = 0;
                  int cy = 0;
                  int firstCX = 0;
                  int firstCY = 0;
                  boolean off = false;
                  boolean firstOff = false;

                  for (int i = 0; i < n; i++) {
                     int px = xs[i];
                     int py = ys[i];
                     if (nextMove == i) {
                        if (i != 0) {
                           close(output, off, firstOff, x, y, firstCX, firstCY, cx, cy);
                        }

                        firstOff = (flags[i] & 1) == 0;
                        if (firstOff) {
                           firstCX = px;
                           firstCY = py;
                           if ((flags[i + 1] & 1) == 0) {
                              x = px + xs[i + 1] >> 1;
                              y = py + ys[i + 1] >> 1;
                           } else {
                              x = xs[i + 1];
                              y = ys[i + 1];
                              i++;
                           }
                        } else {
                           x = px;
                           y = py;
                        }

                        vertex(output, 1, x, y, 0, 0);
                        off = false;
                        nextMove = 1 + u16(b, ends + contour++ * 2);
                     } else if ((flags[i] & 1) == 0) {
                        if (off) {
                           vertex(output, 3, cx + px >> 1, cy + py >> 1, cx, cy);
                        }

                        cx = px;
                        cy = py;
                        off = true;
                     } else {
                        vertex(output, off ? 3 : 2, px, py, off ? cx : 0, off ? cy : 0);
                        off = false;
                     }
                  }

                  close(output, off, firstOff, x, y, firstCX, firstCY, cx, cy);
                  return output;
               }
            } else if (contours == 0) {
               return output;
            } else {
               int p = at + 10;
               float unit = RenderConstants.f(2837572);
               float fraction = RenderConstants.f(2837568);

               int flags;
               do {
                  flags = u16(b, p);
                  int childGlyph = u16(b, p + 2);
                  p += 4;
                  float a = unit;
                  float c = 0.0F;
                  float d = unit;
                  float bb = 0.0F;
                  float tx = 0.0F;
                  float ty = 0.0F;
                  if ((flags & 2) != 0) {
                     if ((flags & 1) != 0) {
                        tx = (float)s16(b, p);
                        ty = (float)s16(b, p + 2);
                        p += 4;
                     } else {
                        tx = (float)b[p];
                        ty = (float)b[p + 1];
                        p += 2;
                     }
                  }

                  if ((flags & 8) != 0) {
                     a = d = (float)s16(b, p) * fraction;
                     p += 2;
                  } else if ((flags & 64) != 0) {
                     a = (float)s16(b, p) * fraction;
                     d = (float)s16(b, p + 2) * fraction;
                     p += 4;
                  } else if ((flags & 128) != 0) {
                     a = (float)s16(b, p) * fraction;
                     bb = (float)s16(b, p + 2) * fraction;
                     c = (float)s16(b, p + 4) * fraction;
                     d = (float)s16(b, p + 6) * fraction;
                     p += 8;
                  }

                  List<FontRasterization.Vertex> child = shape(f, childGlyph);
                  if (!child.isEmpty()) {
                     float sx = (float)StrictMath.sqrt((double)(a * a + bb * bb));
                     float sy = (float)StrictMath.sqrt((double)(c * c + d * d));
                     int n = child.size();
                     int vectorCount = n >= 9 ? n - (n % 8 != 0 ? n % 8 : 8) : 0;

                     for (int ix = 0; ix < n; ix++) {
                        FontRasterization.Vertex v = child.get(ix);
                        boolean packed = ix >= vectorCount || ix % 8 >= 1 && ix % 8 <= 3;
                        boolean packedCX = packed && (ix >= vectorCount || ix % 8 != 2);
                        float x = (float)v.x;
                        float y = (float)v.y;
                        float cx = (float)v.cx;
                        float cy = (float)v.cy;
                        v.x = transformed(sx * (c * y + a * x + tx), packed);
                        v.y = transformed(sy * (bb * x + d * y + ty), packed);
                        v.cx = transformed(sx * (c * cy + a * cx + tx), packedCX);
                        v.cy = transformed(sy * (bb * cx + d * cy + ty), packed);
                     }

                     if (!f.scratch.reserve((output.size() + n) * 14)) {
                        return Collections.emptyList();
                     }

                     output.addAll(child);
                  }
               } while ((flags & 32) != 0);

               return output;
            }
         }
      }
   }

   private FontTables() {
   }

   public static final strictfp class Font {
      public final byte[] data;
      public int glyphCount;
      public int loca;
      public int head;
      public int glyf;
      public int hhea;
      public int hmtx;
      public int kern;
      public int gpos;
      public int cmap;
      public int locaFormat;
      public FontCff.Info cff;
      public FontRasterization.Scratch scratch;

      public Font(byte[] data, FontRasterization.Scratch scratch) {
         this.data = Objects.requireNonNull(data);
         this.scratch = Objects.requireNonNull(scratch);
      }
   }
}
