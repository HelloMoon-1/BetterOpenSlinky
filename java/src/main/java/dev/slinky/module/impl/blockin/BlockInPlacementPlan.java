package dev.slinky.module.impl.blockin;

import dev.slinky.client.ClientContext;
import dev.slinky.client.rotation.RotationMath;
import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Bounded, live-world enclosure plan. Donor adaptation, NOT a wholesale port:
 * Vape's blatant/BlockIn.resolvePlaceTarget (1359-1387) validates support,
 * replaceability and entity clearance; utility/clutch/ClutchPlacementPathUtils
 * (135-189) samples visible support-face points and ranks by rotation distance.
 * These rules are expressed here against vanilla 1.8.9 types. Slinky's enclosure
 * destinations are retained instead of Vape's predictive falling/clutch paths.
 * Movement simulation, silent controllers, mouse locks and packet rejection
 * handling are intentionally not imported. Never assume a simulated block exists.
 */
public final class BlockInPlacementPlan {
   private static final EnumFacing[] HORIZONTAL = {
      EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST
   };
   private static final double[] SAMPLES = {0.5, 0.2, 0.8};
   private final BlockPos anchor;
   private final BlockPos[] destinations = new BlockPos[17];
   private final int[] failures = new int[17];

   public BlockInPlacementPlan(ClientContext c) {
      this.anchor = new BlockPos(c.player().posX, c.player().posY, c.player().posZ);
      // Build a support ring only when needed, then the feet/head walls. Once
      // possible, close the roof before adding any optional upper support ring.
      int index = 0;
      for (int y = -1; y <= 1; y++) {
         for (EnumFacing direction : HORIZONTAL) {
            this.destinations[index++] = this.anchor.add(direction.getFrontOffsetX(), y, direction.getFrontOffsetZ());
         }
      }
      this.destinations[index++] = this.anchor.add(0, 2, 0);
      for (EnumFacing direction : HORIZONTAL) {
         this.destinations[index++] = this.anchor.add(direction.getFrontOffsetX(), 2, direction.getFrontOffsetZ());
      }
   }

   public boolean anchored(ClientContext c) {
      return this.anchor.equals(new BlockPos(c.player().posX, c.player().posY, c.player().posZ));
   }

   public boolean complete(ClientContext c) {
      // Optional foundation and upper supports are not completion requirements.
      // A chest/plant/liquid is not counted as an enclosure wall.
      for (int index = 4; index <= 12; index++) {
         BlockPos pos = this.destinations[index];
         if (!loaded(c, pos) || !block(c, pos).isFullCube()) {
            return false;
         }
      }
      return true;
   }

   private static boolean loaded(ClientContext c, BlockPos pos) {
      return pos.getY() >= 0 && pos.getY() < 256 && c.world().isBlockLoaded(pos);
   }

   private static Block block(ClientContext c, BlockPos pos) {
      return c.world().getBlockState(pos).getBlock();
   }

   private static AxisAlignedBB cube(BlockPos pos) {
      return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
   }

   private static boolean space(ClientContext c, BlockPos pos) {
      if (!loaded(c, pos)) {
         return false;
      }
      Block existing = block(c, pos);
      // Use vanilla replaceability, but never displace liquid as a side effect.
      return !existing.getMaterial().isLiquid() && existing.isReplaceable(c.world(), pos)
         && !c.player().getEntityBoundingBox().intersectsWith(cube(pos))
         && c.world().checkNoEntityCollision(cube(pos));
   }

   private static boolean support(ClientContext c, BlockPos pos) {
      if (!loaded(c, pos)) {
         return false;
      }
      Block block = block(c, pos);
      if (!block.isFullCube()) {
         return false;
      }
      // Do not auto-open/activate a container or workstation. Sneaking would
      // require taking over manual input; simply choose another normal support.
      switch (Block.getIdFromBlock(block)) {
         case 23: case 25: case 54: case 58: case 61: case 62: case 84:
         case 116: case 117: case 130: case 137: case 138: case 146:
         case 149: case 150: case 151: case 154: case 158: case 178:
            return false;
         default:
            return true;
      }
   }

   public boolean valid(ClientContext c, Target target) {
      return this.anchored(c) && this.failures[target.index] < 3 && space(c, target.destination)
         && support(c, target.support) && target.support.offset(target.face).equals(target.destination);
   }

   /** Bound issued or locally invalid transactions; this is not an acknowledgement counter. */
   public void recordAttempt(Target target) {
      this.failures[target.index]++;
   }

   public Target find(ClientContext c) {
      for (int index = 0; index < this.destinations.length; index++) {
         BlockPos destination = this.destinations[index];
         if (this.failures[index] >= 3 || !space(c, destination)) {
            continue;
         }
         Target best = null;
         double bestCost = Double.POSITIVE_INFINITY;
         Vec3 eye = c.player().getPositionEyes(1.0F);
         double reach = c.minecraft().playerController.getBlockReachDistance();
         for (EnumFacing face : EnumFacing.values()) {
            BlockPos support = destination.offset(face.getOpposite());
            if (!support(c, support)) {
               continue;
            }
            // Fixed 3x3 samples on each full-cube face: finite work, no random
            // jitter, no ray through an occluding block, no out-of-reach hits.
            for (double u : SAMPLES) {
               for (double v : SAMPLES) {
                  Vec3 point = point(support, face, u, v);
                  Vec3 inside = new Vec3(point.xCoord - face.getFrontOffsetX() * 0.001,
                     point.yCoord - face.getFrontOffsetY() * 0.001, point.zCoord - face.getFrontOffsetZ() * 0.001);
                  MovingObjectPosition hit = c.world().rayTraceBlocks(eye, inside, false, true, false);
                  if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                     || !support.equals(hit.getBlockPos()) || hit.sideHit != face || hit.hitVec == null
                     || eye.squareDistanceTo(hit.hitVec) > reach * reach) {
                     continue;
                  }
                  float[] angles = RotationMath.angles(eye, hit.hitVec);
                  float yaw = RotationMath.wrap(angles[0] - c.player().rotationYaw);
                  float pitch = angles[1] - c.player().rotationPitch;
                  double cost = (double)yaw * yaw + (double)pitch * pitch;
                  if (cost < bestCost) {
                     bestCost = cost;
                     best = new Target(index, destination, support, face, hit.hitVec);
                  }
               }
            }
         }
         if (best != null) {
            return best;
         }
      }
      return null;
   }

   private static Vec3 point(BlockPos pos, EnumFacing face, double u, double v) {
      int dx = face.getFrontOffsetX();
      int dy = face.getFrontOffsetY();
      int dz = face.getFrontOffsetZ();
      if (dx != 0) {
         return new Vec3(pos.getX() + (dx > 0 ? 1.0 : 0.0), pos.getY() + u, pos.getZ() + v);
      }
      if (dy != 0) {
         return new Vec3(pos.getX() + u, pos.getY() + (dy > 0 ? 1.0 : 0.0), pos.getZ() + v);
      }
      return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + (dz > 0 ? 1.0 : 0.0));
   }

   public static final class Target {
      private final int index;
      public final BlockPos destination;
      public final BlockPos support;
      public final EnumFacing face;
      public final Vec3 point;

      private Target(int index, BlockPos destination, BlockPos support, EnumFacing face, Vec3 point) {
         this.index = index;
         this.destination = destination;
         this.support = support;
         this.face = face;
         this.point = point;
      }

      public boolean matches(MovingObjectPosition hit) {
         return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && this.support.equals(hit.getBlockPos()) && hit.sideHit == this.face && hit.hitVec != null
            && this.destination.equals(hit.getBlockPos().offset(hit.sideHit));
      }
   }
}
