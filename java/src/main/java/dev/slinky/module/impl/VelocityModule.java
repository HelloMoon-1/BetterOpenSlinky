package dev.slinky.module.impl;

import dev.slinky.client.AttackInput;
import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.packet.TimingAccess;
import dev.slinky.client.rotation.RecoveredRandom;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;

public final strictfp class VelocityModule extends EvidenceModule {
   private final WeaponsModule weapons;
   private final RecoveredRandom.Smooth probability = new RecoveredRandom.Smooth();
   private boolean resetPending;
   private boolean knockbackPending;
   private boolean storedPending;
   private boolean attackApplied;
   private boolean jumpOwned;
   private int entityId;
   private int countdown;
   private double storedX;
   private double storedY;
   private double storedZ;
   private double inputX;
   private double inputY;
   private double inputZ;

   public VelocityModule(WeaponsModule weapons) {
      super("velocity", "Velocity", ModuleCategory.COMBAT);
      this.weapons = weapons;
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 0 || t == 2 || t == 14 || t == 15 || t == 17 || t == 18;
   }

   private strictfp boolean eligible(ClientContext c) {
      return (!this.flag("require_forward") || !(0.0F >= c.player().moveForward))
         && (!this.flag("require_mouse") || AttackInput.pressed(c))
         && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
         && (!this.flag("require_target") || TimingAccess.facingPlayer(c));
   }

   private strictfp boolean chance(float percent) {
      if (percent > 99.0F) {
         return true;
      } else {
         double fraction = (double)((float)((double)percent * 0.01));
         return this.probability.gate((float)(-0.05 + fraction), (float)(fraction + 0.05), 60.0, RecoveredRandom.now(), RecoveredRandom.SHARED);
      }
   }

   @Override
   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 2) {
         if (e.dispatchMode == 0) {
            if (e.packet instanceof S12PacketEntityVelocity) {
               if (((S12PacketEntityVelocity)e.packet).getEntityID() == this.entityId) {
                  this.knockbackPending = true;
               }
            } else if (e.packet instanceof S27PacketExplosion) {
               S27PacketExplosion p = (S27PacketExplosion)e.packet;
               float x = p.func_149149_c();
               float z = p.func_149147_e();
               this.knockbackPending = (double)(x * x + z * z) > 0.05;
            } else if (e.packet instanceof S08PacketPlayerPosLook) {
               this.resetPending = true;
            }
         }
      } else if (c.player() != null) {
         if (e.type == 14) {
            if (e.active) {
               this.inputX = c.player().motionX;
               this.inputY = c.player().motionY;
               this.inputZ = c.player().motionZ;
            }
         } else if (e.type == 15) {
            if (this.choice("mode") == 2 && (!this.attackApplied || this.flag("reduce_multiple_per_tick")) && e.active && e.reducing) {
               if ((!this.flag("require_forward") || !(0.0F >= c.player().moveForward)) && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))) {
                  if (c.player().ticksExisted == c.player().getLastAttackerTime()) {
                     this.inputX *= 0.6;
                     this.inputZ = 0.6 * this.inputZ;
                     c.player().motionX = this.inputX;
                     c.player().motionY = this.inputY;
                     c.player().motionZ = this.inputZ;
                     this.attackApplied = true;
                  }
               }
            }
         } else if (e.type == 17) {
            this.storedX = c.player().motionX;
            this.storedY = c.player().motionY;
            this.storedZ = c.player().motionZ;
            this.storedPending = true;
         } else if (e.type == 0) {
            if (this.choice("mode") == 3 && c.inputReady()) {
               if (this.resetPending) {
                  this.resetPending = false;
               } else if (this.storedPending && this.eligible(c) && this.chance(this.number("ignore_chance"))) {
                  c.player().motionX = this.storedX;
                  c.player().motionZ = this.storedZ;
                  if (this.choice("ignore_vertical") == 2 || this.choice("ignore_vertical") == 1 && !c.player().onGround) {
                     c.player().motionY = this.storedY;
                  }

                  this.storedPending = false;
               }
            }
         } else {
            this.attackApplied = false;
            int mode = this.choice("mode");
            if (mode != 2 && c.inputReady()) {
               this.entityId = c.player().getEntityId();
               if (mode == 1) {
                  if (this.jumpOwned) {
                     KeyState.restore(c.minecraft().gameSettings.keyBindJump);
                     this.jumpOwned = false;
                  } else if (this.eligible(c)
                     && this.chance(this.number("jump_chance"))
                     && (!this.flag("require_sprinting_jump") || c.player().isSprinting())
                     && c.player().onGround
                     && !(0.0 >= c.player().motionY)
                     && !c.player().isPotionActive(Potion.jump)
                     && !c.minecraft().gameSettings.keyBindJump.isKeyDown()) {
                     KeyState.set(c.minecraft().gameSettings.keyBindJump, true);
                     this.jumpOwned = true;
                  }
               } else if (mode == 0) {
                  if (this.countdown > 0) {
                     if (--this.countdown == 0) {
                        this.scale(c);
                     }
                  } else if (this.knockbackPending) {
                     this.knockbackPending = false;
                     if (this.eligible(c) && this.chance(this.number("reduce_chance"))) {
                        double x = c.player().motionX;
                        double z = c.player().motionZ;
                        double length = RotationMath.hypot(x, z);
                        if (length != 0.0) {
                           float angle = (float)((double)c.player().rotationYaw * -Math.PI / 180.0 + -Math.PI);
                           double projection = ((double)RotationMath.sine(angle) * x + (double)RotationMath.cosine(angle) * z) / length;
                           if ((double)this.number("fov") >= StrictMath.acos(projection)) {
                              this.countdown = RecoveredMath.truncateInt(this.number("reduce_delay"));
                              if (this.countdown <= 0 || --this.countdown == 0) {
                                 this.scale(c);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private strictfp void scale(ClientContext c) {
      double factor = (double)this.number("reduce_amount");
      c.player().motionX *= factor;
      c.player().motionZ = factor * c.player().motionZ;
   }

   @Override
   protected strictfp synchronized void onEnable(ClientContext c) {
      this.storedPending = false;
   }

   @Override
   protected strictfp synchronized void onDisable(ClientContext c) {
      this.storedPending = false;
      if (this.jumpOwned) {
         KeyState.restore(c.minecraft().gameSettings.keyBindJump);
         this.jumpOwned = false;
      }
   }

   @Override
   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.reset(c);
   }

   private strictfp void reset(ClientContext c) {
      if (this.jumpOwned) {
         KeyState.restore(c.minecraft().gameSettings.keyBindJump);
      }

      this.jumpOwned = this.resetPending = this.knockbackPending = this.storedPending = this.attackApplied = false;
      this.countdown = this.entityId = 0;
      this.storedX = this.storedY = this.storedZ = this.inputX = this.inputY = this.inputZ = 0.0;
   }

   @Override
   public strictfp String statusText() {
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         if (index != 0) {
            return StatusText.label(this.getName(), index);
         } else if (!this.findSetting("reduce_amount").isConfigured()) {
            return null;
         } else {
            float percent = this.number("reduce_amount") * 100.0F;
            return StatusText.number(this.getName(), 1, (double)percent);
         }
      }
   }
}
