package dev.slinky.client.render;

import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.RavenSettingsModule;
import java.awt.Color;

// Theme.java and RenderUtils.blend/rainbow from the supplied OpenB4 donor.
public final class OpenB4Theme {
   public static final String[] NAMES = {
      "Default", "Rainbow", "Aurora", "Cherry", "Cotton candy", "Flare", "Flower", "Forest",
      "Frost", "Gold", "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
   };
   private static final int[][] COLORS = {
      {0xFFFFFF, 0xFFFFFF}, {0xFFFFFF, 0xFFFFFF}, {0x7800C8, 0x00FFB4}, {0xFFC8C8, 0xF33A6A},
      {0x63F9FF, 0xFF68CC}, {0xF12711, 0xF5AF19}, {0xD7A6E7, 0xD35AE8}, {0x228B22, 0x9ACD32},
      {0xFDFDFD, 0xD2E1E6}, {0xFFFA00, 0xFFFDC3}, {0xF0F0F0, 0x6E6E6E}, {0xFF3300, 0x320000},
      {0x7DCCF1, 0x1E47AA}, {0xC2B280, 0xFFECB3}, {0x8CFEFA, 0x0ED2F7}, {0x00FF19, 0xB4FFB4}
   };
   private static ModuleManager manager;

   private OpenB4Theme() {
   }

   public static void bind(ModuleManager value) {
      manager = value;
   }

   private static RavenSettingsModule settings() {
      return manager != null && manager.find("raven_settings") instanceof RavenSettingsModule
         ? (RavenSettingsModule)manager.find("raven_settings") : null;
   }

   public static double offset() {
      RavenSettingsModule settings = settings();
      return settings == null ? 0.5 : settings.offset.get();
   }

   public static double timeMultiplier() {
      RavenSettingsModule settings = settings();
      return settings == null ? 0.5 : settings.timeMultiplier.get();
   }

   public static int getRGB(int theme, double offset) {
      return color(theme, System.currentTimeMillis() / 1000.0, offset, 0);
   }

   public static int[] getRGBArr(int theme) {
      theme = resolve(theme);
      if (theme == 1) {
         int value = rainbow(2L);
         return new int[]{value, value};
      }
      return new int[]{0xFF000000 | COLORS[theme][0], 0xFF000000 | COLORS[theme][1]};
   }

   private static int resolve(int theme) {
      if (theme <= 0 || theme >= COLORS.length) {
         RavenSettingsModule settings = settings();
         return settings == null ? 8 : settings.defaultTheme.index() + 1;
      }
      return theme;
   }

   public static int color(int theme, double now, double offset, int ignoredCustom) {
      theme = resolve(theme);
      double mult = timeMultiplier();
      long millis = (long)(now * 1000.0);
      if (theme == 1) {
         return rainbowAt(millis, (long)Math.max(2.0 * mult, 1.0), (long)offset);
      }
      double phase = millis / 1.0E8 * mult * 400000.0;
      double amount = (Math.sin(phase + offset * (double)0.55F) + 1.0) * 0.5;
      int a = COLORS[theme][0];
      int b = COLORS[theme][1];
      int n = (int)(amount * 255.0);
      int inverse = 255 - n;
      int red = ((a >> 16 & 255) * n + (b >> 16 & 255) * inverse) >> 8;
      int green = ((a >> 8 & 255) * n + (b >> 8 & 255) * inverse) >> 8;
      int blue = ((a & 255) * n + (b & 255) * inverse) >> 8;
      return 0xFF000000 | red << 16 | green << 8 | blue;
   }

   public static int rainbow(long speed, long... offsets) {
      return rainbowAt(System.currentTimeMillis(), speed, offsets.length == 0 ? 0L : offsets[0]);
   }

   private static int rainbowAt(long now, long speed, long offset) {
      speed = Math.max(1L, Math.min(15000L, speed));
      return Color.getHSBColor((float)((now + offset) % (15000L / speed)) / (15000.0F / speed), 0.75F, 1.0F).getRGB();
   }
}
