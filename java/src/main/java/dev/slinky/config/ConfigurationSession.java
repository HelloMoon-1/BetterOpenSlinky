package dev.slinky.config;

public final class ConfigurationSession {
   private volatile String currentProfile;
   private volatile boolean pending;

   public String currentProfile() {
      return this.currentProfile;
   }

   public boolean pending() {
      return this.pending;
   }

   public void controlChanged() {
      this.pending = true;
   }

   public void profileApplied(String name) {
      this.currentProfile = name;
      this.pending = false;
   }
}
