package dev.slinky.client.hook;

public final strictfp class UiFrameDriver {
   public static final double INTERVAL = Double.longBitsToDouble(4575957461383581969L);
   static final float MAX_ELAPSED = 0.1F;
   private double previous;
   private double previousScale = Double.NaN;
   private int resizeFrames;
   private boolean visible;
   private boolean previouslyChanging;

   public UiFrameDriver(double previous, int resizeFrames, boolean visible, boolean previouslyChanging) {
      this.previous = previous;
      this.resizeFrames = resizeFrames;
      this.visible = visible;
      this.previouslyChanging = previouslyChanging;
   }

   public strictfp void frame(UiFrameDriver.Surface surface, int width, int height, double scale, double now) {
      if (!Double.isFinite(now)) {
         return;
      }

      if (width <= 0 || height <= 0) {
         this.previous = now;
         this.previousScale = Double.NaN;
         this.visible = false;
         this.previouslyChanging = true;
         return;
      }

      boolean resetClock = this.previous < 0.0 || !Double.isFinite(this.previous) || now < this.previous;
      if (resetClock) {
         this.previous = now;
      }

      boolean resized = surface.resize(width, height);
      if (resized) {
         this.resizeFrames = 10;
      } else if (this.resizeFrames > 0) {
         this.resizeFrames--;
      }

      double canvasScale = Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
      boolean scaleChanged = Double.compare(this.previousScale, canvasScale) != 0;
      this.previousScale = canvasScale;
      surface.scale(canvasScale);
      double elapsed = now - this.previous;
      if (resetClock || resized || scaleChanged || elapsed >= INTERVAL) {
         float frameElapsed = (float)Math.min((double)MAX_ELAPSED, Math.max(0.0, elapsed));
         int flags = surface.update(frameElapsed);
         boolean wasVisible = this.visible;
         this.visible = (flags & 1) != 0;
         boolean changing = (flags & 2) != 0;
         if (this.visible && (!wasVisible || resetClock || resized || scaleChanged || changing || this.previouslyChanging || this.resizeFrames > 0)) {
            surface.begin(now);

            try {
               surface.draw(frameElapsed);
            } finally {
               surface.end();
            }
         }

         this.previouslyChanging = changing;
         this.previous = now;
      }

      if (this.visible) {
         surface.present(now);
      } else {
         this.previouslyChanging = true;
      }
   }

   public strictfp double previousSeconds() {
      return this.previous;
   }

   public strictfp int resizeFrames() {
      return this.resizeFrames;
   }

   public strictfp boolean visible() {
      return this.visible;
   }

   public strictfp boolean previouslyChanging() {
      return this.previouslyChanging;
   }

   public interface Surface {
      boolean resize(int var1, int var2);

      void scale(double var1);

      int update(float var1);

      void begin(double var1);

      void draw(float var1);

      void end();

      void present(double var1);
   }
}
