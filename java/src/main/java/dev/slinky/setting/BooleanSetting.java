package dev.slinky.setting;

public final class BooleanSetting extends Setting<Boolean> {
   public BooleanSetting(String key, String label, boolean initialValue) {
      super(key, label, initialValue);
   }

   public boolean isEnabled() {
      return this.get();
   }

   @Override
   public String format() {
      return Boolean.toString(this.get());
   }

   protected Boolean parse(String text) {
      if ("true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text) || "1".equals(text)) {
         return true;
      } else if (!"false".equalsIgnoreCase(text) && !"off".equalsIgnoreCase(text) && !"0".equals(text)) {
         throw new IllegalArgumentException("expected true/false, on/off, or 1/0");
      } else {
         return false;
      }
   }
}
