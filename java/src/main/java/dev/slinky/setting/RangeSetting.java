package dev.slinky.setting;

public final class RangeSetting extends Setting<RangeSetting.Value> {
   private final double minimum;
   private final double maximum;

   public double minimum() {
      return this.minimum;
   }

   public double maximum() {
      return this.maximum;
   }

   public RangeSetting(String key, String label, double lower, double upper, double minimum, double maximum) {
      super(key, label, new RangeSetting.Value(lower, upper));
      this.minimum = minimum;
      this.maximum = maximum;
      this.set(new RangeSetting.Value(lower, upper));
   }

   @Override
   public String format() {
      return Double.toString(this.get().lower) + "," + Double.toString(this.get().upper);
   }

   protected RangeSetting.Value parse(String text) {
      String[] fields = text.split(",", -1);
      if (fields.length != 2) {
         throw new IllegalArgumentException("range must be lower,upper");
      } else {
         return new RangeSetting.Value(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]));
      }
   }

   protected RangeSetting.Value requireValid(RangeSetting.Value value) {
      super.requireValid(value);
      if (Double.isFinite(value.lower)
         && Double.isFinite(value.upper)
         && !(value.lower < this.minimum)
         && !(value.upper > this.maximum)
         && !(value.lower > value.upper)) {
         return new RangeSetting.Value((double)((float)value.lower), (double)((float)value.upper));
      } else {
         throw new IllegalArgumentException("invalid range");
      }
   }

   public static final class Value {
      public final double lower;
      public final double upper;

      public Value(double lower, double upper) {
         this.lower = lower;
         this.upper = upper;
      }
   }
}
