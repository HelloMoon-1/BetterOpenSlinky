package dev.slinky.setting;

public final class ColorSetting extends Setting<ColorSetting.Value> {
   public ColorSetting(String key, String label) {
      super(key, label, new ColorSetting.Value(0.0F, 0.0F, 0.0F));
   }

   @Override
   public String format() {
      ColorSetting.Value v = this.get();
      return Float.toString(v.red) + "," + v.green + "," + v.blue;
   }

   protected ColorSetting.Value parse(String text) {
      String[] parts = text.split(",", -1);
      if (parts.length != 3) {
         throw new IllegalArgumentException("color needs red,green,blue");
      } else {
         return new ColorSetting.Value(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
      }
   }

   protected ColorSetting.Value requireValid(ColorSetting.Value value) {
      super.requireValid(value);

      for (float lane : new float[]{value.red, value.green, value.blue}) {
         if (!Float.isFinite(lane) || lane < 0.0F || lane > 1.0F) {
            throw new IllegalArgumentException("color lanes must be finite and in [0,1]");
         }
      }

      return value;
   }

   public static final class Value {
      public final float red;
      public final float green;
      public final float blue;

      public Value(float red, float green, float blue) {
         this.red = red;
         this.green = green;
         this.blue = blue;
      }

      public int argb() {
         return 0xFF000000 | (int)(this.red * 255.0F) << 16 | (int)(this.green * 255.0F) << 8 | (int)(this.blue * 255.0F);
      }
   }
}
