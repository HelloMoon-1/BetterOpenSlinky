package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import dev.slinky.inject.ReflectionHelper;
import dev.slinky.setting.ColorSetting;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/** Native 1.8.9 body-model mask, not an expanded hitbox or a Forge event renderer. */
public final class PlayerEspGlowRenderer {
   public static final int MAX_TARGETS = 24;
   private static final String VERTEX = "#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}";
   private static final String MASK = "#version 120\nuniform vec3 tint;void main(){gl_FragColor=vec4(tint,1.0);}";
   // Dense, normalized 13-tap kernel. Smoothness changes sigma, NEVER the sample spacing:
   // stretching the old five samples skipped tiny distant silhouettes and created disconnected bands.
   private static final String GAUSSIAN = "vec4 blur(sampler2D image,vec2 uv,vec2 step){"
      + "vec4 sum=vec4(0.0);float total=0.0;float sigma=0.9+1.6*smoothness;"
      + "for(int i=-6;i<=6;i++){float d=float(i);float w=exp(-0.5*d*d/(sigma*sigma));"
      + "sum+=texture2D(image,uv+step*d)*w;total+=w;}return sum/total;}";
   private static final String HORIZONTAL = "#version 120\nuniform sampler2D image;uniform vec2 step;uniform float smoothness;"
      + GAUSSIAN + "void main(){gl_FragColor=blur(image,gl_TexCoord[0].xy,step);}";
   private static final String OUTLINE = "#version 120\n"
      + "uniform sampler2D image;uniform sampler2D mask;uniform vec2 texel;uniform vec2 blurTexel;uniform float smoothness;"
      + GAUSSIAN + "void main(){vec2 uv=gl_TexCoord[0].xy;"
      + "vec4 soft=blur(image,uv,vec2(0.0,blurTexel.y));"
      + "float center=texture2D(mask,uv).a*0.5;"
      + "center+=texture2D(mask,uv+vec2(texel.x,0.0)).a*0.125;"
      + "center+=texture2D(mask,uv-vec2(texel.x,0.0)).a*0.125;"
      + "center+=texture2D(mask,uv+vec2(0.0,texel.y)).a*0.125;"
      + "center+=texture2D(mask,uv-vec2(0.0,texel.y)).a*0.125;"
      + "vec3 tint=soft.rgb/max(soft.a,0.0001);"
      + "float a=(1.0-center)*(1.0-exp(-soft.a*(2.2+smoothness)))*0.85;"
      + "gl_FragColor=vec4(tint*a,a);}";

   private int framebuffer;
   private int texture;
   private int glowFramebuffer;
   private int glowTexture;
   private int blurFramebuffer;
   private int blurTexture;
   private int blurProgram;
   private int tintUniform;
   private int blurStepUniform;
   private int blurSmoothnessUniform;
   private int outlineTexelUniform;
   private int outlineBlurTexelUniform;
   private int outlineSmoothnessUniform;
   private int maskProgram;
   private int outlineProgram;
   private int compositeProgram;
   private int width;
   private int height;
   private int blurWidth;
   private int blurHeight;
   private Field outlineFlag;
   private boolean failed;
   private String limitation;
   private final IntBuffer viewport = BufferUtils.createIntBuffer(16);

   public String limitation() { return this.limitation; }

   /** Called only in semantic event 6 while the native world projection is active. */
   public boolean render(Minecraft game, List<Target> targets, float partial, double cameraX, double cameraY, double cameraZ, float smoothness) {
      smoothness = Math.max(0.0F, Math.min(1.0F, smoothness));
      if (this.failed) { return false; }
      if (targets.isEmpty()) { return true; }
      if (!OpenGlHelper.framebufferSupported || !GLContext.getCapabilities().OpenGL20) {
         this.failed = true;
         this.limitation = "Framebuffer/OpenGL 2.0 unavailable; box glow fallback";
         return false;
      }
      // Do not replace an external shader-pack program; its vertex transforms may differ.
      if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) {
         this.limitation = "External shader active; box glow fallback";
         return false;
      }
      int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      int oldMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      int modelDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
      int projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
      IntBuffer viewport = this.viewport;
      viewport.clear();
      GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
      FramebufferBinding binding = new FramebufferBinding();
      try (GlStateScope ignored = GlStateScope.open()) {
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPushMatrix();
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPushMatrix();
         try {
            this.ensure(viewport.get(2), viewport.get(3));
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.framebuffer);
            GL11.glViewport(0, 0, this.width, this.height);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GlStateManager.disableColorLogic();
            GlStateManager.colorMask(true, true, true, true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glUseProgram(this.maskProgram);
            int tint = this.tintUniform;
            // Input is nearest-first. Paint far-to-near so closer targets own overlapping mask pixels.
            for (int i = Math.min(MAX_TARGETS, targets.size()) - 1; i >= 0; i--) {
               Target target = targets.get(i);
               Render<EntityPlayer> renderer = game.getRenderManager().getEntityRenderObject(target.player);
               if (!(renderer instanceof RendererLivingEntity)) {
                  throw new IllegalStateException("Unsupported player renderer");
               }
               RendererLivingEntity<?> living = (RendererLivingEntity<?>)renderer;
               if (this.outlineFlag == null) {
                  this.outlineFlag = ReflectionHelper.findField(RendererLivingEntity.class, "renderOutlines", "field_177098_i");
               }
               boolean oldOutline = this.outlineFlag.getBoolean(living);
               GL20.glUniform3f(tint, target.color.red, target.color.green, target.color.blue);
               GlStateManager.disableDepth();
               GlStateManager.depthMask(false);
               GlStateManager.disableBlend();
               GlStateManager.disableAlpha();
               GlStateManager.disableLighting();
               GL11.glMatrixMode(GL11.GL_MODELVIEW);
               try {
                  // Vanilla outline branch omits names, equipment layers, shadows and fire.
                  living.setRenderOutlines(true);
                  EntityPlayer p = target.player;
                  renderer.doRender(p, lerp(p.prevPosX, p.posX, partial) - cameraX,
                     lerp(p.prevPosY, p.posY, partial) - cameraY, lerp(p.prevPosZ, p.posZ, partial) - cameraZ,
                     p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial, partial);
               } finally {
                  living.setRenderOutlines(oldOutline);
               }
            }
            // Keep fine silhouette coverage separate from the inexpensive, lower-resolution halo.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.blurFramebuffer);
            GL11.glViewport(0, 0, this.blurWidth, this.blurHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(this.texture);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GL20.glUseProgram(this.blurProgram);
            GL20.glUniform2f(this.blurStepUniform, 1.0F / this.blurWidth, 0.0F);
            GL20.glUniform1f(this.blurSmoothnessUniform, smoothness);
            quad();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.glowFramebuffer);
            GL11.glViewport(0, 0, this.width, this.height);
            GlStateManager.bindTexture(this.blurTexture);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.bindTexture(this.texture);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL20.glUseProgram(this.outlineProgram);
            GL20.glUniform2f(this.outlineTexelUniform, 1.0F / this.width, 1.0F / this.height);
            GL20.glUniform2f(this.outlineBlurTexelUniform, 1.0F / this.blurWidth, 1.0F / this.blurHeight);
            GL20.glUniform1f(this.outlineSmoothnessUniform, smoothness);
            quad();
            binding.restore();
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GlStateManager.bindTexture(this.glowTexture);
            GL20.glUseProgram(this.compositeProgram);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            quad();
            this.limitation = null;
            return true;
         } finally {
            GL20.glUseProgram(oldProgram);
            binding.restore();
            restoreStack(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH, modelDepth);
            restoreStack(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH, projectionDepth);
            GL11.glMatrixMode(oldMatrix);
         }
      } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
         this.failed = true;
         this.limitation = "Silhouette renderer unsupported; box glow fallback (" + unsupported.getClass().getSimpleName() + ")";
         this.releaseObjects();
         return false;
      }
   }

   private static void restoreStack(int mode, int depthName, int savedDepth) {
      GL11.glMatrixMode(mode);
      // Also unwind a native renderer's unmatched pushes if it failed mid-model.
      for (int depth = GL11.glGetInteger(depthName); depth > savedDepth; depth--) { GL11.glPopMatrix(); }
   }

   private static void quad() {
      GL11.glBegin(GL11.GL_QUADS);
      try {
         GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
         GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
         GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
         GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
      } finally { GL11.glEnd(); }
   }

   private static double lerp(double previous, double current, float partial) {
      return previous + (current - previous) * partial;
   }

   private void ensure(int viewportWidth, int viewportHeight) {
      if (viewportWidth <= 0 || viewportHeight <= 0) { throw new IllegalStateException("Empty viewport"); }
      if (this.maskProgram == 0) {
         this.maskProgram = program(MASK);
         this.tintUniform = GL20.glGetUniformLocation(this.maskProgram, "tint");
      }
      if (this.blurProgram == 0) {
         this.blurProgram = program(HORIZONTAL);
         GL20.glUseProgram(this.blurProgram);
         GL20.glUniform1i(GL20.glGetUniformLocation(this.blurProgram, "image"), OpenGlHelper.defaultTexUnit - 33984);
         this.blurStepUniform = GL20.glGetUniformLocation(this.blurProgram, "step");
         this.blurSmoothnessUniform = GL20.glGetUniformLocation(this.blurProgram, "smoothness");
      }
      if (this.outlineProgram == 0) {
         this.outlineProgram = program(OUTLINE);
         GL20.glUseProgram(this.outlineProgram);
         GL20.glUniform1i(GL20.glGetUniformLocation(this.outlineProgram, "image"), OpenGlHelper.defaultTexUnit - 33984);
         GL20.glUniform1i(GL20.glGetUniformLocation(this.outlineProgram, "mask"), OpenGlHelper.lightmapTexUnit - 33984);
         this.outlineTexelUniform = GL20.glGetUniformLocation(this.outlineProgram, "texel");
         this.outlineBlurTexelUniform = GL20.glGetUniformLocation(this.outlineProgram, "blurTexel");
         this.outlineSmoothnessUniform = GL20.glGetUniformLocation(this.outlineProgram, "smoothness");
      }
      if (this.compositeProgram == 0) {
         this.compositeProgram = program("#version 120\nuniform sampler2D image;void main(){gl_FragColor=texture2D(image,gl_TexCoord[0].xy);}");
         GL20.glUseProgram(this.compositeProgram);
         GL20.glUniform1i(GL20.glGetUniformLocation(this.compositeProgram, "image"), OpenGlHelper.defaultTexUnit - 33984);
      }
      float scale = Math.min(1.0F, Math.min(1280.0F / viewportWidth, 720.0F / viewportHeight));
      int w = Math.max(1, Math.round(viewportWidth * scale));
      int h = Math.max(1, Math.round(viewportHeight * scale));
      if (this.framebuffer != 0 && this.width == w && this.height == h) { return; }
      this.releaseBuffer();
      this.width = w;
      this.height = h;
      float blurScale = Math.min(1.0F, Math.min(640.0F / w, 360.0F / h));
      this.blurWidth = Math.max(1, Math.round(w * blurScale));
      this.blurHeight = Math.max(1, Math.round(h * blurScale));
      GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
      this.texture = GL11.glGenTextures();
      this.glowTexture = GL11.glGenTextures();
      this.blurTexture = GL11.glGenTextures();
      this.framebuffer = OpenGlHelper.glGenFramebuffers();
      this.glowFramebuffer = OpenGlHelper.glGenFramebuffers();
      this.blurFramebuffer = OpenGlHelper.glGenFramebuffers();
      this.attach(this.texture, this.framebuffer, w, h);
      this.attach(this.glowTexture, this.glowFramebuffer, w, h);
      this.attach(this.blurTexture, this.blurFramebuffer, this.blurWidth, this.blurHeight);
   }

   private void attach(int colorTexture, int colorFramebuffer, int w, int h) {
      GlStateManager.bindTexture(colorTexture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, colorFramebuffer);
      OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
      if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
         throw new IllegalStateException("Incomplete ESP framebuffer");
      }
   }

   private static int shader(int type, String source) {
      int shader = GL20.glCreateShader(type);
      try {
         GL20.glShaderSource(shader, source);
         GL20.glCompileShader(shader);
         if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("ESP shader compile failure");
         }
         return shader;
      } catch (RuntimeException | Error failure) {
         GL20.glDeleteShader(shader);
         throw failure;
      }
   }

   private static int program(String fragmentSource) {
      int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
      int fragment = 0;
      int program = 0;
      try {
         fragment = shader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
         program = GL20.glCreateProgram();
         GL20.glAttachShader(program, vertex);
         GL20.glAttachShader(program, fragment);
         GL20.glLinkProgram(program);
         if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("ESP shader link failure");
         }
         return program;
      } catch (RuntimeException | Error failure) {
         if (program != 0) { GL20.glDeleteProgram(program); }
         throw failure;
      } finally {
         GL20.glDeleteShader(vertex);
         if (fragment != 0) { GL20.glDeleteShader(fragment); }
      }
   }

   private void releaseBuffer() {
      if (this.framebuffer != 0) { OpenGlHelper.glDeleteFramebuffers(this.framebuffer); this.framebuffer = 0; }
      if (this.texture != 0) { GlStateManager.deleteTexture(this.texture); this.texture = 0; }
      if (this.glowFramebuffer != 0) { OpenGlHelper.glDeleteFramebuffers(this.glowFramebuffer); this.glowFramebuffer = 0; }
      if (this.glowTexture != 0) { GlStateManager.deleteTexture(this.glowTexture); this.glowTexture = 0; }
      if (this.blurFramebuffer != 0) { OpenGlHelper.glDeleteFramebuffers(this.blurFramebuffer); this.blurFramebuffer = 0; }
      if (this.blurTexture != 0) { GlStateManager.deleteTexture(this.blurTexture); this.blurTexture = 0; }
      this.width = this.height = this.blurWidth = this.blurHeight = 0;
   }

   private void releaseObjects() {
      this.releaseBuffer();
      if (this.blurProgram != 0) { GL20.glDeleteProgram(this.blurProgram); this.blurProgram = 0; }
      if (this.maskProgram != 0) { GL20.glDeleteProgram(this.maskProgram); this.maskProgram = 0; }
      if (this.outlineProgram != 0) { GL20.glDeleteProgram(this.outlineProgram); this.outlineProgram = 0; }
      if (this.compositeProgram != 0) { GL20.glDeleteProgram(this.compositeProgram); this.compositeProgram = 0; }
   }

   /** Lifecycle callers marshal this onto Minecraft's render/client thread. */
   public void close() {
      this.releaseObjects();
      this.failed = false;
      this.limitation = null;
   }

   public static final class Target {
      public final EntityPlayer player;
      public final ColorSetting.Value color;
      public Target(EntityPlayer player, ColorSetting.Value color) { this.player = player; this.color = color; }
   }

   /** Honest non-silhouette fallback: layered projected entity-bounds rectangles. */
   public static DrawPlan boxFallback(RenderAccess.Frame frame, List<Target> targets, float partial, float smoothness) {
      smoothness = Math.max(0.0F, Math.min(1.0F, smoothness));
      DrawPlan plan = new DrawPlan().canvas();
      for (Target target : targets) {
         EntityPlayer p = target.player;
         double x = lerp(p.prevPosX, p.posX, partial) - frame.camera[0];
         double y = lerp(p.prevPosY, p.posY, partial) - frame.camera[1];
         double z = lerp(p.prevPosZ, p.posZ, partial) - frame.camera[2];
         float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
         float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
         boolean valid = true;
         for (int i = 0; i < 8; i++) {
            RenderMath.Projection point = frame.project(new double[]{x + ((i & 1) == 0 ? -p.width / 2 : p.width / 2),
               y + ((i & 2) == 0 ? 0 : p.height), z + ((i & 4) == 0 ? -p.width / 2 : p.width / 2)});
            if (point == null) { valid = false; break; }
            left = Math.min(left, point.x); right = Math.max(right, point.x);
            top = Math.min(top, point.y); bottom = Math.max(bottom, point.y);
         }
         if (!valid || !RenderMath.visibleBounds(left, top, right, bottom, frame.width, frame.height)) { continue; }
         for (int layer = 5; layer >= 0; layer--) {
            float pad = layer * (0.5F + smoothness);
            float alpha = 0.14F * (float)Math.exp(-layer * layer / 16.0);
            plan.lineWidth(1.5F + smoothness).color(target.color, alpha)
               .roundedStroke(left - pad, top - pad, right - left + pad * 2, bottom - top + pad * 2, 3.0F + pad);
         }
      }
      return plan;
   }

   private static final class FramebufferBinding {
      private final boolean separate = GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_framebuffer_object;
      private final int draw = GL11.glGetInteger(36006);
      private final int read = this.separate ? GL11.glGetInteger(36010) : this.draw;
      void restore() {
         if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindFramebuffer(36009, this.draw); GL30.glBindFramebuffer(36008, this.read);
         } else if (this.separate) {
            ARBFramebufferObject.glBindFramebuffer(36009, this.draw); ARBFramebufferObject.glBindFramebuffer(36008, this.read);
         } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.draw);
         }
      }
   }
}
