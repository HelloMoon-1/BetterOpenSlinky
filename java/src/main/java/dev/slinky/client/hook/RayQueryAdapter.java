package dev.slinky.client.hook;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.GhostHandModule;
import dev.slinky.module.impl.PiercingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class RayQueryAdapter {
   private RayQueryAdapter() {
   }

   public static strictfp boolean update(ModuleManager modules, SemanticEvent query) {
      if (modules != null && query.queryOverride && modules.context().ready()) {
         ClientContext context = modules.context();
         Minecraft mc = context.minecraft();
         Entity viewer = mc.getRenderViewEntity();
         if (viewer != context.player()) {
            return false;
         } else {
            PiercingModule piercing = (PiercingModule)modules.find("piercing");
            GhostHandModule ghost = (GhostHandModule)modules.find("ghosthand");
            boolean throughBlocks = piercing != null && piercing.pierceBlocks(context);
            double entityDistance = query.distance;
            double rayLength = entityDistance + 1.5;
            MovingObjectPosition block = viewer.rayTrace(rayLength, 1.0F);
            Vec3 origin = viewer.getPositionEyes(1.0F);
            if (!throughBlocks && block != null) {
               rayLength = origin.distanceTo(block.hitVec);
            }

            Vec3 direction = direction(viewer.rotationYaw, viewer.rotationPitch);
            Vec3 end = origin.addVector(direction.xCoord * rayLength, direction.yCoord * rayLength, direction.zCoord * rayLength);
            Entity selected = null;
            Vec3 selectedPoint = null;
            double selectedDistance = entityDistance;
            boolean insideSeen = false;
            boolean allSelectedAllowThrough = block != null && block.typeOfHit == MovingObjectType.BLOCK;

            for (Object item : context.world().loadedEntityList) {
               Entity candidate = (Entity)item;
               if (candidate != viewer && candidate.canBeCollidedWith()) {
                  AxisAlignedBB bounds = candidate.getEntityBoundingBox().expand(query.expand, query.expand, query.expand);
                  MovingObjectPosition hit = bounds.calculateIntercept(origin, end);
                  if (bounds.isVecInside(origin)) {
                     insideSeen = true;
                     if (!(selectedDistance < 0.0)) {
                        int rank = selected != null && piercing != null ? piercing.rank(context, selected, candidate, true) : 2;
                        if (rank != 1) {
                           selected = candidate;
                           selectedPoint = hit == null ? origin : hit.hitVec;
                           selectedDistance = 0.0;
                        }
                     }
                  } else {
                     if (hit == null) {
                        continue;
                     }

                     double distance = origin.distanceTo(hit.hitVec);
                     int rank = piercing == null ? 2 : piercing.rank(context, selected, candidate, insideSeen);
                     if (choose(rank, entityDistance, selectedDistance, distance)) {
                        selected = candidate;
                        selectedPoint = hit.hitVec;
                        selectedDistance = distance;
                     }
                  }

                  allSelectedAllowThrough = allSelectedAllowThrough && ghost != null && ghost.canInteractThrough(context, selected);
               }
            }

            mc.pointedEntity = null;
            mc.objectMouseOver = block;
            if (allSelectedAllowThrough) {
               return true;
            } else {
               if (selected != null && (block == null || !(selectedDistance > rayLength) || throughBlocks)) {
                  AxisAlignedBB original = selected.getEntityBoundingBox().expand(0.1F, 0.1F, 0.1F);
                  Vec3 point = new Vec3(
                     clamp(selectedPoint.xCoord, original.minX, original.maxX),
                     clamp(selectedPoint.yCoord, original.minY, original.maxY),
                     clamp(selectedPoint.zCoord, original.minZ, original.maxZ)
                  );
                  mc.objectMouseOver = new MovingObjectPosition(selected, point);
                  if (selected instanceof EntityLivingBase || selected instanceof EntityItemFrame) {
                     mc.pointedEntity = selected;
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static strictfp boolean choose(int rank, double reach, double previous, double candidate) {
      return rank == 2 ? previous == 0.0 || previous > candidate : rank == 0 && reach > candidate;
   }

   private static strictfp double clamp(double value, double low, double high) {
      return value < low ? low : (value > high ? high : value);
   }

   public static strictfp Vec3 direction(float yaw, float pitch) {
      float yawRadians = (float)((double)yaw * -Math.PI / 180.0 + -Math.PI);
      float pitchRadians = (float)((double)pitch * -Math.PI / 180.0);
      float negativeCosine = -RotationMath.cosine(pitchRadians);
      float x = RotationMath.sine(yawRadians) * negativeCosine;
      float z = negativeCosine * RotationMath.cosine(yawRadians);
      return new Vec3((double)x, (double)RotationMath.sine(pitchRadians), (double)z);
   }
}
