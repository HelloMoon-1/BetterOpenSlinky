package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import org.lwjgl.input.Mouse;

public final strictfp class TimerModule extends EvidenceModule {
   public TimerModule() {
      super("timer", "Timer", ModuleCategory.UTILITY);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 1;
   }

   public static strictfp float select(
      float speed,
      boolean requireMouse,
      boolean pressed,
      boolean requireFalling,
      boolean onGround,
      double motionY,
      boolean requireDamage,
      boolean ready,
      int hurtTime
   ) {
      if (requireMouse && !pressed) {
         return 1.0F;
      } else if (!requireFalling || !onGround && !(motionY >= 0.0)) {
         return !requireDamage || ready && hurtTime != 0 ? speed : 1.0F;
      } else {
         return 1.0F;
      }
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      boolean ready = context.ready();
      boolean pressed = !this.flag("require_mouse") || Mouse.isButtonDown(context.minecraft().gameSettings.keyBindAttack.getKeyCode() == -99 ? 1 : 0);
      float selected = select(
         this.number("speed"),
         this.flag("require_mouse"),
         pressed,
         this.flag("require_falling"),
         !ready || context.player().onGround,
         ready ? context.player().motionY : 0.0,
         this.flag("require_damage"),
         context.inputReady(),
         ready ? context.player().hurtTime : 0
      );
      MinecraftFields.timer(context.minecraft()).timerSpeed = selected;
   }

   @Override
   protected strictfp void onDisable(ClientContext context) {
      this.reset(context);
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.reset(context);
   }

   private strictfp void reset(ClientContext context) {
      MinecraftFields.timer(context.minecraft()).timerSpeed = 1.0F;
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("speed").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("speed"));
   }
}
