package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.client.render.TrajectoryPrediction;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final strictfp class TrajectoriesModule extends EvidenceModule {
   private float previousSpeed;
   private float speed;
   private float gravity;
   private TrajectoryPrediction.Result prediction;
   private final List<TrajectoryPrediction.Candidate> candidates = new ArrayList<>();

   public TrajectoriesModule() {
      super("trajectories", "Trajectories", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 5 || t == 6 || t == 18;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 5) {
         if (e.module == this && !e.enabled) {
            this.candidates.clear();
         }
      } else if (e.type == 18) {
         this.update(c);
      } else if (c.ready() && this.prediction != null) {
         RenderAccess.submit(e, this.plan(c, e));
      }
   }

   private strictfp TrajectoryPrediction.Launch launch(ClientContext c, double[] position, float partial) {
      return TrajectoryPrediction.launch(
         position, c.player().rotationYaw, c.player().rotationPitch, partial, this.previousSpeed, this.speed, false, c.player().isSneaking()
      );
   }

   private strictfp void update(ClientContext c) {
      this.prediction = null;
      if (!RotationService.storedRotationValid() && c.inputReady() && c.minecraft().gameSettings.thirdPersonView == 0) {
         ItemStack held = c.player().getHeldItem();
         if (held != null) {
            int id = Item.getIdFromItem(held.getItem());
            boolean pearl = id == 368;
            if (pearl) {
               if (!this.flag("render_pearls")) {
                  return;
               }
            } else if (id != 332 && id != 344) {
               if (id != 261) {
                  return;
               }
            } else if (!this.flag("render_throwables")) {
               return;
            }

            if (id != 261) {
               this.previousSpeed = this.speed = 1.5F;
               this.gravity = Float.intBitsToFloat(1022739087);
            } else {
               if (!this.flag("render_bow")) {
                  return;
               }

               this.gravity = Float.intBitsToFloat(1028443341);
               if (c.player().isUsingItem()) {
                  int ticks = c.player().getItemInUseCount();
                  float charge = TrajectoryPrediction.charge(72000 - ticks);
                  double threshold = RenderConstants.d(1396240);
                  if (threshold > (double)charge) {
                     this.speed = 0.0F;
                     return;
                  }

                  this.speed = TrajectoryPrediction.speed(charge);
                  if (this.speed == 0.0F) {
                     return;
                  }

                  charge = TrajectoryPrediction.charge(71999 - ticks);
                  this.previousSpeed = threshold > (double)charge ? 0.0F : TrajectoryPrediction.speed(charge);
               } else {
                  if (!this.flag("render_uncharged_bow")) {
                     return;
                  }

                  this.previousSpeed = this.speed = 3.0F;
               }
            }

            TrajectoryPrediction.Launch launch = this.launch(c, RenderAccess.position(c.player()), 0.0F);
            double[] broad = TrajectoryPrediction.broad(launch);
            this.candidates.clear();
            double margin = RenderConstants.d(1396280);

            for (Entity entity : c.world().loadedEntityList) {
               if (entity != c.player() && entity.canBeCollidedWith()) {
                  AxisAlignedBB b = entity.getEntityBoundingBox();
                  double[] box = new double[]{b.minX - margin, b.minY - margin, b.minZ - margin, b.maxX + margin, b.maxY + margin, margin + b.maxZ};
                  if (TrajectoryPrediction.intersects(box, broad)) {
                     this.candidates
                        .add(new TrajectoryPrediction.Candidate(box, RenderMath.subtract(RenderAccess.position(entity), RenderAccess.previous(entity))));
                  }
               }
            }

            this.prediction = TrajectoryPrediction.predict(
               launch,
               this.candidates,
               (from, to) -> {
                  MovingObjectPosition hit = c.world().rayTraceBlocks(new Vec3(from[0], from[1], from[2]), new Vec3(to[0], to[1], to[2]), false, true, false);
                  if (hit == null) {
                     return null;
                  } else {
                     BlockPos block = hit.getBlockPos();
                     return new TrajectoryPrediction.BlockHit(
                        new double[]{hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord},
                        new double[]{(double)block.getX(), (double)block.getY(), (double)block.getZ()}
                     );
                  }
               },
               this.gravity,
               pearl
            );
         }
      }
   }

   public strictfp DrawPlan plan(ClientContext c, SemanticEvent e) {
      DrawPlan p = new DrawPlan().lineWidth(this.number("line_width")).color(this.color(this.prediction.hitColor ? "color_hit" : "color_miss"), 1.0F);
      double[] camera = new double[]{e.cameraX, e.cameraY, e.cameraZ};
      TrajectoryPrediction.Launch launch = this.launch(
         c, RenderMath.lerp(RenderAccess.previous(c.player()), RenderAccess.position(c.player()), (double)e.timerRenderPartialTicks), e.timerRenderPartialTicks
      );
      p.vertices(3, 3, TrajectoryPrediction.renderPoints(launch, this.prediction.steps, this.gravity, camera));
      if (this.prediction.marker) {
         double[] low = Arrays.copyOfRange(this.prediction.box, 0, 3);
         double[] high = Arrays.copyOfRange(this.prediction.box, 3, 6);
         double[] center = RenderMath.lerp(low, high, RenderConstants.d(1396216));
         double[] half = RenderMath.subtract(high, center);
         double[] motion = RenderMath.scale(this.prediction.motion, RenderConstants.d(1396224) - (double)e.timerRenderPartialTicks);
         double[] translation = RenderMath.subtract(RenderMath.subtract(center, camera), motion);
         p.push()
            .translate((float)translation[0], (float)translation[1], (float)translation[2])
            .scale((float)half[0], (float)half[1], (float)half[2])
            .graphics(169008)
            .pop();
      }

      return p;
   }

   public strictfp TrajectoryPrediction.Result prediction() {
      return this.prediction;
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.candidates.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.candidates.clear();
      this.prediction = null;
   }
}
