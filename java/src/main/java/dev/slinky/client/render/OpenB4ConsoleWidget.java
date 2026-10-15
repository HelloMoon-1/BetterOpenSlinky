package dev.slinky.client.render;

import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.CommandLineModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** ClickGui's donor console. Coordinates and fonts are vanilla GUI units. */
public final class OpenB4ConsoleWidget {
   private final ModuleManager manager;
   private final OpenB4Console console;
   private GuiTextField input;
   private GuiButton send;
   private int width;
   private int height;
   private int currentPanelWidth;

   public OpenB4ConsoleWidget(ModuleManager manager) {
      this.manager = manager;
      this.console = new OpenB4Console(manager);
   }

   public void init(int scaledWidth, int scaledHeight) {
      Minecraft mc = this.manager.context().minecraft();
      String previous = this.input == null ? "" : this.input.getText();
      boolean focused = this.input != null && this.input.isFocused();
      this.width = scaledWidth;
      this.height = scaledHeight;
      this.input = new GuiTextField(1, mc.fontRendererObj, 22, scaledHeight - 100, 150, 20);
      this.input.setMaxStringLength(256);
      this.input.setText(previous);
      this.input.setFocused(focused);
      this.send = new GuiButton(2, 22, scaledHeight - 70, 150, 20, "Send");
   }

   private CommandLineModule module() {
      Module module = this.manager.find("command_line");
      return module instanceof CommandLineModule ? (CommandLineModule)module : null;
   }

   public boolean visible() {
      CommandLineModule module = this.module();
      return module != null && (module.isEnabled() || module.panelWidth() > 0);
   }

   public boolean capturesKeyboard() {
      return this.visible() && this.input != null && this.input.isFocused();
   }

   public void draw(int mouseX, int mouseY, float partial) {
      Minecraft mc = this.manager.context().minecraft();
      ScaledResolution sr = new ScaledResolution(mc);
      if (this.input == null || this.width != sr.getScaledWidth() || this.height != sr.getScaledHeight()) {
         this.init(sr.getScaledWidth(), sr.getScaledHeight());
      }
      CommandLineModule module = this.module();
      this.currentPanelWidth = module == null ? 0 : module.panelWidth();
      if (this.currentPanelWidth <= 0) {
         if (module == null || !module.isEnabled()) {
            this.input.setFocused(false);
         }
         return;
      }
      int w = this.currentPanelWidth;
      int scale = sr.getScaleFactor();
      this.input.xPosition = w - 178;
      this.send.xPosition = w - 178;
      this.send.enabled = module.isEnabled();
      try (RavenRender.Scope scope = RavenRender.begin()) {
         GL11.glEnable(GL11.GL_SCISSOR_TEST);
         GL11.glScissor(0, 0, Math.max(0, w * scale), mc.displayHeight);
         Gui.drawRect(0, 0, w, this.height, 0x7D000000);
         Gui.drawRect(0, this.height - 360, w, this.height - 359, -1);
         Gui.drawRect(0, this.height - 115, w, this.height - 114, -1);
         Gui.drawRect(w - 1, 0, w, this.height, -1);
         int top = this.height - 360;
         GL11.glScissor(0, mc.displayHeight - (top + 245) * scale, Math.max(0, w * scale - 2), 245 * scale - 2);
         Gui.drawRect(0, 0, Math.max(1000, w), Math.max(1000, this.height), module.background());
         this.drawLines(w - 195, this.height - 130);
         GL11.glScissor(0, 0, Math.max(0, w * scale), mc.displayHeight);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         this.input.drawTextBox();
         this.send.drawButton(mc, mouseX, mouseY);
      }
   }

   private void drawLines(int x, int y) {
      Minecraft mc = this.manager.context().minecraft();
      List<String> lines = this.console.lines();
      for (int i = lines.size() - 1; i >= 0; i--) {
         String text = lines.get(i);
         int color = -1;
         if (text.contains("&a")) {
            text = text.replace("&a", "");
            color = 0xFF00FF00;
         } else if (text.contains("&c")) {
            text = text.replace("&c", "");
            color = 0xFFFF0000;
         } else if (text.contains("&e")) {
            text = text.replace("&e", "");
            color = 0xFFFFFF00;
         }
         mc.fontRendererObj.drawString(text, x, y, color);
         y -= mc.fontRendererObj.FONT_HEIGHT + 5;
      }
   }

   public boolean mouseClicked(int x, int y, int button) {
      if (!this.visible() || this.input == null) {
         return false;
      }
      CommandLineModule module = this.module();
      if (x < 0 || x >= this.currentPanelWidth || y < 0 || y >= this.height) {
         this.input.setFocused(false);
         return false;
      }
      if (module != null && module.isEnabled()) {
         this.input.mouseClicked(x, y, button);
         if (button == 0 && this.send.mousePressed(this.manager.context().minecraft(), x, y)) {
            this.send.playPressSound(this.manager.context().minecraft().getSoundHandler());
            this.submit();
         }
      }
      // The visible panel owns its clicks, not the categories behind it.
      return true;
   }

   public boolean keyTyped(char typed, int keyCode) {
      if (!this.capturesKeyboard()) {
         return false;
      }
      if (keyCode == Keyboard.KEY_ESCAPE) {
         this.input.setFocused(false);
         return false;
      }
      CommandLineModule module = this.module();
      if (module == null || !module.isEnabled()) {
         return true;
      }
      if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
         this.submit();
      } else {
         this.input.textboxKeyTyped(typed, keyCode);
      }
      return true;
   }

   private void submit() {
      this.console.handle(this.input.getText());
      this.input.setText("");
   }

   public void update() {
      if (this.input != null && this.visible()) {
         this.input.updateCursorCounter();
      }
   }

   public void closed() {
      if (this.input != null) {
         this.input.setFocused(false);
      }
   }
}
