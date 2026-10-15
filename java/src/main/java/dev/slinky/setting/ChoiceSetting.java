package dev.slinky.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChoiceSetting extends Setting<Integer> {
   private final List<String> options;

   public ChoiceSetting(String key, String label, int initialValue, String... options) {
      super(key, label, initialValue);
      if (options != null && options.length != 0) {
         List<String> copy = new ArrayList<>();
         Collections.addAll(copy, options);
         this.options = Collections.unmodifiableList(copy);
         this.set(Integer.valueOf(initialValue));
      } else {
         throw new IllegalArgumentException("choice setting needs at least one option");
      }
   }

   public int index() {
      return this.get();
   }

   public String selectedLabel() {
      return this.options.get(this.get());
   }

   public List<String> getOptions() {
      return this.options;
   }

   @Override
   public String format() {
      return this.selectedLabel();
   }

   protected Integer parse(String text) {
      try {
         return Integer.parseInt(text);
      } catch (NumberFormatException var4) {
         for (int i = 0; i < this.options.size(); i++) {
            if (this.options.get(i).equalsIgnoreCase(text)) {
               return i;
            }
         }

         throw new IllegalArgumentException("unknown option: " + text);
      }
   }

   protected Integer requireValid(Integer value) {
      super.requireValid(value);
      if (this.options == null || value >= 0 && value < this.options.size()) {
         return value;
      } else {
         throw new IllegalArgumentException("choice index is out of range");
      }
   }
}
