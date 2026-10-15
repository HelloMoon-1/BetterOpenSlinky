package dev.slinky.client.render;

public final class HoldActivation {
   public static final double ACTION_SECONDS = 1.0;
   private Object identity;
   private double start;
   private boolean fired;
   private boolean seen;
   private float progress;

   public void beginFrame() {
      this.seen = false;
   }

   public boolean update(Object target, boolean hovering, boolean down, boolean available, boolean canStart, double now) {
      if (this.identity != null && this.identity.equals(target)) {
         this.seen = true;
      }

      if (available && hovering && down) {
         if (this.identity == null || !this.identity.equals(target)) {
            if (!canStart) {
               return false;
            }

            this.identity = target;
            this.start = now;
            this.fired = false;
         }

         this.seen = true;
         this.progress = (float)Math.max(0.0, Math.min(1.0, (now - this.start) / 1.0));
         if (!(this.progress < 1.0F) && !this.fired) {
            this.fired = true;
            return true;
         } else {
            return false;
         }
      } else {
         if (this.identity != null && this.identity.equals(target)) {
            this.reset();
         }

         return false;
      }
   }

   public float progress(Object target) {
      return this.identity != null && this.identity.equals(target) ? this.progress : 0.0F;
   }

   public void endFrame() {
      if (!this.seen) {
         this.reset();
      }
   }

   public void reset() {
      this.identity = null;
      this.progress = 0.0F;
      this.fired = false;
      this.seen = false;
   }
}
