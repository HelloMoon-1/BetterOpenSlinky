package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * TargetHUD-only adaptation of OpenB4 GlowShader/Blur/EspShaders.
 * The donor kernels/mask are retained. Unlike the donor utilities, composition
 * returns to the caller's framebuffer (the runtime's UI FBO, not the world FBO).
 */
public final class RavenHudEffects implements AutoCloseable {
   private static final String VERTEX = "#version 120\nvoid main(){gl_TexCoord[0]=gl_MultiTexCoord0;gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}";
   private static final String ROUND = "#version 120\nuniform vec2 u_size;uniform float u_radius;uniform vec4 u_color;\n"
      + "void main(){gl_FragColor=vec4(u_color.rgb,u_color.a*smoothstep(1.0,0.0,length(max((abs(gl_TexCoord[0].st-0.5)+0.5)*u_size-u_size+u_radius,0.0))-u_radius+0.5));}";
   private static final String DOWN = "#version 120\nuniform sampler2D inTexture;uniform vec2 offset,halfpixel,iResolution;\n"
      + "void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv)*4.0;"
      + "sum+=texture2D(inTexture,uv-halfpixel.xy*offset);sum+=texture2D(inTexture,uv+halfpixel.xy*offset);"
      + "sum+=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset);sum+=texture2D(inTexture,uv-vec2(halfpixel.x,-halfpixel.y)*offset);"
      + "gl_FragColor=vec4(sum.rgb*0.125,1.0);}";
   private static final String UP = "#version 120\nuniform sampler2D inTexture,textureToCheck;uniform vec2 halfpixel,offset,iResolution;uniform int check;\n"
      + "void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv+vec2(-halfpixel.x*2.0,0.0)*offset);"
      + "sum+=texture2D(inTexture,uv+vec2(-halfpixel.x,halfpixel.y)*offset)*2.0;"
      + "sum+=texture2D(inTexture,uv+vec2(0.0,halfpixel.y*2.0)*offset);sum+=texture2D(inTexture,uv+vec2(halfpixel.x,halfpixel.y)*offset)*2.0;"
      + "sum+=texture2D(inTexture,uv+vec2(halfpixel.x*2.0,0.0)*offset);sum+=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset)*2.0;"
      + "sum+=texture2D(inTexture,uv+vec2(0.0,-halfpixel.y*2.0)*offset);sum+=texture2D(inTexture,uv+vec2(-halfpixel.x,-halfpixel.y)*offset)*2.0;"
      + "vec4 average=sum/12.0;gl_FragColor=vec4(average.rgb,mix(1.0,texture2D(textureToCheck,gl_TexCoord[0].st).a,float(check)));}";
   private static final String BLOOM_DOWN = "#version 120\nuniform sampler2D inTexture;uniform vec2 offset,halfpixel,iResolution;\n"
      + "void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv);sum.rgb*=sum.a;sum*=4.0;"
      + "vec4 smp1=texture2D(inTexture,uv-halfpixel.xy*offset);smp1.rgb*=smp1.a;sum+=smp1;"
      + "vec4 smp2=texture2D(inTexture,uv+halfpixel.xy*offset);smp2.rgb*=smp2.a;sum+=smp2;"
      + "vec4 smp3=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset);smp3.rgb*=smp3.a;sum+=smp3;"
      + "vec4 smp4=texture2D(inTexture,uv-vec2(halfpixel.x,-halfpixel.y)*offset);smp4.rgb*=smp4.a;sum+=smp4;"
      + "vec4 result=sum/8.0;gl_FragColor=vec4(result.a>0.0?result.rgb/result.a:vec3(0.0),result.a);}";
   private static final String BLOOM_UP = "#version 120\nuniform sampler2D inTexture,textureToCheck;uniform vec2 halfpixel,offset,iResolution;uniform int check;\n"
      + "void main(){vec2 uv=gl_FragCoord.xy/iResolution;vec4 sum=texture2D(inTexture,uv+vec2(-halfpixel.x,0.0)*offset);sum.rgb*=sum.a;"
      + "vec4 smp1=texture2D(inTexture,uv+vec2(-halfpixel.x,halfpixel.y)*offset);smp1.rgb*=smp1.a;sum+=smp1*2.0;"
      + "vec4 smp2=texture2D(inTexture,uv+vec2(0.0,halfpixel.y*2.0)*offset);smp2.rgb*=smp2.a;sum+=smp2;"
      + "vec4 smp3=texture2D(inTexture,uv+vec2(halfpixel.x,halfpixel.y)*offset);smp3.rgb*=smp3.a;sum+=smp3*2.0;"
      + "vec4 smp4=texture2D(inTexture,uv+vec2(halfpixel.x*2.0,0.0)*offset);smp4.rgb*=smp4.a;sum+=smp4;"
      + "vec4 smp5=texture2D(inTexture,uv+vec2(halfpixel.x,-halfpixel.y)*offset);smp5.rgb*=smp5.a;sum+=smp5*2.0;"
      + "vec4 smp6=texture2D(inTexture,uv+vec2(0.0,-halfpixel.y*2.0)*offset);smp6.rgb*=smp6.a;sum+=smp6;"
      + "vec4 smp7=texture2D(inTexture,uv+vec2(-halfpixel.x,-halfpixel.y)*offset);smp7.rgb*=smp7.a;sum+=smp7*2.0;"
      + "vec4 result=sum/12.0;float checkAlpha=texture2D(textureToCheck,gl_TexCoord[0].st).a;"
      + "gl_FragColor=vec4(result.a>0.0?result.rgb/result.a:vec3(0.0),mix(result.a,result.a*(1.0-checkAlpha),float(check)));}";

   private Program round;
   private Program down;
   private Program up;
   private Program bloomDown;
   private Program bloomUp;
   private Framebuffer mask;
   private final Framebuffer[] buffers = new Framebuffer[3];
   private int width;
   private int height;
   private boolean failed;

   public void render(Minecraft game, float left, float top, float right, float bottom, int alpha) {
      if (alpha <= 0) {
         return;
      }
      if (this.failed || !OpenGlHelper.framebufferSupported || !GLContext.getCapabilities().OpenGL20) {
         this.fallback(left, top, right, bottom, alpha);
         return;
      }
      int framebuffer = GL11.glGetInteger(36006);
      int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      boolean rendered = false;
      try (GlStateScope ignored = GlStateScope.open()) {
         try {
            this.ensure(game.displayWidth, game.displayHeight);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GlStateManager.disableDepth();
            GlStateManager.disableCull();
            GlStateManager.disableAlpha();
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            float fade = alpha / 210.0F;
            this.capture(left, top, right, bottom, RavenHudDrawing.alpha(0, alpha));
            this.process(this.mask.framebufferTexture, this.bloomDown, this.bloomUp, 1.3F * fade, true);
            this.composite(framebuffer);
            this.capture(left, top, right, bottom, -1);
            // Never sample the framebuffer currently being written: blur the world texture.
            Framebuffer world = game.getFramebuffer();
            if (world != null && world.framebufferTexture > 0) {
               this.process(world.framebufferTexture, this.down, this.up, 2.0F * fade, false);
               this.composite(framebuffer);
            }
            rendered = true;
         } catch (RuntimeException error) {
            this.close();
            this.failed = true;
            java.util.logging.Logger.getLogger("Slinky").warning("TargetHUD shader effects unavailable: " + error.getMessage());
         } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL20.glUseProgram(program);
            GL11.glMatrixMode(matrixMode);
         }
      }
      if (!rendered) {
         this.fallback(left, top, right, bottom, alpha);
      }
   }

   private void fallback(float left, float top, float right, float bottom, int alpha) {
      // Only an unsupported/failed shader fallback, not a substitute normal Modern layout.
      RavenHudDrawing.rounded(left, top, right, bottom, 10.0F, RavenHudDrawing.alpha(0, Math.min(alpha, 100)));
   }

   private void ensure(int width, int height) {
      if (this.round == null) {
         this.round = new Program(ROUND);
         this.down = new Program(DOWN);
         this.up = new Program(UP);
         this.bloomDown = new Program(BLOOM_DOWN);
         this.bloomUp = new Program(BLOOM_UP);
      }
      if (this.mask != null && this.width == width && this.height == height) {
         return;
      }
      this.deleteBuffers();
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.mask = this.buffer(this.width, this.height);
      for (int i = 0; i < this.buffers.length; i++) {
         this.buffers[i] = this.buffer(Math.max(1, this.width >> i), Math.max(1, this.height >> i));
      }
   }

   private Framebuffer buffer(int width, int height) {
      Framebuffer result = new Framebuffer(width, height, false);
      if (!OpenGlHelper.framebufferSupported || result.framebufferObject <= 0 || result.framebufferTexture <= 0) {
         result.deleteFramebuffer();
         throw new IllegalStateException("Framebuffer allocation failed");
      }
      result.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
      result.setFramebufferFilter(GL11.GL_LINEAR);
      GlStateManager.bindTexture(result.framebufferTexture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33648);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33648);
      return result;
   }

   private void bindAndClear(Framebuffer buffer) {
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, buffer.framebufferObject);
      GL11.glViewport(0, 0, buffer.framebufferWidth, buffer.framebufferHeight);
      GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
      GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
   }

   private void capture(float left, float top, float right, float bottom, int color) {
      this.bindAndClear(this.mask);
      this.round.use();
      this.round.uniform("u_size", right - left, bottom - top);
      this.round.uniform("u_radius", 5.0F);
      this.round.uniform("u_color", (color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
         (color & 255) / 255.0F, (color >>> 24) / 255.0F);
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      // Preserve the donor mask's source-alpha capture (including its alpha-squared edge).
      GlStateManager.blendFunc(770, 771);
      this.quad(left, top, right - left, bottom - top);
      GL20.glUseProgram(0);
   }

   private void process(int texture, Program down, Program up, float offset, boolean bloom) {
      int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      GL11.glOrtho(0.0, this.width, this.height, 0.0, -1.0, 1.0);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      try {
         this.pass(this.buffers[1], texture, down, offset, false, bloom);
         this.pass(this.buffers[2], this.buffers[1].framebufferTexture, down, offset, false, bloom);
         this.pass(this.buffers[1], this.buffers[2].framebufferTexture, up, offset, false, bloom);
         this.pass(this.buffers[0], this.buffers[1].framebufferTexture, up, offset, true, bloom);
      } finally {
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPopMatrix();
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPopMatrix();
         GL11.glMatrixMode(mode);
      }
   }

   private void pass(Framebuffer destination, int texture, Program shader, float offset, boolean check, boolean bloom) {
      this.bindAndClear(destination);
      // The donor uses additive bloom passes into a newly cleared buffer.
      if (bloom) {
         GlStateManager.enableBlend();
         GlStateManager.blendFunc(1, 1);
      } else {
         GlStateManager.disableBlend();
      }
      shader.use();
      shader.integer("inTexture", 0);
      shader.integer("textureToCheck", 1);
      shader.integer("check", check ? 1 : 0);
      shader.uniform("offset", offset, offset);
      shader.uniform("halfpixel", 1.0F / destination.framebufferWidth, 1.0F / destination.framebufferHeight);
      shader.uniform("iResolution", destination.framebufferWidth, destination.framebufferHeight);
      // Two texture units suffice; the donor's hard-coded unit 16 is unnecessary.
      GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
      GlStateManager.enableTexture2D();
      GlStateManager.bindTexture(this.mask.framebufferTexture);
      GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
      GlStateManager.enableTexture2D();
      GlStateManager.bindTexture(texture);
      this.quad(0, 0, this.width, this.height);
      GL20.glUseProgram(0);
   }

   private void composite(int destination) {
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination);
      GL11.glViewport(0, 0, this.width, this.height);
      int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      GL11.glOrtho(0, this.width, this.height, 0, -1, 1);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      try {
         GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
         GlStateManager.disableTexture2D();
         GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
         GlStateManager.enableTexture2D();
         GlStateManager.bindTexture(this.buffers[0].framebufferTexture);
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
         GlStateManager.color(1, 1, 1, 1);
         this.quad(0, 0, this.width, this.height);
      } finally {
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPopMatrix();
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPopMatrix();
         GL11.glMatrixMode(mode);
      }
   }

   private void quad(float x, float y, float width, float height) {
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y + height);
      GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + width, y + height);
      GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + width, y);
      GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y);
      GL11.glEnd();
   }

   private void deleteBuffers() {
      if (this.mask != null) {
         this.mask.deleteFramebuffer();
         this.mask = null;
      }
      for (int i = 0; i < this.buffers.length; i++) {
         if (this.buffers[i] != null) {
            this.buffers[i].deleteFramebuffer();
            this.buffers[i] = null;
         }
      }
   }

   @Override
   public void close() {
      this.failed = false;
      if (this.mask == null && this.round == null && this.down == null && this.up == null
         && this.bloomDown == null && this.bloomUp == null && this.buffers[0] == null
         && this.buffers[1] == null && this.buffers[2] == null) {
         return;
      }
      int framebuffer = GL11.glGetInteger(36006);
      if (this.mask != null && this.mask.framebufferObject == framebuffer) {
         framebuffer = 0;
      }
      for (Framebuffer buffer : this.buffers) {
         if (buffer != null && buffer.framebufferObject == framebuffer) {
            framebuffer = 0;
         }
      }
      int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      Program[] programs = {this.round, this.down, this.up, this.bloomDown, this.bloomUp};
      try (GlStateScope ignored = GlStateScope.open()) {
         try {
            this.deleteBuffers();
            this.round = this.down = this.up = this.bloomDown = this.bloomUp = null;
            for (Program current : programs) {
               if (current != null) {
                  if (current.id == program) {
                     program = 0;
                  }
                  current.close();
               }
            }
         } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL20.glUseProgram(program);
         }
      }
   }

   private static final class Program implements AutoCloseable {
      private int id;

      private Program(String fragment) {
         int vertex = 0, pixel = 0;
         try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            this.id = GL20.glCreateProgram();
            GL20.glAttachShader(this.id, vertex);
            GL20.glAttachShader(this.id, pixel);
            GL20.glLinkProgram(this.id);
            if (GL20.glGetProgrami(this.id, GL20.GL_LINK_STATUS) == 0) {
               throw new IllegalStateException(GL20.glGetProgramInfoLog(this.id, 2048));
            }
         } catch (RuntimeException error) {
            this.close();
            throw error;
         } finally {
            if (vertex != 0) { GL20.glDeleteShader(vertex); }
            if (pixel != 0) { GL20.glDeleteShader(pixel); }
         }
      }

      private static int compile(int type, String source) {
         int shader = GL20.glCreateShader(type);
         GL20.glShaderSource(shader, source);
         GL20.glCompileShader(shader);
         if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String reason = GL20.glGetShaderInfoLog(shader, 2048);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(reason);
         }
         return shader;
      }

      private void use() { GL20.glUseProgram(this.id); }
      private void integer(String name, int value) { GL20.glUniform1i(GL20.glGetUniformLocation(this.id, name), value); }
      private void uniform(String name, float... values) {
         int location = GL20.glGetUniformLocation(this.id, name);
         switch (values.length) {
            case 1: GL20.glUniform1f(location, values[0]); break;
            case 2: GL20.glUniform2f(location, values[0], values[1]); break;
            case 4: GL20.glUniform4f(location, values[0], values[1], values[2], values[3]); break;
            default: throw new IllegalArgumentException("Unsupported uniform arity");
         }
      }
      @Override public void close() {
         if (this.id > 0) {
            GL20.glDeleteProgram(this.id);
            this.id = 0;
         }
      }
   }
}
