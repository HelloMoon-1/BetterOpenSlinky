package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.Locale;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.ChatComponentText;

public final class DebugModule extends EvidenceModule {
   private boolean usingItem;
   private boolean previousUse;
   private int left;
   private int right;
   private int age;

   public DebugModule() {
      super("debug", "Debug", ModuleCategory.UTILITY);
   }

   @Override
   public boolean listens(int t) {
      return t == 0 || t == 3 || t == 15 || t == 18;
   }

   @Override
   public synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 3) {
         if (e.dispatchMode == 0 || e.dispatchMode == 3) {
            if (e.packet instanceof C08PacketPlayerBlockPlacement) {
               this.usingItem = true;
            } else if (e.packet instanceof C07PacketPlayerDigging && ((C07PacketPlayerDigging)e.packet).getStatus().ordinal() == 5) {
               this.usingItem = false;
            }
         }
      } else if (e.type == 15) {
         if (this.flag("clicks")) {
            if (e.active) {
               this.left++;
            } else {
               this.right++;
            }

            this.age = 0;
         }
      } else if (c.inputReady()) {
         long tick = Integer.toUnsignedLong(c.player().ticksExisted) % 1000L;
         if (e.type == 0) {
            int hurt = c.player().hurtTime;
            if (this.flag("hurt_time") && hurt >= 7) {
               char color = hurt <= 10 ? new char[]{'a', 'e', 'c', '4'}[hurt - 7] : 102;
               this.chat(
                  c,
                  "§7[%03d] [debug/hurt_time] §%c%02d §%cS §%cB",
                  tick,
                  color,
                  hurt,
                  Character.valueOf((char)(c.player().isSprinting() ? '8' : 'c')),
                  Character.valueOf((char)(c.player().isUsingItem() ? 'c' : '8'))
               );
            }

            if (this.flag("item_use_state") && this.previousUse != this.usingItem) {
               this.previousUse = this.usingItem;
               this.chat(c, "§7[%03d] [debug/item_use] §%s", tick, this.usingItem ? "aStart" : "cStop");
            }
         } else {
            if (this.flag("clicks")) {
               if (Integer.compareUnsigned(this.age, 9) <= 0) {
                  this.chat(
                     c,
                     "§7[%03d] [debug/click] §%cL %s §%cR %s",
                     tick,
                     Character.valueOf((char)(this.left == 0 ? '8' : (this.left == 1 ? 'a' : '2'))),
                     Integer.toUnsignedString(this.left),
                     Character.valueOf((char)(this.right == 0 ? '8' : (this.right == 1 ? 'b' : '3'))),
                     Integer.toUnsignedString(this.right)
                  );
               }

               this.left = this.right = 0;
            }

            this.age++;
         }
      }
   }

   private void chat(ClientContext c, String format, Object... args) {
      c.player().addChatMessage(new ChatComponentText(String.format(Locale.ROOT, format, args)));
   }

   @Override
   public synchronized void onWorldUnload(ClientContext c) {
      this.clear();
   }

   private void clear() {
      this.usingItem = this.previousUse = false;
      this.left = this.right = this.age = 0;
   }
}
