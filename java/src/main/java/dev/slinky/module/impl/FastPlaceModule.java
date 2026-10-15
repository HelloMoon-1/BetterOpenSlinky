package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.action.ActionAccess;
import dev.slinky.client.action.ActionConstants;
import dev.slinky.client.inventory.InventoryActions;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

public final strictfp class FastPlaceModule extends EvidenceModule {
   private final FastPlaceModule.State state = new FastPlaceModule.State();

   public FastPlaceModule() {
      super("fastplace", "Fast Place", ModuleCategory.UTILITY);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 0 || type == 26;
   }

   @Override
   public strictfp void onEvent(final ClientContext c, SemanticEvent e) {
      if (e.type == 26) {
         this.state.clear();
      } else {
         this.state.update(new FastPlaceModule.Input() {
            @Override
            public strictfp boolean ready() {
               return c.inputReady();
            }

            @Override
            public strictfp boolean physical() {
               return KeyState.physical(c.minecraft().gameSettings.keyBindUseItem);
            }

            @Override
            public strictfp int presses() {
               return ActionAccess.presses(c.minecraft().gameSettings.keyBindUseItem);
            }

            @Override
            public strictfp boolean block() {
               return ActionAccess.isBlock(c.player().getHeldItem());
            }

            @Override
            public strictfp int itemType() {
               return InventoryActions.type(c.player().getHeldItem());
            }

            @Override
            public strictfp MovingObjectPosition hit() {
               return c.minecraft().objectMouseOver;
            }

            @Override
            public strictfp AxisAlignedBB playerBox() {
               return c.player().getEntityBoundingBox();
            }

            @Override
            public strictfp void press() {
               ActionAccess.increment(c.minecraft().gameSettings.keyBindUseItem);
            }
         }, this.number("max_cps"), this.number("activate_delay"), RecoveredRandom.SHARED, RecoveredRandom.now());
      }
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.state.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.state.clear();
   }

   @Override
   public strictfp String statusText() {
      return !this.findSetting("max_cps").isConfigured() ? null : StatusText.number(this.getName(), (double)this.number("max_cps"));
   }

   public interface Input {
      boolean ready();

      boolean physical();

      int presses();

      boolean block();

      int itemType();

      MovingObjectPosition hit();

      AxisAlignedBB playerBox();

      void press();
   }

   public static final class State {
      public float timer;
      public boolean previouslyPressed;
      public int activationCountdown;
      private final RecoveredRandom.Smooth gate = new RecoveredRandom.Smooth();

      public strictfp void update(FastPlaceModule.Input access, float cps, float activationDelay, RecoveredRandom random, double now) {
         if (!Float.isNaN(this.timer) && this.timer > 0.0F) {
            this.timer = this.timer + ActionConstants.f(1379560);
            if (!Float.isNaN(this.timer) && this.timer > 0.0F) {
               return;
            }
         }

         if (access.ready()) {
            boolean pressed = access.physical();
            boolean previous = this.previouslyPressed;
            this.previouslyPressed = pressed;
            if (!pressed) {
               this.activationCountdown = RecoveredMath.truncateInt(activationDelay);
            } else if (this.activationCountdown >= 1) {
               this.activationCountdown--;
            } else if (access.presses() <= 0 && !Float.isNaN(this.timer) && !(this.timer > 0.0F)) {
               boolean phase = !previous;

               do {
                  this.timer = (float)((double)this.timer + ActionConstants.d(1379568) / (double)cps);
                  if (!phase && access.block()) {
                     MovingObjectPosition hit = access.hit();
                     boolean useGate = hit == null || ActionAccess.hitKind(hit) != 1;
                     if (!useGate && access.itemType() != 65) {
                        BlockPos adjacent = hit.getBlockPos().offset(hit.sideHit);
                        double extent = ActionConstants.d(1379576);
                        AxisAlignedBB placed = new AxisAlignedBB(
                           (double)adjacent.getX(),
                           (double)adjacent.getY(),
                           (double)adjacent.getZ(),
                           (double)adjacent.getX() + extent,
                           (double)adjacent.getY() + extent,
                           (double)adjacent.getZ() + extent
                        );
                        useGate = placed.intersectsWith(access.playerBox());
                     }

                     if (!useGate || this.gate.gate(ActionConstants.f(1379584), ActionConstants.f(1379588), ActionConstants.d(1379568), now, random)) {
                        access.press();
                     }
                  }

                  phase = false;
               } while (!Float.isNaN(this.timer) && this.timer <= 0.0F);
            }
         }
      }

      public strictfp void clear() {
         this.timer = 0.0F;
         this.previouslyPressed = false;
         this.activationCountdown = 0;
      }
   }
}
