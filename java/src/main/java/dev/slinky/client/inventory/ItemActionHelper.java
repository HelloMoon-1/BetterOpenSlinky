package dev.slinky.client.inventory;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.hud.KeystrokesClicks;
import dev.slinky.inject.ReflectionHelper;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

/** A slot-owned, synchronous item action. No KeyBinding press is queued or consumed here. */
public final class ItemActionHelper implements ConsumableAccess {
   private static final Method SYNC_SLOT = ReflectionHelper.findMethod(
      PlayerControllerMP.class, null, new String[]{"syncCurrentPlayItem", "func_78750_j"}
   );
   private static final Method RIGHT_CLICK = ReflectionHelper.findMethod(Minecraft.class, null, new String[]{"rightClickMouse", "func_147121_ag"});
   private static final Field USE_DELAY = ReflectionHelper.findField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
   private static UseAttempt vanillaUse;
   private final Module module;
   private final ModuleManager manager;
   private InventoryPlayer inventory;
   private EntityPlayerSP player;
   private WorldClient world;
   private PlayerControllerMP controller;
   private int savedSlot;
   private int selected;
   private ItemStack expected;
   private ItemStack expectedIdentity;
   public ClientContext context;

   public enum Result {
      USED, WAIT, FAILED, CANCELLED
   }

   public ItemActionHelper(Module module, ModuleManager manager) {
      this.module = module;
      this.manager = manager;
   }

   public static boolean ready(ClientContext c) {
      return c != null && c.inputReady() && c.minecraft().currentScreen == null && c.minecraft().playerController != null
         && c.player().isEntityAlive() && !c.player().isSpectator();
   }

   private static boolean pending(KeyBinding key) {
      return key.isKeyDown() || ActionAccess.presses(key) > 0;
   }

   private static boolean inputPending(ClientContext c) {
      if (pending(c.minecraft().gameSettings.keyBindUseItem) || pending(c.minecraft().gameSettings.keyBindAttack)
         || pending(c.minecraft().gameSettings.keyBindPickBlock) || pending(c.minecraft().gameSettings.keyBindDrop)) {
         return true;
      }
      for (KeyBinding key : c.minecraft().gameSettings.keyBindsHotbar) {
         if (pending(key)) {
            return true;
         }
      }
      return false;
   }

   public static void syncSlot(ClientContext c) {
      if (c != null && c.ready() && c.minecraft().playerController != null) {
         try {
            SYNC_SLOT.invoke(c.minecraft().playerController);
         } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot synchronize held item", failure);
         }
      }
   }

   /** Use the actual tick rotation, without re-entering the render/ray semantic event. */
   public static MovingObjectPosition refreshBlockRay(ClientContext c) {
      if (!ready(c)) {
         return null;
      }
      MovingObjectPosition hit = c.player().rayTrace((double)c.minecraft().playerController.getBlockReachDistance(), 1.0F);
      c.minecraft().objectMouseOver = hit;
      return hit;
   }

   private static int useDelay(ClientContext c) {
      try {
         return USE_DELAY.getInt(c.minecraft());
      } catch (IllegalAccessException failure) {
         throw new IllegalStateException("Cannot read item use delay", failure);
      }
   }

   private static Result gate(ClientContext c, Object token) {
      if (!ready(c) || ModuleActionService.owner() != token) {
         return Result.FAILED;
      }
      if (inputPending(c)) {
         return Result.CANCELLED;
      }
      return c.player().isUsingItem() || c.minecraft().playerController.getIsHittingBlock() || useDelay(c) > 0 ? Result.WAIT : Result.USED;
   }

   private static Result placementGate(ClientContext c, Object token) {
      if (!ready(c) || ModuleActionService.owner() != token) {
         return Result.FAILED;
      }
      // Keep the existing block-placement input contract separate from item
      // transactions; do not make attack/hotbar bindings disable BlockIn/Clutch.
      return c.player().isUsingItem() || pending(c.minecraft().gameSettings.keyBindUseItem) || useDelay(c) > 0 ? Result.WAIT : Result.USED;
   }

   private static void delayUse(ClientContext c) {
      try {
         USE_DELAY.setInt(c.minecraft(), 4);
      } catch (IllegalAccessException failure) {
         throw new IllegalStateException("Cannot set item use delay", failure);
      }
   }

   /**
    * Called by GameHooks after interaction listeners/placement selection, before
    * entering vanilla rightClickMouse. Manual clicks have no active attempt.
    */
   public static boolean allowVanillaUse() {
      return vanillaUse == null || vanillaUse.valid();
   }

   /** A normal vanilla right click, including its entity/block-first interaction rules. */
   public static Result useItem(ClientContext c, Object token) {
      return click(c, token, null);
   }

   private static Result click(ClientContext c, Object token, ItemActionHelper transaction) {
      Result gate = gate(c, token);
      if (gate != Result.USED) {
         return gate;
      }
      if (vanillaUse != null) {
         return Result.CANCELLED;
      }
      UseAttempt attempt = new UseAttempt(c, token, transaction);
      if (!attempt.valid()) {
         return Result.CANCELLED;
      }
      // Selection is synchronized before the ordinary vanilla interaction. No
      // extra look/movement packet or lastReported rotation is manufactured.
      syncSlot(c);
      if (!attempt.valid()) {
         return Result.CANCELLED;
      }
      vanillaUse = attempt;
      try {
         // The patched vanilla method owns both semantic interaction hooks,
         // entity/block dispatch, the four-tick use delay and renderer feedback.
         RIGHT_CLICK.invoke(c.minecraft());
         // A cancelled beforeInteraction never reaches vanilla's delay write.
         // USED means one click was dispatched, not server acknowledgement or
         // proof that a block/entity interaction actually consumed this item.
         if (useDelay(c) > 0) {
            KeystrokesClicks.onUseClick();
            return Result.USED;
         }
         return Result.CANCELLED;
      } catch (InvocationTargetException failure) {
         Throwable cause = failure.getCause();
         if (cause instanceof RuntimeException) {
            throw (RuntimeException)cause;
         }
         if (cause instanceof Error) {
            throw (Error)cause;
         }
         throw new IllegalStateException("Cannot perform vanilla item interaction", cause);
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException("Cannot perform vanilla item interaction", failure);
      } finally {
         vanillaUse = null;
      }
   }

   private static final class UseAttempt {
      private final ClientContext context;
      private final Object token;
      private final ItemActionHelper transaction;
      private final EntityPlayerSP player;
      private final WorldClient world;
      private final PlayerControllerMP controller;
      private final InventoryPlayer inventory;
      private final int slot;
      private final ItemStack held;
      private final ItemStack snapshot;
      private final float yaw;
      private final float pitch;

      private UseAttempt(ClientContext c, Object token, ItemActionHelper transaction) {
         this.context = c;
         this.token = token;
         this.transaction = transaction;
         this.player = c.player();
         this.world = c.world();
         this.controller = c.minecraft().playerController;
         this.inventory = this.player.inventory;
         this.slot = this.inventory.currentItem;
         this.held = this.player.getHeldItem();
         this.snapshot = this.held == null ? null : this.held.copy();
         this.yaw = this.player.rotationYaw;
         this.pitch = this.player.rotationPitch;
      }

      private boolean valid() {
         return gate(this.context, this.token) == Result.USED && this.context.player() == this.player && this.context.world() == this.world
            && this.context.minecraft().playerController == this.controller && this.player.inventory == this.inventory
            && this.slot >= 0 && this.slot < 9 && this.inventory.currentItem == this.slot && this.held != null && this.held.stackSize > 0
            && this.player.getHeldItem() == this.held && ItemStack.areItemStacksEqual(this.held, this.snapshot)
            && Float.compare(this.player.rotationYaw, this.yaw) == 0 && Float.compare(this.player.rotationPitch, this.pitch) == 0
            && (this.transaction == null || this.transaction.ready() && this.transaction.unchanged());
      }
   }

   /** A block-only use; USED means onPlayerRightClick returned true, not merely that a click was requested. */
   public static Result useBlock(ClientContext c, Object token, MovingObjectPosition hit) {
      if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK || hit.getBlockPos() == null || hit.sideHit == null || hit.hitVec == null) {
         return Result.FAILED;
      }
      Result gate = placementGate(c, token);
      if (gate != Result.USED) {
         return gate;
      }
      EntityPlayerSP player = c.player();
      WorldClient world = c.world();
      PlayerControllerMP controller = c.minecraft().playerController;
      int slot = player.inventory.currentItem;
      ItemStack held = player.getHeldItem();
      if (slot < 0 || slot >= 9 || held == null || held.stackSize <= 0) {
         return Result.FAILED;
      }
      ItemStack before = held.copy();
      c.minecraft().objectMouseOver = hit;
      syncSlot(c);
      // Placements deliberately keep the block-only primitive and its existing
      // hooks; calling patched rightClickMouse here would also use the item on a
      // failed placement and would dispatch those hooks twice.
      if (GameHooks.beforeInteraction(true)) {
         return Result.CANCELLED;
      }
      try {
         if (!ready(c) || c.player() != player || c.world() != world || c.minecraft().playerController != controller
            || ModuleActionService.owner() != token || player.inventory.currentItem != slot || player.getHeldItem() != held
            || !ItemStack.areItemStacksEqual(held, before) || c.minecraft().objectMouseOver != hit) {
            return Result.CANCELLED;
         }
         syncSlot(c);
         if (placementGate(c, token) != Result.USED || c.player() != player || c.world() != world
            || c.minecraft().playerController != controller || player.inventory.currentItem != slot || player.getHeldItem() != held
            || !ItemStack.areItemStacksEqual(held, before) || c.minecraft().objectMouseOver != hit) {
            return Result.CANCELLED;
         }
         delayUse(c);
         boolean used = controller.onPlayerRightClick(player, world, held, hit.getBlockPos(), hit.sideHit, hit.hitVec);
         if (held.stackSize <= 0 && player.inventory.currentItem == slot && player.getHeldItem() == held) {
            player.inventory.mainInventory[slot] = null;
         }
         if (used) {
            KeystrokesClicks.onUseClick();
            player.swingItem();
            c.minecraft().entityRenderer.itemRenderer.resetEquippedProgress();
         }
         return used ? Result.USED : Result.FAILED;
      } finally {
         GameHooks.afterInteraction(true);
      }
   }

   @Override
   public boolean ready() {
      return ready(this.context) && this.manager.requestedEnabled(this.module);
   }

   @Override
   public float health() {
      return this.context.player().getHealth();
   }

   @Override
   public float maximumHealth() {
      return this.context.player().getMaxHealth();
   }

   @Override
   public boolean usingItem() {
      return this.context.player().isUsingItem();
   }

   @Override
   public int current() {
      return this.context.player().inventory.currentItem;
   }

   @Override
   public int type(int slot) {
      return InventoryActions.type(this.context.player().inventory.getStackInSlot(slot));
   }

   @Override
   public int damage(int slot) {
      ItemStack stack = this.context.player().inventory.getStackInSlot(slot);
      return stack == null ? 0 : stack.getItemDamage();
   }

   @Override
   public boolean claim() {
      return this.ready() && !inputPending(this.context) && !this.usingItem() && !this.context.minecraft().playerController.getIsHittingBlock()
         && ModuleActionService.claim(this.module);
   }

   @Override
   public void remember() {
      this.player = this.context.player();
      this.world = this.context.world();
      this.controller = this.context.minecraft().playerController;
      this.inventory = this.player.inventory;
      this.savedSlot = this.inventory.currentItem;
      this.selected = this.savedSlot;
      this.snapshot();
   }

   public void restoreTo(int slot) {
      if (slot >= 0 && slot < 9) {
         this.savedSlot = slot;
      }
   }

   public boolean valid() {
      return this.inventory != null && this.context != null && this.context.ready() && this.context.player() == this.player
         && this.context.world() == this.world && this.context.minecraft().playerController == this.controller
         && this.player.inventory == this.inventory && ModuleActionService.owner() == this.module && this.inventory.currentItem == this.selected
         && !inputPending(this.context);
   }

   private void snapshot() {
      this.expectedIdentity = this.inventory.getStackInSlot(this.selected);
      this.expected = this.expectedIdentity == null ? null : this.expectedIdentity.copy();
   }

   public boolean unchanged() {
      return this.valid() && this.inventory.getStackInSlot(this.selected) == this.expectedIdentity
         && ItemStack.areItemStacksEqual(this.expectedIdentity, this.expected);
   }

   private boolean consumed() {
      if (!this.valid() || this.expected == null) {
         return false;
      }
      ItemStack actual = this.inventory.getStackInSlot(this.selected);
      return actual == null || actual.isItemEqual(this.expected) && ItemStack.areItemStackTagsEqual(actual, this.expected)
         && actual.stackSize < this.expected.stackSize;
   }

   /** Local consumption feedback only; this is not an authoritative server acknowledgement. */
   public static boolean consumed(ConsumableAccess access) {
      return !(access instanceof ItemActionHelper) || ((ItemActionHelper)access).consumed();
   }

   @Override
   public void select(int slot) {
      if (this.valid() && slot >= 0 && slot < 9) {
         this.inventory.currentItem = slot;
         this.selected = slot;
         syncSlot(this.context);
         this.snapshot();
      }
   }

   @Override
   public void use() {
      useOnce(this);
   }

   public static Result useOnce(ConsumableAccess access) {
      if (access instanceof ItemActionHelper) {
         ItemActionHelper action = (ItemActionHelper)access;
         return action.ready() && action.unchanged() ? click(action.context, action.module, action) : Result.FAILED;
      }
      access.use();
      return Result.USED;
   }

   public static boolean intact(ConsumableAccess access) {
      return !(access instanceof ItemActionHelper) || ((ItemActionHelper)access).valid();
   }

   public static boolean unchanged(ConsumableAccess access) {
      return !(access instanceof ItemActionHelper) || ((ItemActionHelper)access).unchanged();
   }

   @Override
   public void drop() {
      // A delayed drop key could drop the restored weapon. Drop only the actual
      // bowl produced in our still-owned slot, synchronously, after consumption.
      if (this.valid() && this.type(this.selected) == 281) {
         syncSlot(this.context);
         this.player.dropOneItem(false);
      }
   }

   @Override
   public void release() {
      ModuleActionService.release(this.module);
   }

   @Override
   public void disable() {
      this.manager.requestEnabled(this.module, false);
   }

   public void cleanup() {
      try {
         if (this.valid()) {
            this.inventory.currentItem = this.savedSlot;
            syncSlot(this.context);
         }
      } finally {
         this.forget();
      }
   }

   /** World changes and manual input relinquish ownership without overriding the user. */
   public void forget() {
      this.inventory = null;
      this.player = null;
      this.world = null;
      this.controller = null;
      this.expected = null;
      this.expectedIdentity = null;
      this.release();
   }

   public static void abandon(ConsumableAccess access) {
      if (access instanceof ItemActionHelper) {
         ((ItemActionHelper)access).forget();
      } else {
         access.release();
      }
   }

   public static void finish(ConsumableAccess access, int previous) {
      if (access instanceof ItemActionHelper) {
         ((ItemActionHelper)access).cleanup();
      } else {
         access.select(previous);
         access.release();
      }
   }
}
