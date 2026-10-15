package dev.slinky.client.render;

import dev.slinky.client.numeric.FloatTrigonometry;
import dev.slinky.client.packet.PredictionGeometry;
import java.util.List;

public final strictfp class TrajectoryPrediction {
   private TrajectoryPrediction() {
   }

   public static strictfp float charge(int ticks) {
      float t = (float)ticks / RenderConstants.f(1396232);
      return (t * t + (t + t)) / RenderConstants.f(1396236);
   }

   public static strictfp float speed(float strength) {
      double value = (double)RenderMath.min(RenderConstants.f(1396248), strength) * RenderConstants.d(1396256);
      return (float)(value + value);
   }

   public static strictfp TrajectoryPrediction.Launch launch(
      double[] input, float yawDegrees, float pitchDegrees, float fraction, float previousSpeed, float speed, boolean positionModeZero, boolean sneaking
   ) {
      double radians = RenderConstants.d(1396304);
      float yaw = (float)((double)yawDegrees * radians);
      float pitch = (float)((double)pitchDegrees * radians);
      float cy = FloatTrigonometry.cosine(yaw);
      float cp = FloatTrigonometry.cosine(pitch);
      float sy = FloatTrigonometry.sine(yaw);
      float sp = FloatTrigonometry.sine(pitch);
      int sign = RenderConstants.i(1396320);
      double[] direction = new double[]{
         (double)(Float.intBitsToFloat(Float.floatToRawIntBits(sy) ^ sign) * cp),
         (double)Float.intBitsToFloat(Float.floatToRawIntBits(sp) ^ sign),
         (double)(cp * cy)
      };
      float currentSpeed = RenderMath.lerp(previousSpeed, speed, fraction);
      double[] position = (double[])input.clone();
      if (!positionModeZero) {
         position[1] += RenderConstants.d(1304656);
         if (sneaking) {
            position[1] += RenderConstants.d(1396336);
         }
      }

      double offset = RenderConstants.d(1396344);
      position[0] -= (double)cy * offset;
      position[1] += RenderConstants.d(1396352);
      position[2] -= (double)sy * offset;
      return new TrajectoryPrediction.Launch(position, RenderMath.scale(direction, (double)currentSpeed));
   }

   public static strictfp double[] sortedBox(double[] a, double[] b) {
      double[] out = new double[6];

      for (int i = 0; i < 3; i++) {
         out[i] = Double.isNaN(a[i]) ? b[i] : (b[i] < a[i] ? b[i] : a[i]);
         out[i + 3] = Double.isNaN(a[i]) ? b[i] : (b[i] > a[i] ? b[i] : a[i]);
      }

      return out;
   }

   public static strictfp double[] broad(TrajectoryPrediction.Launch launch) {
      double h = RenderConstants.d(1396264);
      return sortedBox(
         new double[]{launch.position[0], -100.0, launch.position[2]},
         new double[]{
            launch.velocity[0] * h + launch.position[0], launch.position[1] + RenderConstants.d(1396272), h * launch.velocity[2] + launch.position[2]
         }
      );
   }

   public static strictfp boolean intersects(double[] a, double[] b) {
      return a[0] < b[3] && b[0] < a[3] && a[1] < b[4] && b[1] < a[4] && a[2] < b[5] && b[2] < a[5];
   }

   public static strictfp TrajectoryPrediction.Result predict(
      TrajectoryPrediction.Launch launch, List<TrajectoryPrediction.Candidate> candidates, TrajectoryPrediction.World world, float gravity, boolean pearl
   ) {
      double[] position = (double[])launch.position.clone();
      double[] velocity = (double[])launch.velocity.clone();
      double drag = RenderConstants.d(1396296);
      double blockSize = RenderConstants.d(1396288);

      for (int step = 0; step < 100; step++) {
         double[] old = (double[])position.clone();
         position = RenderMath.add(old, velocity);
         TrajectoryPrediction.BlockHit hit = world.trace(old, position);
         double[] box = null;
         if (hit != null) {
            position = (double[])hit.point.clone();
            velocity = RenderMath.subtract(position, old);
            box = new double[]{hit.corner[0], hit.corner[1], hit.corner[2], hit.corner[0] + blockSize, hit.corner[1] + blockSize, hit.corner[2] + blockSize};
         }

         for (TrajectoryPrediction.Candidate entity : candidates) {
            if (PredictionGeometry.ray(entity.box, old, velocity)) {
               return new TrajectoryPrediction.Result(step + 1, true, !pearl, (double[])entity.box.clone(), (double[])entity.motion.clone());
            }
         }

         if (hit != null) {
            return new TrajectoryPrediction.Result(step + 1, true, pearl, box, new double[3]);
         }

         velocity = RenderMath.scale(velocity, drag);
         velocity[1] -= (double)gravity;
      }

      return new TrajectoryPrediction.Result(100, false, false, null, new double[3]);
   }

   public static strictfp float[] renderPoints(TrajectoryPrediction.Launch launch, int steps, float gravity, double[] camera) {
      double[] position = (double[])launch.position.clone();
      double[] velocity = (double[])launch.velocity.clone();
      float[] points = new float[steps * 3];

      for (int i = 0; i < steps; i++) {
         position = RenderMath.add(position, velocity);

         for (int lane = 0; lane < 3; lane++) {
            points[i * 3 + lane] = (float)(position[lane] - camera[lane]);
         }

         velocity = RenderMath.scale(velocity, RenderConstants.d(1396208));
         velocity[1] -= (double)gravity;
      }

      return points;
   }

   public static final strictfp class BlockHit {
      public final double[] point;
      public final double[] corner;

      public BlockHit(double[] point, double[] corner) {
         this.point = (double[])point.clone();
         this.corner = (double[])corner.clone();
      }
   }

   public static final strictfp class Candidate {
      public final double[] box;
      public final double[] motion;

      public Candidate(double[] box, double[] motion) {
         this.box = (double[])box.clone();
         this.motion = (double[])motion.clone();
      }
   }

   public static final strictfp class Launch {
      public final double[] position;
      public final double[] velocity;

      public Launch(double[] p, double[] v) {
         this.position = p;
         this.velocity = v;
      }
   }

   public static final strictfp class Result {
      public final int steps;
      public final boolean marker;
      public final boolean hitColor;
      public final double[] box;
      public final double[] motion;

      public Result(int steps, boolean marker, boolean hitColor, double[] box, double[] motion) {
         this.steps = steps;
         this.marker = marker;
         this.hitColor = hitColor;
         this.box = box;
         this.motion = motion;
      }
   }

   public interface World {
      TrajectoryPrediction.BlockHit trace(double[] var1, double[] var2);
   }
}
