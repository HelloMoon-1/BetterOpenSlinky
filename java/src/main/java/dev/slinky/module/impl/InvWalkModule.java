package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.RecoveredMath;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.packet.NetworkSendAdapter;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.Arrays;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

public final class InvWalkModule extends EvidenceModule {
   private final InvWalkModule.State state = new InvWalkModule.State();
   private KeyBinding[] changedKeys;
   private KeyBinding changedSprint;

   @Override
   public String statusText() {
      return !this.findSetting("mode").isConfigured() ? null : StatusText.label(this.getName(), this.choice("mode"));
   }

   public InvWalkModule() {
      super("invwalk", "Inv Walk", ModuleCategory.MOVEMENT);
   }

   @Override
   public boolean listens(int type) {
      return type == 17 || type == 5 || type == 23 || type == 25;
   }

   private static NetworkManager receiver(ClientContext c) {
      return c.minecraft().getNetHandler() == null ? null : c.minecraft().getNetHandler().getNetworkManager();
   }

   @Override
   public synchronized void onEvent(ClientContext c, SemanticEvent e) {
      InvWalkModule.Live a = new InvWalkModule.Live(c, e.type == 23 ? e.receiver : receiver(c));
      if (e.type == 17) {
         this.state
            .update(
               a, this.choice("mode"), this.choice("stop_on_click"), this.choice("stop_non_inv"), (int)RecoveredMath.truncateLong(this.number("stop_duration"))
            );
      } else if (e.type == 23) {
         int kind = e.packet instanceof C16PacketClientStatus
            ? 0
            : (e.packet instanceof C0EPacketClickWindow ? 1 : (e.packet instanceof C0DPacketCloseWindow ? 2 : -1));
         int action = kind == 0 ? ((C16PacketClientStatus)e.packet).getStatus().ordinal() : -1;
         if (this.state.packet(a, e.packet, kind, action, this.choice("mode"))) {
            e.cancelled = true;
         }
      } else if (e.type == 25) {
         if (this.state.finish(a)) {
            e.cancelled = true;
         }
      } else if (e.type == 5 && e.module == this && !e.enabled) {
         this.state.flush(a);
      }
   }

   private void restoreKeys() {
      if (this.changedKeys != null) {
         for (KeyBinding key : this.changedKeys) {
            KeyState.restore(key);
         }

         this.changedKeys = null;
      }

      if (this.changedSprint != null) {
         KeyState.restore(this.changedSprint);
         this.changedSprint = null;
      }
   }

   @Override
   protected synchronized void onDisable(ClientContext c) {
      this.state.flush(new InvWalkModule.Live(c, receiver(c)));
      this.restoreKeys();
   }

   @Override
   public synchronized void onWorldUnload(ClientContext c) {
      this.state.flush(new InvWalkModule.Live(c, receiver(c)));
      this.state.clear();
      this.restoreKeys();
   }

   public interface Access {
      boolean screen();

      boolean inventoryScreen();

      boolean ready();

      boolean physical(int var1);

      boolean cursorStack();

      boolean craftingStack(int var1);

      boolean connection();

      void send(Object var1);

      Object open();

      Object close();

      void key(int var1, boolean var2);

      void stopSprint();
   }

   private final class Live implements InvWalkModule.Access {
      private final ClientContext context;
      private final NetworkManager receiver;
      private final KeyBinding[] keys;

      Live(ClientContext c, NetworkManager receiver) {
         this.context = c;
         this.receiver = receiver;
         GameSettings s = c.minecraft().gameSettings;
         this.keys = new KeyBinding[]{s.keyBindForward, s.keyBindBack, s.keyBindLeft, s.keyBindRight, s.keyBindJump};
      }

      @Override
      public boolean screen() {
         return this.context.minecraft().currentScreen != null;
      }

      @Override
      public boolean inventoryScreen() {
         return this.context.minecraft().currentScreen instanceof GuiInventory;
      }

      @Override
      public boolean ready() {
         return this.context.inputReady();
      }

      @Override
      public boolean physical(int key) {
         return KeyState.physical(this.keys[key]);
      }

      @Override
      public boolean cursorStack() {
         return this.context.player() != null && this.context.player().inventory.getItemStack() != null;
      }

      @Override
      public boolean craftingStack(int index) {
         if (this.context.player() == null) {
            return false;
         } else {
            Container container = this.context.player().inventoryContainer;
            return container instanceof ContainerPlayer && ((ContainerPlayer)container).craftMatrix.getStackInSlot(index) != null;
         }
      }

      @Override
      public boolean connection() {
         return this.receiver != null && this.receiver.isChannelOpen();
      }

      @Override
      public void send(Object packet) {
         if (this.connection()) {
            NetworkSendAdapter.sendDirect(this.receiver, (Packet<?>)packet);
         }
      }

      @Override
      public Object open() {
         return new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
      }

      @Override
      public Object close() {
         return new C0DPacketCloseWindow(0);
      }

      @Override
      public void key(int index, boolean value) {
         InvWalkModule.this.changedKeys = this.keys;
         KeyState.set(this.keys[index], value);
      }

      @Override
      public void stopSprint() {
         if (this.context.player() != null) {
            this.context.player().setSprinting(false);
         }

         InvWalkModule.this.changedSprint = this.context.minecraft().gameSettings.keyBindSprint;
         KeyState.set(InvWalkModule.this.changedSprint, false);
      }
   }

   public static final class State {
      public final Object[] packets = new Object[32];
      public int count;
      public int tick;
      public int phase;
      public int lastTick;
      public boolean stopped;
      public boolean busy;

      public boolean flush(InvWalkModule.Access a) {
         if (this.count == 0) {
            return false;
         } else {
            boolean valid = a.connection();

            for (int i = 0; i < this.count; i++) {
               if (valid) {
                  a.send(this.packets[i]);
               }

               this.packets[i] = null;
            }

            this.count = 0;
            return valid;
         }
      }

      public boolean finish(InvWalkModule.Access a) {
         if (!this.busy && this.flush(a)) {
            this.lastTick = this.tick;
            return a.ready();
         } else {
            return false;
         }
      }

      public boolean packet(InvWalkModule.Access a, Object packet, int kind, int action, int mode) {
         if (mode != 1) {
            return false;
         } else if (kind == 0) {
            if (action == 2) {
               this.phase = 2;
               return true;
            } else {
               return false;
            }
         } else {
            if (kind == 1) {
               if (this.phase == 2) {
                  a.send(a.open());
                  this.phase = 3;
                  this.busy = true;
               } else if (this.count == 0 && !this.busy) {
                  this.lastTick = this.tick;
                  return false;
               }
            } else {
               if (kind != 2) {
                  return false;
               }

               if (this.phase == 2) {
                  return true;
               }

               if (this.count == 0) {
                  return false;
               }
            }

            if (this.count != 32 && packet != null) {
               this.packets[this.count++] = packet;
            }

            return true;
         }
      }

      private boolean recent(int duration) {
         return Integer.compareUnsigned(this.tick - this.lastTick, duration) < 0;
      }

      public void update(InvWalkModule.Access a, int mode, int interact, int other, int duration) {
         boolean[] pressed = new boolean[5];
         boolean moving = false;

         for (int i = 0; i < 5; i++) {
            pressed[i] = a.physical(i);
            moving |= pressed[i];
         }

         boolean block = mode == 1 && interact != 2 && this.count != 0;
         boolean stop = interact == 0 && block;
         this.tick++;
         boolean active;
         if (!a.screen()) {
            if (this.phase != 0) {
               set(a, pressed);
            }

            this.phase = 0;
            if (!a.ready()) {
               return;
            }

            active = false;
         } else {
            if (!a.inventoryScreen()) {
               this.phase = 1;
               if (mode == 1) {
                  boolean var15 = block | !moving;
                  boolean blocked = var15 || interact != 2 && this.recent(duration);
                  block = other != 2 || blocked;
                  stop = interact == 0 && blocked || other == 0 && block;
               }

               if (stop) {
                  zero(a);
               } else {
                  set(a, pressed);
               }

               this.finishUpdate(a, block, stop);
               return;
            }

            boolean deny;
            if (mode != 1) {
               this.phase = 3;
               active = true;
               deny = false;
            } else {
               deny = true;
               if (!a.cursorStack()) {
                  if (this.phase == 1) {
                     deny = block;
                  } else {
                     boolean craft = false;

                     for (int i = 0; i < 4; i++) {
                        if (a.craftingStack(i)) {
                           craft = true;
                           break;
                        }
                     }

                     if (!craft) {
                        deny = block;
                     }
                  }
               }

               boolean allowed = moving && !deny;
               if (this.phase == 2 && !allowed) {
                  a.send(a.open());
                  this.phase = 3;
               } else if (this.count == 0 && this.phase == 3 && allowed && (interact == 2 || !this.recent(duration))) {
                  a.send(a.close());
                  this.phase = 2;
               }

               deny |= this.phase == 3;
               active = this.phase != 0;
            }

            block |= interact != 2 && deny;
            stop = interact == 0 && block;
         }

         if (active || this.stopped || stop) {
            if (stop) {
               zero(a);
            } else {
               set(a, pressed);
            }
         }

         this.finishUpdate(a, block, stop);
      }

      private static void set(InvWalkModule.Access a, boolean[] pressed) {
         for (int i = 0; i < 5; i++) {
            a.key(i, pressed[i]);
         }
      }

      private static void zero(InvWalkModule.Access a) {
         for (int i = 0; i < 5; i++) {
            a.key(i, false);
         }
      }

      private void finishUpdate(InvWalkModule.Access a, boolean block, boolean stop) {
         if (block) {
            a.stopSprint();
         }

         this.stopped = stop;
         this.busy = false;
      }

      public void clear() {
         Arrays.fill(this.packets, null);
         this.count = 0;
         this.tick = 0;
         this.phase = 0;
         this.lastTick = 0;
         this.stopped = false;
         this.busy = false;
      }
   }
}
