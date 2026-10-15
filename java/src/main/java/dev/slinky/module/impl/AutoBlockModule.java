package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import org.lwjgl.input.Mouse;

public final class AutoBlockModule extends EvidenceModule {
   private boolean blocking;
   private boolean manualBlock;
   private EntityPlayer animationPlayer;
   private int blockStartTick = -1;
   private int lastSelfHurtTime;
   private volatile boolean lagging;
   private int lagStartTick = -1;
   private int tickCounter;

   public AutoBlockModule(ModuleManager modules) {
      super("autoblock", "Auto Block", ModuleCategory.COMBAT);
   }

   private static int ticks(float value) {
      return value <= 0.0F ? 0 : (int)Math.ceil((double)value);
   }

   private static boolean holdingSword(ClientContext c) {
      return c.player() != null && ActionAccess.canBlock(c.player().getHeldItem());
   }

   private EntityPlayer target() {
      EntityPlayer target = GameHooks.target();
      float range = this.number("range");
      return target != null && GameHooks.targetDistanceSquared() <= (double)(range * range) ? target : null;
   }

   @Override
   public boolean listens(int type) {
      return type == 3 || type == 5 || type == 14 || type == 23 || type == 26;
   }

   @Override
   protected void onEnable(ClientContext c) {
      this.tickCounter = 0;
      this.resetState(c, false);
   }

   @Override
   protected void onDisable(ClientContext c) {
      this.resetState(c, true);
   }

   @Override
   public void onWorldUnload(ClientContext c) {
      this.resetState(c, false);
   }

   public boolean swallowMouseEvent(ClientContext c) {
      return this.isEnabled() && Mouse.getEventButton() == 1 && c.ready() && c.minecraft().currentScreen == null && holdingSword(c);
   }

   @Override
   public void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 3) {
         if (this.lagging) {
            e.dispatchMode = 1;
         }
      } else if (e.type == 23) {
         if (this.lagging
            && this.flag("lag_flush_attack")
            && e.packet instanceof C02PacketUseEntity
            && ((C02PacketUseEntity)e.packet).getAction() == Action.ATTACK) {
            this.releaseLag();
            if (this.flag("lag_reblock") && holdingSword(c)) {
               this.startBlocking(c, this.tickCounter);
            }
         }
      } else if (e.type == 14) {
         if (!e.active && this.shouldBlockVanillaUse(c)) {
            e.cancelled = true;
         }
      } else {
         if (e.type == 26 || e.type == 5 && e.module == this && !e.enabled) {
            this.resetState(c, true);
         }
      }
   }

   @Override
   public void onRenderTickStart(ClientContext c) {
      if (!c.ready() || c.player().isDead || c.minecraft().currentScreen != null) {
         this.resetState(c, true);
         return;
      }

      boolean show = this.flag("fake_block_animation") && holdingSword(c) && (this.blocking || this.lagging);
      if (show && this.flag("fake_block_animation_require_target")) {
         show = this.target() != null;
      }

      this.setItemInUse(c, show);
   }

   @Override
   public void onClientTickStart(ClientContext c) {
      if (c.ready() && !c.player().isDead && c.minecraft().currentScreen == null) {
         int selfHurtTime = c.player().hurtTime;
         boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
         this.lastSelfHurtTime = selfHurtTime;
         if (!holdingSword(c)) {
            this.resetState(c, false);
         } else {
            int currentTick = ++this.tickCounter;
            boolean rightDown = Mouse.isButtonDown(1);
            boolean leftDown = Mouse.isButtonDown(0);
            boolean conditionsMet = this.target() != null && (!this.flag("require_left_mouse_pressed") || leftDown)
               && (!this.flag("require_right_mouse_pressed") || rightDown);
            if (!leftDown && !conditionsMet) {
               if (!rightDown) {
                  this.resetState(c, true);
               } else {
                  if (this.lagging) {
                     this.releaseLag();
                  }

                  if (!this.blocking) {
                     this.startBlocking(c, currentTick);
                  }

                  this.manualBlock = true;
               }
            } else {
               if (this.manualBlock) {
                  this.stopBlocking(c, true);
                  this.manualBlock = false;
               }

               if (this.lagging) {
                  int lagTicks = ticks(this.number("lag_duration"));
                  boolean lagExpired = lagTicks > 0 && this.lagStartTick >= 0 && currentTick - this.lagStartTick >= lagTicks;
                  if (lagExpired || !conditionsMet) {
                     this.releaseLag();
                     if (lagExpired && this.flag("lag_reblock") && conditionsMet) {
                        this.startBlocking(c, currentTick);
                     }
                  }
               }

               if (!conditionsMet) {
                  this.stopBlocking(c, true);
               } else {
                  if (!this.blocking && !this.lagging && (!this.flag("require_damage") || this.shouldPredictiveBlock(c))) {
                     this.startBlocking(c, currentTick);
                  }

                  if (this.blocking) {
                     int holdTicks = ticks(this.number("max_hold_time"));
                     boolean shouldStop = holdTicks > 0 && this.blockStartTick >= 0 && currentTick - this.blockStartTick >= holdTicks;
                     if (this.flag("require_damage") && hurtAgain) {
                        shouldStop = true;
                     }

                     if (shouldStop) {
                        if (this.shouldStartLag()) {
                           this.startLag(currentTick);
                        }

                        this.stopBlocking(c, true);
                     }
                  }
               }
            }
         }
      } else {
         this.resetState(c, true);
      }
   }

   private boolean shouldPredictiveBlock(ClientContext c) {
      int trigger = Math.max(1, Math.min(10, Math.round(this.number("max_hurt_time"))));
      return c.player().hurtTime == trigger;
   }

   private boolean shouldBlockVanillaUse(ClientContext c) {
      return this.isEnabled() && this.lagging && c.ready() && holdingSword(c) && c.minecraft().currentScreen == null;
   }

   private void startBlocking(ClientContext c, int currentTick) {
      if (holdingSword(c)) {
         int key = c.minecraft().gameSettings.keyBindUseItem.getKeyCode();
         KeyBinding.setKeyBindState(key, true);
         KeyBinding.onTick(key);
         this.blocking = true;
         this.blockStartTick = currentTick;
      }
   }

   private void stopBlocking(ClientContext c, boolean force) {
      if (this.blocking || force) {
         if (c.minecraft() != null && c.minecraft().gameSettings != null) {
            KeyBinding.setKeyBindState(c.minecraft().gameSettings.keyBindUseItem.getKeyCode(), false);
         }

         this.blocking = false;
         this.blockStartTick = -1;
      }
   }

   private boolean shouldStartLag() {
      float chance = this.number("lag_chance");
      return chance <= 0.0F ? false : chance >= 1.0F || RecoveredRandom.SHARED.chance(chance);
   }

   private void startLag(int currentTick) {
      if (!this.lagging) {
         this.lagging = true;
         this.lagStartTick = currentTick;
      }
   }

   private void releaseLag() {
      this.lagging = false;
      this.lagStartTick = -1;
   }

   public boolean isActive() {
      return this.isEnabled() && (this.blocking || this.lagging);
   }

   private void setItemInUse(ClientContext c, boolean show) {
      EntityPlayer player = c.player();
      if (this.animationPlayer != null && (this.animationPlayer != player || !show || player.isUsingItem())) {
         if (!this.animationPlayer.isUsingItem()) {
            ActionAccess.useCount(this.animationPlayer, 0);
         }

         this.animationPlayer = null;
      }

      // The fake animation must not replace a real item's remaining use duration.
      if (show && player != null && !player.isUsingItem()) {
         ActionAccess.useCount(player, 72000);
         this.animationPlayer = player;
      }
   }

   private void resetState(ClientContext c, boolean releaseUseKey) {
      this.releaseLag();
      this.stopBlocking(c, releaseUseKey);
      this.manualBlock = false;
      this.setItemInUse(c, false);
      this.lastSelfHurtTime = 0;
      if (releaseUseKey && c.minecraft() != null && c.minecraft().gameSettings != null && c.inputReady() && !c.player().isDead
         && Mouse.isCreated() && Mouse.isButtonDown(1) && c.minecraft().currentScreen == null) {
         KeyBinding.setKeyBindState(c.minecraft().gameSettings.keyBindUseItem.getKeyCode(), true);
      }
   }
}
