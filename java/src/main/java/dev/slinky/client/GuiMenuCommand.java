package dev.slinky.client;

import dev.slinky.inject.ReflectionHelper;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public final class GuiMenuCommand {
   public static boolean matches(String text) {
      return text != null && text.trim().equals("!gui");
   }

   public static boolean handle(GuiScreen screen, int key, boolean pressed, ModuleManager modules) {
      if (screen instanceof GuiChat && pressed && (key == 28 || key == 156)) {
         GuiTextField input = ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat)screen, "inputField", "field_146415_a");
         if (!matches(input.getText())) {
            return false;
         } else {
            Module gui = modules.find("GUI");
            if (gui == null) {
               return false;
            } else {
               Minecraft.getMinecraft().displayGuiScreen(null);
               modules.requestEnabled(gui, true);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private GuiMenuCommand() {
   }
}
