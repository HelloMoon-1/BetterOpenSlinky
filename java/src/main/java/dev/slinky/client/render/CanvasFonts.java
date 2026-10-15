package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import dev.slinky.client.hook.RenderHooks;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final strictfp class CanvasFonts {
   private static final float[] IDENTITY = new float[]{1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
   private static CanvasFonts.Engine engine;
   private static CanvasFonts.Engine secondary;
   private static boolean listener;
   private static final Map<Integer, Integer> textures = new HashMap<>();
   private static boolean guiFace;
   private static float rasterRatio;
   private static long generation;

   /** Retained HUD plans must be rebuilt after the font engines/textures are released. */
   public static long generation() { return generation; }
   private static CanvasFonts.Engine guiEngine;
   private static CanvasFonts.Engine guiSecondary;
   private static final Map<Integer, Integer> uploadedVersions = new HashMap<>();

   static strictfp int allocateTextureIdentity() {
      return CanvasFonts.Engine.textureSequence++;
   }

   private static strictfp CanvasFonts.Engine current() {
      return current(0);
   }

   public static strictfp CanvasFonts.GuiFaceScope guiFace() {
      return new CanvasFonts.GuiFaceScope(0.0F);
   }

   /** Pixel density of the caller's projection, independent of the ClickGUI scale setting. */
   public static CanvasFonts.GuiFaceScope guiFace(float pixelsPerUnit) {
      return new CanvasFonts.GuiFaceScope(pixelsPerUnit);
   }

   private static float deviceRatio() {
      return rasterRatio > 0.0F ? rasterRatio : (float)RenderHooks.canvasScale();
   }

   private static void listenForReload() {
      if (!listener) {
         Minecraft game = Minecraft.getMinecraft();
         if (game.getResourceManager() instanceof IReloadableResourceManager) {
            listener = true;
            ((IReloadableResourceManager)game.getResourceManager()).registerReloadListener(resources -> release());
         }
      }
   }

   private static strictfp CanvasFonts.Engine current(int style) {
      if (guiFace) {
         CanvasFonts.Engine selected = style == 1 ? guiSecondary : guiEngine;
         if (selected == null) {
            listenForReload();
            selected = BundledGuiFonts.load(style);
            if (style == 1) {
               guiSecondary = selected;
            } else {
               guiEngine = selected;
            }
         }

         return selected;
      } else {
         CanvasFonts.Engine selected = style == 1 ? secondary : engine;
         if (selected != null && selected.resourceSource != null && !selected.resourceSource.isCurrent()) {
            release();
            selected = null;
         }

         if (selected == null) {
            Minecraft game = Minecraft.getMinecraft();
            listenForReload();
            try {
               GameFontProvider source = new GameFontProvider(game, style == 1);
               int[] heads = new int[256];
               Arrays.fill(heads, -1);
               FontGlyphCache.Font font = new FontGlyphCache.Font(null, heads, 0.8888889F, -0.11111111F, 1.0F);
               selected = new CanvasFonts.Engine(font, source, 512, 256);
               if (style == 1) {
                  secondary = selected;
               } else {
                  engine = selected;
               }
            } catch (IOException var6) {
               throw new IllegalStateException("Cannot load game font resources", var6);
            }
         }

         return selected;
      }
   }

   public static strictfp CanvasFonts.Engine bindEngine(CanvasFonts.Engine next) {
      generation++;
      CanvasFonts.Engine previous = engine;
      engine = Objects.requireNonNull(next);
      secondary = next;
      return previous;
   }

   public static strictfp void restoreEngine(CanvasFonts.Engine previous) {
      generation++;
      engine = previous;
      secondary = null;
   }

   public static strictfp void bindGuiEngine(CanvasFonts.Engine next) {
      generation++;
      guiSecondary = next;
      guiEngine = next;
   }

   public static strictfp float bounds(String text, float x, float y, float size, int alignment, float[] output) {
      return current().bounds(text, x, y, size, alignment, output);
   }

   public static strictfp CanvasFonts.Text text(String text, float x, float y, float size, int alignment, float[] transform) {
      return current().text(text, x, y, size, alignment, transform);
   }

   public static strictfp CanvasFonts.Text text(String text, float x, float y, float size, int alignment, float[] transform, int style) {
      return current(style).text(text, x, y, size, alignment, transform);
   }

   public static strictfp float width(String text, int style, float size) {
      return current(style).bounds(text, 0.0F, 0.0F, size, 9, null);
   }

   public static strictfp void render(CanvasFonts.Text text, int color, float alpha) {
      if (GlStateScope.active()) {
         draw(text, color, alpha);
      } else {
         try (GlStateScope attributes = GlStateScope.open()) {
            draw(text, color, alpha);
         }
      }
   }

   private static strictfp void draw(CanvasFonts.Text text, int color, float alpha) {
      GlStateManager.enableTexture2D();
      GlStateManager.disableAlpha();
      GL11.glShadeModel(7425);
      GL11.glTexEnvi(8960, 8704, 8448);
      GlStateManager.color(
         (float)(color >>> 16 & 0xFF) / 255.0F, (float)(color >>> 8 & 0xFF) / 255.0F, (float)(color & 0xFF) / 255.0F, (float)(color >>> 24) / 255.0F * alpha
      );

      for (CanvasFonts.Batch batch : text.batches) {
         Integer texture = textures.get(batch.texture);
         if (texture == null) {
            texture = GL11.glGenTextures();
            textures.put(batch.texture, texture);
         }

         GlStateManager.bindTexture(texture);
         Integer uploaded = uploadedVersions.get(batch.texture);
         if (uploaded == null || uploaded != batch.version[0]) {
            GL11.glTexParameteri(3553, 10241, 9729);
            GL11.glTexParameteri(3553, 10240, 9729);
            GL11.glTexParameteri(3553, 10242, 10496);
            GL11.glTexParameteri(3553, 10243, 10496);
            ByteBuffer pixels = BufferUtils.createByteBuffer(batch.pixels.length);
            ((Buffer)pixels.put(batch.pixels)).flip();
            GL11.glPixelStorei(3317, 1);
            GL11.glTexImage2D(3553, 0, 6406, batch.width, batch.height, 0, 6406, 5121, pixels);
            uploadedVersions.put(batch.texture, batch.version[0]);
         }

         GL11.glBegin(4);

         try {
            for (float[] vertex : batch.vertices) {
               GL11.glTexCoord2f(vertex[2], vertex[3]);
               GL11.glVertex2f(vertex[0], vertex[1]);
            }
         } finally {
            GL11.glEnd();
         }
      }

      GlStateManager.disableTexture2D();
   }

   public static strictfp void release() {
      generation++;
      if (!textures.isEmpty() && Display.isCreated()) {
         try {
            if (Display.isCurrent()) {
               for (int texture : textures.values()) {
                  GlStateManager.deleteTexture(texture);
               }
            }
         } catch (LWJGLException var5) {
            throw new IllegalStateException("Cannot release font textures", var5);
         } finally {
            textures.clear();
            uploadedVersions.clear();
            guiSecondary = null;
            guiEngine = null;
            secondary = null;
            engine = null;
         }
      } else {
         textures.clear();
         uploadedVersions.clear();
         guiSecondary = null;
         guiEngine = null;
         secondary = null;
         engine = null;
      }
   }

   private CanvasFonts() {
   }

   public static final strictfp class Batch {
      public final int texture;
      public final int width;
      public final int height;
      public final byte[] pixels;
      public final List<float[]> vertices;
      final int[] version;

      Batch(int texture, int width, int height, byte[] pixels, List<float[]> vertices) {
         this(texture, width, height, pixels, new int[1], vertices);
      }

      Batch(int texture, int width, int height, byte[] pixels, int[] version, List<float[]> vertices) {
         this.texture = texture;
         this.width = width;
         this.height = height;
         this.pixels = pixels;
         this.version = version;
         this.vertices = new ArrayList<>();

         for (float[] vertex : vertices) {
            this.vertices.add((float[])vertex.clone());
         }
      }
   }

   public static final strictfp class Engine {
      private final FontGlyphCache face;
      private final FontAtlasService.Canvas atlas;
      private final GameFontProvider resourceSource;
      private final Map<Integer, int[]> dimensions = new HashMap<>();
      private final Map<Integer, byte[]> images = new HashMap<>();
      private final Map<Integer, int[]> versions = new HashMap<>();
      private static int textureSequence = 1;

      public Engine(FontGlyphCache.Font font, FontGlyphCache.Source source, int width, int height) {
         this.resourceSource = source instanceof GameFontProvider ? (GameFontProvider)source : null;
         FontGlyphCache.Stash cache = new FontGlyphCache.Stash(
            new FontServices.Atlas(width, height), new byte[width * height], width, width, height, 0, 0, null
         );
         FontAtlasService.Stash stash = new FontAtlasService.Stash(cache, width, height, Arrays.asList(font), (previous, count) -> new byte[(int)count]);
         FontAtlasService.Driver driver = new FontAtlasService.Driver() {
            @Override
            public strictfp void upload(int texture, int x, int y, int w, int h, byte[] pixels) {
               int stride = Engine.this.dimensions.get(texture)[0];
               byte[] target = Engine.this.images.get(texture);

               for (int row = 0; row < h; row++) {
                  System.arraycopy(pixels, (y + row) * stride + x, target, (y + row) * stride + x, w);
               }

               Engine.this.versions.get(texture)[0]++;
            }

            @Override
            public strictfp void dimensions(int texture, int[] output) {
               int[] found = Engine.this.dimensions.get(texture);
               if (found == null) {
                  throw new IllegalStateException("Unknown caller atlas");
               } else {
                  output[0] = found[0];
                  output[1] = found[1];
               }
            }

            @Override
            public strictfp int create(int type, int w, int h, int flags, byte[] pixels) {
               int id = CanvasFonts.Engine.textureSequence++;
               Engine.this.dimensions.put(id, new int[]{w, h});
               Engine.this.images.put(id, pixels == null ? new byte[w * h] : (byte[])pixels.clone());
               Engine.this.versions.put(id, new int[1]);
               return id;
            }
         };
         int texture = driver.create(1, width, height, 0, null);
         this.atlas = new FontAtlasService.Canvas(stash, 0, new int[]{texture, 0, 0, 0}, driver, width, height, 0, 0);
         FontAtlasService.reset(stash, width, height, 0, 0);
         this.face = new FontGlyphCache(cache, font, source);
      }

      public strictfp float bounds(String text, float x, float y, float size, int alignment, float[] output) {
         byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
         return FontServices.canvasBounds(
            this.face,
            0,
            x,
            y,
            bytes,
            bytes.length,
            size,
            0.0F,
            0.0F,
            alignment,
            true,
            this.atlas.stash.inverseWidth,
            this.atlas.stash.inverseHeight,
            CanvasFonts.IDENTITY,
            deviceRatio(),
            output
         );
      }

      public strictfp CanvasFonts.Text text(String text, float x, float y, float size, int alignment, float[] transform) {
         float scale = FontServices.transformScale(
            transform, RenderConstants.f(2840336), RenderConstants.f(2840340), RenderConstants.f(2840344), deviceRatio()
         );
         final FontServices.Iterator iterator = new FontServices.Iterator(
            this.face,
            x * scale,
            y * scale,
            text.getBytes(StandardCharsets.UTF_8),
            size * scale,
            0.0F,
            0.0F,
            alignment,
            2,
            true,
            this.atlas.stash.inverseWidth,
            this.atlas.stash.inverseHeight
         );
         final List<CanvasFonts.Batch> batches = new ArrayList<>();
         float end = FontServices.drawText(
            iterator,
            x,
            scale,
            CanvasFonts.IDENTITY,
            new FontServices.TextBackend() {
               private float[] reserved;

               @Override
               public strictfp boolean reserve(int count) {
                  this.reserved = new float[count * 4];
                  return this.reserved.length >= count * 4;
               }

               @Override
               public strictfp boolean allocateTextAtlas() {
                  boolean result = FontAtlasService.allocate(Engine.this.atlas);
                  if (result) {
                     iterator.inverseWidth = Engine.this.atlas.stash.inverseWidth;
                     iterator.inverseHeight = Engine.this.atlas.stash.inverseHeight;
                  }

                  return result;
               }

               @Override
               public strictfp void draw(List<float[]> vertices) {
                  if (!vertices.isEmpty()) {
                     this.uploadDirtyTexture();
                     int offset = 0;

                     for (float[] vertex : vertices) {
                        for (float value : vertex) {
                           this.reserved[offset++] = value;
                        }
                     }

                     int texture = Engine.this.atlas.textures[Engine.this.atlas.index];
                     batches.add(
                        new CanvasFonts.Batch(
                           texture,
                           Engine.this.atlas.stash.width,
                           Engine.this.atlas.stash.height,
                           Engine.this.images.get(texture),
                           Engine.this.versions.get(texture),
                           vertices
                        )
                     );
                  }
               }

               @Override
               public strictfp void uploadDirtyTexture() {
                  FontGlyphCache.Stash cache = Engine.this.atlas.stash.cache;
                  if (Engine.this.atlas.stash.dirty()) {
                     Engine.this.atlas
                        .driver
                        .upload(
                           Engine.this.atlas.textures[Engine.this.atlas.index],
                           cache.dirtyX,
                           cache.dirtyY,
                           cache.dirtyRight - cache.dirtyX,
                           cache.dirtyBottom - cache.dirtyY,
                           cache.pixels
                        );
                  }

                  Engine.this.atlas.stash.clean();
               }
            },
            new FontServices.TextStats()
         );
         return new CanvasFonts.Text(text, batches, end);
      }
   }

   public static final strictfp class GuiFaceScope implements AutoCloseable {
      private final boolean previous = CanvasFonts.guiFace;
      private final float previousRatio = CanvasFonts.rasterRatio;

      private GuiFaceScope(float pixelsPerUnit) {
         CanvasFonts.guiFace = true;
         CanvasFonts.rasterRatio = Float.isFinite(pixelsPerUnit) && pixelsPerUnit > 0.0F ? pixelsPerUnit : 0.0F;
      }

      @Override
      public strictfp void close() {
         CanvasFonts.guiFace = this.previous;
         CanvasFonts.rasterRatio = this.previousRatio;
      }
   }

   public static final strictfp class Text {
      public final String text;
      public final List<CanvasFonts.Batch> batches;
      public final float end;

      Text(String text, List<CanvasFonts.Batch> batches, float end) {
         this.text = text;
         this.batches = batches;
         this.end = end;
      }
   }
}
