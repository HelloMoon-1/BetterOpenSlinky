package dev.slinky.client.rotation;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.numeric.FloatNumeric;
import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;

public final strictfp class RotationService {
   private static int priority;
   private static int deltaAge;
   private static float quantum;
   private static float pendingYaw;
   private static float pendingPitch;
   private static float manualYaw;
   private static float manualPitch;
   private static float cameraYaw;
   private static float cameraPitch;
   private static float retainedYaw;
   private static float retainedPitch;
   private static float savedMouseYaw;
   private static float savedMousePitch;
   private static float displayYaw;
   private static float displayPitch;
   private static boolean active;
   private static boolean useRetained;
   private static boolean remap;
   private static boolean keysRemapped;
   private static boolean restoreCameraPending;
   private static boolean mousePending;
   private static int savedKeys;
   private static KeyBinding[] remappedKeys;
   private static Entity mouseEntity;
   private static final RotationService.RotationSnapshot camera = new RotationService.RotationSnapshot();
   private static final RotationService.RotationSnapshot renderInfo = new RotationService.RotationSnapshot();
   private static final Field PRESSED = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");

   private RotationService() {
   }

   public static strictfp void beginFrame(ClientContext context, double elapsedSeconds, float partialTicks) {
      if (!context.ready()) {
         reset(context);
      } else {
         float sensitivity = context.minecraft().gameSettings.mouseSensitivity * 0.6F + 0.2F;
         quantum = sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
         int previousAge = deltaAge++;
         if (previousAge > 0) {
            pendingPitch = 0.0F;
            pendingYaw = 0.0F;
         }

         priority = 0;
      }
   }

   public static strictfp boolean claim(int requested) {
      if (Integer.compareUnsigned(requested, priority) < 0) {
         return false;
      } else {
         priority = requested;
         return true;
      }
   }

   public static strictfp boolean storedRotationValid() {
      return active;
   }

   public static strictfp float storedYaw() {
      return retainedYaw;
   }

   public static strictfp float storedPitch() {
      return retainedPitch;
   }

   public static strictfp float cameraYaw() {
      return cameraYaw;
   }

   public static strictfp float cameraPitch() {
      return cameraPitch;
   }

   public static strictfp float pendingYaw() {
      return pendingYaw;
   }

   public static strictfp float pendingPitch() {
      return pendingPitch;
   }

   public static strictfp float manualYaw() {
      return manualYaw;
   }

   public static strictfp float manualPitch() {
      return manualPitch;
   }

   public static strictfp void clearManual() {
      manualPitch = 0.0F;
      manualYaw = 0.0F;
   }

   public static strictfp void retain(ClientContext context, boolean retained, float yaw, float pitch) {
      endMovement(context);
      if (!active && context.player() != null) {
         active = true;
         cameraYaw = context.player().rotationYaw;
         cameraPitch = context.player().rotationPitch;
      }

      useRetained = retained;
      retainedYaw = yaw;
      retainedPitch = pitch;
      remap = false;
   }

   public static strictfp void applyPending(ClientContext context) {
      applyPending(context, false);
   }

   public static strictfp void applyPending(ClientContext context, boolean alsoArm) {
      if (context.player() != null) {
         if (quantum != 0.0F) {
            float yawStep = quantize(pendingYaw, quantum);
            float pitchStep = quantize(pendingPitch, quantum);
            pendingYaw -= yawStep;
            pendingPitch -= pitchStep;
            if (yawStep != 0.0F || pitchStep != 0.0F) {
               float yaw = yawStep + context.player().rotationYaw;
               float pitch = pitchStep + context.player().rotationPitch;
               setRotation(context.player(), yaw, pitch, true);
               if (alsoArm) {
                  setArms(context, yaw, pitch);
               }
            }
         }
      }
   }

   public static strictfp void add(float yaw, float pitch) {
      pendingYaw += yaw;
      pendingPitch += pitch;
      deltaAge = 0;
   }

   public static strictfp void manualDelta(float yaw, float pitch) {
      manualYaw += yaw;
      manualPitch += pitch;
   }

   public static strictfp boolean mouseAngles(Entity entity, ClientContext context, float yaw, float pitch) {
      return false;
   }

   public static strictfp void prepareMouse(ClientContext context) {
      closeMouse();
      Entity player = context.player();
      if (player != null) {
         savedMouseYaw = player.rotationYaw;
         savedMousePitch = player.rotationPitch;
         if (restoreCameraPending) {
            restoreCameraPending = false;
            setRotation(player, cameraYaw, cameraPitch, true);
         }

         if (active) {
            mousePending = true;
            mouseEntity = player;
            setRotation(player, cameraYaw, cameraPitch, false);
         }
      }
   }

   private static strictfp void finishMouse(Entity player) {
      if (mousePending) {
         if (player != mouseEntity) {
            closeMouse();
         } else {
            cameraYaw = player.rotationYaw;
            cameraPitch = player.rotationPitch;
            closeMouse();
         }
      }
   }

   private static strictfp void closeMouse() {
      Entity player = mousePending ? mouseEntity : null;
      mousePending = false;
      mouseEntity = null;
      if (player != null) {
         setRotation(player, savedMouseYaw, savedMousePitch, true);
      }
   }

   public static strictfp void endRender() {
      endRenderInfo();
      endCamera();
      closeMouse();
   }

   public static strictfp void beforeDisplay(ClientContext context) {
      if (context.ready()) {
         float yaw = context.player().rotationYaw - displayYaw;
         float pitch = context.player().rotationPitch - displayPitch;
         if (((Float.floatToRawIntBits(yaw) | Float.floatToRawIntBits(pitch)) & 2147483647) != 0) {
            manualDelta(yaw, pitch);
         }
      }
   }

   public static strictfp void afterDisplay(ClientContext context) {
      if (context.ready()) {
         displayYaw = context.player().rotationYaw;
         displayPitch = context.player().rotationPitch;
      }
   }

   public static strictfp void restoreCamera(float yaw, float pitch) {
      cameraYaw = RotationMath.wrap(yaw - cameraYaw) + cameraYaw;
      cameraPitch = pitch;
      restoreCameraPending = true;
   }

   public static strictfp float quantize(float value, float quantum) {
      if (quantum != 0.0F && (-quantum >= value || value >= quantum)) {
         float quotient = value / quantum;
         float whole = FloatNumeric.truncate(quotient);
         return whole * quantum;
      } else {
         return 0.0F;
      }
   }

   public static strictfp void finishFrame(ClientContext context, SemanticEvent event) {
      endMovement(context);
      if (context.ready()) {
         boolean requested = event.active;
         if (requested) {
            if (!active) {
               cameraYaw = context.player().rotationYaw;
               cameraPitch = context.player().rotationPitch;
            }

            active = true;
            useRetained = event.overrideRotation;
            retainedYaw = event.yaw;
            retainedPitch = event.pitch;
            remap = event.remapMovement;
         } else if (active) {
            active = false;
            float yaw = useRetained ? retainedYaw : cameraYaw;
            float pitch = useRetained ? retainedPitch : cameraPitch;
            yaw = RotationMath.wrap(yaw - context.player().rotationYaw) + context.player().rotationYaw;
            setRotation(context.player(), yaw, pitch, true);
            setArms(context, yaw, pitch);
         }
      }
   }

   public static strictfp int remapMask(int mask, float yawDifference) {
      int forward = (mask & 1) - (mask >>> 1 & 1);
      int sideways = (mask >>> 2 & 1) - (mask >>> 3 & 1);
      if (forward == 0 && sideways == 0) {
         return 0;
      } else {
         int sector = forward == 0 ? (sideways == 1 ? 2 : 6) : (forward == -1 ? 5 - (sideways + 1) : (sideways == 1 ? 1 : (sideways == -1 ? 7 : 0)));
         float divided = RotationMath.wrap(yawDifference) / 45.0F;
         float rounded = FloatNumeric.round(divided);
         int[] table = new int[]{1, 5, 4, 6, 2, 10, 8, 9};
         return table[RecoveredMath.truncateInt(rounded) + sector & 7];
      }
   }

   private static strictfp KeyBinding[] movementKeys(ClientContext context) {
      GameSettings g = context.minecraft().gameSettings;
      return new KeyBinding[]{g.keyBindForward, g.keyBindBack, g.keyBindLeft, g.keyBindRight};
   }

   public static strictfp void beginMovement(ClientContext context) {
      if (remap && !keysRemapped) {
         if (context.inputReady()) {
            captureMovement(movementKeys(context), context.player().rotationYaw - retainedYaw);
         }

         keysRemapped = true;
      }
   }

   private static strictfp void captureMovement(KeyBinding[] keys, float yawDifference) {
      remappedKeys = keys;
      savedKeys = 0;

      for (int i = 0; i < 4; i++) {
         if (keys[i].isKeyDown()) {
            savedKeys |= 1 << i;
         }
      }

      int mask = remapMask(savedKeys, yawDifference);
      setKeys(keys, mask);
      keysRemapped = true;
   }

   public static strictfp void endMovement(ClientContext context) {
      if (context == null) {
         if (remappedKeys != null) {
            setKeys(remappedKeys, savedKeys);
         }

         remappedKeys = null;
      }

      if (keysRemapped) {
         if (context != null && context.inputReady()) {
            setKeys(movementKeys(context), savedKeys);
            remappedKeys = null;
         }

         remap = false;
         keysRemapped = false;
      }
   }

   public static strictfp void beginCamera(ClientContext context) {
      if (mousePending && mouseEntity != context.player()) {
         closeMouse();
      }

      if (active && camera.entity == null && context.player() != null) {
         if (context.minecraft().renderGlobal != null) {
            context.minecraft().renderGlobal.setDisplayListEntitiesDirty();
         }

         finishMouse(context.player());
         captureCamera(context.player());
      }
   }

   private static strictfp void captureCamera(Entity entity) {
      camera.capture(entity);
   }

   public static strictfp void endCamera() {
      camera.restore();
   }

   public static strictfp void beginRenderInfo(ClientContext context) {
      if (active && renderInfo.entity == null) {
         renderInfo.capture(context.player());
      }
   }

   public static strictfp void endRenderInfo() {
      renderInfo.restore();
   }

   public static strictfp float renderViewPitch(float vanilla) {
      return active ? cameraPitch : vanilla;
   }

   public static strictfp float renderViewYaw(float vanilla) {
      return active ? cameraYaw : vanilla;
   }

   private static strictfp void setRotation(Entity entity, float yaw, float pitch, boolean previous) {
      entity.rotationYaw = yaw;
      entity.rotationPitch = RotationMath.clamp(-90.0F, 90.0F, pitch);
      if (previous) {
         entity.prevRotationYaw = yaw;
         entity.prevRotationPitch = entity.rotationPitch;
      }
   }

   private static strictfp void setArms(ClientContext context, float yaw, float pitch) {
      context.player().prevRenderArmYaw = yaw;
      context.player().prevRenderArmPitch = pitch;
      context.player().renderArmYaw = yaw;
      context.player().renderArmPitch = pitch;
   }

   private static strictfp void setKeys(KeyBinding[] keys, int mask) {
      try {
         for (int i = 0; i < 4; i++) {
            PRESSED.setBoolean(keys[i], (mask & 1 << i) != 0);
         }
      } catch (IllegalAccessException var3) {
         throw new IllegalStateException(var3);
      }
   }

   public static strictfp void reset(ClientContext context) {
      endRender();
      endMovement(null);
      remap = false;
      useRetained = false;
      active = false;
      deltaAge = 0;
      priority = 0;
      quantum = 0.0F;
      mousePending = false;
      restoreCameraPending = false;
      mouseEntity = null;
      manualPitch = 0.0F;
      manualYaw = 0.0F;
      pendingPitch = 0.0F;
      pendingYaw = 0.0F;
      displayPitch = 0.0F;
      displayYaw = 0.0F;
      retainedPitch = 0.0F;
      retainedYaw = 0.0F;
      cameraPitch = 0.0F;
      cameraYaw = 0.0F;
      savedMousePitch = 0.0F;
      savedMouseYaw = 0.0F;
      savedKeys = 0;
   }

   private static final strictfp class RotationSnapshot {
      Entity entity;
      float yaw;
      float pitch;
      float prevYaw;
      float prevPitch;

      private RotationSnapshot() {
      }

      strictfp void capture(Entity value) {
         if (this.entity == null && value != null) {
            this.entity = value;
            this.yaw = value.rotationYaw;
            this.pitch = value.rotationPitch;
            this.prevYaw = value.prevRotationYaw;
            this.prevPitch = value.prevRotationPitch;
            RotationService.setRotation(value, RotationService.cameraYaw, RotationService.cameraPitch, true);
         }
      }

      strictfp void restore() {
         if (this.entity != null) {
            RotationService.setRotation(this.entity, this.yaw, this.pitch, false);
            this.entity.prevRotationYaw = this.prevYaw;
            this.entity.prevRotationPitch = RotationMath.clamp(-90.0F, 90.0F, this.prevPitch);
            this.entity = null;
         }
      }
   }
}
