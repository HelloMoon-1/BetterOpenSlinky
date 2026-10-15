package dev.slinky.client.placement;

import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.rotation.RotationMath;
import net.minecraft.util.Vec3;

public final strictfp class PlacementGeometry {
   private PlacementGeometry() {
   }

   private static strictfp double narrow(double v) {
      return (double)((float)v);
   }

   private static strictfp double min(double a, double b) {
      return a < b ? a : b;
   }

   private static strictfp double max(double a, double b) {
      return a > b ? a : b;
   }

   private static strictfp double minRight(double a, double b) {
      return Double.isNaN(b) ? a : min(a, b);
   }

   private static strictfp double maxRight(double a, double b) {
      return Double.isNaN(b) ? a : max(a, b);
   }

   public static strictfp Vec3 rayPoint(double[] b, Vec3 o, Vec3 v) {
      double numerator = ActionConstants.d(1304712);
      double limit = ActionConstants.d(1304720);
      double x0 = narrow((b[0] - o.xCoord) * (numerator / v.xCoord));
      double x1 = narrow((b[3] - o.xCoord) * (numerator / v.xCoord));
      double entryX = narrow(min(max(x0, 0.0), max(x1, 0.0)));
      double exitX = narrow(max(min(x0, limit), min(x1, limit)));
      double y0 = narrow((b[1] - o.yCoord) * (numerator / v.yCoord));
      double y1 = narrow((b[4] - o.yCoord) * (numerator / v.yCoord));
      double entryY = narrow(min(max(y0, entryX), max(y1, entryX)));
      double exitY = narrow(max(min(y0, exitX), min(y1, exitX)));
      double z0 = narrow((b[2] - o.zCoord) * (numerator / v.zCoord));
      double z1 = narrow((b[5] - o.zCoord) * (numerator / v.zCoord));
      float entry = (float)minRight(maxRight(entryY, z1), maxRight(entryY, z0));
      float exit = (float)maxRight(minRight(exitY, z1), minRight(exitY, z0));
      return !(exit <= entry) && !(ActionConstants.f(1304728) < entry)
         ? new Vec3(o.xCoord + v.xCoord * (double)entry, o.yCoord + v.yCoord * (double)entry, o.zCoord + v.zCoord * (double)entry)
         : null;
   }

   public static strictfp Vec3 direction(float yaw, float pitch) {
      float yr = (float)((double)yaw * ActionConstants.d(1374032) / ActionConstants.d(1374040) + ActionConstants.d(1374032));
      float pr = (float)((double)pitch * ActionConstants.d(1374032) / ActionConstants.d(1374040));
      float yc = RotationMath.cosine(yr);
      float ys = RotationMath.sine(yr);
      float pc = RotationMath.cosine(pr);
      float ps = RotationMath.sine(pr);
      float negative = Float.intBitsToFloat(Float.floatToRawIntBits(pc) ^ ActionConstants.intAt(1374048));
      return new Vec3((double)(ys * negative), (double)ps, (double)(negative * yc));
   }

   public static strictfp double squared(Vec3 a, Vec3 b) {
      double x = a.xCoord - b.xCoord;
      double y = a.yCoord - b.yCoord;
      double z = a.zCoord - b.zCoord;
      return z * z + (x * x + y * y);
   }

   public static strictfp int magnitude(int value) {
      int negated = -value;
      return negated < 0 ? value : negated;
   }

   public static strictfp long absdiff(int a, int b) {
      return Integer.toUnsignedLong(b < a ? a - b : b - a);
   }
}
