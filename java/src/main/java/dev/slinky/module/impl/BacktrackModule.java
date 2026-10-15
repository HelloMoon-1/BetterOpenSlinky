package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.packet.PacketAccess;
import dev.slinky.client.packet.PredictionGeometry;
import dev.slinky.client.packet.RemoteBoxRenderer;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.util.Vec3;

public final strictfp class BacktrackModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private boolean active;
   private boolean disableOnHit;
   private boolean absolute;
   private boolean hasYaw;
   private boolean render;
   private int targetId;
   private int selfId;
   private int cooldown;
   private int delayedSequence;
   private int releasedSequence;
   private int teleportSequence;
   private int interpolation;
   private long delay;
   private Vec3 offset = zero();
   private Vec3 previous = zero();
   private Vec3 current = zero();
   private Vec3 desired = zero();
   private float delayedYaw;
   private float previousYaw;
   private float currentYaw;
   private float desiredYaw;

   public BacktrackModule(WeaponsModule weapons) {
      super("backtrack", "Backtrack", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 2 || t == 6 || t == 18;
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 2) {
         this.packet(e);
      } else if (e.type == 6) {
         if (this.render) {
            RemoteBoxRenderer.draw(
               e,
               this.previous,
               this.current,
               this.previousYaw,
               this.currentYaw,
               this.flag("indicator_rotate"),
               this.flag("indicator_filled"),
               this.number("indicator_line_width"),
               this.color("indicator_color")
            );
         }
      } else if (this.cooldown <= 0 || --this.cooldown == 0) {
         EntityPlayer target = GameHooks.target();
         float low = (float)this.range("target_dist").lower;
         float high = (float)this.range("target_dist").upper;
         boolean allowed = c.inputReady()
            && (!this.flag("require_mouse_pressed") || AttackInput.pressed(c))
            && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
            && c.player().ticksExisted - c.player().getLastAttackerTime() <= 20
            && target != null
            && !((float)target.hurtTime > this.number("max_hurt_time"))
            && !((double)(low * low) > GameHooks.targetDistanceSquared())
            && !(GameHooks.targetDistanceSquared() > (double)(high * high));
         if (!allowed) {
            if (this.active) {
               this.cooldown = RecoveredMath.truncateInt(this.number("cooldown"));
            }

            this.active = this.render = false;
         } else {
            Vec3 self = TimingAccess.position(c.player());
            Vec3 server = TimingAccess.server(target);
            int oldId = this.targetId;
            this.delay = RecoveredMath.truncateLong(this.number("delay"));
            this.targetId = target.getEntityId();
            this.selfId = c.player().getEntityId();
            this.disableOnHit = this.flag("disable_on_hit");
            if (!this.active) {
               if (!(0.01 < RotationMath.distance(self, server) - RotationMath.distance(self, TimingAccess.position(target)))) {
                  this.render = false;
               } else {
                  this.active = true;
                  this.offset = zero();
                  this.absolute = this.hasYaw = false;
                  this.previous = this.current = this.desired = TimingAccess.position(target);
                  this.previousYaw = this.currentYaw = this.desiredYaw = target.rotationYaw;
                  this.render = this.flag("render_remote_pos");
               }
            } else {
               Vec3 candidate = this.absolute ? this.offset : this.offset.add(server);
               if (!(-0.05 > metric(self, server, candidate)) && oldId == this.targetId) {
                  this.updatePosition(candidate, this.hasYaw ? this.delayedYaw : target.rotationYaw);
               } else {
                  this.offset = zero();
                  this.absolute = this.hasYaw = false;
                  this.active = this.render = false;
                  this.cooldown = RecoveredMath.truncateInt(this.number("cooldown"));
               }
            }
         }
      }
   }

   public static strictfp double metric(Vec3 self, Vec3 server, Vec3 delayed) {
      return RotationMath.distance(self, delayed) - RotationMath.distance(self, server);
   }

   private strictfp void packet(SemanticEvent e) {
      if (this.active) {
         if (e.packet instanceof S19PacketEntityStatus) {
            if (PacketAccess.statusId((S19PacketEntityStatus)e.packet) == this.targetId) {
               e.dispatchMode = 3;
               return;
            }
         } else if (e.packet instanceof S06PacketUpdateHealth || e.packet instanceof S29PacketSoundEffect) {
            e.dispatchMode = 3;
            return;
         }

         if (e.packet instanceof S12PacketEntityVelocity) {
            if (this.disableOnHit && ((S12PacketEntityVelocity)e.packet).getEntityID() == this.selfId) {
               this.active = false;
               return;
            }
         } else if (e.packet instanceof S08PacketPlayerPosLook) {
            this.active = false;
            return;
         }

         if (e.packetAgeMillis <= this.delay) {
            e.dispatchMode = 1;
            if (e.packetSequence > this.delayedSequence) {
               this.delayedSequence = e.packetSequence;
               this.publish(e, false);
            }
         } else if (e.dispatchMode == 0 && e.packetSequence > this.releasedSequence) {
            this.publish(e, true);
            this.releasedSequence = e.packetSequence;
         }
      }
   }

   private strictfp void publish(SemanticEvent e, boolean reconcile) {
      if (!reconcile || this.teleportSequence <= e.packetSequence) {
         boolean relative = e.packet instanceof S14PacketEntity;
         if (relative || e.packet instanceof S18PacketEntityTeleport) {
            S14PacketEntity move = relative ? (S14PacketEntity)e.packet : null;
            S18PacketEntityTeleport teleport = relative ? null : (S18PacketEntityTeleport)e.packet;
            if ((relative ? PacketAccess.relativeId(move) : teleport.getEntityId()) == this.targetId) {
               Vec3 value = relative
                  ? new Vec3((double)move.func_149062_c() * 0.03125, (double)move.func_149061_d() * 0.03125, (double)move.func_149064_e() * 0.03125)
                  : new Vec3((double)teleport.getX() * 0.03125, (double)teleport.getY() * 0.03125, (double)teleport.getZ() * 0.03125);
               if (reconcile) {
                  this.offset = this.offset.subtract(value);
                  if (!relative) {
                     this.absolute = false;
                  }
               } else {
                  if (relative) {
                     this.offset = this.offset.add(value);
                     if (!move.func_149060_h()) {
                        return;
                     }
                  } else {
                     this.offset = value;
                     this.absolute = true;
                  }

                  this.delayedYaw = (float)(relative ? move.func_149066_f() : teleport.getYaw()) * 360.0F * 0.00390625F;
                  this.hasYaw = true;
                  if (!relative) {
                     this.teleportSequence = e.packetSequence;
                  }
               }
            }
         }
      }
   }

   private strictfp void updatePosition(Vec3 position, float yaw) {
      if (!sameBits(this.desired, position) || yaw != this.desiredYaw) {
         this.interpolation = 3;
      }

      this.desired = position;
      this.previous = this.current;
      this.previousYaw = this.currentYaw;
      this.desiredYaw = yaw;
      if (this.interpolation < 1) {
         this.current = position;
      } else {
         double fraction = 1.0 / (double)this.interpolation;
         this.current = this.current.add(PredictionGeometry.scale(position.subtract(this.current), fraction));
         yaw = (float)((double)RotationMath.wrap(this.desiredYaw - this.currentYaw) * fraction) + this.currentYaw;
         this.interpolation--;
      }

      this.currentYaw = yaw;
   }

   private static strictfp boolean sameBits(Vec3 a, Vec3 b) {
      return Double.doubleToRawLongBits(a.xCoord) == Double.doubleToRawLongBits(b.xCoord)
         && Double.doubleToRawLongBits(a.yCoord) == Double.doubleToRawLongBits(b.yCoord)
         && Double.doubleToRawLongBits(a.zCoord) == Double.doubleToRawLongBits(b.zCoord);
   }

   private static strictfp Vec3 zero() {
      return new Vec3(0.0, 0.0, 0.0);
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.clear();
   }

   private strictfp void clear() {
      this.active = this.disableOnHit = this.absolute = this.hasYaw = this.render = false;
      this.targetId = this.selfId = this.cooldown = this.delayedSequence = this.releasedSequence = this.teleportSequence = this.interpolation = 0;
      this.delay = 0L;
      this.offset = this.previous = this.current = this.desired = zero();
      this.delayedYaw = this.previousYaw = this.currentYaw = this.desiredYaw = 0.0F;
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("delay").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("delay"));
   }
}
