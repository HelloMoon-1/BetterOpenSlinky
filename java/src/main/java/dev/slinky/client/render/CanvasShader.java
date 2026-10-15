package dev.slinky.client.render;

import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

final class CanvasShader {
   private static final String VERTEX = "#version 110\nvarying vec2 position;\nvarying vec2 coverage;\nvoid main() {\n    position = gl_Vertex.xy;\n    coverage = gl_MultiTexCoord0.xy;\n    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}\n";
   private static final String FRAGMENT = "#version 110\nuniform vec4 inner;\nuniform vec4 outer;\nuniform vec3 paintX;\nuniform vec3 paintY;\nuniform vec2 extent;\nuniform float radius;\nuniform float feather;\nuniform float strokeMultiplier;\nuniform float fringe;\nvarying vec2 position;\nvarying vec2 coverage;\nvoid main() {\n    float mask = 1.0;\n    if (fringe > 0.5)\n        mask = clamp((1.0 - abs(coverage.x * 2.0 - 1.0)) * strokeMultiplier, 0.0, 1.0) * min(1.0, coverage.y);\n    float amount = 0.0;\n    if (feather != 0.0) {\n        vec2 p = vec2(dot(paintX, vec3(position, 1.0)), dot(paintY, vec3(position, 1.0)));\n        vec2 d = abs(p) - extent + vec2(radius);\n        float distance = min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - radius;\n        amount = clamp((distance + feather * 0.5) / feather, 0.0, 1.0);\n    }\n    vec4 color = mix(inner, outer, amount);\n    gl_FragColor = vec4(color.rgb, color.a * mask);\n}\n";
   private static boolean tried;
   private static int program;
   private static int innerLocation;
   private static int outerLocation;
   private static int paintXLocation;
   private static int paintYLocation;
   private static int extentLocation;
   private static int radiusLocation;
   private static int featherLocation;
   private static int multiplierLocation;
   private static int fringeLocation;

   private CanvasShader() {
   }

   static boolean available() {
      if (!tried) {
         tried = true;

         try {
            if (GLContext.getCapabilities().OpenGL20) {
               program = link();
            }
         } catch (LinkageError | RuntimeException var1) {
            program = 0;
         }
      }

      return program != 0;
   }

   static void reset() {
      tried = false;
      program = 0;
   }

   private static int compile(int type, String source) {
      int shader = GL20.glCreateShader(type);
      GL20.glShaderSource(shader, source);
      GL20.glCompileShader(shader);
      if (GL20.glGetShaderi(shader, 35713) == 0) {
         GL20.glDeleteShader(shader);
         throw new IllegalStateException("canvas shader did not compile");
      } else {
         return shader;
      }
   }

   private static int link() {
      int vertex = compile(
         35633,
         "#version 110\nvarying vec2 position;\nvarying vec2 coverage;\nvoid main() {\n    position = gl_Vertex.xy;\n    coverage = gl_MultiTexCoord0.xy;\n    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}\n"
      );
      int fragment = compile(
         35632,
         "#version 110\nuniform vec4 inner;\nuniform vec4 outer;\nuniform vec3 paintX;\nuniform vec3 paintY;\nuniform vec2 extent;\nuniform float radius;\nuniform float feather;\nuniform float strokeMultiplier;\nuniform float fringe;\nvarying vec2 position;\nvarying vec2 coverage;\nvoid main() {\n    float mask = 1.0;\n    if (fringe > 0.5)\n        mask = clamp((1.0 - abs(coverage.x * 2.0 - 1.0)) * strokeMultiplier, 0.0, 1.0) * min(1.0, coverage.y);\n    float amount = 0.0;\n    if (feather != 0.0) {\n        vec2 p = vec2(dot(paintX, vec3(position, 1.0)), dot(paintY, vec3(position, 1.0)));\n        vec2 d = abs(p) - extent + vec2(radius);\n        float distance = min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - radius;\n        amount = clamp((distance + feather * 0.5) / feather, 0.0, 1.0);\n    }\n    vec4 color = mix(inner, outer, amount);\n    gl_FragColor = vec4(color.rgb, color.a * mask);\n}\n"
      );
      int linked = GL20.glCreateProgram();
      GL20.glAttachShader(linked, vertex);
      GL20.glAttachShader(linked, fragment);
      GL20.glLinkProgram(linked);
      GL20.glDeleteShader(vertex);
      GL20.glDeleteShader(fragment);
      if (GL20.glGetProgrami(linked, 35714) == 0) {
         GL20.glDeleteProgram(linked);
         throw new IllegalStateException("canvas shader did not link");
      } else {
         innerLocation = GL20.glGetUniformLocation(linked, "inner");
         outerLocation = GL20.glGetUniformLocation(linked, "outer");
         paintXLocation = GL20.glGetUniformLocation(linked, "paintX");
         paintYLocation = GL20.glGetUniformLocation(linked, "paintY");
         extentLocation = GL20.glGetUniformLocation(linked, "extent");
         radiusLocation = GL20.glGetUniformLocation(linked, "radius");
         featherLocation = GL20.glGetUniformLocation(linked, "feather");
         multiplierLocation = GL20.glGetUniformLocation(linked, "strokeMultiplier");
         fringeLocation = GL20.glGetUniformLocation(linked, "fringe");
         return linked;
      }
   }

   static void draw(int mode, List<float[]> vertices, GuiCanvasState.Paint paint, float multiplier, boolean fringe) {
      int previous = GL11.glGetInteger(35725);
      GL20.glUseProgram(program);

      try {
         float[] m = paint.transform;
         float determinant = m[0] * m[3] - m[1] * m[2];
         float feather = paint.feather;
         if (determinant == 0.0F) {
            feather = 0.0F;
         } else {
            GL20.glUniform3f(paintXLocation, m[3] / determinant, -m[2] / determinant, (m[2] * m[5] - m[3] * m[4]) / determinant);
            GL20.glUniform3f(paintYLocation, -m[1] / determinant, m[0] / determinant, (m[1] * m[4] - m[0] * m[5]) / determinant);
         }

         GL20.glUniform4f(innerLocation, paint.inner[0], paint.inner[1], paint.inner[2], paint.inner[3]);
         GL20.glUniform4f(outerLocation, paint.outer[0], paint.outer[1], paint.outer[2], paint.outer[3]);
         GL20.glUniform2f(extentLocation, paint.extent[0], paint.extent[1]);
         GL20.glUniform1f(radiusLocation, paint.radius);
         GL20.glUniform1f(featherLocation, feather);
         GL20.glUniform1f(multiplierLocation, multiplier);
         GL20.glUniform1f(fringeLocation, fringe ? 1.0F : 0.0F);
         GL11.glBegin(mode);

         try {
            for (float[] vertex : vertices) {
               GL11.glTexCoord2f(vertex.length > 2 ? vertex[2] : 0.5F, vertex.length > 3 ? vertex[3] : 1.0F);
               GL11.glVertex2f(vertex[0], vertex[1]);
            }
         } finally {
            GL11.glEnd();
         }
      } finally {
         GL20.glUseProgram(previous);
      }
   }
}
