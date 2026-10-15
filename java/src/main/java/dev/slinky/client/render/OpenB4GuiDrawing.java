package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** GUI-only port of donor RenderUtils geometry and Animation, not Canvas approximations. */
final class OpenB4GuiDrawing {
   private static final double[] SIN_0_90 = new double[31], COS_0_90 = new double[31];
   private static final double[] SIN_90_180 = new double[31], COS_90_180 = new double[31];

   static {
      for (int n = 0; n <= 90; n += 3) {
         double angle = (float)n * ((float)Math.PI / 180);
         SIN_0_90[n / 3] = Math.sin(angle);
         COS_0_90[n / 3] = Math.cos(angle);
      }
      for (int n = 90; n <= 180; n += 3) {
         double angle = (float)n * ((float)Math.PI / 180);
         SIN_90_180[(n - 90) / 3] = Math.sin(angle);
         COS_90_180[(n - 90) / 3] = Math.cos(angle);
      }
   }

   private OpenB4GuiDrawing() {
   }

   static void color(int argb) {
      GlStateManager.color((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F,
         (argb & 255) / 255.0F, (argb >>> 24) / 255.0F);
   }

   static void resetColor() {
      GlStateManager.resetColor();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   static int withAlpha(int rgb, int alpha) {
      return rgb & 0xFFFFFF | alpha << 24;
   }

   static int clampAlpha(int alpha) {
      return Math.max(4, Math.min(255, alpha));
   }

   static int blend(int first, int second, double fraction) {
      int n = (int)(fraction * 255.0), inverse = 255 - n;
      int r = ((first >> 16 & 255) * n + (second >> 16 & 255) * inverse) >> 8;
      int g = ((first >> 8 & 255) * n + (second >> 8 & 255) * inverse) >> 8;
      int b = ((first & 255) * n + (second & 255) * inverse) >> 8;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   static void rect(float x, float y, float right, float bottom, int argb) {
      GlStateManager.enableBlend();
      GlStateManager.disableTexture2D();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
      color(argb);
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glVertex2f(x, bottom);
      GL11.glVertex2f(right, bottom);
      GL11.glVertex2f(right, y);
      GL11.glVertex2f(x, y);
      GL11.glEnd();
      resetColor();
      GlStateManager.enableTexture2D();
      GlStateManager.disableBlend();
   }

   static void roundedRect(float x, float y, float right, float bottom, float radius, int fill) {
      rounded(x, y, right, bottom, radius, fill, null, null);
   }

   static void roundedRectOutlined(float x, float y, float right, float bottom, float radius, int fill, int left, int rightColor) {
      rounded(x, y, right, bottom, radius, fill, left, rightColor);
   }

   private static void rounded(float x, float y, float right, float bottom, float radius, int fill, Integer left, Integer rightColor) {
      x *= 2.0F;
      y *= 2.0F;
      right *= 2.0F;
      bottom *= 2.0F;
      if (left == null) {
         radius = Math.min(radius, Math.min((right - x) / 2.0F, (bottom - y) / 2.0F));
      }
      // Donor uses doubled coordinates and 3-degree samples, radius 10 -> 5 GUI px.
      GL11.glPushMatrix();
      GL11.glScaled(0.5, 0.5, 0.5);
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
      GlStateManager.disableTexture2D();
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      float x1 = x + radius, y1 = y + radius, x2 = right - radius, y2 = bottom - radius;
      GL11.glBegin(GL11.GL_POLYGON);
      color(fill);
      vertices(x1, y1, x2, y2, radius, null, null);
      GL11.glEnd();
      if (left != null) {
         GL11.glShadeModel(GL11.GL_SMOOTH);
         GL11.glLineWidth(2.0F);
         GL11.glBegin(GL11.GL_LINE_LOOP);
         vertices(x1, y1, x2, y2, radius, left, rightColor);
         GL11.glEnd();
      }
      GlStateManager.enableTexture2D();
      GlStateManager.disableBlend();
      GL11.glDisable(GL11.GL_LINE_SMOOTH);
      GL11.glLineWidth(1.0F);
      GL11.glShadeModel(GL11.GL_FLAT);
      GL11.glPopMatrix();
      resetColor();
   }

   private static void vertices(float x1, float y1, float x2, float y2, float r, Integer left, Integer right) {
      if (left != null) color(left);
      for (int n = 0; n <= 30; n++) GL11.glVertex2d(x1 - SIN_0_90[n] * r, y1 - COS_0_90[n] * r);
      for (int n = 0; n <= 30; n++) GL11.glVertex2d(x1 - SIN_90_180[n] * r, y2 - COS_90_180[n] * r);
      if (right != null) color(right);
      for (int n = 0; n <= 30; n++) GL11.glVertex2d(x2 + SIN_0_90[n] * r, y2 + COS_0_90[n] * r);
      for (int n = 0; n <= 30; n++) GL11.glVertex2d(x2 + SIN_90_180[n] * r, y1 + COS_90_180[n] * r);
   }

   static void drawItem(Minecraft mc, ItemStack stack, float x, float y) {
      if (stack == null || stack.getItem() == null) return;
      RenderItem renderer = mc.getRenderItem();
      float oldZ = renderer.zLevel;
      try (GlStateScope state = GlStateScope.open()) {
         RenderHelper.enableGUIStandardItemLighting();
         renderer.zLevel = -100.0F;
         IBakedModel model = renderer.getItemModelMesher().getItemModel(stack);
         GlStateManager.pushMatrix();
         try {
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
            try {
               GlStateManager.enableRescaleNormal();
               GlStateManager.enableBlend();
               GlStateManager.blendFunc(770, 771);
               resetColor();
               GlStateManager.translate(x, y, 100.0F + renderer.zLevel);
               GlStateManager.translate(8.0F, 8.0F, 0.0F);
               GlStateManager.scale(1.0F, 1.0F, -1.0F);
               GlStateManager.scale(0.5F, 0.5F, 0.5F);
               if (model.isGui3d()) {
                  GlStateManager.scale(40.0F, 40.0F, 40.0F);
                  GlStateManager.rotate(210.0F, 1.0F, 0.0F, 0.0F);
                  GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
                  GlStateManager.enableLighting();
               } else {
                  GlStateManager.scale(64.0F, 64.0F, 64.0F);
                  GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
                  GlStateManager.disableLighting();
               }
               try {
                  Class<?> forge = Class.forName("net.minecraftforge.client.ForgeHooksClient");
                  model = (IBakedModel)forge.getMethod("handleCameraTransforms", IBakedModel.class, ItemCameraTransforms.TransformType.class)
                     .invoke(null, model, ItemCameraTransforms.TransformType.GUI);
               } catch (ClassNotFoundException absent) {
                  model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.GUI);
               } catch (ReflectiveOperationException failure) {
                  throw new IllegalStateException("Cannot apply Forge GUI item transform", failure);
               }
               renderer.renderItem(stack, model);
            } finally {
               mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
               mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();
            }
         } finally {
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
         }
      } finally {
         renderer.zLevel = oldZ;
      }
   }

   static String number(double value) {
      value = Math.round(value * 1000.0) / 1000.0;
      return value == Math.floor(value) ? Long.toString((long)value) : Double.toString(value);
   }

   static final class Animation {
      long start;
      float last;
      final float duration;

      Animation(float duration) {
         this.duration = duration;
      }

      void start() {
         this.last = 0.0F;
         this.start = System.currentTimeMillis();
      }

      float getValue(float from, float to, int mode) {
         if (this.last == to) return this.last;
         float t = (float)(System.currentTimeMillis() - this.start) / this.duration;
         if (mode == 1) {
            t = t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
         } else if (mode == 2) {
            t = (float)(1.0 - Math.pow(1.0F - t, 5.0));
         }
         float value = from + t * (to - from);
         if (to < value) value = to;
         if (value == to) this.last = value;
         return value;
      }

      int getValueInt(int from, int to, int mode) {
         return Math.round(this.getValue(from, to, mode));
      }
   }
}
