package dev.slinky.client.hook;

import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.inventory.InvWalkControllerInputAdapter;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.render.HudCombatTarget;
import dev.slinky.client.packet.NetworkSendAdapter;
import dev.slinky.client.packet.PacketPipeline;
import dev.slinky.client.placement.PlacementSelection;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.NoHurtCamModule;
import dev.slinky.module.impl.SafeWalkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;

public final class GameHooks {
   private static ModuleManager modules;
   private static SemanticEvent ray;
   private static double previousDisplaySeconds;
   private static final TargetCombatStatus combat = new TargetCombatStatus();
   private static boolean uiDrawn;
   private static boolean baseSwing;

   private GameHooks() {
   }

   public static void install(ModuleManager manager) {
      modules = manager;
      RenderHooks.install(manager);
   }

   private static boolean local(Entity entity) {
      return modules != null && entity == Minecraft.getMinecraft().thePlayer;
   }

   public static boolean mouseAngles(Entity entity, float yaw, float pitch) {
      return local(entity) && RotationService.mouseAngles(entity, modules.context(), yaw, pitch);
   }

   public static void beginMovement() {
      if (modules != null) {
         RotationService.beginMovement(modules.context());
      }
   }

   public static void endMovement() {
      if (modules != null) {
         RotationService.endMovement(modules.context());
      }
   }

   public static void updateMovement(MovementInput input) {
      beginMovement();

      try {
         input.updatePlayerMoveState();
      } finally {
         endMovement();
      }
   }

   public static void beginCamera() {
      if (modules != null) {
         RotationService.beginCamera(modules.context());
      }
   }

   public static void endCamera() {
      RotationService.endCamera();
   }

   public static void endRender() {
      RotationService.endRender();
   }

   public static void beginRenderInfo() {
      if (modules != null) {
         RotationService.beginRenderInfo(modules.context());
      }
   }

   public static void endRenderInfo() {
      RotationService.endRenderInfo();
   }

   public static float renderViewPitch(float vanilla) {
      return RotationService.renderViewPitch(vanilla);
   }

   public static float renderViewYaw(float vanilla) {
      return RotationService.renderViewYaw(vanilla);
   }

   public static void beginTick() {
      if (modules != null) {
         combat.select(modules.context(), modules);
      } else {
         combat.reset();
      }
   }

   public static EntityPlayer target() {
      return combat.target();
   }

   public static double targetDistanceSquared() {
      return combat.distanceSquared();
   }

   public static void beforeEntities(World world) {
      if (modules != null && world == modules.context().world()) {
         modules.clientInputTick();
         modules.dispatch(new SemanticEvent(16));
         beginMovement();
         modules.dispatch(new SemanticEvent(17));
      }
   }

   public static void afterEntities(World world) {
      if (modules != null && world == modules.context().world()) {
         modules.dispatch(new SemanticEvent(18));
         if (!modules.context().ready()) {
            endMovement();
         } else {
            SemanticEvent event = new SemanticEvent(13);
            event.active = false;
            event.yaw = RotationService.storedRotationValid() ? RotationService.cameraYaw() : modules.context().player().rotationYaw;
            event.pitch = RotationService.storedRotationValid() ? RotationService.cameraPitch() : modules.context().player().rotationPitch;
            modules.dispatch(event);
            RotationService.finishFrame(modules.context(), event);
            RotationService.applyPending(modules.context(), true);
         }
      }
   }

   public static void abortEntities(World world) {
      if (modules != null && world == modules.context().world()) {
         endMovement();
      }
   }

   public static SemanticEvent beforeMoveFlying(Entity player, float sideways, float forward, float friction) {
      SemanticEvent event = new SemanticEvent(21);
      event.sideways = sideways;
      event.forward = forward;
      event.factor = friction;
      if (!local(player)) {
         return event;
      } else {
         event.jump = MinecraftFields.jumpTicks((EntityPlayerSP)player) > 0 && modules.context().minecraft().gameSettings.keyBindJump.isKeyDown();
         modules.dispatch(event);
         return event;
      }
   }

   public static void afterMoveFlying(Entity player, SemanticEvent before) {
      if (local(player)) {
         SemanticEvent event = new SemanticEvent(22);
         event.sideways = before.sideways;
         event.forward = before.forward;
         event.factor = before.factor;
         event.jump = before.jump;
         modules.dispatch(event);
      }
   }

   public static boolean safeWalk(Entity entity) {
      if (local(entity)) {
         Module module = modules.find("safewalk");
         if (module instanceof SafeWalkModule && ((SafeWalkModule)module).allowed()) {
            return true;
         }
      }

      return entity.isSneaking();
   }

   public static void beginRay(float partialTicks) {
      ray = null;
      if (modules != null && modules.context().ready()) {
         RotationService.beginFrame(modules.context(), 0.0, partialTicks);
         beginTick();
         modules.dispatch(new SemanticEvent(0));
         if (modules.context().minecraft().inGameHasFocus) {
            ray = new SemanticEvent(8);
            ray.distance = 3.0;
            modules.dispatch(ray);
         }
      }
   }

   public static void afterFrameRender() {
      if (modules != null) {
         uiDrawn = true;
         RenderHooks.beforeDisplay();
      }
   }

   public static strictfp void beforeDisplay() {
      if (modules != null) {
         if (!uiDrawn) {
            RenderHooks.beforeDisplay();
         }

         uiDrawn = false;
         modules.applyPendingChanges();
         beforeDisplay((double)System.nanoTime() / 1.0E9);
      }
   }

   private static strictfp void beforeDisplay(double now) {
      if (previousDisplaySeconds == 0.0) {
         previousDisplaySeconds = now;
      }

      if (!modules.context().ready()) {
         previousDisplaySeconds = now;
      } else {
         RotationService.beforeDisplay(modules.context());
         double elapsed = now - previousDisplaySeconds;
         if (!(0.002 > elapsed)) {
            SemanticEvent frame = new SemanticEvent(1);
            frame.elapsedSeconds = elapsed;
            frame.timerRenderPartialTicks = MinecraftFields.timer(modules.context().minecraft()).renderPartialTicks;
            modules.dispatch(frame);
            RotationService.applyPending(modules.context(), false);
            previousDisplaySeconds = now;
            RotationService.afterDisplay(modules.context());
         }
      }
   }

   public static boolean customRay() {
      boolean selected = ray != null && RayQueryAdapter.update(modules, ray);
      if (ray != null) {
         PlacementSelection.frameReset();
      }

      return selected;
   }

   public static double entityReach(double vanilla) {
      return ray != null && ray.enabled ? ray.distance : vanilla;
   }

   public static float queryReach(float vanilla) {
      return ray != null && ray.enabled ? (float)Math.max((double)vanilla, ray.distance) : vanilla;
   }

   public static float hitboxBorder(float vanilla) {
      return ray == null ? vanilla : (float)((double)vanilla + ray.expand);
   }

   public static void attack(EntityPlayer player, Entity target) {
      if (modules != null && player == modules.context().player()) {
         HudCombatTarget.recordAttack(modules.context().minecraft(), target);
      }
   }

   public static boolean beforeInteraction(boolean use) {
      if (modules == null) {
         return false;
      } else {
         if (!use) {
            combat.prepare(modules.context());
         }

         SemanticEvent event = combat.event(14, use);
         modules.dispatch(event);
         if (use) {
            if (event.cancelled) {
               PlacementSelection.discardCapture();
            } else {
               PlacementSelection.capture(modules.context());
               if (!ItemActionHelper.allowVanillaUse()) {
                  PlacementSelection.discardCapture();
                  event.cancelled = true;
               }
            }
         }

         return event.cancelled;
      }
   }

   public static void afterInteraction(boolean use) {
      if (modules != null) {
         modules.dispatch(combat.event(15, use));
         if (use) {
            PlacementSelection.consume(modules.context());
         }
      }
   }

   public static void baseSwing(EntityPlayerSP player) {
      baseSwing = true;

      try {
         player.swingItem();
      } finally {
         baseSwing = false;
      }
   }

   public static boolean baseSwingOnly() {
      return baseSwing;
   }

   public static void clear() {
      ray = null;
      combat.clear();
      HudCombatTarget.clear();
      previousDisplaySeconds = 0.0;
      try {
         RenderHooks.clear();
      } finally {
         try {
            PlacementSelection.reset();
         } finally {
            try {
               RotationService.reset(null);
            } finally {
               PacketPipeline.releaseAll(false);
            }
         }
      }
   }

   public static void shutdown() {
      if (modules != null) {
         modules.shutdown();
      }

      PacketPipeline.releaseAll(true);
   }

   public static boolean beforeSend(NetworkManager receiver, Packet<?> packet) {
      if (!NetworkSendAdapter.consumeDirect(receiver, packet) && modules != null) {
         SemanticEvent event = new SemanticEvent(23);
         event.receiver = receiver;
         event.packet = packet;
         modules.dispatch(event);
         return event.cancelled;
      } else {
         return false;
      }
   }

   public static void updateController() {
      if (modules != null) {
         SemanticEvent event = new SemanticEvent(25);
         modules.dispatch(event);
         if (event.cancelled) {
            InvWalkControllerInputAdapter.drain();
         }
      }
   }

   public static void preRender() {
      if (modules != null) {
         RotationService.prepareMouse(modules.context());
         Module module = modules.find("No Hurt Cam");
         if (module != null && module.isEnabled()) {
            ((NoHurtCamModule)module).applyPreRender(modules.context());
         }
      }
   }
}
