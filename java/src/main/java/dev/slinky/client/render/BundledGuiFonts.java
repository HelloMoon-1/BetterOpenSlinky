package dev.slinky.client.render;

import dev.slinky.inject.Platform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class BundledGuiFonts {
   public static CanvasFonts.Engine load(int style) {
      String name = style == 1 ? "InterSemiBold.ttf" : "Inter-Medium.ttf";

      byte[] bytes;
      try (InputStream input = Platform.resource("/assets/slinkysemantic/fonts/" + name)) {
         if (input == null) {
            throw new IOException("Missing bundled GUI font " + name);
         }

         ByteArrayOutputStream output = new ByteArrayOutputStream();
         byte[] buffer = new byte[8192];

         int count;
         while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
         }

         bytes = output.toByteArray();
      } catch (IOException var19) {
         throw new IllegalStateException("Cannot load GUI font", var19);
      }

      FontTables.Font font = new FontTables.Font(bytes, new FontRasterization.Scratch(() -> {
         throw new IllegalStateException("GUI glyph scratch capacity exceeded");
      }));
      font.head = table(bytes, "head");
      font.hhea = table(bytes, "hhea");
      font.hmtx = table(bytes, "hmtx");
      font.loca = table(bytes, "loca");
      font.glyf = table(bytes, "glyf");
      font.kern = table(bytes, "kern");
      font.gpos = table(bytes, "GPOS");
      font.glyphCount = FontTables.u16(bytes, required(table(bytes, "maxp")) + 4);
      font.locaFormat = FontTables.u16(bytes, required(font.head) + 50);
      int map = required(table(bytes, "cmap"));

      for (int i = 0; i < FontTables.u16(bytes, map + 2); i++) {
         int record = map + 4 + i * 8;
         int candidate = map + FontTables.u32(bytes, record + 4);
         int format = FontTables.u16(bytes, candidate);
         int platform = FontTables.u16(bytes, record);
         int encoding = FontTables.u16(bytes, record + 2);
         if ((platform == 0 || platform == 3 && (encoding == 1 || encoding == 10)) && (format == 4 || format == 12) && (font.cmap == 0 || format == 12)) {
            font.cmap = candidate;
         }
      }

      required(font.cmap);
      required(font.glyf);
      required(font.loca);
      required(font.hhea);
      required(font.hmtx);
      float units = (float)(FontTables.s16(bytes, font.hhea + 4) - FontTables.s16(bytes, font.hhea + 6));
      float ascent = (float)FontTables.s16(bytes, font.hhea + 4) / units;
      float descent = (float)FontTables.s16(bytes, font.hhea + 6) / units;
      float gap = (float)FontTables.s16(bytes, font.hhea + 8) / units;
      int[] buckets = new int[256];
      Arrays.fill(buckets, -1);
      return new CanvasFonts.Engine(new FontGlyphCache.Font(font, buckets, ascent, descent, ascent - descent + gap), null, 512, 256);
   }

   private static int required(int offset) {
      if (offset == 0) {
         throw new IllegalStateException("Incomplete bundled TrueType face");
      } else {
         return offset;
      }
   }

   private static int table(byte[] bytes, String tag) {
      int count = FontTables.u16(bytes, 4);

      for (int i = 0; i < count; i++) {
         int record = 12 + i * 16;
         boolean match = true;

         for (int n = 0; n < 4; n++) {
            match &= bytes[record + n] == tag.charAt(n);
         }

         if (match) {
            return FontTables.u32(bytes, record + 8);
         }
      }

      return 0;
   }

   private BundledGuiFonts() {
   }
}
