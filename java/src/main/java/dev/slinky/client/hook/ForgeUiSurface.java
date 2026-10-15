package dev.slinky.client.hook;

import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.Module;
import dev.slinky.module.impl.NotificationsModule;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

final class ForgeUiSurface implements UiFrameDriver.Surface {
   private static final int GL_FRAMEBUFFER_BINDING = 36006;
   private static final int GL_RENDERBUFFER_BINDING = 36007;
   private final ModuleManager modules;
   private int framebuffer = -1;
   private int texture = -1;
   private int depth = -1;
   private int width;
   private int height;
   private int previousMatrix;
   private int previousFramebuffer;
   private boolean drawing;
   private boolean direct;
   private double previousDirectDraw = -1.0;
   private double canvasScale = 1.0;
   private GlStateScope state;
   private boolean projectionPushed;
   private boolean modelPushed;
   private boolean texturePushed;

   ForgeUiSurface(ModuleManager modules) {
      this.modules = modules;
   }

   @Override
   public boolean resize(int width, int height) {
      boolean sameSize = this.width == width && this.height == height;
      if (!sameSize || this.framebuffer == -1 && !this.direct) {
         this.width = width;
         this.height = height;
         this.deleteBuffer();
         this.direct = !OpenGlHelper.framebufferSupported || !this.createBuffer();
         this.previousDirectDraw = -1.0;
         return true;
      } else {
         return false;
      }
   }

   private boolean createBuffer() {
      int previous = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
      int previousRenderbuffer = GL11.glGetInteger(GL_RENDERBUFFER_BINDING);
      boolean complete = false;

      try (GlStateScope allocation = GlStateScope.open()) {
         this.texture = TextureUtil.glGenTextures();
         GlStateManager.bindTexture(this.texture);
         GL11.glTexParameteri(3553, 10241, 9728);
         GL11.glTexParameteri(3553, 10240, 9728);
         GL11.glTexParameteri(3553, 10242, 10496);
         GL11.glTexParameteri(3553, 10243, 10496);
         GL11.glTexImage2D(3553, 0, 32856, this.width, this.height, 0, 6408, 5121, (ByteBuffer)null);
         this.framebuffer = OpenGlHelper.glGenFramebuffers();
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.framebuffer);
         OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, 3553, this.texture, 0);
         this.depth = OpenGlHelper.glGenRenderbuffers();
         OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, this.depth);
         OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, 33190, this.width, this.height);
         OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT,
            OpenGlHelper.GL_RENDERBUFFER, this.depth);
         complete = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE;
         if (complete) {
            this.clearBuffer();
         }
      } finally {
         OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, previousRenderbuffer);
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
         if (!complete) {
            this.deleteBuffer();
         }
      }

      return complete;
   }

   private void clearBuffer() {
      // Both callers own a GlStateScope; inherited masks must not clip the clear.
      GL11.glViewport(0, 0, this.width, this.height);
      GL11.glDisable(3089);
      GlStateManager.colorMask(true, true, true, true);
      GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
      GlStateManager.depthMask(true);
      GL11.glClearDepth(1.0);
      GL11.glClear(16384 | 256);
   }

   private void prepare2D(boolean writeAlpha) {
      GL11.glViewport(0, 0, this.width, this.height);
      GL11.glDisable(3089);
      GL11.glDisable(2960);
      GL11.glPolygonMode(1032, 6914);
      GlStateManager.colorMask(true, true, true, writeAlpha);
      GlStateManager.disableDepth();
      GlStateManager.depthMask(false);
      GlStateManager.disableLighting();
      GlStateManager.disableFog();
      GlStateManager.disableCull();
      GlStateManager.disableAlpha();
      GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
      GlStateManager.disableTexture2D();
      GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
      GlStateManager.disableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
      GlStateManager.resetColor();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Override
   public void scale(double value) {
      this.canvasScale = Double.isFinite(value) && value > 0.0 ? value : 1.0;
   }

   @Override
   public int update(float elapsed) {
      SemanticEvent event = new SemanticEvent(9);
      event.elapsedSeconds = (double)elapsed;
      RenderEventPayload payload = RenderEventPayload.attach(event);

      int var4;
      try {
         this.modules.dispatch(event);
         var4 = payload.redrawFlags;
         if (this.modules.context().minecraft().currentScreen instanceof RenderHooks.GuiFrame) {
            var4 |= 3;
         }
      } finally {
         RenderEventPayload.detach(event);
      }

      return var4;
   }

   @Override
   public void begin(double now) {
      // Direct mode draws only from presentDirect, once per display frame.
      if (!this.direct) {
         this.open(true);
      }
   }

   private void open(boolean offscreen) {
      this.previousMatrix = GL11.glGetInteger(2976);
      this.previousFramebuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(GL_FRAMEBUFFER_BINDING) : 0;
      this.state = GlStateScope.open();
      this.drawing = true;

      try {
         if (offscreen) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.framebuffer);
            this.clearBuffer();
         }

         this.prepare2D(true);
         GL11.glMatrixMode(5890);
         GL11.glPushMatrix();
         this.texturePushed = true;
         GL11.glLoadIdentity();
         GL11.glMatrixMode(5889);
         GL11.glPushMatrix();
         this.projectionPushed = true;
         GL11.glLoadIdentity();
         GL11.glOrtho(0.0, (double)this.width, (double)this.height, 0.0, -3000.0, 3000.0);
         GL11.glMatrixMode(5888);
         GL11.glPushMatrix();
         this.modelPushed = true;
         GL11.glLoadIdentity();
      } catch (Error | RuntimeException var5) {
         try {
            this.end();
         } catch (Error | RuntimeException var4) {
            var5.addSuppressed(var4);
         }

         throw var5;
      }
   }

   @Override
   public void draw(float elapsed) {
      if (this.drawing) {
         Minecraft game = this.modules.context().minecraft();
         SemanticEvent event = new SemanticEvent(10);
         event.elapsedSeconds = (double)elapsed;
         event.renderWidth = (float)this.width;
         event.renderHeight = (float)this.height;
         event.timerRenderPartialTicks = MinecraftFields.timer(game).renderPartialTicks;
         RenderEventPayload payload = RenderEventPayload.attach(event);
         if (game.currentScreen instanceof RenderHooks.GuiFrame) {
            RenderHooks.GuiFrame screen = (RenderHooks.GuiFrame)game.currentScreen;
            payload.guiDraw = () -> screen.drawRecoveredFrame(event.timerRenderPartialTicks);
         }

         try {
            this.modules.dispatch(event);
            if (payload.guiDraw != null) {
               payload.guiDraw.run();
               // Toasts belong above the GUI backdrop/blur, not underneath it.
               Module notifications = this.modules.find("notifications");
               if (notifications instanceof NotificationsModule) {
                  ((NotificationsModule)notifications).renderOverlay(this.modules.context(), event);
               }
            }
         } finally {
            RenderEventPayload.detach(event);
         }
      }
   }

   @Override
   public void end() {
      if (this.drawing) {
         this.drawing = false;

         try {
            if (OpenGlHelper.framebufferSupported) {
               OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.previousFramebuffer);
            }

            if (this.modelPushed) {
               GL11.glMatrixMode(5888);
               GL11.glPopMatrix();
            }

            if (this.projectionPushed) {
               GL11.glMatrixMode(5889);
               GL11.glPopMatrix();
            }

            if (this.texturePushed) {
               GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
               GL11.glMatrixMode(5890);
               GL11.glPopMatrix();
            }

            GL11.glMatrixMode(this.previousMatrix);
         } finally {
            this.modelPushed = this.projectionPushed = this.texturePushed = false;

            try {
               this.state.close();
            } finally {
               this.state = null;
            }
         }
      }
   }

   @Override
   public void present(double now) {
      if (this.direct) {
         this.presentDirect(now);
      } else {
         int mode = GL11.glGetInteger(2976);

         try (GlStateScope presentation = GlStateScope.open()) {
            this.prepare2D(false);
            GL11.glMatrixMode(5890);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(5889);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, (double)this.width, (double)this.height, 0.0, 1000.0, 3000.0);
            GL11.glMatrixMode(5888);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

            try {
               GlStateManager.enableTexture2D();
               GlStateManager.tryBlendFuncSeparate(1, 771, 1, 771);
               GlStateManager.bindTexture(this.texture);
               GL11.glBegin(7);
               GL11.glTexCoord2f(0.0F, 0.0F);
               GL11.glVertex3f(0.0F, (float)this.height, 0.0F);
               GL11.glTexCoord2f(1.0F, 0.0F);
               GL11.glVertex3f((float)this.width, (float)this.height, 0.0F);
               GL11.glTexCoord2f(1.0F, 1.0F);
               GL11.glVertex3f((float)this.width, 0.0F, 0.0F);
               GL11.glTexCoord2f(0.0F, 1.0F);
               GL11.glVertex3f(0.0F, 0.0F, 0.0F);
               GL11.glEnd();
            } finally {
               GL11.glMatrixMode(5888);
               GL11.glPopMatrix();
               GL11.glMatrixMode(5889);
               GL11.glPopMatrix();
               GL11.glMatrixMode(5890);
               GL11.glPopMatrix();
               GL11.glMatrixMode(mode);
            }
         }
      }
   }

   private void presentDirect(double now) {
      double delta = this.previousDirectDraw < 0.0 ? 0.0 : now - this.previousDirectDraw;
      float elapsed = Double.isFinite(delta) ? (float)Math.max(0.0, Math.min((double)UiFrameDriver.MAX_ELAPSED, delta)) : 0.0F;
      this.previousDirectDraw = now;
      this.open(false);

      try {
         this.draw(elapsed);
      } finally {
         this.end();
      }
   }

   private void deleteBuffer() {
      try {
         if (this.framebuffer != -1) {
            OpenGlHelper.glDeleteFramebuffers(this.framebuffer);
         }
      } finally {
         this.framebuffer = -1;

         try {
            if (this.depth != -1) {
               OpenGlHelper.glDeleteRenderbuffers(this.depth);
            }
         } finally {
            this.depth = -1;
            try {
               if (this.texture != -1) {
                  TextureUtil.deleteTexture(this.texture);
               }
            } finally {
               this.texture = -1;
            }
         }
      }
   }

   int drawingTexture() {
      return this.drawing && !this.direct ? this.texture : -1;
   }

   public double canvasScale() {
      return this.canvasScale;
   }

   void close() {
      this.end();
      if (this.framebuffer != -1 || this.texture != -1 || this.depth != -1) {
         try {
            if (Display.isCreated() && Display.isCurrent()) {
               this.deleteBuffer();
            }
         } catch (LWJGLException var5) {
            throw new IllegalStateException("Could not release the canvas framebuffer", var5);
         } finally {
            this.framebuffer = this.texture = this.depth = -1;
         }
      }
   }
}
