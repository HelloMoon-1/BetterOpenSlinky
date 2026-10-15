package dev.slinky.client.render;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final strictfp class FontAtlasService {
   public static strictfp boolean allocate(FontAtlasService.Canvas canvas) {
      FontAtlasService.Stash stash = canvas.stash;
      FontGlyphCache.Stash cache = stash.cache;
      if (stash.dirty()) {
         int x = cache.dirtyX;
         int y = cache.dirtyY;
         int width = cache.dirtyRight - x;
         int height = cache.dirtyBottom - y;
         stash.clean();
         int texture = canvas.textures[canvas.index];
         if (texture != 0) {
            canvas.driver.upload(texture, x, y, width, height, cache.pixels);
         }
      }

      int index = canvas.index;
      if (index > 2) {
         return false;
      } else {
         int[] dimensions = new int[]{canvas.queryWidth, canvas.queryHeight};
         int texture = canvas.textures[index + 1];
         if (texture != 0) {
            canvas.driver.dimensions(texture, dimensions);
         } else {
            canvas.driver.dimensions(canvas.textures[index], dimensions);
            if (dimensions[0] > dimensions[1]) {
               dimensions[1] += dimensions[1];
            } else {
               dimensions[0] += dimensions[0];
            }

            if (dimensions[0] >= 2049 || dimensions[1] >= 2049) {
               dimensions[0] = dimensions[1] = 2048;
            }

            int created = canvas.driver.create(1, dimensions[0], dimensions[1], 0, null);
            canvas.textures[canvas.index + 1] = created;
         }

         canvas.index++;
         reset(canvas.stash, dimensions[0], dimensions[1], canvas.resetX, canvas.resetY);
         return true;
      }
   }

   public static strictfp boolean reset(FontAtlasService.Stash stash, int width, int height, int incomingX, int incomingY) {
      if (stash == null) {
         return false;
      } else {
         FontGlyphCache.Stash cache = stash.cache;
         if (stash.dirty()) {
            if (stash.upload != null) {
               stash.upload.upload(new int[]{cache.dirtyX, cache.dirtyY, cache.dirtyRight, cache.dirtyBottom}, cache.pixels);
            }

            stash.clean();
         }

         if (stash.vertexCount > 0) {
            if (stash.flush != null) {
               stash.flush.flush(stash.positions, stash.uv, stash.colors, stash.vertexCount);
            }

            stash.vertexCount = 0;
         }

         if (stash.resized != null && !stash.resized.resize(width, height)) {
            return false;
         } else {
            cache.atlas = new FontServices.Atlas(width, height);
            long size = (long)(height * width);
            cache.pixels = stash.allocation.resize(cache.pixels, size);
            if (cache.pixels == null) {
               return false;
            } else {
               Arrays.fill(cache.pixels, 0, (int)size, (byte)0);
               cache.dirtyX = width;
               cache.dirtyY = height;
               cache.dirtyRight = cache.dirtyBottom = 0;

               for (FontGlyphCache.Font font : stash.fonts) {
                  font.glyphs.clear();
                  Arrays.fill(font.heads, -1);
               }

               stash.width = width;
               stash.height = height;
               cache.stride = width;
               stash.inverseWidth = RenderConstants.f(2838368) / (float)width;
               stash.inverseHeight = RenderConstants.f(2838372) / (float)height;
               int[] point = cache.atlas.add(2, 2);
               if (point != null) {
                  int x = point[0];
                  int y = point[1];
                  int offset = width * y + x;
                  cache.pixels[offset] = cache.pixels[offset + 1] = cache.pixels[offset + width] = cache.pixels[offset + width + 1] = -1;
                  cache.dirtyX = Math.min(cache.dirtyX, x);
                  cache.dirtyY = Math.min(cache.dirtyY, y);
                  cache.dirtyRight = Math.max(cache.dirtyRight, x + 2);
                  cache.dirtyBottom = Math.max(cache.dirtyBottom, y + 2);
               }

               return true;
            }
         }
      }
   }

   private FontAtlasService() {
   }

   public interface Allocation {
      byte[] resize(byte[] var1, long var2);
   }

   public static final strictfp class Canvas {
      public FontAtlasService.Stash stash;
      public int index;
      public final int[] textures;
      public final FontAtlasService.Driver driver;
      public final int queryWidth;
      public final int queryHeight;
      public final int resetX;
      public final int resetY;

      public Canvas(
         FontAtlasService.Stash stash, int index, int[] textures, FontAtlasService.Driver driver, int queryWidth, int queryHeight, int resetX, int resetY
      ) {
         this.stash = stash;
         this.index = index;
         this.textures = Objects.requireNonNull(textures);
         this.driver = Objects.requireNonNull(driver);
         this.queryWidth = queryWidth;
         this.queryHeight = queryHeight;
         this.resetX = resetX;
         this.resetY = resetY;
      }
   }

   public interface DirtyUpload {
      void upload(int[] var1, byte[] var2);
   }

   public interface Driver {
      void upload(int var1, int var2, int var3, int var4, int var5, byte[] var6);

      void dimensions(int var1, int[] var2);

      int create(int var1, int var2, int var3, int var4, byte[] var5);
   }

   public interface ResizeNotification {
      boolean resize(int var1, int var2);
   }

   public static final strictfp class Stash {
      public final FontGlyphCache.Stash cache;
      public int width;
      public int height;
      public int vertexCount;
      public float inverseWidth;
      public float inverseHeight;
      public float[] positions;
      public float[] uv;
      public int[] colors;
      public final List<FontGlyphCache.Font> fonts;
      public FontAtlasService.DirtyUpload upload;
      public FontAtlasService.VertexFlush flush;
      public FontAtlasService.ResizeNotification resized;
      public FontAtlasService.Allocation allocation;

      public Stash(FontGlyphCache.Stash cache, int width, int height, List<FontGlyphCache.Font> fonts, FontAtlasService.Allocation allocation) {
         this.cache = Objects.requireNonNull(cache);
         this.width = width;
         this.height = height;
         this.fonts = Objects.requireNonNull(fonts);
         this.allocation = Objects.requireNonNull(allocation);
      }

      public strictfp boolean dirty() {
         return this.cache.dirtyX < this.cache.dirtyRight && this.cache.dirtyY < this.cache.dirtyBottom;
      }

      public strictfp void clean() {
         this.cache.dirtyX = this.width;
         this.cache.dirtyY = this.height;
         this.cache.dirtyRight = this.cache.dirtyBottom = 0;
      }
   }

   public interface VertexFlush {
      void flush(float[] var1, float[] var2, int[] var3, int var4);
   }
}
