package dev.slinky.client;

import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.hud.KeystrokesClicks;
import dev.slinky.module.impl.KeystrokesModule;
import dev.slinky.client.hook.RenderHooks;
import dev.slinky.client.packet.PacketPipeline;
import dev.slinky.inject.ReflectionHelper;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.AutoBlockModule;
import dev.slinky.setting.Setting;
import io.netty.channel.Channel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ClientEventBridge {
   private static ClientEventBridge instance;
   private final ModuleManager modules;
   private final KeyBinding openGui;
   private final CommandBase command;
   private boolean keyPending;
   private boolean mousePending;
   private Channel attachedChannel;
   private static Field channelField;

   public ClientEventBridge(ModuleManager modules, KeyBinding openGui, CommandBase command) {
      this.modules = modules;
      this.openGui = openGui;
      this.command = command;
   }

   public static void install(ClientEventBridge bridge) {
      synchronized (ClientEventBridge.class) {
         ClientEventBridge previous = instance;
         instance = bridge;
         if (previous != null && previous != bridge) {
            Channel channel = previous.attachedChannel;
            previous.attachedChannel = null;
            previous.keyPending = false;
            previous.mousePending = false;
            if (channel != null && channel.pipeline().get("slinky_semantic_packets") != null) {
               channel.pipeline().remove("slinky_semantic_packets");
            }
         }
      }
   }

   public static void clientTick(boolean start) {
      ClientEventBridge bridge = instance;
      if (bridge != null) {
         if (start) {
            bridge.attachPipeline();
         }

         bridge.modules.clientTick(start);
      }
   }

   public static void renderTick(boolean start) {
      ClientEventBridge bridge = instance;
      if (bridge != null) {
         bridge.modules.renderTick(start);
         if (!start) {
            GameHooks.afterFrameRender();
         }
      }
   }

   public static boolean keyboardNext() {
      ClientEventBridge bridge = instance;
      if (bridge != null && bridge.keyPending) {
         bridge.keyPending = false;
         bridge.onKeyInput();
      }

      boolean next = Keyboard.next();
      if (bridge != null) {
         bridge.keyPending = next;
      }

      return next;
   }

   public static boolean mouseNext() {
      ClientEventBridge bridge = instance;
      if (bridge != null && bridge.mousePending) {
         bridge.mousePending = false;
         ForgeInputAdapter.mouse(bridge.modules);
      }

      boolean next = Mouse.next();
      if (next && bridge != null) { bridge.trackMouseClick(); }

      while (next && bridge != null && bridge.swallowMouseEvent()) {
         next = Mouse.next();
         if (next) { bridge.trackMouseClick(); }
      }

      if (bridge != null) {
         bridge.mousePending = next;
      }

      return next;
   }

   private void trackMouseClick() {
      if (this.modules.context().minecraft().currentScreen == null) {
         KeystrokesClicks.onMouseButton(Mouse.getEventButton(), Mouse.getEventButtonState());
      }
   }

   private boolean swallowMouseEvent() {
      Module module = this.modules.find("autoblock");
      return module instanceof AutoBlockModule && ((AutoBlockModule)module).swallowMouseEvent(this.modules.context());
   }

   private void onKeyInput() {
      ForgeInputAdapter.keyboard(this.modules);
      if (this.openGui.isPressed() && this.needsConfiguration()) {
         Minecraft.getMinecraft().displayGuiScreen(new GuiModuleList(this.modules));
      }
   }

   private boolean needsConfiguration() {
      Module gui = this.modules.find("GUI");
      if (gui == null) {
         return true;
      } else {
         for (Setting<?> setting : gui.getSettings()) {
            if (!setting.isConfigured()) {
               return true;
            }
         }

         return false;
      }
   }

   public static void handleKeyboardInput(GuiScreen screen) throws IOException {
      ClientEventBridge bridge = instance;
      if (bridge == null || !bridge.screenKeyInput(screen)) {
         screen.handleKeyboardInput();
      }
   }

   public static void handleMouseInput(GuiScreen screen) throws IOException {
      ClientEventBridge bridge = instance;
      if (bridge == null || !bridge.screenMouseInput(screen)) {
         screen.handleMouseInput();
      }
   }

   private boolean screenKeyInput(GuiScreen screen) {
      this.keyPending = false;
      if (GuiMenuCommand.handle(screen, Keyboard.getEventKey(), Keyboard.getEventKeyState(), this.modules)) {
         return true;
      } else if (screen instanceof RenderHooks.GuiFrame) {
         return false;
      } else {
         int result = ForgeInputAdapter.keyboard(this.modules);
         return !isConfigurationScreen(screen) && (result & 0xFF) != 0;
      }
   }

   private boolean screenMouseInput(GuiScreen screen) {
      this.mousePending = false;
      if (screen instanceof RenderHooks.GuiFrame) {
         return false;
      }
      int result = ForgeInputAdapter.mouse(this.modules);
      return !isConfigurationScreen(screen) && (result & 0xFF) != 0;
   }

   private static boolean isConfigurationScreen(GuiScreen screen) {
      return screen instanceof RenderHooks.GuiFrame || screen instanceof GuiModuleList || screen instanceof GuiModuleSettings;
   }

   public static void loadWorld(Minecraft minecraft) {
      ClientEventBridge bridge = instance;
      if (bridge != null && minecraft.theWorld != null) {
         bridge.modules.worldUnload();
      }
   }

   public static void disconnect() {
      final ClientEventBridge bridge = instance;
      if (bridge != null) {
         bridge.attachedChannel = null;
         Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
               bridge.modules.worldUnload();
            }
         });
      }
   }

   private void attachPipeline() {
      NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
      final Channel channel = handler == null ? null : channel(handler.getNetworkManager());
      if (channel != null && channel != this.attachedChannel && channel.isOpen()) {
         this.attachedChannel = channel;
         channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
               synchronized (ClientEventBridge.class) {
                  try {
                     // Keep the existing Java 8 synthetic accessor schema for live replacement.
                     Field active = ClientEventBridge.class.getDeclaredField("instance");
                     active.setAccessible(true);
                     if (active.get(null) == ClientEventBridge.this && channel.isOpen()
                        && channel.pipeline().get("slinky_semantic_packets") == null
                        && channel.pipeline().get("packet_handler") != null) {
                        channel.pipeline().addBefore("packet_handler", "slinky_semantic_packets", new PacketPipeline(ClientEventBridge.this.modules));
                     }
                  } catch (ReflectiveOperationException failure) {
                     throw new IllegalStateException("Could not check the active Slinky session", failure);
                  }
               }
            }
         });
      }
   }

   private static Channel channel(NetworkManager manager) {
      if (channelField == null) {
         channelField = ReflectionHelper.findField(NetworkManager.class, "channel", "field_150746_k");
      }

      try {
         return (Channel)channelField.get(manager);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static boolean sendChatMessage(String message) {
      ClientEventBridge bridge = instance;
      if (bridge != null && message != null && message.startsWith("/")) {
         String[] words = message.substring(1).trim().split(" ");
         String name = words[0];
         if (name.equalsIgnoreCase("keystrokesmod")) {
            Module keystrokes = bridge.modules.find("keystrokes");
            if (keystrokes instanceof KeystrokesModule) {
               ((KeystrokesModule)keystrokes).openEditor();
               return true;
            }
         }
         if (name.equalsIgnoreCase(bridge.command.getCommandName())
            || bridge.command.getCommandAliases() != null && bridge.command.getCommandAliases().contains(name)) {
            Minecraft minecraft = Minecraft.getMinecraft();

            try {
               bridge.command.processCommand(minecraft.thePlayer, Arrays.copyOfRange(words, 1, words.length));
            } catch (CommandException var7) {
               ChatComponentTranslation error = new ChatComponentTranslation(var7.getMessage(), var7.getErrorObjects());
               error.getChatStyle().setColor(EnumChatFormatting.RED);
               minecraft.thePlayer.addChatMessage(error);
            } catch (Throwable var8) {
               ChatComponentText errorx = new ChatComponentText("Slinky command failed: " + var8);
               errorx.getChatStyle().setColor(EnumChatFormatting.RED);
               minecraft.thePlayer.addChatMessage(errorx);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
