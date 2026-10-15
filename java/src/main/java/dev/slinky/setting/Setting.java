package dev.slinky.setting;

public abstract class Setting<T> {
   private final String key;
   private final String label;
   private volatile T value;
   private volatile boolean configured;
   private T saved;
   private boolean savedConfigured;

   protected Setting(String key, String label, T initialValue) {
      if (key != null && !key.isEmpty()) {
         this.key = key;
         this.label = label == null ? key : label;
         if (initialValue == null) {
            throw new IllegalArgumentException("setting value must not be null");
         } else {
            this.value = initialValue;
         }
      } else {
         throw new IllegalArgumentException("setting key must not be empty");
      }
   }

   public final String getKey() {
      return this.key;
   }

   public final String getLabel() {
      return this.label;
   }

   public final T get() {
      return this.value;
   }

   public final void set(T value) {
      this.value = this.requireValid(value);
      this.configured = true;
   }

   public final boolean isConfigured() {
      return this.configured;
   }

   public final void markUnconfigured() {
      this.configured = false;
   }

   public final synchronized void saveCurrent() {
      this.saved = this.value;
      this.savedConfigured = this.configured;
   }

   public synchronized void restoreSaved() {
      if (this.savedConfigured) {
         this.value = this.saved;
      }

      this.configured = this.savedConfigured;
   }

   public final void parseAndSet(String text) {
      this.set(this.parse(text));
   }

   public abstract String format();

   protected abstract T parse(String var1);

   protected T requireValid(T value) {
      if (value == null) {
         throw new IllegalArgumentException("setting value must not be null");
      } else {
         return value;
      }
   }
}
