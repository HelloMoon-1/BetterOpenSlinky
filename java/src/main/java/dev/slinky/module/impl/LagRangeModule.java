package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.packet.RemoteBoxRenderer;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public final strictfp class LagRangeModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private final RecoveredRandom.Smooth radius = new RecoveredRandom.Smooth();
   private boolean input;
   private boolean wasSprinting;
   private boolean active;
   private boolean approaching;
   private boolean flushAttack;
   private boolean flushSprint;
   private boolean flushMovement;
   private boolean render;
   private int potionCooldown;
   private int lastSequence;
   private long delay;
   private Vec3 targetPosition = zero();
   private Vec3 sent = zero();
   private Vec3 previous = zero();
   private Vec3 current = zero();

   public LagRangeModule(WeaponsModule weapons) {
      super("lagrange", "Lag Range", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 3 || t == 6 || t == 15 || t == 18;
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 3) {
         this.packet(e);
      } else if (e.type == 6) {
         if (this.render) {
            RemoteBoxRenderer.draw(
               e,
               this.previous,
               this.current,
               0.0F,
               0.0F,
               false,
               this.flag("indicator_filled"),
               this.number("indicator_line_width"),
               this.color("indicator_color")
            );
         }
      } else if (e.type == 15) {
         if (e.active && c.player() != null) {
            this.input = c.player().ticksExisted == c.player().getLastAttackerTime();
         }
      } else {
         boolean clicked = this.input;
         this.input = false;
         if (c.inputReady() && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))) {
            if (this.flag("flush_splash_potion")) {
               ItemStack stack = c.player().getHeldItem();
               if (stack != null && Item.getIdFromItem(stack.getItem()) == 373 && (stack.getItemDamage() & 16384) != 0) {
                  this.potionCooldown = 15;
                  this.reject();
                  return;
               }

               if (this.potionCooldown > 0) {
                  this.potionCooldown--;
                  this.reject();
                  return;
               }
            }

            boolean sprint = c.player().isSprinting();
            boolean stopped = this.wasSprinting && this.flag("flush_sprint_reset") && !sprint;
            this.wasSprinting = sprint;
            EntityPlayer target = GameHooks.target();
            if (target == null) {
               this.reject();
            } else {
               double distance = (double)this.radius.sample(10.0F, 15.0F, 10.0, RecoveredRandom.now(), RecoveredRandom.SHARED);
               if (GameHooks.targetDistanceSquared() > distance * distance) {
                  this.reject();
               } else {
                  this.delay = RecoveredMath.truncateLong(this.number("delay"));
                  this.flushAttack = clicked && target.hurtTime < 5;
                  this.flushSprint = stopped;
                  this.active = true;
                  this.targetPosition = TimingAccess.server(target);
                  this.previous = this.render ? this.current : this.sent;
                  this.current = this.sent;
                  this.render = this.flag("render_remote_pos") && c.minecraft().gameSettings.thirdPersonView != 0 && this.approaching;
               }
            }
         } else {
            this.reject();
         }
      }
   }

   private strictfp void packet(SemanticEvent e) {
      C03PacketPlayer movement = e.packet instanceof C03PacketPlayer ? (C03PacketPlayer)e.packet : null;
      if (e.packetSequence > this.lastSequence) {
         if (this.active && movement != null && movement.isMoving()) {
            Vec3 point = new Vec3(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
            this.approaching = TimingAccess.squared(this.targetPosition, this.sent) > TimingAccess.squared(this.targetPosition, point);
         }

         this.lastSequence = e.packetSequence;
      }

      if (this.active && this.approaching && !this.flushAttack && !this.flushMovement && !this.flushSprint && e.packetAgeMillis < this.delay) {
         e.dispatchMode = 1;
      } else if (e.dispatchMode == 0) {
         if (movement != null) {
            this.flushMovement = false;
            if (movement.isMoving()) {
               this.sent = new Vec3(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
            }
         } else if (e.packet instanceof C02PacketUseEntity) {
            if (this.flushAttack) {
               this.flushAttack = false;
               this.flushMovement = true;
            }
         } else if (e.packet instanceof C0BPacketEntityAction && this.flushSprint && ((C0BPacketEntityAction)e.packet).getAction().ordinal() == 4) {
            this.flushSprint = false;
            this.flushMovement = true;
         }
      }
   }

   private strictfp void reject() {
      this.flushMovement = this.flushAttack = this.flushSprint = this.active = this.render = false;
   }

   private static strictfp Vec3 zero() {
      return new Vec3(0.0, 0.0, 0.0);
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.clear();
   }

   private strictfp void clear() {
      this.reject();
      this.input = this.wasSprinting = this.approaching = false;
      this.potionCooldown = this.lastSequence = 0;
      this.delay = 0L;
      this.targetPosition = this.sent = this.previous = this.current = zero();
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("delay").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("delay"));
   }
}
