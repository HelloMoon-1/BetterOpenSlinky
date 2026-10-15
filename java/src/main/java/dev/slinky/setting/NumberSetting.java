package dev.slinky.setting;

public final class NumberSetting extends Setting<Double> {
   private final double minimum;
   private final double maximum;
   private final double step;

   public NumberSetting(String key, String label, double initialValue, double minimum, double maximum, double step) {
      super(key, label, initialValue);
      if (!Double.isNaN(minimum) && !Double.isNaN(maximum) && !(minimum > maximum)) {
         this.minimum = minimum;
         this.maximum = maximum;
         this.step = step;
         this.set(Double.valueOf(initialValue));
      } else {
         throw new IllegalArgumentException("invalid numeric range");
      }
   }

   public double doubleValue() {
      return this.get();
   }

   public float floatValue() {
      return this.get().floatValue();
   }

   public int intValue() {
      return this.get().intValue();
   }

   public double getMinimum() {
      return this.minimum;
   }

   public double getMaximum() {
      return this.maximum;
   }

   public double getStep() {
      return this.step;
   }

   public void loadNumber(double stored) {
      float converted = (float)stored;
      if (!Float.isNaN(converted)) {
         float value = (float)this.minimum;
         if (!((float)this.minimum > converted)) {
            value = converted;
            if ((float)this.maximum < converted) {
               value = (float)this.maximum;
            }
         }

         this.set(Double.valueOf((double)value));
      }
   }

   @Override
   public String format() {
      return Double.toString(this.get());
   }

   protected Double parse(String text) {
      return Double.parseDouble(text);
   }

   protected Double requireValid(Double value) {
      super.requireValid(value);
      if (!Double.isFinite(value) || value < this.minimum || value > this.maximum) {
         throw new IllegalArgumentException("value must be between " + this.minimum + " and " + this.maximum);
      } else if (this.step <= 0.0) {
         return (double)value.floatValue();
      } else {
         double steps = Math.rint((value - this.minimum) / this.step);
         double snapped = this.minimum + steps * this.step;
         return Math.max(this.minimum, Math.min(this.maximum, snapped));
      }
   }
}
