package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.rotation.AimNoise;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.inject.ReflectionHelper;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.lang.reflect.Field;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.input.Mouse;

public final strictfp class AimAssistModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private final RecoveredRandom random;
   private final RecoveredRandom.Smooth pitchDamping = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth pitchNoise = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth horizontalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth verticalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth speedFactor = new RecoveredRandom.Smooth();
   private EntityPlayer target;
   private boolean targetActive;
   private boolean silent;
   private Vec3 previousAim = new Vec3(0.0, 0.0, 0.0);
   private Vec3 currentAim = this.previousAim;
   private int graceTicks;
   private long tieCounter;

   public AimAssistModule(WeaponsModule weapons) {
      this(weapons, RecoveredRandom.SHARED);
   }

   public AimAssistModule(WeaponsModule weapons, RecoveredRandom random) {
      super("aimassist", "Aim Assist", ModuleCategory.COMBAT);
      this.weapons = weapons;
      this.random = random;
      this.reorderSettings(
         new String[]{
            "mode",
            "lock_on_vertical",
            "silent_move_correct",
            "silent_ignore_manual_aim",
            "hor_speed",
            "ver_speed",
            "predict",
            "aim_randomization",
            "hor_hitbox_correct",
            "ver_hitbox_correct",
            "mode_target",
            "sort_by",
            "allow_exceed_fov",
            "range",
            "fov",
            "require_sprint",
            "require_mouse_pressed",
            "require_mouse_moved",
            "disable_on_block_break",
            "require_weapon",
            "ignore_invis",
            "require_line_of_sight"
         }
      );
   }

   @Override
   public strictfp boolean settingVisible(String key) {
      int mode = this.choice("mode");
      if (key.equals("lock_on_vertical")) {
         return mode == 2;
      } else if (key.equals("hor_speed") || key.equals("ver_speed")) {
         return mode != 2;
      } else if (key.equals("silent_move_correct") || key.equals("silent_ignore_manual_aim")) {
         return mode == 3;
      } else if (key.equals("require_sprint")) {
         return mode != 3;
      } else if (key.equals("sort_by")) {
         return this.choice("mode_target") == 1;
      } else {
         return key.equals("allow_exceed_fov") ? this.choice("mode_target") == 0 : true;
      }
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 1 || type == 13 || type == 18;
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 13) {
         if (this.silent) {
            event.active = true;
            if (this.flag("silent_move_correct")) {
               event.remapMovement = true;
            }
         }
      } else if (event.type == 18) {
         this.updateTarget(context);
      } else if (event.type == 1) {
         this.rotate(context, event);
      }
   }

   private strictfp boolean focused(ClientContext context) {
      return context.ready() && context.minecraft().inGameHasFocus && context.minecraft().currentScreen == null;
   }

   private strictfp void clearTarget() {
      this.target = null;
      this.targetActive = false;
   }

   @Override
   protected strictfp void onDisable(ClientContext context) {
      this.clearTarget();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext context) {
      this.clearTarget();
      this.silent = false;
      this.graceTicks = 0;
   }

   public strictfp EntityPlayer currentTarget() {
      return this.target;
   }

   public strictfp boolean targetActive() {
      return this.targetActive;
   }

   private strictfp boolean consider(
      ClientContext context, EntityPlayer candidate, Vec3 eye, float viewYaw, boolean locked, AimAssistModule.Score best, AimAssistModule.Candidate result
   ) {
      if (candidate.isDead || candidate.isPlayerSleeping()) {
         return false;
      } else if (this.flag("ignore_invis") && candidate.isInvisibleToPlayer(context.player())) {
         return false;
      } else if ((RelationshipService.flags(candidate) & 7) != 0) {
         return false;
      } else {
         Vec3 position = new Vec3(candidate.posX, candidate.posY, candidate.posZ);
         float prediction = this.number("predict");
         if (prediction > 0.0F && candidate instanceof EntityOtherPlayerMP) {
            position = RotationMath.interpolate(position, AimAssistModule.ServerPosition.read((EntityOtherPlayerMP)candidate), (double)prediction);
         }

         AxisAlignedBB box = candidate.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
         Vec3 nearest = new Vec3(
            RotationMath.clamp(box.minX, box.maxX, eye.xCoord),
            RotationMath.clamp(box.minY, box.maxY, eye.yCoord),
            RotationMath.clamp(box.minZ, box.maxZ, eye.zCoord)
         );
         double distance = RotationMath.distance(eye, nearest);
         boolean enabled = !((double)((float)this.range("range").lower) > distance);
         if (distance > best.distance) {
            return false;
         } else {
            Vec3 point = aimPoint(
               eye, position, nearest, distance, this.number("hor_hitbox_correct"), this.number("ver_hitbox_correct"), this.number("aim_randomization")
            );
            float angle = RotationMath.abs(RotationMath.wrap(RotationMath.angles(eye, point)[0] - viewYaw));
            if (angle > best.angle) {
               if (!locked) {
                  return false;
               }

               enabled = this.flag("allow_exceed_fov");
            }

            float health = candidate.getHealth();
            if (Float.isNaN(health)) {
               health = 20.0F;
            }

            if (!(health > best.health) && candidate.hurtTime <= best.hurtTime) {
               if (this.flag("require_line_of_sight") && context.world().rayTraceBlocks(eye, point, false, true, true) != null) {
                  return false;
               } else {
                  if (this.choice("mode_target") != 0) {
                     switch (this.choice("sort_by")) {
                        case 0:
                           best.distance = distance;
                           break;
                        case 1:
                           best.health = health;
                           break;
                        case 2:
                           int hash = RotationMath.tieHash(candidate.getEntityId(), this.tieCounter++);
                           if (candidate.hurtTime == best.hurtTime && Integer.compareUnsigned(hash, best.tieBreak) < 0) {
                              return false;
                           }

                           best.hurtTime = candidate.hurtTime;
                           best.tieBreak = hash;
                           break;
                        case 3:
                           best.angle = angle;
                           break;
                        default:
                           throw new IllegalStateException("target sort");
                     }
                  } else {
                     best.angle = angle;
                  }

                  result.point = point;
                  result.enabled = enabled;
                  if (locked) {
                     this.targetActive = enabled;
                  }

                  return true;
               }
            } else {
               return false;
            }
         }
      }
   }

   private strictfp void updateTarget(ClientContext context) {
      RotationService.clearManual();
      if (!this.focused(context)) {
         this.clearTarget();
      } else if (this.flag("require_sprint") && this.choice("mode") != 3 && !context.player().isSprinting()) {
         this.targetActive = false;
      } else {
         int keyCode = context.minecraft().gameSettings.keyBindAttack.getKeyCode();
         boolean attack = Mouse.isButtonDown(keyCode == -99 ? 1 : 0);
         if (this.flag("require_mouse_pressed")) {
            if (!attack) {
               this.graceTicks = (this.random.chance(0.5F) ? 1 : 0) + 1;
               this.clearTarget();
               return;
            }

            if (this.graceTicks != 0 && this.choice("mode") < 2) {
               this.graceTicks--;
               this.clearTarget();
               return;
            }
         }

         MovingObjectPosition hit = context.minecraft().objectMouseOver;
         if (this.flag("disable_on_block_break") && attack && hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            this.targetActive = false;
         } else if (this.flag("require_weapon") && !this.weapons.isWeapon(context.player())) {
            this.clearTarget();
         } else {
            Vec3 eye = new Vec3(context.player().posX, context.player().posY + 1.62, context.player().posZ);
            float viewYaw = RotationService.storedRotationValid() ? RotationService.storedYaw() : context.player().rotationYaw;
            AimAssistModule.Score best = new AimAssistModule.Score((double)((float)this.range("range").upper), (float)this.range("fov").upper);
            AimAssistModule.Candidate selected = new AimAssistModule.Candidate();
            if (this.choice("mode_target") == 0 && this.target != null) {
               if (this.consider(context, this.target, eye, viewYaw, true, best, selected)) {
                  this.previousAim = this.currentAim;
                  this.currentAim = selected.point;
                  return;
               }

               this.clearTarget();
            }

            EntityPlayer chosen = null;

            for (int i = 0; i < context.world().playerEntities.size(); i++) {
               EntityPlayer candidate = (EntityPlayer)context.world().playerEntities.get(i);
               if (candidate != context.player()) {
                  AimAssistModule.Candidate result = new AimAssistModule.Candidate();
                  if (this.consider(context, candidate, eye, viewYaw, false, best, result)) {
                     chosen = candidate;
                     selected = result;
                  }
               }
            }

            if (chosen == null) {
               this.clearTarget();
            } else {
               this.previousAim = this.target == chosen ? this.currentAim : selected.point;
               this.currentAim = selected.point;
               this.targetActive = selected.enabled;
               this.target = chosen;
            }
         }
      }
   }

   public static strictfp Vec3 aimPoint(Vec3 localEye, Vec3 predicted, Vec3 nearest, double distance, float horizontal, float vertical, float randomization) {
      Vec3 targetEye = new Vec3(predicted.xCoord, predicted.yCoord + 1.62, predicted.zCoord);
      double x = targetEye.xCoord;
      double z = targetEye.zCoord;
      if (horizontal > 0.0F) {
         float fraction = distance < 3.5 ? (float)((double)horizontal * (distance / 3.5)) : horizontal;
         Vec3 point = RotationMath.interpolate(targetEye, nearest, (double)fraction);
         x = point.xCoord;
         z = point.zCoord;
      }

      double y = targetEye.yCoord + (nearest.yCoord - targetEye.yCoord) * (double)vertical;
      double sum = localEye.xCoord + localEye.yCoord;
      sum += localEye.zCoord;
      sum += x;
      sum += y;
      sum += z;
      float input = (float)(sum * 0.1);
      float noiseX = AimNoise.sample(input);
      float noiseY = AimNoise.sample(10000.0F + input);
      double halfX = (double)noiseX * 0.5;
      double halfY = (double)noiseY * 0.5;
      float noiseZ = AimNoise.sample(20000.0F + input);
      double halfZ = (double)noiseZ * 0.5;
      float randomScale = randomization;
      if (distance < 3.5) {
         randomScale = (float)((double)randomization * (distance / 3.5 * 0.9 + 0.1));
      }

      double scale = (double)randomScale;
      return new Vec3(x + scale * halfX, y + scale * halfY, z + scale * halfZ);
   }

   private strictfp void rotate(ClientContext context, SemanticEvent event) {
      if (this.focused(context)) {
         float currentYaw = context.player().rotationYaw + RotationService.pendingYaw();
         float currentPitch = context.player().rotationPitch + RotationService.pendingPitch();
         if (!this.targetActive) {
            if (this.silent) {
               if (RotationService.storedRotationValid() && RotationService.claim(100)) {
                  float yaw = RotationMath.wrap(RotationService.storedYaw() - currentYaw);
                  float pitch = RotationService.storedPitch() - currentPitch;
                  if (RotationMath.magnitude(yaw, pitch) < 10.0F) {
                     this.silent = false;
                  }

                  this.applyStep(yaw, pitch, (float)event.elapsedSeconds);
               } else {
                  this.silent = false;
               }
            }
         } else if (RotationService.claim(100)) {
            if (!this.silent && this.choice("mode") == 3) {
               this.silent = true;
            }

            if (!this.silent || RotationService.storedRotationValid()) {
               double fraction = (double)event.timerRenderPartialTicks;
               Vec3 previous = new Vec3(context.player().prevPosX, context.player().prevPosY, context.player().prevPosZ);
               Vec3 current = new Vec3(context.player().posX, context.player().posY, context.player().posZ);
               Vec3 eye = RotationMath.interpolate(previous, current, fraction).addVector(0.0, 1.62, 0.0);
               float[] desired = RotationMath.angles(eye, RotationMath.interpolate(this.previousAim, this.currentAim, fraction));
               float yaw = RotationMath.wrap(desired[0] - currentYaw);
               float pitch = desired[1] - currentPitch;
               if (this.choice("mode") == 3 && !this.flag("silent_ignore_manual_aim")) {
                  float restoreYaw = RotationMath.wrap(RotationService.storedYaw() - currentYaw);
                  float restorePitch = RotationService.storedPitch() - currentPitch;
                  float targetYaw = RotationMath.wrap(desired[0] - RotationService.storedYaw());
                  float targetPitch = desired[1] - RotationService.storedPitch();
                  if (RotationMath.magnitude(restoreYaw, restorePitch) < 10.0F
                     && RotationMath.magnitude(targetYaw, targetPitch) < RotationMath.magnitude(yaw, pitch)) {
                     RotationService.add(restoreYaw, restorePitch);
                     return;
                  }
               }

               this.applyStep(yaw, pitch, (float)event.elapsedSeconds);
            }
         }
      }
   }

   private strictfp void applyStep(float yaw, float pitch, float time) {
      float[] step = this.aimStep(yaw, pitch, time, RecoveredRandom.now(), RotationService.manualYaw(), RotationService.manualPitch());
      if (this.flag("require_mouse_moved")) {
         RotationService.clearManual();
      }

      RotationService.add(step[0], step[1]);
   }

   public strictfp float[] aimStep(float yaw, float pitch, float time, double now, float manualYaw, float manualPitch) {
      float deadZone = (float)this.range("fov").lower;
      yaw = yaw > 0.0F ? RotationMath.max(yaw - deadZone, 0.0F) : RotationMath.min(yaw + deadZone, 0.0F);
      pitch = pitch > 0.0F ? RotationMath.max(pitch - deadZone, 0.0F) : RotationMath.min(deadZone + pitch, 0.0F);
      int mode = this.choice("mode");
      if (mode != 2) {
         float draw = this.pitchDamping.sample(40.0F, 120.0F, 5.0, now, this.random);
         double slope = -1.0 / (double)draw;
         float magnitudeScale = RotationMath.max((float)(slope * (double)RotationMath.abs(yaw) + 1.0), 0.0F);
         pitch *= magnitudeScale;
      }

      if (mode == 2) {
         if (!this.flag("lock_on_vertical")) {
            pitch = 0.0F;
         }
      } else {
         float hSpeed = this.number("hor_speed");
         float vSpeed = this.number("ver_speed");
         if (mode == 3) {
            hSpeed = RotationMath.max(5.0F, hSpeed);
            if (5.0F > vSpeed) {
               vSpeed = 5.0F;
            }
         }

         float hHalf = hSpeed * 0.5F;
         float hSquared = hHalf * hHalf;
         float yawFactor = this.horizontalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * hSquared;
         float vHalf = vSpeed * 0.5F;
         float vSquared = vHalf * vHalf;
         float pitchFactor = this.verticalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * vSquared;
         float global = this.speedFactor.sample(0.95F, 1.05F, 60.0, now, this.random);
         if (mode == 1) {
            yawFactor *= 5.0F;
            yawFactor *= global;
            float yawStep = yawFactor * time;
            yaw = RotationMath.clamp(-yawStep, yawStep, yaw);
            pitchFactor *= 5.0F;
            pitchFactor *= global;
            float pitchStep = pitchFactor * time;
            pitch = RotationMath.clamp(-pitchStep, pitchStep, pitch);
         } else {
            float rawYaw = yawFactor * yaw;
            float rawPitch = pitchFactor * pitch;
            rawYaw *= global;
            rawPitch *= global;
            rawYaw *= time;
            rawPitch *= time;
            if (!(RotationMath.abs(rawYaw) > RotationMath.abs(yaw))) {
               yaw = rawYaw;
            }

            if (!(RotationMath.abs(rawPitch) > RotationMath.abs(pitch))) {
               pitch = rawPitch;
            }
         }

         float bound = (float)((double)RotationMath.abs(yaw) * 0.1);
         pitch += this.pitchNoise.sample(-bound, bound, 1.0, now, this.random);
      }

      if (this.flag("require_mouse_moved")) {
         float yawMagnitude = RotationMath.abs(yaw);
         float pitchMagnitude = RotationMath.abs(pitch);
         float manualYawMagnitude = RotationMath.abs(manualYaw);
         float manualPitchMagnitude = RotationMath.abs(manualPitch);
         boolean adjustYaw = manualYawMagnitude < yawMagnitude && yaw != 0.0F;
         float ratio = manualYawMagnitude / yawMagnitude;
         float scaledYaw = ratio * yaw;
         if (manualPitchMagnitude < pitchMagnitude && pitch != 0.0F) {
            float pitchRatio = manualPitchMagnitude / pitchMagnitude;
            pitch *= pitchRatio;
         }

         if (adjustYaw) {
            yaw = scaledYaw;
         }
      }

      return new float[]{yaw, pitch};
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         return StatusText.label(this.getName(), index);
      }
   }

   private static final strictfp class Candidate {
      Vec3 point;
      boolean enabled;

      private Candidate() {
      }
   }

   public static final strictfp class Score {
      public double distance;
      public float health = Float.MAX_VALUE;
      public float angle;
      public int hurtTime = Integer.MAX_VALUE;
      public int tieBreak;

      public Score(double range, float fov) {
         this.distance = range;
         this.angle = fov;
      }
   }

   private static final class ServerPosition {
      static final Field X = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPX", "field_71185_c");
      static final Field Y = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPY", "field_71182_d");
      static final Field Z = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPZ", "field_71183_e");

      static strictfp Vec3 read(EntityOtherPlayerMP player) {
         try {
            return new Vec3(X.getDouble(player), Y.getDouble(player), Z.getDouble(player));
         } catch (IllegalAccessException var2) {
            throw new IllegalStateException("Cannot read target interpolation position", var2);
         }
      }
   }
}
