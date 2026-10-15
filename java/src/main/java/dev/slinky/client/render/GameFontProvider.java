package dev.slinky.client.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public final class GameFontProvider implements FontGlyphCache.Source {
   private final Minecraft game;
   private final boolean bold;
   private final boolean unicode;
   private final IResourceManager resources;
   private final BufferedImage ascii;
   private final byte[] sizes = new byte[65536];
   private final Map<Integer, BufferedImage> pages = new HashMap<>();
   private final Map<Integer, List<FontRasterization.Vertex>> outlines = new HashMap<>();
   private final FontRasterization.Scratch scratch = new FontRasterization.Scratch(() -> {
      throw new IllegalStateException("Game glyph scratch budget exceeded");
   });

   public GameFontProvider(Minecraft game) throws IOException {
      this(game, false);
   }

   public GameFontProvider(Minecraft game, boolean bold) throws IOException {
      this.game = game;
      this.bold = bold;
      this.unicode = game.fontRendererObj.getUnicodeFlag();
      this.resources = game.getResourceManager();
      this.ascii = this.image("textures/font/ascii.png");

      try (InputStream input = this.resources.getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream()) {
         int offset = 0;

         while (offset < this.sizes.length) {
            int read = input.read(this.sizes, offset, this.sizes.length - offset);
            if (read < 0) {
               throw new IOException("Incomplete game glyph-size resource");
            }

            offset += read;
         }
      }
   }

   private BufferedImage image(String path) throws IOException {
      BufferedImage var5;
      try (InputStream input = this.resources.getResource(new ResourceLocation(path)).getInputStream()) {
         BufferedImage result = ImageIO.read(input);
         if (result == null) {
            throw new IOException("Unreadable game font bitmap " + path);
         }

         var5 = result;
      }

      return var5;
   }

   @Override
   public int unitsPerEm() {
      return 18;
   }

   boolean isCurrent() {
      return this.unicode == this.game.fontRendererObj.getUnicodeFlag();
   }

   @Override
   public int map(int codepoint) {
      return codepoint >= 0 && codepoint <= 65535 ? codepoint : 0;
   }

   @Override
   public int advance(int glyph) {
      return (Math.max(0, this.game.fontRendererObj.getCharWidth((char)glyph)) + (this.bold && glyph != 167 ? 1 : 0)) * 2;
   }

   @Override
   public int kern(int previous, int current) {
      return 0;
   }

   @Override
   public FontRasterization.Scratch scratch() {
      return this.scratch;
   }

   @Override
   public int[] box(int glyph) {
      List<FontRasterization.Vertex> vertices = this.shape(glyph);
      if (vertices.isEmpty()) {
         return null;
      } else {
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxY = Integer.MIN_VALUE;

         for (FontRasterization.Vertex vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
         }

         return new int[]{minX, minY, maxX, maxY};
      }
   }

   @Override
   public List<FontRasterization.Vertex> shape(int glyph) {
      List<FontRasterization.Vertex> existing = this.outlines.get(glyph);
      if (existing != null) {
         return existing;
      } else {
         List<FontRasterization.Vertex> result = new ArrayList<>();
         if (glyph != 32 && glyph != 167) {
            boolean basic = glyph >= 33 && glyph <= 126 && !this.unicode;

            BufferedImage bitmap;
            try {
               if (basic) {
                  bitmap = this.ascii;
               } else {
                  int page = glyph >>> 8;
                  bitmap = this.pages.get(page);
                  if (bitmap == null && this.sizes[glyph] != 0) {
                     bitmap = this.image(String.format(Locale.ROOT, "textures/font/unicode_page_%02x.png", page));
                     this.pages.put(page, bitmap);
                  }

                  if (bitmap == null) {
                     this.outlines.put(glyph, result);
                     return result;
                  }
               }
            } catch (IOException var20) {
               throw new IllegalStateException("Game font resource failed", var20);
            }

            int slot = glyph & 0xFF;
            int cellWidth = bitmap.getWidth() / 16;
            int cellHeight = bitmap.getHeight() / 16;
            int left = (slot & 15) * cellWidth;
            int top = (slot >>> 4) * cellHeight;
            int start = basic ? 0 : (this.sizes[glyph] & 255) >>> 4;
            int units = basic ? 2 : 1;
            int end = basic ? cellWidth : Math.min(cellWidth, (this.sizes[glyph] & 15) + 1);

            for (int y = 0; y < cellHeight; y++) {
               for (int x = start; x < end; x++) {
                  if (bitmap.getRGB(left + x, top + y) >>> 24 != 0) {
                     int x0 = (x - start) * units;
                     int x1 = x0 + units + (this.bold ? 2 : 0);
                     int y1 = 16 - y * units;
                     int y0 = y1 - units;
                     result.add(new FontRasterization.Vertex(1, x0, y0, 0, 0, 0, 0));
                     result.add(new FontRasterization.Vertex(2, x1, y0, 0, 0, 0, 0));
                     result.add(new FontRasterization.Vertex(2, x1, y1, 0, 0, 0, 0));
                     result.add(new FontRasterization.Vertex(2, x0, y1, 0, 0, 0, 0));
                     result.add(new FontRasterization.Vertex(2, x0, y0, 0, 0, 0, 0));
                  }
               }
            }

            this.outlines.put(glyph, result);
            return result;
         } else {
            this.outlines.put(glyph, result);
            return result;
         }
      }
   }
}
