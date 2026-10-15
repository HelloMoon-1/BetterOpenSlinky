package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.numeric.DoubleNumeric;
import dev.slinky.client.numeric.NumericServices;
import dev.slinky.client.placement.PlacementFrame;
import dev.slinky.client.placement.PlacementGeometry;
import dev.slinky.client.placement.PlacementSelection;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public final strictfp class ClutchModule extends EvidenceModule {
   private final ClutchModule.State state = new ClutchModule.State();
   private final ModuleManager modules;
   private Object player;
   private Object world;
   private int selectedSlot = -1;
   private boolean selectionInterrupted;

   public ClutchModule(ModuleManager modules) {
      super("clutch", "Clutch", ModuleCategory.UTILITY);
      this.modules = modules;
   }

   private strictfp ClutchModule.Settings settings() {
      ClutchModule.Settings s = new ClutchModule.Settings();
      s.range = this.number("range");
      s.fov = this.number("max_aim_dist");
      s.height = this.number("min_height");
      s.clickSpeed = this.number("click_speed");
      s.randomization = this.number("randomization");
      s.aimSpeed = this.number("aim_speed");
      s.aimMult = this.number("aim_mult");
      s.aimStrength = this.number("aim_mult_strength");
      s.snapDelay = this.number("snapback_delay");
      s.snapDuration = this.number("snapback_duration");
      s.select = this.choice("select_blocks");
      s.sideways = this.flag("sideways_only");
      s.multipoint = this.flag("aim_multipoint");
      s.instantJump = this.flag("snapback_instant_on_jump");
      s.disableAfter = this.flag("snapback_disable_after");
      s.air = this.flag("require_air");
      s.damage = this.flag("require_damage");
      s.backwards = this.flag("require_backwards");
      return s;
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 13 || type == 18 || type == 26;
   }

   private strictfp boolean context(ClientContext c) {
      if (this.player != c.player() || this.world != c.world()) {
         this.cleanup();
         this.player = c.player();
         this.world = c.world();
         return false;
      }

      if (this.selectedSlot >= 0 && ModuleActionService.owner() == this && c.ready()
         && c.player().inventory.currentItem != this.selectedSlot) {
         PlacementSelection.release(this);
         this.selectedSlot = -1;
         this.selectionInterrupted = true;
         this.state.placement = false;
      }

      return true;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      PlacementFrame i = new PlacementFrame.Game(c, this.modules, this);
      if (e.type != 26 && !this.context(c)) {
         return;
      }
      if (e.type == 26) {
         this.cleanup();
      } else {
         if (e.type == 18) {
            Object owner = ModuleActionService.owner();
            if (owner != null && owner != this && !(owner instanceof BridgeAssistModule)) {
               this.state.placement = this.state.correctMovement = false;
            } else {
               ClutchModule.Settings s = this.settings();
               if (this.selectionInterrupted) {
                  s.select = 0;
               }

               this.state.update(i, s, RecoveredRandom.SHARED, RecoveredRandom.now());
               if (!this.state.searching) {
                  this.selectionInterrupted = false;
               }
            }

            if (!this.state.placement) {
               PlacementSelection.release(this);
               this.selectedSlot = -1;
            } else if (ModuleActionService.owner() == this) {
               this.selectedSlot = c.player().inventory.currentItem;
            }
         } else if (this.state.correctMovement) {
            e.active = true;
            e.remapMovement = true;
         }
      }
   }

   @Override
   public strictfp void onClientTick(ClientContext c) {
      if (!this.modules.requestedEnabled(this) || !ItemActionHelper.ready(c)) {
         this.cleanup();
         return;
      }

      if (!this.context(c)) {
         return;
      }

      PlacementFrame i = new PlacementFrame.Game(c, this.modules, this);
      if (!this.state.placement) {
         this.state.clickTimer = 0.0F;
         PlacementSelection.release(this);
         return;
      }

      if (!ModuleActionService.claim(this)) {
         return;
      }

      int select = this.choice("select_blocks");
      if (!this.selectionInterrupted && (select == 1 || select == 2)) {
         i.select(select == 2, false);
      }

      if (!i.heldBlock()) {
         this.state.placement = false;
         PlacementSelection.release(this);
         this.selectedSlot = -1;
         return;
      }

      this.selectedSlot = c.player().inventory.currentItem;
      MovingObjectPosition hit = ItemActionHelper.refreshBlockRay(c);
      float cps = this.number("click_speed");
      if (this.state.placementTick(i, cps)) {
         BlockPos destination = hit.getBlockPos().offset(hit.sideHit);
         int before = i.block(destination.getX(), destination.getY(), destination.getZ());
         ItemActionHelper.Result result = ItemActionHelper.useBlock(c, this, hit);
         if (result == ItemActionHelper.Result.USED || result == ItemActionHelper.Result.FAILED) {
            this.state.clickTimer += Math.max(1.0F, 20.0F / cps);
         }

         if (result == ItemActionHelper.Result.USED && c.ready() && this.player == c.player() && this.world == c.world()
            && i.block(destination.getX(), destination.getY(), destination.getZ()) != before
            && i.block(destination.getX(), destination.getY(), destination.getZ()) != 0) {
            this.state.x = destination.getX();
            this.state.y = destination.getY();
            this.state.z = destination.getZ();
         }
      }
   }

   private strictfp void cleanup() {
      this.state.clear();
      PlacementSelection.release(this);
      this.player = this.world = null;
      this.selectedSlot = -1;
      this.selectionInterrupted = false;
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.cleanup();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.cleanup();
   }

   public static final strictfp class Face {
      public final Vec3 point;
      public final float score;

      Face(Vec3 point, float score) {
         this.point = point;
         this.score = score;
      }
   }

   public static final class Settings {
      public float range;
      public float fov;
      public float height;
      public float clickSpeed;
      public float randomization;
      public float aimSpeed;
      public float aimMult;
      public float aimStrength;
      public float snapDelay;
      public float snapDuration;
      public int select;
      public boolean sideways;
      public boolean multipoint;
      public boolean instantJump;
      public boolean disableAfter;
      public boolean air;
      public boolean damage;
      public boolean backwards;
   }

   public static final class State {
      public float clickTimer;
      public int x;
      public int y;
      public int z;
      public int unhurt;
      public int phase;
      public int snapDelay;
      public int snapRemaining;
      public boolean placement;
      public boolean searching;
      public boolean centered;
      public boolean correctMovement;
      public boolean disableLatched;
      public Vec3 selected = new Vec3(0.0, 0.0, 0.0);
      public double jumpAccumulator;
      private final RecoveredRandom.Smooth inward = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth jitterX = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth jitterZ = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth holeX = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth holeZ = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth pitchNoise = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Segment speedX = new RecoveredRandom.Segment();
      private final RecoveredRandom.Segment speedZ = new RecoveredRandom.Segment();

      private static strictfp float f(int a) {
         return ActionConstants.f(a);
      }

      private static strictfp double d(int a) {
         return ActionConstants.d(a);
      }

      private static strictfp float smooth(RecoveredRandom.Smooth s, float low, float high, double period, RecoveredRandom r, double now) {
         return s.sample(low, high, period, now, r);
      }

      public static strictfp boolean scoreRejected(float score, float best, long maximum, long distance) {
         return score > best && maximum <= distance;
      }

      public strictfp ClutchModule.Face face(
         Vec3 eye,
         float yaw,
         float pitch,
         Vec3 position,
         int bx,
         int by,
         int bz,
         int dx,
         int dy,
         int dz,
         ClutchModule.Settings s,
         RecoveredRandom r,
         double now
      ) {
         float inner = smooth(this.inward, 0.0F, f(1401200), d(1401208), r, now);
         float jx = smooth(this.jitterX, f(1401216), f(1401220), d(1401224), r, now);
         float jz = smooth(this.jitterZ, f(1401216), f(1401220), d(1401224), r, now);
         double[] jitter = new double[]{(double)(jz * s.randomization), (double)(jx * s.randomization)};
         if (!s.multipoint) {
            double[] remainder = new double[]{DoubleNumeric.remainder(position.xCoord, d(1401208)), DoubleNumeric.remainder(position.zCoord, d(1401208))};
            double[] observed = new double[]{position.zCoord, position.xCoord};
            double[] previous = new double[]{this.selected.zCoord, this.selected.xCoord};

            for (int lane = 0; lane < 2; lane++) {
               long mask = ActionConstants.longAt(1401232 + lane * 8);
               double value = Double.longBitsToDouble(Double.doubleToRawLongBits(remainder[lane]) & mask);
               boolean beyond = d(1401248 + lane * 8) < value;
               double adjustment = d(1401264 + lane * 8);
               double changed = value + adjustment;
               double folded = beyond ? Double.longBitsToDouble(Double.doubleToRawLongBits(changed) & mask) : value;
               double weight = previous[lane] < observed[lane] ? adjustment : d(1401280 + lane * 8);
               jitter[lane] += folded * weight;
            }
         }

         int ax = PlacementGeometry.magnitude(dx);
         int az = PlacementGeometry.magnitude(dz);
         double offset = (double)(s.randomization * inner) + d(1401296);
         double inverse = d(1401208) - offset;
         double xLow = offset;
         double xHigh = inverse;
         double zLow = (double)(dz >>> 31);
         double zHigh = zLow;
         if (Integer.compareUnsigned(az, ax) < 0) {
            xLow = (double)(dx >>> 31);
            xHigh = xLow;
            zLow = offset;
            zHigh = inverse;
         }

         int blockX = dx + bx;
         int blockY = dy + by;
         int blockZ = dz + bz;
         float pointX = RotationMath.clamp((float)(xLow + (double)blockX), (float)(xHigh + (double)blockX), (float)(jitter[1] + position.xCoord));
         double half = d(1401304);
         double baseY = (double)blockY + half;
         int magnitude = PlacementGeometry.magnitude(dx) + PlacementGeometry.magnitude(dz);
         float pointY = RotationMath.clamp(0.0F, f(1401312), (float)((double)magnitude * half));
         float pointZ = RotationMath.clamp((float)(zLow + (double)blockZ), (float)(zHigh + (double)blockZ), (float)(jitter[0] + position.zCoord));
         Vec3 point = new Vec3((double)pointX, (double)pointY + baseY, (double)pointZ);
         float[] desired = RotationMath.angles(eye, point);
         float yawValue = Float.intBitsToFloat(Float.floatToRawIntBits(RotationMath.wrap(desired[0] - yaw)) & ActionConstants.intAt(1401328));
         float pitchValue = Float.intBitsToFloat(Float.floatToRawIntBits(desired[1] - pitch) & 2147483647);
         return new ClutchModule.Face(point, yawValue + pitchValue);
      }

      public strictfp boolean placementTick(PlacementFrame i, float cps) {
         if (!i.ready() || !this.placement || !i.heldBlock() || !(cps > 0.0F) || Float.isInfinite(cps)) {
            this.clickTimer = 0.0F;
            return false;
         }

         if (this.clickTimer > 0.0F) {
            this.clickTimer -= 1.0F;
            if (this.clickTimer > 0.0F) {
               return false;
            }
         }

         MovingObjectPosition hit = i.hit();
         if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK || hit.sideHit == null || hit.getBlockPos() == null) {
            return false;
         }

         BlockPos block = hit.getBlockPos();
         return block.getX() == this.x && block.getY() == this.y && block.getZ() == this.z
            && this.centered == (hit.sideHit.ordinal() == 1);
      }

      private strictfp boolean search(
         PlacementFrame i, ClutchModule.Settings s, Vec3 eye, AxisAlignedBB b, float yaw, float pitch, RecoveredRandom r, double now
      ) {
         this.centered = false;
         double gx = (b.minX + b.maxX) * d(1400984);
         double gy = b.minY + d(1400992);
         double gz = (b.minZ + b.maxZ) * d(1400984);
         int ix = RecoveredMath.truncateInt(DoubleNumeric.floor(gx));
         int iy = RecoveredMath.truncateInt(DoubleNumeric.floor(gy));
         int iz = RecoveredMath.truncateInt(DoubleNumeric.floor(gz));
         if (s.height > 0.0F) {
            int step = 1;
            int queryY = iy;

            int compared;
            do {
               if (i.block(ix, queryY, iz) != 0) {
                  return false;
               }

               compared = step++;
               queryY--;
            } while (s.height > (float)compared);
         }

         Vec3 position = new Vec3(gx, gy, gz);
         Vec3 bestTarget = eye;
         int bestX = ix;
         int bestY = iy;
         int bestZ = iz;
         float bestScore = f(1401000);
         long maxDistance = -3L;
         int count = ActionConstants.shortAt(1401472 + 2 * RecoveredMath.truncateInt(s.range));
         if ((double)this.y <= gy && i.block(this.x, this.y, this.z) != 0) {
            ClutchModule.Face retained = this.face(eye, yaw, pitch, position, ix, iy, iz, this.x - ix, this.y - iy, this.z - iz, s, r, now);
            if (!(retained.score > s.fov) && PlacementGeometry.squared(eye, retained.point) < d(1401008)) {
               MovingObjectPosition hit = i.ray(eye, retained.point);
               if (hit != null
                  && hit.getBlockPos() != null
                  && hit.getBlockPos().getX() == this.x
                  && hit.getBlockPos().getY() == this.y
                  && hit.getBlockPos().getZ() == this.z) {
                  bestTarget = retained.point;
                  bestX = this.x;
                  bestY = this.y;
                  bestZ = this.z;
                  bestScore = retained.score;
               }
            }
         }

         if (bestScore != f(1401000) || Float.isNaN(bestScore)) {
            int sum = (int)PlacementGeometry.absdiff(bestX, ix) + (int)PlacementGeometry.absdiff(bestY, iy) + (int)PlacementGeometry.absdiff(bestZ, iz);
            maxDistance = Integer.toUnsignedLong(sum) - 2L;
         }

         for (int k = 0; k < count; k++) {
            int code = ActionConstants.shortAt(1401488 + 2 * k);
            int dx = (code & 15) - 8;
            int dy = (code >>> 4 & 15) - 8;
            int dz = (code >>> 8 & 15) - 8;
            int cx = ix + dx;
            int cy = iy + dy;
            int cz = iz + dz;
            if (i.block(cx, cy, cz) != 0) {
               if (dx == 0 && dz == 0) {
                  if (s.sideways) {
                     return false;
                  }

                  float j = smooth(this.inward, 0.0F, f(1401016), d(1401024), r, now);
                  double inset = d(1401032) + (double)(j * s.randomization);
                  float sy = smooth(this.holeX, f(1401040), f(1401044), d(1401024), r, now);
                  float sz = smooth(this.holeZ, f(1401040), f(1401044), d(1401024), r, now);
                  float px = RotationMath.clamp((float)((double)cx + inset), (float)((double)(cx + 1) - inset), (float)(gx + (double)(sy * s.randomization)));
                  float pz = RotationMath.clamp((float)((double)cz + inset), (float)((double)(cz + 1) - inset), (float)(gz + (double)(sz * s.randomization)));
                  this.selected = new Vec3((double)px, (double)cy + d(1401024), (double)pz);
                  this.x = cx;
                  this.y = cy;
                  this.z = cz;
                  this.centered = true;
                  return true;
               }

               long distance = (long)Math.abs(dx) + (long)Math.abs(dy) + (long)Math.abs(dz);
               count = ActionConstants.shortAt(1401472 + 2 * (int)distance);
               ClutchModule.Face candidate = this.face(eye, yaw, pitch, position, ix, iy, iz, dx, dy, dz, s, r, now);
               if (!scoreRejected(candidate.score, bestScore, maxDistance, distance) && PlacementGeometry.squared(eye, candidate.point) < d(1401008)) {
                  MovingObjectPosition visible = i.ray(eye, candidate.point);
                  if (visible != null && visible.typeOfHit == MovingObjectType.BLOCK && visible.getBlockPos() != null
                     && visible.getBlockPos().getX() == cx && visible.getBlockPos().getY() == cy && visible.getBlockPos().getZ() == cz) {
                     bestTarget = candidate.point;
                     bestX = cx;
                     bestY = cy;
                     bestZ = cz;
                     bestScore = candidate.score;
                  }
               }
            }
         }

         if (!(bestScore <= s.fov)) {
            return false;
         } else {
            this.selected = bestTarget;
            this.x = bestX;
            this.y = bestY;
            this.z = bestZ;
            return true;
         }
      }

      public strictfp void update(PlacementFrame i, ClutchModule.Settings s, RecoveredRandom r, double now) {
         this.placement = this.searching = this.correctMovement = false;
         if (i.ready()) {
            boolean ground = i.ground();
            this.unhurt = i.hurt() == 0 ? this.unhurt + 1 : 0;
            boolean phaseActive = this.phase > 0;
            if (phaseActive) {
               this.phase--;
            }

            if (phaseActive && ground && i.rotationAvailable() && s.instantJump && i.jump()) {
               float yaw = i.yaw() + i.offsetYaw();
               float pitch = i.pitch() + i.offsetPitch();
               double multiplier = this.jumpAccumulator < 0.0 ? d(1401184) : d(1401176);
               float a = (float)((double)r.interval(f(1401192), f(1401196)) * multiplier);
               float b = (float)((double)r.interval(f(1401192), f(1401196)) * multiplier);
               this.jumpAccumulator += (double)a;
               float outYaw = a * s.randomization + i.storedYaw() - yaw;
               float outPitch = b * s.randomization + i.storedPitch() - pitch;
               if (!i.claim(1000)) {
                  return;
               }

               i.publish(RotationMath.wrap(outYaw), outPitch);
               this.correctMovement = true;
               this.snapDelay = RecoveredMath.truncateInt(s.snapDuration);
            } else {
               AxisAlignedBB box = i.box();
               Vec3 eye = i.eye();
               float yaw = i.yaw() + i.offsetYaw();
               float pitch = i.pitch() + i.offsetPitch();
               boolean doSearch = (!s.air || !ground) && (this.unhurt < 11 || !s.damage) && !i.mounted();
               if (doSearch && s.backwards) {
                  double[] velocity = i.velocity();
                  double length = NumericServices.hypot(velocity[0], velocity[2]);
                  if (length == 0.0) {
                     doSearch = false;
                  } else {
                     float normalized = (float)((double)i.yaw() * d(1400960) / d(1400968) + d(1400960));
                     float fx = RotationMath.sine(normalized);
                     float fy = RotationMath.cosine(normalized);
                     double ratio = NumericServices.acos(((double)fy * velocity[2] + (double)fx * velocity[0]) / length);
                     if (!(d(1400976) > ratio)) {
                        doSearch = false;
                     }
                  }
               }

               this.searching = doSearch;
               boolean found = doSearch && this.search(i, s, eye, box, yaw, pitch, r, now);
               if (found) {
                  if (s.select == 1 || s.select == 2) {
                     i.select(s.select == 2, false);
                  }

                  this.placement = i.heldBlock();
                  if (this.placement) {
                     float high = (float)((double)s.randomization * d(1400984) + d(1401024));
                     this.snapDelay = RecoveredMath.truncateInt(r.interval(f(1401048), high) * s.snapDelay);
                     this.phase = 10;
                     this.disableLatched = s.disableAfter;
                  }
               }

               if (!this.placement) {
                  if (this.snapDelay > 0) {
                     this.snapDelay--;
                     this.snapRemaining = RecoveredMath.truncateInt(s.snapDuration);
                  } else {
                     if (this.snapRemaining <= 0) {
                        if (this.disableLatched) {
                           i.disable();
                           this.disableLatched = false;
                        }

                        return;
                     }

                     this.snapRemaining--;
                  }
               }

               if (i.claim(1000)) {
                  this.correctMovement = true;
                  if (!this.placement) {
                     if (this.snapDelay <= 0) {
                        if (this.snapRemaining < 1) {
                           if (this.disableLatched) {
                              i.disable();
                              this.disableLatched = false;
                           }
                        } else if (i.rotationAvailable()) {
                           float deltaYaw = RotationMath.wrap(i.storedYaw() - (i.yaw() + i.offsetYaw()));
                           float deltaPitch = i.storedPitch() - (i.pitch() + i.offsetPitch());
                           float rate = (float)((double)f(1401048) / (double)this.snapRemaining);
                           i.publish(deltaYaw * rate, deltaPitch * rate);
                        }
                     }
                  } else {
                     Vec3 direction = PlacementGeometry.direction(i.yaw(), i.pitch());
                     double extent = d(1401064);
                     Vec3 collision = new Vec3(extent * direction.xCoord, extent * direction.yCoord, extent * direction.zCoord);
                     double[] query = new double[]{
                        (double)this.x + d(1401072),
                        (double)this.y + d(1401080),
                        (double)this.z + d(1401088),
                        (double)this.x + d(1401096),
                        (double)this.y + 1.0,
                        (double)this.z + d(1401056)
                     };
                     Vec3 hit = PlacementGeometry.rayPoint(query, eye, collision);
                     if (hit == null || !(hit.yCoord < (double)this.y + d(1401104))) {
                        float[] desired = RotationMath.angles(eye, this.selected);
                        float deltaYaw = desired[0] - yaw;
                        float deltaPitch = desired[1] - pitch;
                        float gate = !((double)desired[1] > d(1401112))
                           ? RotationMath.wrap(deltaYaw)
                           : smooth(this.pitchNoise, f(1401120), f(1401124), d(1400984), r, now) * s.randomization;
                        float low = (float)((double)s.randomization * d(1401128) + d(1401024));
                        float high = f(1401048);
                        float a = this.speedX.sample(low, high, d(1401136), d(1401024), r) * s.aimSpeed;
                        float b = this.speedZ.sample(low, high, d(1401136), d(1401024), r) * s.aimSpeed;
                        float negativeA = Float.intBitsToFloat(Float.floatToRawIntBits(a) ^ ActionConstants.intAt(1401152));
                        float negativeB = Float.intBitsToFloat(Float.floatToRawIntBits(b) ^ ActionConstants.intAt(1401152));
                        float first = RotationMath.clamp(negativeA, a, gate);
                        float second = RotationMath.clamp(negativeB, b, deltaPitch);
                        float accumA = s.aimMult;
                        float accumB = s.aimMult;
                        accumA += this.speedX.sample(f(1401168), f(1401016), d(1401136), d(1401024), r);
                        accumB += this.speedZ.sample(f(1401168), f(1401016), d(1401136), d(1401024), r);
                        accumA *= gate;
                        accumB *= deltaPitch;
                        i.publish((accumA - first) * s.aimStrength + first, (accumB - second) * s.aimStrength + second);
                     }
                  }
               } else {
                  this.placement = false;
               }
            }
         }
      }

      public strictfp void clear() {
         this.placement = this.searching = this.centered = this.correctMovement = this.disableLatched = false;
         this.clickTimer = 0.0F;
         this.x = this.y = this.z = this.unhurt = this.phase = this.snapDelay = this.snapRemaining = 0;
         this.selected = new Vec3(0.0, 0.0, 0.0);
         this.jumpAccumulator = 0.0;
      }
   }
}
