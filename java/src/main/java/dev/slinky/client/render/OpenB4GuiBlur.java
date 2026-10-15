package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import dev.slinky.client.hook.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Full-screen donor Blur.render(2, offset), composited into the active GuiFrame target. */
final class OpenB4GuiBlur {
   private static final String VERTEX = "#version 120\nvoid main(){gl_TexCoord[0]=gl_MultiTexCoord0;gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}";
   private static final String DOWN = "#version 120\nuniform sampler2D inTexture;uniform vec2 offset,halfpixel,iResolution;void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv)*4.0;sum+=texture2D(inTexture,uv-halfpixel.xy*offset);sum+=texture2D(inTexture,uv+halfpixel.xy*offset);sum+=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset);sum+=texture2D(inTexture,uv-vec2(halfpixel.x,-halfpixel.y)*offset);gl_FragColor=vec4(sum.rgb*0.125,1.0);}";
   // The donor's full-screen stencil is white everywhere; check=0 has exactly
   // that alpha without a redundant stencil FBO/texture unit 16 allocation.
   private static final String UP = "#version 120\nuniform sampler2D inTexture;uniform vec2 halfpixel,offset,iResolution;void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv+vec2(-halfpixel.x*2.0,0.0)*offset);sum+=texture2D(inTexture,uv+vec2(-halfpixel.x,halfpixel.y)*offset)*2.0;sum+=texture2D(inTexture,uv+vec2(0.0,halfpixel.y*2.0)*offset);sum+=texture2D(inTexture,uv+vec2(halfpixel.x,halfpixel.y)*offset)*2.0;sum+=texture2D(inTexture,uv+vec2(halfpixel.x*2.0,0.0)*offset);sum+=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset)*2.0;sum+=texture2D(inTexture,uv+vec2(0.0,-halfpixel.y*2.0)*offset);sum+=texture2D(inTexture,uv+vec2(-halfpixel.x,-halfpixel.y)*offset)*2.0;gl_FragColor=vec4((sum/12.0).rgb,1.0);}";
   private final Framebuffer[] buffers = new Framebuffer[3];
   private int down;
   private int up;
   private boolean unavailable;

   void draw(float width, float height, float offset) {
      Minecraft mc = Minecraft.getMinecraft();
      if (offset <= 0.0F || this.unavailable || !OpenGlHelper.framebufferSupported || !OpenGlHelper.areShadersSupported()
         || !GLContext.getCapabilities().OpenGL20
         || mc.getFramebuffer() == null || mc.getFramebuffer().framebufferTexture < 0) return;
      int target = GL11.glGetInteger(36006);
      int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      try (GlStateScope state = GlStateScope.open()) {
         if (this.down == 0) {
            this.down = program(DOWN);
            this.up = program(UP);
         }
         if (this.buffers[0] == null || this.buffers[0].framebufferWidth != mc.displayWidth
            || this.buffers[0].framebufferHeight != mc.displayHeight) {
            this.deleteBuffers();
            for (int i = 0; i < this.buffers.length; i++) {
               this.buffers[i] = new Framebuffer(Math.max(1, mc.displayWidth >> i), Math.max(1, mc.displayHeight >> i), false);
               this.buffers[i].setFramebufferFilter(GL11.GL_LINEAR);
               GlStateManager.bindTexture(this.buffers[i].framebufferTexture);
               GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33648);
               GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33648);
            }
         }
         GlStateManager.disableDepth();
         GlStateManager.depthMask(false);
         GlStateManager.disableAlpha();
         GlStateManager.disableLighting();
         GlStateManager.disableBlend();
         GlStateManager.enableTexture2D();
         GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
         OpenB4GuiDrawing.resetColor();
         this.captureSource(mc, target, width, height);
         this.pass(this.buffers[1], this.buffers[0].framebufferTexture, this.down, offset, width, height);
         this.pass(this.buffers[2], this.buffers[1].framebufferTexture, this.down, offset, width, height);
         this.pass(this.buffers[1], this.buffers[2].framebufferTexture, this.up, offset, width, height);
         this.pass(this.buffers[0], this.buffers[1].framebufferTexture, this.up, offset, width, height);
         GL20.glUseProgram(0);
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
         GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
         GlStateManager.bindTexture(this.buffers[0].framebufferTexture);
         quad(width, height);
      } catch (RuntimeException failure) {
         this.unavailable = true;
         this.release();
      } finally {
         GL20.glUseProgram(oldProgram);
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
      }
   }

   private void captureSource(Minecraft mc, int target, float width, float height) {
      int uiTexture = RenderHooks.uiTexture();
      Framebuffer source = this.buffers[0];
      GL20.glUseProgram(0);
      if (uiTexture >= 0 && uiTexture != source.framebufferTexture) {
         source.framebufferClear();
         source.bindFramebuffer(true);
         GlStateManager.disableBlend();
         GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
         quad(width, height);
         // ForgeUiSurface stores premultiplied UI alpha. Reconstruct the donor
         // world + HUD + tint before the first down-pass, without feedback.
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GlStateManager.bindTexture(uiTexture);
         quad(width, height);
         GlStateManager.disableBlend();
      } else {
         // Direct presentation already contains the donor HUD and tint. Copy
         // that framebuffer to a distinct texture before any blur FBO is bound.
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
         GlStateManager.bindTexture(source.framebufferTexture);
         GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, mc.displayWidth, mc.displayHeight);
      }
   }

   private void pass(Framebuffer target, int texture, int shader, float offset, float width, float height) {
      target.framebufferClear();
      target.bindFramebuffer(true);
      GL20.glUseProgram(shader);
      GlStateManager.bindTexture(texture);
      GL20.glUniform1i(GL20.glGetUniformLocation(shader, "inTexture"), 0);
      GL20.glUniform2f(GL20.glGetUniformLocation(shader, "offset"), offset, offset);
      GL20.glUniform2f(GL20.glGetUniformLocation(shader, "halfpixel"), 1.0F / target.framebufferWidth, 1.0F / target.framebufferHeight);
      GL20.glUniform2f(GL20.glGetUniformLocation(shader, "iResolution"), target.framebufferWidth, target.framebufferHeight);
      quad(width, height);
   }

   private static void quad(float width, float height) {
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(0.0F, height);
      GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(width, height);
      GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(width, 0.0F);
      GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(0.0F, 0.0F);
      GL11.glEnd();
   }

   private static int program(String fragment) {
      int vertex = 0, pixel = 0, program = 0;
      try {
         vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
         pixel = shader(GL20.GL_FRAGMENT_SHADER, fragment);
         program = GL20.glCreateProgram();
         GL20.glAttachShader(program, vertex);
         GL20.glAttachShader(program, pixel);
         GL20.glLinkProgram(program);
         if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
         }
         return program;
      } catch (RuntimeException failure) {
         if (program != 0) GL20.glDeleteProgram(program);
         throw failure;
      } finally {
         if (vertex != 0) GL20.glDeleteShader(vertex);
         if (pixel != 0) GL20.glDeleteShader(pixel);
      }
   }

   private static int shader(int type, String source) {
      int shader = GL20.glCreateShader(type);
      GL20.glShaderSource(shader, source);
      GL20.glCompileShader(shader);
      if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
         String message = GL20.glGetShaderInfoLog(shader, 4096);
         GL20.glDeleteShader(shader);
         throw new IllegalStateException(message);
      }
      return shader;
   }

   private void deleteBuffers() {
      for (int i = 0; i < this.buffers.length; i++) {
         if (this.buffers[i] != null) {
            this.buffers[i].deleteFramebuffer();
            this.buffers[i] = null;
         }
      }
   }

   void release() {
      if (this.down == 0 && this.up == 0 && this.buffers[0] == null && this.buffers[1] == null && this.buffers[2] == null) return;
      if (!OpenGlHelper.framebufferSupported || !GLContext.getCapabilities().OpenGL20) return;
      int target = GL11.glGetInteger(36006);
      for (Framebuffer buffer : this.buffers) {
         if (buffer != null && buffer.framebufferObject == target) target = 0;
      }
      int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      if (oldProgram == this.down || oldProgram == this.up) oldProgram = 0;
      try (GlStateScope state = GlStateScope.open()) {
         this.deleteBuffers();
         if (this.down != 0) GL20.glDeleteProgram(this.down);
         if (this.up != 0) GL20.glDeleteProgram(this.up);
         this.down = this.up = 0;
      } finally {
         GL20.glUseProgram(oldProgram);
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
      }
   }
}
