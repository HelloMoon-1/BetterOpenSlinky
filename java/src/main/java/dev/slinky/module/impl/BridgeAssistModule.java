package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.numeric.DoubleNumeric;
import dev.slinky.client.placement.PlacementSelection;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final strictfp class BridgeAssistModule extends EvidenceModule {
   private final BridgeAssistModule.State state = new BridgeAssistModule.State();
   private int expectedSlot = -1;
   private int restorationSlot = -1;
   private boolean selectionInterrupted;
   private Object player;
   private Object world;

   public BridgeAssistModule() {
      super("bridgeassist", "Bridge Assist", ModuleCategory.UTILITY);
   }

   private strictfp BridgeAssistModule.Settings settings() {
      BridgeAssistModule.Settings s = new BridgeAssistModule.Settings();
      s.requireSneak = this.flag("require_sneak");
      s.down = this.flag("require_looking_down");
      s.notForward = this.flag("require_not_forward");
      s.holding = this.flag("require_holding_blocks");
      s.jump = this.flag("sneak_on_jump");
      s.randomize = this.flag("randomize");
      s.avoidDouble = this.flag("avoid_double_sneak");
      s.edge = this.number("edge_offset");
      s.unsneak = this.number("unsneak_delay");
      s.select = this.choice("select_blocks");
      return s;
   }

   private strictfp BridgeAssistModule.Input input(final ClientContext c) {
      return new BridgeAssistModule.Input() {
         @Override
         public strictfp boolean ready() {
            return c.inputReady();
         }

         @Override
         public strictfp boolean physicalSneak() {
            return KeyState.physical(c.minecraft().gameSettings.keyBindSneak);
         }

         @Override
         public strictfp boolean sneak() {
            return c.minecraft().gameSettings.keyBindSneak.isKeyDown();
         }

         @Override
         public strictfp boolean ground() {
            return c.player().onGround;
         }

         @Override
         public strictfp boolean block() {
            return ActionAccess.isBlock(c.player().getHeldItem());
         }

         @Override
         public strictfp float pitch() {
            return c.player().rotationPitch;
         }

         @Override
         public strictfp float yaw() {
            return c.player().rotationYaw;
         }

         @Override
         public strictfp float forward() {
            return c.player().moveForward;
         }

         @Override
         public strictfp double vertical() {
            return c.player().motionY;
         }

         @Override
         public strictfp AxisAlignedBB box() {
            return c.player().getEntityBoundingBox();
         }

         @Override
         public strictfp double[] velocity() {
            return new double[]{c.player().motionX, c.player().motionY, c.player().motionZ};
         }

         @Override
         public strictfp boolean backward() {
            return c.minecraft().gameSettings.keyBindBack.isKeyDown();
         }

         @Override
         public strictfp boolean forwardKey() {
            return c.minecraft().gameSettings.keyBindForward.isKeyDown();
         }

         @Override
         public strictfp boolean left() {
            return c.minecraft().gameSettings.keyBindLeft.isKeyDown();
         }

         @Override
         public strictfp boolean right() {
            return c.minecraft().gameSettings.keyBindRight.isKeyDown();
         }

         @Override
         public strictfp float speed() {
            return c.player().getAIMoveSpeed();
         }

         @Override
         public strictfp boolean air(int x, int y, int z) {
            return ActionAccess.registeredAir(c.world(), new BlockPos(x, y, z));
         }

         @Override
         public strictfp void sneak(boolean v) {
            KeyState.set(c.minecraft().gameSettings.keyBindSneak, v);
         }

         @Override
         public strictfp void restore() {
            KeyState.restore(c.minecraft().gameSettings.keyBindSneak);
         }

         @Override
         public strictfp MovingObjectPosition hit() {
            return c.minecraft().objectMouseOver;
         }

         @Override
         public strictfp Vec3 position() {
            return new Vec3(c.player().posX, c.player().posY, c.player().posZ);
         }
      };
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 5 || type == 14 || type == 15 || type == 16 || type == 26;
   }

   private strictfp void releaseSelection(ClientContext c, boolean reset) {
      if (c.ready() && ModuleActionService.owner() == this && this.expectedSlot >= 0
         && c.player().inventory.currentItem != this.expectedSlot) {
         this.selectionInterrupted = true;
      }

      Object owner = ModuleActionService.owner();
      PlacementSelection.release(this);
      if (owner == null || owner == this) {
         this.expectedSlot = c.ready() ? c.player().inventory.currentItem : -1;
      }

      this.restorationSlot = -1;
      if (reset) {
         this.selectionInterrupted = false;
         this.expectedSlot = -1;
      }
   }

   private strictfp boolean context(ClientContext c, BridgeAssistModule.Input i) {
      if (this.player != c.player() || this.world != c.world()) {
         this.state.clear(i);
         this.releaseSelection(c, true);
         this.player = c.player();
         this.world = c.world();
         return false;
      }

      return true;
   }

   private strictfp boolean prepare(ClientContext c, BridgeAssistModule.Input i, BridgeAssistModule.Settings s) {
      if (!this.context(c, i)) {
         return false;
      }

      if (!BridgeAssistModule.State.eligible(i, s)) {
         this.state.clear(i);
         this.releaseSelection(c, true);
         return false;
      }

      Object owner = ModuleActionService.owner();
      if ((owner == null || owner == this) && this.expectedSlot >= 0 && c.player().inventory.currentItem != this.expectedSlot) {
         this.selectionInterrupted = true;
      }

      if (s.select == 0 || this.selectionInterrupted) {
         this.releaseSelection(c, false);
      } else if (owner == null || owner == this) {
         // Selection must precede the holding-block check and the actual right click.
         int original = c.player().inventory.currentItem;
         if (PlacementSelection.select(c, this, s.select == 2, false)) {
            if (owner != this) {
               this.restorationSlot = original;
            }

            this.expectedSlot = c.player().inventory.currentItem;
            PlacementSelection.untilTickEnd(this);
         }
      }

      return true;
   }

   @Override
   public strictfp void onClientTickStart(ClientContext c) {
      BridgeAssistModule.Input i = this.input(c);
      BridgeAssistModule.Settings s = this.settings();
      if (this.prepare(c, i, s)) {
         this.state.update(i, s, RecoveredRandom.SHARED, RecoveredRandom.now());
         if (!this.state.owned) {
            this.releaseSelection(c, false);
         }
      }
   }

   @Override
   public strictfp void onClientTick(ClientContext c) {
      if (!this.context(c, this.input(c))) {
         return;
      }

      // The temporary lease is restored before END callbacks, so other actions can claim first.
      if (this.restorationSlot >= 0) {
         if (c.ready() && ModuleActionService.owner() == null && c.player().inventory.currentItem != this.restorationSlot) {
            this.selectionInterrupted = true;
         }

         this.expectedSlot = this.restorationSlot;
         this.restorationSlot = -1;
      }
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      BridgeAssistModule.Input i = this.input(c);
      if (e.type == 26 || e.type == 5 && e.module == this && !e.enabled) {
         this.state.clear(i);
         this.releaseSelection(c, true);
      } else if (e.type == 14 && !e.active) {
         BridgeAssistModule.Settings s = this.settings();
         if (this.prepare(c, i, s) && !e.cancelled && this.state.filter(i)) {
            e.cancelled = true;
         }
      } else if (e.type == 15 && !e.active && this.context(c, i)) {
         this.state.afterUse(i);
      } else if (e.type == 16) {
         // Observe hotbar input before finishTick can restore a temporary lease.
         this.prepare(c, i, this.settings());
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.state.clear(this.input(c));
      this.releaseSelection(c, true);
      this.player = this.world = null;
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.onDisable(c);
   }

   public interface Input {
      boolean ready();

      boolean physicalSneak();

      boolean sneak();

      boolean ground();

      boolean block();

      float pitch();

      float yaw();

      float forward();

      double vertical();

      AxisAlignedBB box();

      double[] velocity();

      boolean backward();

      boolean forwardKey();

      boolean left();

      boolean right();

      float speed();

      boolean air(int var1, int var2, int var3);

      void sneak(boolean var1);

      void restore();

      MovingObjectPosition hit();

      Vec3 position();
   }

   public static final class Settings {
      public boolean requireSneak;
      public boolean down;
      public boolean notForward;
      public boolean holding;
      public boolean jump;
      public boolean randomize;
      public boolean avoidDouble;
      public float edge;
      public float unsneak;
      public int select;
   }

   public static final class State {
      public boolean owned;
      public boolean inputGate;
      public boolean actionGate;
      public int cooldown;
      public int grace;
      public float countdown;
      private BlockPos pendingPlacement;
      private final RecoveredRandom.Smooth edge = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth unsneak = new RecoveredRandom.Smooth();
      private final RecoveredRandom.Smooth extra = new RecoveredRandom.Smooth();

      public strictfp boolean footprintAir(BridgeAssistModule.Input i, BridgeAssistModule.Settings s, RecoveredRandom r, double now) {
         AxisAlignedBB b = i.box();
         double baseX = (b.minX + b.maxX) * ActionConstants.d(1378288);
         double baseY = b.minY + ActionConstants.d(1378296);
         double baseZ = (b.minZ + b.maxZ) * ActionConstants.d(1378288);
         double radius = (double)s.edge;
         if (s.randomize) {
            radius -= (double)this.edge.sample(0.0F, ActionConstants.f(1378304), ActionConstants.d(1378312), now, r);
            if (0.0 > radius) {
               radius = 0.0;
            }
         }

         float forward = i.backward() ? ActionConstants.f(1378320) : 0.0F;
         if (i.forwardKey()) {
            forward += ActionConstants.f(1378324);
         }

         float sideways = i.left() ? ActionConstants.f(1378320) : 0.0F;
         if (i.right()) {
            sideways += ActionConstants.f(1378324);
         }

         double[] generated = RotationMath.movement(i.yaw(), forward, sideways, i.speed());
         double[] velocity = i.velocity();
         double x = baseX + (velocity[0] + generated[0]);
         double y = baseY + velocity[1] + 0.0;
         double z = baseZ + (velocity[2] + generated[1]);
         int lowX = RecoveredMath.truncateInt(DoubleNumeric.floor(x - radius));
         int lowZ = RecoveredMath.truncateInt(DoubleNumeric.floor(z - radius));
         int iy = RecoveredMath.truncateInt(DoubleNumeric.floor(y));
         int highX = RecoveredMath.truncateInt(DoubleNumeric.floor(x + radius));
         int highZ = RecoveredMath.truncateInt(DoubleNumeric.floor(radius + z));
         return i.air(lowX, iy, lowZ)
            && (lowZ == highZ || i.air(lowX, iy, highZ))
            && (lowX == highX || i.air(highX, iy, lowZ) && (lowZ == highZ || i.air(highX, iy, highZ)));
      }

      public static strictfp boolean eligible(BridgeAssistModule.Input i, BridgeAssistModule.Settings s) {
         return i.ready() && s.requireSneak == i.physicalSneak()
            && (!s.down || !(ActionConstants.f(1378240) > i.pitch()))
            && (!s.notForward || !(i.forward() > 0.0F));
      }

      public strictfp void update(BridgeAssistModule.Input i, BridgeAssistModule.Settings s, RecoveredRandom r, double now) {
         this.inputGate = this.actionGate = false;
         this.pendingPlacement = null;
         if (i.ready()) {
            if (this.cooldown > 0) {
               this.cooldown--;
            }

            if (eligible(i, s)) {
               this.actionGate = s.select != 0;
               if (s.holding) {
                  if (i.block()) {
                     this.grace = 2;
                  } else {
                     if (this.grace <= 0) {
                        this.release(i);
                        return;
                     }

                     this.grace--;
                  }
               }

               this.owned = true;
               boolean sneaking = i.sneak();
               if (sneaking) {
                  boolean countdownNeeded = false;
                  if (s.requireSneak) {
                     if (i.forward() >= 0.0F || this.cooldown <= 0) {
                        this.inputGate = s.avoidDouble;
                        return;
                     }
                  } else if (!i.ground() && (!s.jump || 0.0 > i.vertical())) {
                     countdownNeeded = true;
                  }

                  if (!countdownNeeded && this.footprintAir(i, s, r, now)) {
                     this.inputGate = s.avoidDouble;
                  } else {
                     this.countdown = this.countdown + ActionConstants.f(1378244);
                     if (this.countdown > 0.0F) {
                        this.inputGate = s.avoidDouble;
                     } else {
                        i.sneak(false);
                        float next = s.unsneak;
                        if (s.randomize) {
                           next += this.unsneak.sample(ActionConstants.f(1378248), ActionConstants.f(1378252), ActionConstants.d(1378256), now, r);
                           if (this.extra.gate(ActionConstants.f(1378264), ActionConstants.f(1378268), ActionConstants.d(1378272), now, r)) {
                              next += r.interval(0.0F, ActionConstants.f(1378280));
                           }
                        }

                        this.countdown += next;
                     }
                  }
               } else if (i.ground() || s.jump && !(0.0 > i.vertical())) {
                  if (this.footprintAir(i, s, r, now)) {
                     i.sneak(true);
                     this.inputGate = s.avoidDouble;
                  }
               }
            } else {
               this.release(i);
            }
         } else {
            this.clear(i);
         }
      }

      public strictfp void release(BridgeAssistModule.Input i) {
         if (this.owned) {
            i.restore();
         }

         this.owned = this.inputGate = this.actionGate = false;
         this.pendingPlacement = null;
         this.grace = 0;
         this.countdown = 0.0F;
      }

      private static strictfp double absolute(double v) {
         return Double.longBitsToDouble(Double.doubleToRawLongBits(v) & ActionConstants.longAt(1378192));
      }

      public strictfp boolean filter(BridgeAssistModule.Input i) {
         this.pendingPlacement = null;
         if (!i.ready() || this.cooldown == 20) {
            return false;
         } else {
            MovingObjectPosition hit = i.hit();
            boolean cancel = false;
            if (this.inputGate
               && hit != null
               && ActionAccess.hitKind(hit) == 1
               && hit.sideHit != null && hit.hitVec != null && hit.getBlockPos() != null
               && hit.sideHit.ordinal() >= 2
               && i.ground()
               && i.forwardKey()
               && !i.left()
               && !i.right()) {
               float folded = Float.intBitsToFloat(
                  Float.floatToRawIntBits((float)DoubleNumeric.remainder((double)i.yaw(), ActionConstants.d(1378144))) & ActionConstants.intAt(1378160)
               );
               if (!(ActionConstants.f(1378176) > folded) && !(folded > ActionConstants.f(1378180))) {
                  Vec3 self = i.position();
                  Vec3 point = hit.hitVec;
                  double dy = point.yCoord - self.yCoord;
                  if (!(absolute(point.xCoord - self.xCoord) > ActionConstants.d(1378208))
                     && !(ActionConstants.d(1378216) > dy)
                     && !(dy > 0.0)
                     && !(absolute(point.zCoord - self.zCoord) > ActionConstants.d(1378208))) {
                     BlockPos block = hit.getBlockPos().offset(hit.sideHit);
                     double unit = ActionConstants.d(1378208);
                     boolean inside = (double)block.getX() < self.xCoord
                        && self.xCoord < (double)block.getX() + unit
                        && (double)block.getZ() < self.zCoord
                        && self.zCoord < (double)block.getZ() + unit;
                     double margin = ActionConstants.d(inside ? 1378224 : 1378232);
                     double upper = unit - margin;
                     double rx = absolute(DoubleNumeric.remainder(point.xCoord, unit));
                     double rz = absolute(DoubleNumeric.remainder(point.zCoord, unit));
                     cancel = !(upper < rx) && !(rx < margin) || !(upper < rz) && !(rz < margin);
                  }
               }
            }

            if (!cancel && i.block() && hit != null && ActionAccess.hitKind(hit) == 1
               && hit.getBlockPos() != null && hit.sideHit != null) {
               BlockPos destination = hit.getBlockPos().offset(hit.sideHit);
               if (i.air(destination.getX(), destination.getY(), destination.getZ())) {
                  this.pendingPlacement = destination;
               }
            }

            return cancel;
         }
      }

      public strictfp void afterUse(BridgeAssistModule.Input i) {
         BlockPos destination = this.pendingPlacement;
         this.pendingPlacement = null;
         if (destination != null && i.ready() && !i.air(destination.getX(), destination.getY(), destination.getZ())) {
            this.cooldown = 20;
         }
      }

      public strictfp void clear(BridgeAssistModule.Input i) {
         this.release(i);
         this.inputGate = this.actionGate = false;
         this.cooldown = this.grace = 0;
         this.countdown = 0.0F;
      }
   }
}
