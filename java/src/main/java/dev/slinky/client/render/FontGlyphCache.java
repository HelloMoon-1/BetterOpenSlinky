package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatNumeric;
import java.util.ArrayList;
import java.util.List;

public final strictfp class FontGlyphCache implements FontServices.Face {
   private final FontGlyphCache.Stash stash;
   private final FontGlyphCache.Font font;
   private final FontGlyphCache.Source source;

   public FontGlyphCache(FontGlyphCache.Stash stash, FontGlyphCache.Font font) {
      this(stash, font, null);
   }

   public FontGlyphCache(FontGlyphCache.Stash stash, FontGlyphCache.Font font, FontGlyphCache.Source source) {
      this.stash = stash;
      this.font = font;
      this.source = source;
   }

   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   @Override
   public strictfp int kern(int a, int b) {
      return this.source == null ? FontTables.kerning(this.font.tables, a, b) : this.source.kern(a, b);
   }

   @Override
   public strictfp float ascender() {
      return this.font.ascender;
   }

   @Override
   public strictfp float descender() {
      return this.font.descender;
   }

   @Override
   public strictfp float lineHeight() {
      return this.font.lineHeight;
   }

   @Override
   public strictfp int unitsPerEm() {
      return this.source == null ? FontTables.u16(this.font.tables.data, this.font.tables.head + 18) : this.source.unitsPerEm();
   }

   @Override
   public strictfp FontServices.Glyph glyph(int cp, int sizeBits, short blur, int bitmap) {
      short size = (short)sizeBits;
      if (size < 2) {
         return null;
      } else {
         short clamped = (short)Math.min(blur, 20);
         (this.source == null ? this.font.tables.scratch : this.source.scratch()).used = 0;
         int bucket = FontServices.hash(cp);
         int index = this.font.heads[bucket];
         FontGlyphCache.Row row = null;

         while (index != -1) {
            FontGlyphCache.Row candidate = this.font.glyphs.get(index);
            if (candidate.codepoint == cp && candidate.size == size && candidate.blur == clamped) {
               row = candidate;
               break;
            }

            index = candidate.next;
         }

         if (row == null || bitmap != 1 && (row.glyph.x0 < 0 || row.glyph.y0 < 0)) {
            float scaleSize = (float)size / c(2838208);
            FontGlyphCache.Font render = this.font;
            int glyphIndex = this.source == null ? FontTables.map(this.font.tables, cp) : this.source.map(cp);
            if (glyphIndex == 0) {
               for (FontGlyphCache.Font fallback : this.font.fallbacks) {
                  int mapped = FontTables.map(fallback.tables, cp);
                  if (mapped != 0) {
                     render = fallback;
                     glyphIndex = mapped;
                     break;
                  }
               }
            }

            FontTables.Font table = render.tables;
            float scale = scaleSize / (float)(this.source == null ? FontTables.u16(table.data, table.head + 18) : this.source.unitsPerEm());
            int padding = clamped + 2;
            int advance;
            if (this.source == null) {
               int metricCount = FontTables.u16(table.data, table.hhea + 34);
               int metric = table.hmtx + (glyphIndex < metricCount ? glyphIndex : metricCount - 1) * 4;
               advance = FontTables.u16(table.data, metric);
            } else {
               advance = this.source.advance(glyphIndex);
            }

            int[] box = this.source == null ? FontTables.box(table, glyphIndex) : this.source.box(glyphIndex);
            int x0 = 0;
            int y0 = 0;
            int x1 = 0;
            int y1 = 0;
            if (box != null) {
               x0 = RecoveredMath.truncateInt(RecoveredGui.floor((float)box[0] * scale + 0.0F));
               y0 = RecoveredMath.truncateInt(RecoveredGui.floor((float)(-box[3]) * scale + 0.0F));
               x1 = RecoveredMath.truncateInt(RecoveredMath.ceiling((float)box[2] * scale + 0.0F));
               y1 = RecoveredMath.truncateInt(RecoveredMath.ceiling((float)(-box[1]) * scale + 0.0F));
            }

            int boxWidth = x1 - x0;
            int boxHeight = y1 - y0;
            int width = boxWidth + padding * 2;
            int height = boxHeight + padding * 2;
            int atlasX = -1;
            int atlasY = -1;
            if (bitmap == 2) {
               int[] packed = this.stash.atlas.add(width, height);
               if (packed == null) {
                  if (this.stash.atlasError == null) {
                     return null;
                  }

                  this.stash.atlasError.run();
                  packed = this.stash.atlas.add(width, height);
                  if (packed == null) {
                     return null;
                  }
               }

               atlasX = packed[0];
               atlasY = packed[1];
            }

            if (row == null) {
               row = new FontGlyphCache.Row();
               row.codepoint = cp;
               row.size = size;
               row.blur = clamped;
               row.next = this.font.heads[bucket];
               this.font.heads[bucket] = this.font.glyphs.size();
               this.font.glyphs.add(row);
            }

            FontServices.Glyph g = row.glyph;
            g.index = glyphIndex;
            g.x0 = (short)atlasX;
            g.y0 = (short)atlasY;
            g.x1 = (short)(atlasX + width);
            g.y1 = (short)(atlasY + height);
            g.advance = (short)RecoveredMath.truncateInt((float)((short)advance) * scale * c(2838208));
            g.xOffset = (short)(x0 - padding);
            g.yOffset = (short)(y0 - padding);
            if (bitmap == 1) {
               return g;
            } else {
               int destination = ((short)atlasY + padding) * this.stash.stride + (short)atlasX + padding;
               if (this.source == null) {
                  makeBitmap(table, this.stash.pixels, destination, boxWidth, boxHeight, this.stash.stride, scale, scale, 0.0F, 0.0F, glyphIndex);
               } else if (boxWidth != 0 && boxHeight != 0) {
                  FontRasterization.rasterize(
                     this.stash.pixels,
                     destination,
                     boxWidth,
                     boxHeight,
                     this.stash.stride,
                     c(2837800),
                     this.source.shape(glyphIndex),
                     scale,
                     scale,
                     0.0F,
                     0.0F,
                     x0,
                     y0,
                     true,
                     this.source.scratch()
                  );
               }

               int border = g.y0 * this.stash.stride + g.x0;

               for (int y = 0; y < height; y++) {
                  this.stash.pixels[border + this.stash.stride * y] = 0;
                  this.stash.pixels[border + this.stash.stride * y + width - 1] = 0;
               }

               for (int x = 0; x < width; x++) {
                  this.stash.pixels[border + x] = 0;
                  this.stash.pixels[border + (height - 1) * this.stash.stride + x] = 0;
               }

               if (blur > 0) {
                  (this.source == null ? this.font.tables.scratch : this.source.scratch()).used = 0;
                  float sigma = (float)clamped * c(2838212) + c(2838216);
                  float exponential = FloatNumeric.exp(c(2838220) / sigma);
                  int coefficient = RecoveredMath.truncateInt((c(2838216) - exponential) * c(2838224));
                  FontServices.blur(this.stash.pixels, border, width, height, this.stash.stride, coefficient);
               }

               this.stash.dirtyX = Math.min(this.stash.dirtyX, g.x0);
               this.stash.dirtyY = Math.min(this.stash.dirtyY, g.y0);
               this.stash.dirtyRight = Math.max(this.stash.dirtyRight, g.x1);
               this.stash.dirtyBottom = Math.max(this.stash.dirtyBottom, g.y1);
               return g;
            }
         } else {
            return row.glyph;
         }
      }
   }

   public static strictfp void makeBitmap(
      FontTables.Font font, byte[] output, int start, int width, int height, int stride, float scaleX, float scaleY, float shiftX, float shiftY, int glyph
   ) {
      List<FontRasterization.Vertex> shape = FontTables.shape(font, glyph);
      int[] box = FontTables.box(font, glyph);
      int offsetX = 0;
      int offsetY = 0;
      if (box != null) {
         offsetX = RecoveredMath.truncateInt(RecoveredGui.floor((float)box[0] * scaleX + shiftX));
         offsetY = RecoveredMath.truncateInt(RecoveredGui.floor((float)(-box[3]) * scaleY + shiftY));
      }

      if (width != 0 && height != 0) {
         FontRasterization.rasterize(
            output, start, width, height, stride, c(2837800), shape, scaleX, scaleY, shiftX, shiftY, offsetX, offsetY, true, font.scratch
         );
      }
   }

   public static final strictfp class Font {
      public final FontTables.Font tables;
      public final int[] heads;
      public final List<FontGlyphCache.Row> glyphs = new ArrayList<>();
      public final List<FontGlyphCache.Font> fallbacks = new ArrayList<>();
      public final float ascender;
      public final float descender;
      public final float lineHeight;

      public Font(FontTables.Font tables, int[] heads, float ascender, float descender, float lineHeight) {
         if (heads.length != 256) {
            throw new IllegalArgumentException("Font hash table must contain 256 supplied entries");
         } else {
            this.tables = tables;
            this.heads = heads;
            this.ascender = ascender;
            this.descender = descender;
            this.lineHeight = lineHeight;
         }
      }
   }

   public static final class Row {
      public final FontServices.Glyph glyph = new FontServices.Glyph();
      public int codepoint;
      public int next;
      public short size;
      public short blur;
   }

   public interface Source {
      int unitsPerEm();

      int map(int var1);

      int advance(int var1);

      int[] box(int var1);

      int kern(int var1, int var2);

      List<FontRasterization.Vertex> shape(int var1);

      FontRasterization.Scratch scratch();
   }

   public static final strictfp class Stash {
      public FontServices.Atlas atlas;
      public byte[] pixels;
      public int stride;
      public int dirtyX;
      public int dirtyY;
      public int dirtyRight;
      public int dirtyBottom;
      public Runnable atlasError;

      public Stash(FontServices.Atlas atlas, byte[] pixels, int stride, int dirtyX, int dirtyY, int dirtyRight, int dirtyBottom, Runnable atlasError) {
         this.atlas = atlas;
         this.pixels = pixels;
         this.stride = stride;
         this.dirtyX = dirtyX;
         this.dirtyY = dirtyY;
         this.dirtyRight = dirtyRight;
         this.dirtyBottom = dirtyBottom;
         this.atlasError = atlasError;
      }
   }
}
