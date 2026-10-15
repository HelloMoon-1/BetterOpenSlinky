package dev.slinky.client.hook;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

final class GlCacheResync {
   private static final ByteBuffer MASK = BufferUtils.createByteBuffer(16);
   private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(16);
   private static final int BLEND_DST_RGB = 32968;
   private static final int BLEND_SRC_RGB = 32969;
   private static final int BLEND_DST_ALPHA = 32970;
   private static final int BLEND_SRC_ALPHA = 32971;
   private static int spareTexture;

   private GlCacheResync() {
   }

   static void run() {
      flag(3042, GlStateManager::enableBlend, GlStateManager::disableBlend);
      flag(3553, GlStateManager::enableTexture2D, GlStateManager::disableTexture2D);
      flag(3008, GlStateManager::enableAlpha, GlStateManager::disableAlpha);
      flag(2929, GlStateManager::enableDepth, GlStateManager::disableDepth);
      flag(2884, GlStateManager::enableCull, GlStateManager::disableCull);
      flag(2896, GlStateManager::enableLighting, GlStateManager::disableLighting);
      int src = GL11.glGetInteger(32969);
      int dst = GL11.glGetInteger(32968);
      int srcAlpha = GL11.glGetInteger(32971);
      int dstAlpha = GL11.glGetInteger(32970);
      GlStateManager.tryBlendFuncSeparate(other(src), other(dst), other(srcAlpha), other(dstAlpha));
      GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
      ((Buffer)MASK).clear();
      GL11.glGetBoolean(3107, MASK);
      boolean r = MASK.get(0) != 0;
      boolean g = MASK.get(1) != 0;
      boolean b = MASK.get(2) != 0;
      boolean a = MASK.get(3) != 0;
      GlStateManager.colorMask(!r, !g, !b, !a);
      GlStateManager.colorMask(r, g, b, a);
      boolean depthMask = GL11.glGetBoolean(2930);
      GlStateManager.depthMask(!depthMask);
      GlStateManager.depthMask(depthMask);
      int shade = GL11.glGetInteger(2900);
      GlStateManager.shadeModel(shade == 7424 ? 7425 : 7424);
      GlStateManager.shadeModel(shade);
      ((Buffer)COLOR).clear();
      GL11.glGetFloat(2816, COLOR);
      GlStateManager.resetColor();
      GlStateManager.color(COLOR.get(0), COLOR.get(1), COLOR.get(2), COLOR.get(3));
      int bound = GL11.glGetInteger(32873);
      if (bound == 0 && spareTexture == 0) {
         spareTexture = GL11.glGenTextures();
      }

      GlStateManager.bindTexture(bound == 0 ? spareTexture : 0);
      GlStateManager.bindTexture(bound);
   }

   private static void flag(int capability, Runnable enable, Runnable disable) {
      if (GL11.glIsEnabled(capability)) {
         disable.run();
         enable.run();
      } else {
         enable.run();
         disable.run();
      }
   }

   private static int other(int factor) {
      return factor == 0 ? 1 : 0;
   }
}
