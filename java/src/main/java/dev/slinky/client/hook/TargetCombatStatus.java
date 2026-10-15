package dev.slinky.client.hook;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.AimAssistModule;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class TargetCombatStatus {
   private boolean valid;
   private boolean reducing;
   private EntityPlayer target;
   private double distanceSquared;

   public strictfp EntityPlayer target() {
      return this.target;
   }

   public strictfp double distanceSquared() {
      return this.distanceSquared;
   }

   public strictfp void select(ClientContext c, ModuleManager modules) {
      this.valid = false;
      if (!c.ready()) {
         this.target = null;
      } else {
         Vec3 eye = new Vec3(c.player().posX, c.player().posY + 1.62, c.player().posZ);
         EntityPlayer chosen = null;
         MovingObjectPosition hit = c.minecraft().objectMouseOver;
         if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY && hit.entityHit instanceof EntityOtherPlayerMP) {
            EntityPlayer p = (EntityPlayer)hit.entityHit;
            if (RelationshipService.hostile(p) && !(0.0F >= p.getHealth())) {
               chosen = p;
            }
         }

         if (chosen == null) {
            Module aim = modules.find("aimassist");
            if (aim instanceof AimAssistModule) {
               chosen = ((AimAssistModule)aim).currentTarget();
            }
         }

         if (chosen != null) {
            this.target = chosen;
            this.distanceSquared = boxDistance(eye, chosen);
         } else {
            double best = 225.0;

            for (EntityPlayer p : c.world().playerEntities) {
               if (p != c.player() && RelationshipService.hostile(p) && !(0.0F >= p.getHealth()) && !p.isPlayerSleeping()) {
                  double distance = boxDistance(eye, p);
                  if (!(distance > best)) {
                     float[] angle = RotationMath.angles(eye, new Vec3(p.posX, p.posY, p.posZ));
                     float yaw = RotationMath.wrap(angle[0] - c.player().rotationYaw);
                     float pitch = angle[1] - c.player().rotationPitch;
                     float metric = yaw * yaw;
                     metric += pitch;
                     metric += pitch;
                     if (!(metric > 4900.0F)) {
                        best = distance;
                        chosen = p;
                     }
                  }
               }
            }

            this.target = chosen;
            if (chosen != null) {
               this.distanceSquared = best;
            }
         }
      }
   }

   private static strictfp double boxDistance(Vec3 eye, Entity entity) {
      AxisAlignedBB box = entity.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
      double x = RotationMath.clamp(box.minX, box.maxX, eye.xCoord) - eye.xCoord;
      double y = RotationMath.clamp(box.minY, box.maxY, eye.yCoord) - eye.yCoord;
      double z = RotationMath.clamp(box.minZ, box.maxZ, eye.zCoord) - eye.zCoord;
      return z * z + (x * x + y * y);
   }

   public strictfp void clear() {
      this.valid = false;
      this.target = null;
      this.distanceSquared = 0.0;
   }

   public strictfp void reset() {
      this.valid = false;
   }

   public strictfp void prepare(ClientContext context) {
      if (!this.valid) {
         MovingObjectPosition hit = context.minecraft().objectMouseOver;
         if (context.ready() && hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
            this.sample(
               true,
               new Vec3(context.player().posX, context.player().posY + 1.62, context.player().posZ),
               hit.hitVec,
               context.player().motionX,
               context.player().motionY,
               context.player().motionZ,
               context.player().rotationYaw,
               context.player().moveForward,
               context.player().moveStrafing,
               context.player().onGround ? context.player().getAIMoveSpeed() : 0.026F
            );
         } else {
            this.sample(false, null, null, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 0.0F);
         }
      }
   }

   public strictfp void sample(boolean entityHit, Vec3 eye, Vec3 hit, double x, double y, double z, float yaw, float forward, float sideways, float speed) {
      if (!this.valid) {
         this.reducing = true;
         if (entityHit) {
            double[] movement = RotationMath.movement(yaw, forward, sideways, speed);
            double normalX = x + movement[0];
            double normalY = y + 0.0;
            double normalZ = movement[1] + z;
            double reducedX = x * 0.6 + movement[0];
            double reducedY = y + 0.0;
            double reducedZ = movement[1] + 0.6 * z;
            double reducedDistance = squared(eye.xCoord + reducedX, eye.yCoord + reducedY, reducedZ + eye.zCoord, hit);
            double normalDistance = squared(eye.xCoord + normalX, eye.yCoord + normalY, normalZ + eye.zCoord, hit);
            this.reducing = normalDistance > reducedDistance;
         }

         this.valid = true;
      }
   }

   private static strictfp double squared(double x, double y, double z, Vec3 hit) {
      x -= hit.xCoord;
      y -= hit.yCoord;
      z -= hit.zCoord;
      return z * z + (x * x + y * y);
   }

   public strictfp SemanticEvent event(int type, boolean use) {
      if (!this.valid && !use) {
         throw new IllegalStateException("Attack status must be prepared before dispatch");
      } else {
         SemanticEvent event = new SemanticEvent(type);
         event.active = !use;
         event.reducing = this.reducing;
         return event;
      }
   }
}
