package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.packet.PredictionGeometry;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;

public final strictfp class TimerRangeModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private int remaining;
   private boolean frozen;
   private boolean eligible;
   private boolean packetHold;
   private boolean useOwned;
   private float forward;
   private float lateral;
   private double freezeRemaining;
   private double cooldown;

   public TimerRangeModule(WeaponsModule weapons) {
      super("timerrange", "Timer Range", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 0 || t == 1 || t == 3;
   }

   public strictfp synchronized boolean frozenOrAdvancing() {
      return this.isEnabled() && (this.remaining > 0 || this.frozen);
   }

   private strictfp boolean sword(ClientContext c) {
      ItemStack stack = c.player() == null ? null : c.player().getHeldItem();
      if (stack == null) {
         return false;
      } else {
         int index = Item.getIdFromItem(stack.getItem()) - 267;
         return Integer.compareUnsigned(index, 16) <= 0 && (66083 >>> index & 1) != 0;
      }
   }

   private strictfp void beginFreeze(ClientContext c) {
      MinecraftFields.timer(c.minecraft()).timerSpeed = 0.0F;
      this.frozen = true;
      if (this.choice("mode") == 1) {
         this.packetHold = true;
      } else {
         if (this.flag("block_while_frozen") && this.sword(c)) {
            KeyBinding key = c.minecraft().gameSettings.keyBindUseItem;
            KeyState.set(key, true);
            KeyBinding.onTick(key.getKeyCode());
            this.useOwned = true;
         }
      }
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 3) {
         if (this.packetHold) {
            e.dispatchMode = 1;
         }
      } else if (e.type == 0) {
         this.tick(c);
      } else {
         Timer timer = MinecraftFields.timer(c.minecraft());
         if (this.frozen) {
            this.freezeRemaining = this.freezeRemaining - e.elapsedSeconds;
            if (this.freezeRemaining > 0.0) {
               timer.timerSpeed = 0.0F;
               return;
            }

            timer.timerSpeed = 1.0F;
            this.frozen = this.packetHold = false;
            if (this.choice("mode") != 1 && this.flag("block_while_frozen") && this.sword(c)) {
               KeyState.set(c.minecraft().gameSettings.keyBindUseItem, false);
               this.useOwned = false;
            }

            if (this.choice("mode") == 1) {
               timer.elapsedPartialTicks = (float)this.remaining;
            }
         }

         if (this.cooldown > 0.0) {
            this.cooldown = this.cooldown - e.elapsedSeconds;
         } else {
            EntityPlayer target = GameHooks.target();
            if (this.eligible && c.inputReady() && target != null) {
               Vec3 velocity = new Vec3(c.player().motionX, c.player().motionY, c.player().motionZ);
               Vec3 self = TimingAccess.position(c.player()).addVector(0.0, 1.62, 0.0);
               Vec3 position = TimingAccess.position(target);
               Vec3 desired = TimingAccess.server(target);
               int targetTicks = TimingAccess.interpolationTicks(target);
               int mode = this.choice("mode");
               float yaw = (float)((float) -Math.PI - (double)c.player().rotationYaw * (Math.PI / 180.0));
               float pitch = Float.intBitsToFloat(Float.floatToRawIntBits(c.player().rotationPitch) + Integer.MIN_VALUE);
               float cosine = RotationMath.cosine(yaw);
               float sine = RotationMath.sine(yaw);
               pitch = (float)((double)pitch * (Math.PI / 180.0));
               float pitchCos = Float.intBitsToFloat(Float.floatToRawIntBits(RotationMath.cosine(pitch)) ^ Integer.MIN_VALUE);
               Vec3 direction = new Vec3((double)(sine * pitchCos), (double)RotationMath.sine(pitch), (double)(cosine * pitchCos));
               int steps = RecoveredMath.truncateInt(this.number(mode == 0 ? "duration" : "duration_legit"));

               for (int step = 1; step <= steps; step++) {
                  double[] box = new double[]{
                     position.xCoord - 0.4, position.yCoord - 0.1, position.zCoord - 0.4, position.xCoord + 0.4, position.yCoord + 1.9, position.zCoord + 0.4
                  };
                  if (PredictionGeometry.ray(box, self, PredictionGeometry.scale(direction, 3.0))) {
                     double distance = TimingAccess.squared(self, PredictionGeometry.clamp(box, self));
                     if (distance > 0.0) {
                        float radius = this.number("min_dist");
                        if ((double)(radius * radius) > distance) {
                           return;
                        }

                        float count = (float)step;
                        if (mode == 1) {
                           this.beginFreeze(c);
                        } else {
                           timer.elapsedPartialTicks = count;
                        }

                        this.remaining = step;
                        this.freezeRemaining = (double)(count - e.timerRenderPartialTicks) * 0.05;
                        this.cooldown = (double)this.number("cooldown");
                        return;
                     }
                  }

                  Vec3 motion = PredictionGeometry.movement(c.player().rotationYaw, this.forward, this.lateral, c.player().getAIMoveSpeed());
                  velocity = velocity.add(motion);
                  self = self.add(velocity);
                  velocity = PredictionGeometry.scale(velocity, 0.546);
                  if (mode == 1) {
                     double length = RotationMath.hypot(velocity.xCoord, velocity.zCoord);
                     Vec3 difference = self.subtract(desired);
                     double scale = length / RotationMath.hypot(difference.xCoord, difference.zCoord);
                     Vec3 change = PredictionGeometry.scale(difference, scale);
                     desired = desired.addVector(change.xCoord, 0.0, change.zCoord);
                  }

                  if (targetTicks > 0) {
                     position = position.add(PredictionGeometry.scale(desired.subtract(position), 1.0 / (double)targetTicks));
                     targetTicks--;
                  }
               }
            }
         }
      }
   }

   private strictfp void tick(ClientContext c) {
      GameSettings keys = c.minecraft().gameSettings;
      EntityPlayer target = GameHooks.target();
      if (this.remaining > 0) {
         if ((--this.remaining | this.choice("mode")) == 0) {
            this.beginFreeze(c);
         }

         if (c.inputReady() && target != null) {
            MovingObjectPosition hit = c.minecraft().objectMouseOver;
            if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
               MinecraftFields.setLeftClickCounter(c.minecraft(), 0);
               KeyBinding.onTick(keys.keyBindAttack.getKeyCode());
            }
         }
      } else {
         this.eligible = false;
         if (c.inputReady()
            && target != null
            && !(3.0 > GameHooks.targetDistanceSquared())
            && c.player().onGround
            && target.hurtTime <= 1
            && (!this.flag("require_mouse_pressed") || AttackInput.pressed(c))
            && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
            && (!this.flag("require_not_using_item") || !c.player().isUsingItem())
            && !(c.player().motionY > 0.0)
            && !keys.keyBindJump.isKeyDown()) {
            Vec3 self = TimingAccess.position(c.player()).addVector(0.0, 1.62, 0.0);
            Vec3 remote = TimingAccess.server(target).addVector(0.0, 1.62, 0.0);
            if (clearWorldRay(c.world(), self, remote)) {
               this.forward = keys.keyBindForward.isKeyDown() ? 1.0F : 0.0F;
               if (keys.keyBindBack.isKeyDown()) {
                  this.forward += -1.0F;
               }

               this.lateral = keys.keyBindLeft.isKeyDown() ? 1.0F : 0.0F;
               if (keys.keyBindRight.isKeyDown()) {
                  this.lateral += -1.0F;
               }

               this.eligible = this.forward > 0.0F;
            }
         }
      }
   }

   public static strictfp boolean clearWorldRay(World world, Vec3 from, Vec3 to) {
      return world.rayTraceBlocks(from, to, false, true, true) == null;
   }

   @Override
   protected strictfp synchronized void onDisable(ClientContext c) {
      MinecraftFields.timer(c.minecraft()).timerSpeed = 1.0F;
      this.frozen = this.packetHold = false;
      this.remaining = 0;
      if (this.useOwned) {
         KeyState.restore(c.minecraft().gameSettings.keyBindUseItem);
         this.useOwned = false;
      }
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.reset(c);
   }

   private strictfp void reset(ClientContext c) {
      MinecraftFields.timer(c.minecraft()).timerSpeed = 1.0F;
      if (this.useOwned) {
         KeyState.restore(c.minecraft().gameSettings.keyBindUseItem);
      }

      this.frozen = this.packetHold = this.eligible = this.useOwned = false;
      this.remaining = 0;
      this.forward = this.lateral = 0.0F;
      this.freezeRemaining = this.cooldown = 0.0;
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
}
