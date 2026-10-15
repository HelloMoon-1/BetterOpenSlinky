package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.BooleanSetting;
import java.util.Random;

/** The donor's local, embedded ClickGui console toggle and slide animation. */
public final class CommandLineModule extends Module {
   private static final int[] BACKGROUNDS = {0x32AA6B94, 0x327A9E86, 0x32101010, 0x32407294};
   private final BooleanSetting animate;
   private final Random random = new Random();
   private long animationStarted;
   private boolean closing;
   private boolean opened;
   private int lastBackground = -1;
   private int background;

   public CommandLineModule() {
      super("command_line", "Command Line", "Local command line embedded in the Raven ClickGui.", ModuleCategory.UTILITY);
      BooleanSetting setting = new BooleanSetting("animate", "Animate", true);
      setting.set(setting.get());
      setting.saveCurrent();
      this.animate = this.add(setting);
   }

   @Override
   public int descriptorFlags() {
      return 0;
   }

   @Override
   protected void onEnable(ClientContext context) {
      int next = this.random.nextInt(BACKGROUNDS.length);
      if (next == this.lastBackground) {
         next = (next + 1) % BACKGROUNDS.length;
      }
      this.background = BACKGROUNDS[next];
      this.lastBackground = next;
      this.opened = true;
      this.closing = false;
      this.animationStarted = System.currentTimeMillis();
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.closing = this.opened;
      this.animationStarted = System.currentTimeMillis();
   }

   public int background() {
      return this.background;
   }

   public int panelWidth() {
      if (!this.isEnabled() && !this.closing) {
         return 0;
      }
      double t = this.animate.isEnabled() ? Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - this.animationStarted) / 500.0)) : 1.0;
      int width = (int)Math.round((1.0 - Math.pow(1.0 - t, 5.0)) * 200.0);
      if (this.closing) {
         width = 200 - width;
         if (width == 0) {
            this.closing = false;
            this.opened = false;
         }
      }
      return width;
   }
}
