package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.inventory.ItemActionHelper;
import dev.slinky.client.placement.PlacementFrame;
import dev.slinky.client.placement.PlacementSelection;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.blockin.BlockInPlacementPlan;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

/**
 * Slinky's enclosure policy with the donor's support/face/space validation order.
 * See BlockInPlacementPlan for the precise scope of the donor adaptation. This
 * module deliberately does not lock movement, consume keys or spoof rotations.
 */
public final strictfp class BlockInModule extends EvidenceModule {
   private final ModuleManager modules;
   private Object player;
   private Object world;
   private Object controller;
   private BlockInPlacementPlan plan;
   private BlockInPlacementPlan.Target target;
   private boolean stopped;
   private int slotAtTickStart = -1;
   private int lastPlanTick = Integer.MIN_VALUE;
   private int lastActionTick = Integer.MIN_VALUE;
   private int plannedTick = Integer.MIN_VALUE;
   private int elapsedTicks;
   private int idleTicks;
   private int attempts;
   private int observationTicks;
   private float observedManualYaw;
   private float observedManualPitch;

   public BlockInModule(ModuleManager modules) {
      super("blockin", "Block In", ModuleCategory.UTILITY);
      this.modules = modules;
   }

   @Override
   public boolean listens(int type) {
      // 18 is post-entities, BEFORE GameHooks applies the ordinary visible turn.
      // No placement or slot selection is performed by a render/frame event.
      return type == 18 || type == 26;
   }

   @Override
   protected void onEnable(ClientContext c) {
      this.reset();
   }

   private boolean context(ClientContext c) {
      if (this.stopped || !this.modules.requestedEnabled(this)) {
         return false;
      }
      if (!ItemActionHelper.ready(c)) {
         this.stop();
         return false;
      }
      if (this.player == null) {
         this.player = c.player();
         this.world = c.world();
         this.controller = c.minecraft().playerController;
         this.plan = new BlockInPlacementPlan(c);
      } else if (this.player != c.player() || this.world != c.world() || this.controller != c.minecraft().playerController) {
         this.stop();
         return false;
      }
      if (!this.plan.anchored(c) || c.player().ridingEntity != null) {
         this.stop();
         return false;
      }
      return true;
   }

   private static boolean pending(KeyBinding key) {
      return KeyState.physical(key) || key.isKeyDown() || ActionAccess.presses(key) > 0;
   }

   private boolean manualInput(ClientContext c) {
      GameSettings g = c.minecraft().gameSettings;
      if (this.slotAtTickStart < 0 || c.player().inventory.currentItem != this.slotAtTickStart
         || pending(g.keyBindUseItem) || pending(g.keyBindAttack) || pending(g.keyBindPickBlock)
         || pending(g.keyBindDrop) || pending(g.keyBindForward) || pending(g.keyBindBack)
         || pending(g.keyBindLeft) || pending(g.keyBindRight) || pending(g.keyBindJump) || pending(g.keyBindSneak)) {
         return true;
      }
      for (KeyBinding key : g.keyBindsHotbar) {
         if (pending(key)) {
            return true;
         }
      }
      return c.player().isUsingItem() || c.minecraft().playerController.getIsHittingBlock();
   }

   private boolean cameraInput() {
      // Observe, but never clear, the shared manual-angle accumulator. A user
      // turn takes precedence over our next visible aiming step.
      float yaw = RotationService.manualYaw();
      float pitch = RotationService.manualPitch();
      boolean changed = Float.compare(yaw, this.observedManualYaw) != 0 || Float.compare(pitch, this.observedManualPitch) != 0;
      this.observedManualYaw = yaw;
      this.observedManualPitch = pitch;
      return changed;
   }

   @Override
   public void onClientTickStart(ClientContext c) {
      // Capture before vanilla processes number keys / the mouse wheel. Never
      // change the slot in this callback: manual input has not been consumed yet.
      this.target = null;
      this.plannedTick = Integer.MIN_VALUE;
      this.slotAtTickStart = c.ready() ? c.player().inventory.currentItem : -1;
   }

   @Override
   public void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 26) {
         this.onDisable(c);
         return;
      }
      if (e.type != 18 || !this.context(c)) {
         return;
      }
      int tick = c.player().ticksExisted;
      if (tick == this.lastPlanTick) {
         return;
      }
      this.lastPlanTick = tick;
      this.target = null;
      if (++this.elapsedTicks > 240 || this.attempts >= 48) {
         this.stop();
         return;
      }
      // Allow vanilla's local placement feedback to settle. Re-scan the live
      // world afterwards; no permanent "placed" bits or fake acknowledgements.
      if (this.observationTicks > 0) {
         this.observationTicks--;
         return;
      }
      if (this.plan.complete(c)) {
         this.stop();
         return;
      }
      boolean cameraChanged = this.cameraInput();
      if (cameraChanged || this.manualInput(c) || this.flag("require_ground") && !c.player().onGround
         || ModuleActionService.busy()) {
         return;
      }
      BlockInPlacementPlan.Target next = this.plan.find(c);
      if (next == null) {
         if (++this.idleTicks >= 40) {
            this.stop();
         }
         return;
      }
      this.idleTicks = 0;
      PlacementFrame frame = new PlacementFrame.Game(c, this.modules, this);
      if (!frame.claim(500)) {
         return;
      }
      float[] angles = RotationMath.angles(frame.eye(), next.point);
      float yaw = RotationMath.wrap(angles[0] - (frame.yaw() + frame.offsetYaw()));
      float pitch = angles[1] - (frame.pitch() + frame.offsetPitch());
      // Existing profile setting remains a 0.3..1.0 visible turn-speed fraction.
      float speed = this.number("speed");
      if (Float.isNaN(speed) || Float.isInfinite(speed)) {
         this.stop();
         return;
      }
      float limit = 90.0F * Math.max(0.3F, Math.min(1.0F, speed));
      frame.publish(Math.max(-limit, Math.min(limit, yaw)), Math.max(-limit, Math.min(limit, pitch)));
      this.target = next;
      this.plannedTick = tick;
   }

   @Override
   public void onClientTick(ClientContext c) {
      if (!this.context(c)) {
         return;
      }
      int tick = c.player().ticksExisted;
      BlockInPlacementPlan activePlan = this.plan;
      BlockInPlacementPlan.Target next = this.target;
      this.target = null;
      if (next == null || this.plannedTick != tick || this.lastActionTick == tick || this.cameraInput() || this.manualInput(c)
         || this.flag("require_ground") && !c.player().onGround || !this.plan.valid(c, next)) {
         return;
      }
      // The target must match the REAL ray after GameHooks.afterEntities applies
      // the turn. Never right-click a pre-turn ray or an unrelated support face.
      MovingObjectPosition hit = ItemActionHelper.refreshBlockRay(c);
      if (!next.matches(hit) || !ModuleActionService.claim(this)) {
         return;
      }
      this.lastActionTick = tick;
      try {
         // Selection's boolean result matters: a foreign selection lease or an
         // empty hotbar must not fall through and use the previously held item.
         if (!PlacementSelection.select(c, this, true, true)) {
            this.stop();
            return;
         }
         ItemStack held = c.player().getHeldItem();
         if (held == null || held.stackSize <= 0 || !(held.getItem() instanceof ItemBlock)
            || !((ItemBlock)held.getItem()).getBlock().isFullCube()) {
            this.stop();
            return;
         }
         ItemBlock item = (ItemBlock)held.getItem();
         if (!activePlan.valid(c, next)
            || !c.world().canBlockBePlaced(item.getBlock(), next.destination, false, next.face, null, held)
            || !item.canPlaceBlockOnSide(c.world(), next.support, next.face, c.player(), held)) {
            activePlan.recordAttempt(next);
            return;
         }
         hit = ItemActionHelper.refreshBlockRay(c);
         if (!next.matches(hit)) {
            return;
         }
         ItemActionHelper.Result result = ItemActionHelper.useBlock(c, this, hit);
         // Interaction hooks may disable us or unload the world re-entrantly.
         if (this.stopped || this.plan != activePlan || !c.ready() || this.player != c.player() || this.world != c.world()) {
            return;
         }
         if (result == ItemActionHelper.Result.USED || result == ItemActionHelper.Result.FAILED) {
            this.attempts++;
            activePlan.recordAttempt(next);
            this.observationTicks = 4;
         } else if (result == ItemActionHelper.Result.CANCELLED) {
            this.stop();
         }
      } finally {
         // No held-slot lease survives to next tick's manual hotbar/use input.
         // PlacementSelection restores only if our selected slot is unchanged.
         PlacementSelection.release(this);
      }
   }

   private void stop() {
      this.stopped = true;
      this.target = null;
      this.plan = null;
      this.player = this.world = this.controller = null;
      PlacementSelection.release(this);
      this.modules.requestEnabled(this, false);
   }

   private void reset() {
      PlacementSelection.release(this);
      this.player = this.world = this.controller = null;
      this.plan = null;
      this.target = null;
      this.stopped = false;
      this.slotAtTickStart = -1;
      this.lastPlanTick = this.lastActionTick = this.plannedTick = Integer.MIN_VALUE;
      this.elapsedTicks = this.idleTicks = this.attempts = this.observationTicks = 0;
      this.observedManualYaw = RotationService.manualYaw();
      this.observedManualPitch = RotationService.manualPitch();
   }

   @Override
   protected void onDisable(ClientContext c) {
      this.reset();
      this.stopped = true;
   }

   @Override
   public void onWorldUnload(ClientContext c) {
      this.stop();
   }
}
