package dev.slinky.client.render;

import dev.slinky.client.hook.RenderHooks;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.GuiModule;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public class RavenHudEditor extends GuiScreen implements RenderHooks.GuiFrame {
   protected final ModuleManager manager;
   private int startX;
   private int startY;

   public RavenHudEditor(ModuleManager manager) {
      this.manager = manager;
   }

   protected void initPos(int x, int y) {
      this.startX = x;
      this.startY = y;
   }

   protected void closed(int x, int y) {
      if (x != this.startX || y != this.startY) {
         this.manager.persist();
      }
   }

   @Override
   public final void drawScreen(int mouseX, int mouseY, float partial) {
      // GuiFrame is rendered once from ForgeUiSurface, not both GUI passes.
   }

   @Override
   public final void drawRecoveredFrame(float partial) {
      try {
         this.handleEditorInput();
      } catch (IOException ignored) {
      }
      if (this.mc.currentScreen != this) {
         return;
      }
      int mouseX = Mouse.getX() * this.width / Math.max(1, this.mc.displayWidth);
      int mouseY = this.height - Mouse.getY() * this.height / Math.max(1, this.mc.displayHeight) - 1;
      int program = GLContext.getCapabilities().OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
      try (RavenRender.Scope scope = RavenRender.begin()) {
         // An inherited world/UI program must not reinterpret the font's alpha-only atlas.
         if (program != 0) GL20.glUseProgram(0);
         if (this.editorDecoration()) {
            OpenB4GuiScreen.drawEditorBackground(this.manager, this.width, this.height);
            OpenB4GuiScreen.drawWatermark(this.manager, 4, this.height - 12, false);
         }
         this.drawEditor(mouseX, mouseY, partial);
      } finally {
         if (program != 0) GL20.glUseProgram(program);
      }
   }

   private void handleEditorInput() throws IOException {
      // Keep the checked-exception boundary independent of named JAR metadata.
      this.handleInput();
   }

   protected boolean editorDecoration() {
      return true;
   }

   protected void drawEditor(int mouseX, int mouseY, float partial) {
      super.drawScreen(mouseX, mouseY, partial);
   }

   @Override
   protected void keyTyped(char character, int key) throws IOException {
      GuiModule gui = (GuiModule)this.manager.find("gui");
      if (key == 1 || gui != null && dev.slinky.client.NativeKeyMap.keyboardId(key) == gui.getBinding()) {
         this.mc.displayGuiScreen(null);
         if (gui != null) gui.finishEditorClose();
         else if (this.mc.currentScreen == null && org.lwjgl.opengl.Display.isActive()) {
            this.mc.setIngameFocus();
            if (this.mc.inGameHasFocus && !Mouse.isGrabbed()) this.mc.mouseHelper.grabMouseCursor();
         }
      } else {
         super.keyTyped(character, key);
      }
   }

   @Override
   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public void onGuiClosed() {
      GuiModule gui = (GuiModule)this.manager.find("gui");
      if (gui != null) {
         gui.saveOnClose();
         gui.screenClosed(this);
      } else {
         this.manager.persist();
      }
   }
}
