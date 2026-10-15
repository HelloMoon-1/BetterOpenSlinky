package dev.slinky.module;

public enum ModuleCategory {
   COMBAT("Combat"),
   MOVEMENT("Movement"),
   RENDER("Render"),
   WORLD("World"),
   UTILITY("Utility"),
   UNKNOWN("Other");

   private final String displayName;

   private ModuleCategory(String displayName) {
      this.displayName = displayName;
   }

   public String getDisplayName() {
      return this.displayName;
   }
}
