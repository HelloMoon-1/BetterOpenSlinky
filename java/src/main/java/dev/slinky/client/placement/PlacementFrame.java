package dev.slinky.client.placement;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public interface PlacementFrame {
   boolean ready();

   boolean ground();

   int hurt();

   boolean mounted();

   boolean jump();

   Vec3 position();

   Vec3 eye();

   AxisAlignedBB box();

   double[] velocity();

   float yaw();

   float pitch();

   int block(int var1, int var2, int var3);

   MovingObjectPosition ray(Vec3 var1, Vec3 var2);

   MovingObjectPosition hit();

   boolean heldBlock();

   void select(boolean var1, boolean var2);

   void press();

   void disable();

   boolean claim(int var1);

   boolean rotationAvailable();

   float storedYaw();

   float storedPitch();

   float offsetYaw();

   float offsetPitch();

   void publish(float var1, float var2);

   void movement(int var1);

   void restoreMovement();

   public static final class Game implements PlacementFrame {
      private final ClientContext c;
      private final ModuleManager modules;
      private final Module token;

      public Game(ClientContext c, ModuleManager modules, Module token) {
         this.c = c;
         this.modules = modules;
         this.token = token;
      }

      @Override
      public boolean ready() {
         return this.c.inputReady();
      }

      @Override
      public boolean ground() {
         return this.c.player().onGround;
      }

      @Override
      public int hurt() {
         return this.c.player().hurtTime;
      }

      @Override
      public boolean mounted() {
         return this.c.player().ridingEntity != null;
      }

      @Override
      public boolean jump() {
         return KeyState.physical(this.c.minecraft().gameSettings.keyBindJump);
      }

      @Override
      public Vec3 position() {
         return new Vec3(this.c.player().posX, this.c.player().posY, this.c.player().posZ);
      }

      @Override
      public Vec3 eye() {
         return this.c.player().getPositionEyes((float)ActionConstants.d(1374064));
      }

      @Override
      public AxisAlignedBB box() {
         return this.c.player().getEntityBoundingBox();
      }

      @Override
      public double[] velocity() {
         return new double[]{this.c.player().motionX, this.c.player().motionY, this.c.player().motionZ};
      }

      @Override
      public float yaw() {
         return this.c.player().rotationYaw;
      }

      @Override
      public float pitch() {
         return this.c.player().rotationPitch;
      }

      @Override
      public int block(int x, int y, int z) {
         return Block.getIdFromBlock(this.c.world().getBlockState(new BlockPos(x, y, z)).getBlock());
      }

      @Override
      public MovingObjectPosition ray(Vec3 from, Vec3 to) {
         return this.c.world().rayTraceBlocks(from, to, false, true, false);
      }

      @Override
      public MovingObjectPosition hit() {
         return this.c.minecraft().objectMouseOver;
      }

      @Override
      public boolean heldBlock() {
         return ActionAccess.isBlock(this.c.player().getHeldItem());
      }

      @Override
      public void select(boolean force, boolean blockIn) {
         PlacementSelection.select(this.c, this.token, force, blockIn);
      }

      @Override
      public void press() {
         ActionAccess.increment(this.c.minecraft().gameSettings.keyBindUseItem);
      }

      @Override
      public void disable() {
         this.modules.requestEnabled(this.token, false);
      }

      @Override
      public boolean claim(int priority) {
         return RotationService.claim(priority);
      }

      @Override
      public boolean rotationAvailable() {
         return RotationService.storedRotationValid();
      }

      @Override
      public float storedYaw() {
         return RotationService.storedYaw();
      }

      @Override
      public float storedPitch() {
         return RotationService.storedPitch();
      }

      @Override
      public float offsetYaw() {
         return RotationService.pendingYaw();
      }

      @Override
      public float offsetPitch() {
         return RotationService.pendingPitch();
      }

      @Override
      public void publish(float yaw, float pitch) {
         RotationService.add(yaw, pitch);
      }

      @Override
      public void movement(int mode) {
         GameSettings g = this.c.minecraft().gameSettings;
         KeyState.set(g.keyBindBack, mode == 1);
         KeyState.set(g.keyBindForward, false);
         KeyState.set(g.keyBindLeft, false);
         KeyState.set(g.keyBindRight, false);
      }

      @Override
      public void restoreMovement() {
         GameSettings g = this.c.minecraft().gameSettings;
         KeyState.restore(g.keyBindBack);
         KeyState.restore(g.keyBindForward);
         KeyState.restore(g.keyBindLeft);
         KeyState.restore(g.keyBindRight);
      }
   }
}
