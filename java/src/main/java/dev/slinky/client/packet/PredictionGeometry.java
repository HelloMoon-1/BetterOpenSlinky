package dev.slinky.client.packet;

import dev.slinky.client.rotation.RotationMath;
import net.minecraft.util.Vec3;

public final strictfp class PredictionGeometry {
   private PredictionGeometry() {
   }

   private static strictfp double f(double x) {
      return (double)((float)x);
   }

   private static strictfp double min(double x, double y) {
      return x < y ? x : y;
   }

   private static strictfp double max(double x, double y) {
      return x > y ? x : y;
   }

   private static strictfp double minRight(double x, double y) {
      return Double.isNaN(y) ? x : min(x, y);
   }

   private static strictfp double maxRight(double x, double y) {
      return Double.isNaN(y) ? x : max(x, y);
   }

   public static strictfp boolean ray(double[] box, Vec3 origin, Vec3 ray) {
      return ray(box, new double[]{origin.xCoord, origin.yCoord, origin.zCoord}, new double[]{ray.xCoord, ray.yCoord, ray.zCoord});
   }

   public static strictfp boolean ray(double[] box, double[] origin, double[] ray) {
      double x0 = f((box[0] - origin[0]) * (1.0 / ray[0]));
      double x1 = f((box[3] - origin[0]) * (1.0 / ray[0]));
      double enterX = f(min(max(x0, 0.0), max(x1, 0.0)));
      double exitX = f(max(min(x0, Double.POSITIVE_INFINITY), min(x1, Double.POSITIVE_INFINITY)));
      double y0 = f((box[1] - origin[1]) * (1.0 / ray[1]));
      double y1 = f((box[4] - origin[1]) * (1.0 / ray[1]));
      double enterY = f(min(max(y0, enterX), max(y1, enterX)));
      double exitY = f(max(min(y0, exitX), min(y1, exitX)));
      double z0 = f((box[2] - origin[2]) * (1.0 / ray[2]));
      double z1 = f((box[5] - origin[2]) * (1.0 / ray[2]));
      float entry = (float)minRight(maxRight(enterY, z1), maxRight(enterY, z0));
      float exit = (float)maxRight(minRight(exitY, z1), minRight(exitY, z0));
      return entry < exit && entry <= 1.0F;
   }

   public static strictfp Vec3 clamp(double[] box, Vec3 point) {
      return new Vec3(
         RotationMath.clamp(box[0], box[3], point.xCoord), RotationMath.clamp(box[1], box[4], point.yCoord), RotationMath.clamp(box[2], box[5], point.zCoord)
      );
   }

   public static strictfp Vec3 movement(float yaw, float forward, float lateral, float speed) {
      double[] xz = RotationMath.movement(yaw, forward, lateral, speed);
      return new Vec3(xz[0], 0.0, xz[1]);
   }

   public static strictfp Vec3 scale(Vec3 value, double factor) {
      return new Vec3(value.xCoord * factor, value.yCoord * factor, value.zCoord * factor);
   }
}
