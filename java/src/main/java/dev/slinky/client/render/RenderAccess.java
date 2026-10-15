package dev.slinky.client.render;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hook.GlStateScope;
import dev.slinky.inject.ReflectionHelper;
import dev.slinky.setting.ColorSetting;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class RenderAccess {
   private static float[] model;
   private static float[] projection;
   private static Consumer<DrawPlan> sink = RenderAccess::execute;
   private static DoubleSupplier clock = () -> (double)System.nanoTime() * 1.0E-9;
   private static Method fovModifier;
   private static final ThreadLocal<Deque<FloatBuffer>> MODEL_BUFFERS = ThreadLocal.withInitial(ArrayDeque::new);
   private static boolean itemTransformResolved;
   private static Method forgeItemTransform;

   private RenderAccess() {
   }

   public static double now() {
      return clock.getAsDouble();
   }

   public static DoubleSupplier bindClock(DoubleSupplier next) {
      DoubleSupplier previous = clock;
      clock = Objects.requireNonNull(next);
      return previous;
   }

   public static Consumer<DrawPlan> bindSink(Consumer<DrawPlan> next) {
      Consumer<DrawPlan> previous = sink;
      sink = Objects.requireNonNull(next);
      return previous;
   }

   public static void submit(SemanticEvent event, DrawPlan plan) {
      if (!event.cancelled) {
         sink.accept(plan);
      }
   }

   public static void captureWorldProjection() {
      FloatBuffer m = BufferUtils.createFloatBuffer(16);
      FloatBuffer p = BufferUtils.createFloatBuffer(16);
      GL11.glGetFloat(2982, m);
      GL11.glGetFloat(2983, p);
      float[] ma = new float[16];
      float[] pa = new float[16];
      m.get(ma);
      p.get(pa);
      model = ma;
      projection = pa;
   }

   public static void setProjection(float[] m, float[] p) {
      model = (float[])m.clone();
      projection = (float[])p.clone();
   }

   public static void clearProjection() {
      projection = null;
      model = null;
   }

   public static void releaseCanvasResources() {
      CanvasFonts.release();
   }

   public static RenderAccess.Frame frame(ClientContext c, SemanticEvent e) {
      return model != null && projection != null && e.renderWidth > 0.0F && e.renderHeight > 0.0F
         ? new RenderAccess.Frame(e.renderWidth, e.renderHeight, model, projection, new double[]{e.cameraX, e.cameraY, e.cameraZ})
         : null;
   }

   public static RenderAccess.Frame fixtureFrame(float width, float height, float[] m, float[] p, double[] camera) {
      return new RenderAccess.Frame(width, height, m, p, camera);
   }

   public static double[] position(Entity e) {
      return new double[]{e.posX, e.posY, e.posZ};
   }

   public static double[] previous(Entity e) {
      return new double[]{e.prevPosX, e.prevPosY, e.prevPosZ};
   }

   public static boolean arrowInGround(EntityArrow arrow) {
      try {
         return ReflectionHelper.findField(EntityArrow.class, "inGround", "field_70254_i").getBoolean(arrow);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static float width(ClientContext c, String text) {
      return (float)c.minecraft().fontRendererObj.getStringWidth(text);
   }

   public static float textHeight(ClientContext c) {
      return (float)c.minecraft().fontRendererObj.FONT_HEIGHT;
   }

   public static float fov(ClientContext c, float partial) {
      if (fovModifier == null) {
         fovModifier = ReflectionHelper.findMethod(EntityRenderer.class, null, new String[]{"getFOVModifier", "func_78481_a"}, float.class, boolean.class);
      }

      try {
         return (Float)fovModifier.invoke(c.minecraft().entityRenderer, partial, true);
      } catch (ReflectiveOperationException var3) {
         throw new IllegalStateException(var3);
      }
   }

   public static ColorSetting.Value nameColor(EntityPlayer player) {
      String prefix = player.getTeam() instanceof ScorePlayerTeam ? ((ScorePlayerTeam)player.getTeam()).getColorPrefix() : "";
      int index = -1;

      for (int i = 0; i + 1 < prefix.length(); i++) {
         if (prefix.charAt(i) == 167) {
            int code = "0123456789abcdef".indexOf(Character.toLowerCase(prefix.charAt(i + 1)));
            if (code >= 0) {
               index = code;
            }
         }
      }

      int rgb = index < 0 ? 16777215 : Minecraft.getMinecraft().fontRendererObj.getColorCode("0123456789abcdef".charAt(index));
      return new ColorSetting.Value((float)(rgb >>> 16 & 0xFF) / 255.0F, (float)(rgb >>> 8 & 0xFF) / 255.0F, (float)(rgb & 0xFF) / 255.0F);
   }

   private static void execute(DrawPlan plan) {
      try (GlStateScope attributes = GlStateScope.open()) {
         Deque<FloatBuffer> buffers = MODEL_BUFFERS.get();
         FloatBuffer baseModel = buffers.isEmpty() ? BufferUtils.createFloatBuffer(16) : buffers.pop();
         GuiCanvasState canvas = canvas();
         GuiCanvasState.Stats stats = new GuiCanvasState.Stats();
         GlCanvasBackend backend = new GlCanvasBackend();
         int pushes = 0;
         float alpha = 1.0F;
         float savedItemDepth = Minecraft.getMinecraft().getRenderItem().zLevel;
         Deque<Float> alphas = new ArrayDeque<>();
         GL11.glPushMatrix();

         try {
            baseModel.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, baseModel);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.disableTexture2D();

            for (DrawPlan.Op op : plan.operations()) {
               float[] v = op.values;
               if (op.kind.equals("push")) {
                  GL11.glPushMatrix();
                  canvas.save();
                  pushes++;
                  alphas.push(alpha);
               } else if (op.kind.equals("pop")) {
                  if (pushes <= 0) {
                     throw new IllegalStateException("Unbalanced drawing matrix");
                  }

                  GL11.glPopMatrix();
                  canvas.restore();
                  pushes--;
                  alpha = alphas.pop();
               } else if (op.kind.equals("translate")) {
                  GL11.glTranslatef(v[0], v[1], v[2]);
                  canvas.translate(v[0], v[1]);
               } else if (op.kind.equals("scale")) {
                  GL11.glScalef(v[0], v[1], v[2]);
                  canvas.scale(v[0], v[1]);
               } else if (op.kind.equals("rotate")) {
                  GL11.glRotatef(v[0], v[1], v[2], v[3]);
               } else if (op.kind.equals("color")) {
                  GlStateManager.color(v[0], v[1], v[2], v[3] * alpha);
                  canvas.fillColor(v);
                  canvas.strokeColor(v);
               } else if (op.kind.equals("opacity")) {
                  alpha = v[0];
                  canvas.selected().alpha = alpha;
               } else if (op.kind.equals("screenClip")) {
                  GL11.glEnable(3089);
                  GL11.glScissor((int)v[0], Minecraft.getMinecraft().displayHeight - (int)(v[1] + v[3]), Math.max(0, (int)v[2]), Math.max(0, (int)v[3]));
               } else if (op.kind.equals("unclip")) {
                  GL11.glDisable(3089);
               } else if (op.kind.equals("gradient")) {
                  GuiCanvasState.Paint paint = GuiCanvasState.linearGradient(
                     v[0], v[1], v[0] + v[2], v[1], new float[]{v[4], v[5], v[6], v[7]}, new float[]{v[8], v[9], v[10], v[11]}
                  );
                  canvas.fillPaint(paint);
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).rectangle(v[0], v[1], v[2], v[3]), false, backend, stats, baseModel);
               } else if (op.kind.equals("lineWidth")) {
                  GL11.glLineWidth(v[0]);
                  canvas.selected().lineWidth = v[0];
               } else if (op.kind.equals("enable")) {
                  capability((int)v[0], true);
               } else if (op.kind.equals("disable")) {
                  capability((int)v[0], false);
               } else if (op.kind.equals("vertices2") && op.value == Boolean.TRUE) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).command(0.0F, v[1], v[2], 1.0F, v[3], v[4]), true, backend, stats, baseModel);
               } else if (op.kind.startsWith("vertices")) {
                  int dimensions = op.kind.equals("vertices2") ? 2 : 3;
                  GL11.glBegin((int)v[0]);

                  try {
                     for (int i = 1; i < v.length; i += dimensions) {
                        GL11.glVertex3f(v[i], v[i + 1], dimensions == 3 ? v[i + 2] : 0.0F);
                     }
                  } finally {
                     GL11.glEnd();
                  }
               } else if (op.kind.equals("roundedRect") || op.kind.equals("roundedStroke")) {
                  shape(
                     canvas,
                     new CanvasPath(0.25F, 0.01F, 1.0F).rounded(v[0], v[1], v[2], v[3], v[4], v[4], v[4], v[4]),
                     op.kind.equals("roundedStroke"),
                     backend,
                     stats,
                     baseModel
                  );
               } else if (op.kind.equals("roundedRectVarying")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).rounded(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]), false, backend, stats, baseModel);
               } else if (op.kind.equals("circle")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).ellipse(v[0], v[1], v[2], v[2]), false, backend, stats, baseModel);
               } else if (op.kind.equals("circleStroke")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).ellipse(v[0], v[1], v[2], v[2]), true, backend, stats, baseModel);
               } else if (op.kind.equals("pillGradient")) {
                  pillGradient(canvas.selected().transform, v, alpha, baseModel);
               } else if (op.kind.equals("itemDepth")) {
                  Minecraft.getMinecraft().getRenderItem().zLevel = v[0];
               } else if (op.kind.equals("depthFunc")) {
                  GlStateManager.depthFunc((int)v[0]);
               } else if (op.kind.equals("donorItem")) {
                  donorItem((ItemStack)op.value, v[0], v[1]);
               } else if (op.kind.equals("dripText")) {
                  CanvasFonts.render((CanvasFonts.Text)op.value, DripLiteStyle.accent((int)v[0]), alpha);
               } else if (op.kind.equals("text") && op.value instanceof CanvasFonts.Text) {
                  CanvasFonts.render((CanvasFonts.Text)op.value, Float.floatToRawIntBits(v[2]), alpha);
               } else if (op.kind.equals("text")) {
                  GlStateManager.enableTexture2D();
                  GL11.glPushMatrix();

                  try {
                     float factor = v[4] / (float)Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
                     GL11.glTranslatef(v[0], v[1], 0.0F);
                     GL11.glScalef(factor, factor, 1.0F);
                     int color = Float.floatToRawIntBits(v[2]);
                     int opacity = (int)((float)(color >>> 24) * alpha);
                     Minecraft.getMinecraft().fontRendererObj.drawString((String)op.value, 0.0F, 0.0F, opacity << 24 | color & 16777215, v[3] != 0.0F);
                  } finally {
                     GL11.glPopMatrix();
                     GlStateManager.disableTexture2D();
                  }
               } else if (op.kind.equals("item")) {
                  GlStateManager.enableTexture2D();
                  RenderHelper.enableGUIStandardItemLighting();

                  try {
                     Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI((ItemStack)op.value, (int)v[0], (int)v[1]);
                     Minecraft.getMinecraft().getRenderItem().renderItemOverlays(Minecraft.getMinecraft().fontRendererObj, (ItemStack)op.value, (int)v[0], (int)v[1]);
                  } finally {
                     RenderHelper.disableStandardItemLighting();
                     GlStateManager.disableTexture2D();
                  }
               }
            }
         } finally {
            Minecraft.getMinecraft().getRenderItem().zLevel = savedItemDepth;

            while (pushes-- > 0) {
               GL11.glPopMatrix();
            }

            GL11.glPopMatrix();
            buffers.push(baseModel);
         }
      }
   }

   private static void donorItem(ItemStack stack, float x, float y) {
      if (stack == null || stack.getItem() == null) { return; }
      Minecraft game = Minecraft.getMinecraft();
      RenderItem renderer = game.getRenderItem();
      float oldDepth = renderer.zLevel;
      RenderHelper.enableGUIStandardItemLighting();
      renderer.zLevel = -100.0F;
      IBakedModel model = renderer.getItemModelMesher().getItemModel(stack);
      GL11.glPushMatrix();
      game.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
      game.getTextureManager().getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
      try {
         GlStateManager.enableTexture2D();
         GlStateManager.enableRescaleNormal();
         GlStateManager.enableBlend();
         GlStateManager.blendFunc(770, 771);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         GlStateManager.translate(x + 8.0F, y + 8.0F, 100.0F + renderer.zLevel);
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
            if (!itemTransformResolved) {
               try {
                  Class<?> forge = Class.forName("net.minecraftforge.client.ForgeHooksClient");
                  forgeItemTransform = forge.getMethod("handleCameraTransforms", IBakedModel.class, ItemCameraTransforms.TransformType.class);
               } catch (ClassNotFoundException absent) {
                  forgeItemTransform = null;
               }
               itemTransformResolved = true;
            }
            if (forgeItemTransform != null) {
               model = (IBakedModel)forgeItemTransform.invoke(null, model, ItemCameraTransforms.TransformType.GUI);
            } else {
               model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.GUI);
            }
         } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot apply Forge GUI item transform", failure);
         }
         renderer.renderItem(stack, model);
      } finally {
         GlStateManager.disableRescaleNormal();
         GlStateManager.disableLighting();
         GL11.glPopMatrix();
         game.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
         game.getTextureManager().getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();
         renderer.zLevel = oldDepth;
         RenderHelper.disableStandardItemLighting();
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
         GlStateManager.disableTexture2D();
      }
   }

   private static void pillGradient(float[] transform, float[] v, float alpha, FloatBuffer baseModel) {
      float x = v[0];
      float y = v[1];
      float width = v[2];
      float height = v[3];
      float radius = Math.max(0.0F, Math.min(v[4], Math.min(height, width) * 0.5F));
      int stops = (v.length - 5) / 4;
      if (stops >= 1 && !(width <= 0.0F) && !(height <= 0.0F)) {
         TreeSet<Float> columns = new TreeSet<>();
         int capSteps = Math.max(4, (int)Math.ceil((double)(radius * 2.0F)));

         for (int i = 0; i <= capSteps; i++) {
            float inset = radius - radius * (float)Math.cos((Math.PI / 2) * (double)i / (double)capSteps);
            columns.add(x + inset);
            columns.add(x + width - inset);
         }

         for (int i = 0; i < stops; i++) {
            columns.add(x + (stops == 1 ? 0.0F : width * (float)i / (float)(stops - 1)));
         }

         float middle = y + height * 0.5F;
         float straight = height * 0.5F - radius;
         float[] color = new float[4];
         GL11.glShadeModel(7425);
         GL11.glPushMatrix();
         GL11.glLoadMatrix(baseModel);
         GL11.glBegin(5);

         try {
            for (float px : columns) {
               float dx = px < x + radius ? x + radius - px : (px > x + width - radius ? px - (x + width - radius) : 0.0F);
               float half = straight + (float)Math.sqrt((double)Math.max(0.0F, radius * radius - dx * dx));
               if (stops == 1) {
                  System.arraycopy(v, 5, color, 0, 4);
               } else {
                  float position = Math.max(0.0F, Math.min(1.0F, (px - x) / width)) * (float)(stops - 1);
                  int index = Math.min(stops - 2, (int)position);
                  float f = position - (float)index;

                  for (int c = 0; c < 4; c++) {
                     color[c] = v[5 + index * 4 + c] + (v[5 + (index + 1) * 4 + c] - v[5 + index * 4 + c]) * f;
                  }
               }

               GL11.glColor4f(color[0], color[1], color[2], color[3] * alpha);
               vertex(transform, px, middle - half);
               vertex(transform, px, middle + half);
            }
         } finally {
            GL11.glEnd();
            GL11.glPopMatrix();
         }
      }
   }

   private static void vertex(float[] m, float x, float y) {
      GL11.glVertex2f(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]);
   }

   private static void capability(int value, boolean enable) {
      switch (value) {
         case 2884:
            if (enable) {
               GlStateManager.enableCull();
            } else {
               GlStateManager.disableCull();
            }
            break;
         case 2896:
            if (enable) {
               GlStateManager.enableLighting();
            } else {
               GlStateManager.disableLighting();
            }
            break;
         case 2929:
            if (enable) {
               GlStateManager.enableDepth();
            } else {
               GlStateManager.disableDepth();
            }
            break;
         case 3008:
            if (enable) {
               GlStateManager.enableAlpha();
            } else {
               GlStateManager.disableAlpha();
            }
            break;
         case 3042:
            if (enable) {
               GlStateManager.enableBlend();
            } else {
               GlStateManager.disableBlend();
            }
            break;
         case 3553:
            if (enable) {
               GlStateManager.enableTexture2D();
            } else {
               GlStateManager.disableTexture2D();
            }
            break;
         default:
            if (enable) {
               GL11.glEnable(value);
            } else {
               GL11.glDisable(value);
            }
      }
   }

   public static GuiCanvasState canvas() {
      GuiCanvasState.State initial = new GuiCanvasState.State();
      initial.transform[0] = initial.transform[3] = 1.0F;
      initial.alpha = 1.0F;
      initial.lineWidth = 1.0F;
      initial.miter = 10.0F;
      initial.cap = 0;
      initial.join = 1;
      initial.shapeAntialias = 1;
      initial.scissorExtent[0] = initial.scissorExtent[1] = -1.0F;
      initial.composite[0] = 770;
      initial.composite[2] = 1;
      initial.composite[1] = initial.composite[3] = 771;
      return new GuiCanvasState(initial);
   }

   private static void shape(GuiCanvasState canvas, CanvasPath path, boolean stroke, GuiCanvasState.Backend backend, GuiCanvasState.Stats stats, FloatBuffer baseModel) {
      path.transform(canvas.selected().transform);
      GL11.glPushMatrix();

      try {
         // The path already contains DrawPlan's 2D transforms, but still needs the
         // caller's HUD camera (notably RavenRender's z=-2000 near/far translation).
         GL11.glLoadMatrix(baseModel);
         if (stroke) {
            canvas.stroke(path, backend, stats, true);
         } else {
            canvas.fill(path, backend, stats, true);
         }
      } finally {
         GL11.glPopMatrix();
      }
   }

   public static final class Frame {
      public final float width;
      public final float height;
      public final float[] model;
      public final float[] projection;
      public final double[] camera;

      private Frame(float w, float h, float[] m, float[] p, double[] c) {
         this.width = w;
         this.height = h;
         this.model = (float[])m.clone();
         this.projection = (float[])p.clone();
         this.camera = (double[])c.clone();
      }

      public RenderMath.Projection project(double[] relative) {
         return RenderMath.project(relative, this.model, this.projection, this.width, this.height);
      }
   }
}
