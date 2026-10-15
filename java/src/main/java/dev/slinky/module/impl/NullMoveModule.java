package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.Arrays;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

public final class NullMoveModule extends EvidenceModule {
   private final NullMoveModule.State state = new NullMoveModule.State();

   public NullMoveModule() {
      super("nullmove", "Null Move", ModuleCategory.MOVEMENT);
   }

   @Override
   public boolean listens(int type) {
      return type == 16;
   }

   private KeyBinding[] keys(ClientContext context) {
      GameSettings g = context.minecraft().gameSettings;
      return new KeyBinding[]{g.keyBindForward, g.keyBindLeft, g.keyBindBack, g.keyBindRight};
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (context.ready() && context.minecraft().inGameHasFocus) {
         final KeyBinding[] bindings = this.keys(context);
         this.state.update(context.player().ticksExisted, new NullMoveModule.Keys() {
            @Override
            public boolean pressed(int index) {
               return bindings[index].isKeyDown();
            }

            @Override
            public boolean physical(int index) {
               return KeyState.physical(bindings[index]);
            }

            @Override
            public void set(int index, boolean pressed) {
               KeyState.set(bindings[index], pressed);
            }
         });
      }
   }

   private void restore(ClientContext context) {
      for (KeyBinding key : this.keys(context)) {
         KeyState.restore(key);
      }

      this.state.clear();
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.restore(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.restore(context);
   }

   public interface Keys {
      boolean pressed(int var1);

      boolean physical(int var1);

      void set(int var1, boolean var2);
   }

   public static final class State {
      private final int[] stamps = new int[4];
      private static final int[] OPPOSITE = new int[]{2, 3, 0, 1};

      public void update(int stamp, NullMoveModule.Keys keys) {
         for (int direction = 0; direction < 4; direction++) {
            int previous = this.stamps[direction];
            int other = OPPOSITE[direction];
            int next = 0;
            boolean pressed = keys.pressed(direction);
            boolean oppositePressed = keys.pressed(other);
            if (pressed) {
               next = stamp;
               if (previous == 0 && oppositePressed) {
                  keys.set(other, false);
               }
            } else if (previous != 0 && keys.physical(other)) {
               keys.set(other, true);
            }

            this.stamps[direction] = next;
         }
      }

      public int stamp(int index) {
         return this.stamps[index];
      }

      public void clear() {
         Arrays.fill(this.stamps, 0);
      }
   }
}
