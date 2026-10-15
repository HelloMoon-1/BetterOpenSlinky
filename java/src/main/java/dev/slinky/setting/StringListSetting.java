package dev.slinky.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

public final class StringListSetting extends Setting<List<String>> {
   private UnaryOperator<List<String>> normalizer;

   public void normalizeWith(UnaryOperator<List<String>> normalizer) {
      this.normalizer = normalizer;
      boolean configured = this.isConfigured();
      this.set(this.get());
      if (!configured) {
         this.markUnconfigured();
      }
   }

   public StringListSetting(String key, String label, List<String> initialValue) {
      super(key, label, new ArrayList<>(initialValue));
      this.set(initialValue);
   }

   public List<String> values() {
      return this.get();
   }

   @Override
   public synchronized void restoreSaved() {
      this.set(Collections.emptyList());
   }

   public boolean containsIgnoreCase(String candidate) {
      for (String value : this.get()) {
         if (value.equalsIgnoreCase(candidate)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String format() {
      StringBuilder result = new StringBuilder();

      for (String value : this.get()) {
         if (result.length() > 0) {
            result.append(',');
         }

         result.append(value);
      }

      return result.toString();
   }

   protected List<String> parse(String text) {
      List<String> values = new ArrayList<>();
      if (text.trim().isEmpty()) {
         return values;
      } else {
         for (String field : text.split(",", -1)) {
            String value = field.trim();
            if (!value.isEmpty()) {
               values.add(value);
            }
         }

         return values;
      }
   }

   protected List<String> requireValid(List<String> value) {
      super.requireValid(value);
      return Collections.unmodifiableList(new ArrayList<>(this.normalizer == null ? value : this.normalizer.apply(value)));
   }
}
