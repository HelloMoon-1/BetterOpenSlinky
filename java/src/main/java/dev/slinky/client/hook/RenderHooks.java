package dev.slinky.client.hook;

import dev.slinky.client.ClientContext;
import dev.slinky.client.MinecraftFields;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.OpenB4Console;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.ChamsModule;
import dev.slinky.module.impl.GuiModule;
import dev.slinky.module.impl.NameTagsModule;
import java.util.ArrayDeque;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public final strictfp class RenderHooks {
   private static ModuleManager modules;
   private static ForgeUiSurface uiSurface;
   private static UiFrameDriver uiFrames;
   private static final ArrayDeque<Boolean> livingRenders = new ArrayDeque<>();

   private RenderHooks() {
   }

   public static strictfp void install(ModuleManager manager) {
      if (manager != null && modules != manager) {
         if (uiSurface != null) {
            clear();
         }
         livingRenders.clear();
      }
      modules = manager;
      RavenRender.bind(manager);
      uiFrames = new UiFrameDriver(-1.0, 0, false, true);
   }

   public static strictfp void beforeDisplay() {
      if (modules != null) {
         initializeGuiBinding(modules);
         if (hasListeners(10)) {
            Minecraft game = modules.context().minecraft();
            if (uiSurface == null) {
               uiSurface = new ForgeUiSurface(modules);
            }

            Module gui = modules.find("gui");
            double scale = gui != null && gui.findSetting("scale").isConfigured() ? (double)((GuiModule)gui).scale() : 1.0;
            uiFrames.frame(uiSurface, game.displayWidth, game.displayHeight, scale, RenderAccess.now());
         }
         OpenB4Console.renderHud(modules.context().minecraft());
      }
   }

   public static strictfp void initializeGuiBinding(ModuleManager manager) {
      Module gui = manager.find("GUI");
      if (gui != null && gui.getBinding() == 0) {
         manager.setBinding(gui, 99);
      }
   }

   public static strictfp boolean hasListeners(int type) {
      if (modules == null) {
         return false;
      } else {
         if (type == 10 && modules.context().minecraft().currentScreen instanceof GuiFrame) {
            return true;
         }
         for (Module module : modules.all()) {
            if (module.isEnabled() && module.listens(type)) {
               return true;
            }
         }

         return false;
      }
   }

   public static strictfp double canvasScale() {
      return uiSurface == null ? 1.0 : uiSurface.canvasScale();
   }

   public static strictfp int uiTexture() {
      return uiSurface == null ? -1 : uiSurface.drawingTexture();
   }

   public static strictfp void clearWorld(int mask) {
      if (mask == 256 && modules != null && modules.context().ready() && (hasListeners(6) || hasListeners(7))) {
         ClientContext context = modules.context();
         Minecraft game = context.minecraft();
         Entity view = game.getRenderViewEntity();
         if (view != null) {
            float partial = MinecraftFields.timer(game).renderPartialTicks;
            SemanticEvent world = worldEvent(6, view, partial, game.displayWidth, game.displayHeight);
            GlStateScope state = GlStateScope.open();
            int previousMatrix = GL11.glGetInteger(2976);
            GL11.glMatrixMode(5889);
            GL11.glPushMatrix();
            GL11.glMatrixMode(5888);
            GL11.glPushMatrix();

            try {
               GlStateManager.disableDepth();
               GlStateManager.disableTexture2D();
               GlStateManager.disableLighting();
               GlStateManager.enableBlend();
               GlStateManager.blendFunc(770, 771);
               GL11.glEnable(2848);
               if (hasListeners(6)) {
                  modules.dispatch(world);
               }

               if (hasListeners(7)) {
                  RenderAccess.captureWorldProjection();
                  GL11.glMatrixMode(5889);
                  GL11.glLoadIdentity();
                  GL11.glOrtho(0.0, (double)game.displayWidth, (double)game.displayHeight, 0.0, -3000.0, 3000.0);
                  GL11.glMatrixMode(5888);
                  GL11.glLoadIdentity();
                  modules.dispatch(worldEvent(7, view, partial, game.displayWidth, game.displayHeight));
               }
            } finally {
               GL11.glMatrixMode(5888);
               GL11.glPopMatrix();
               GL11.glMatrixMode(5889);
               GL11.glPopMatrix();
               GL11.glMatrixMode(previousMatrix);
               state.close();
            }
         }
      }

      GlStateManager.clear(mask);
   }

   public static strictfp SemanticEvent worldEvent(int type, Entity view, float partial, int width, int height) {
      SemanticEvent event = new SemanticEvent(type);
      double fraction = (double)partial;
      event.cameraX = fraction * (view.posX - view.prevPosX) + view.prevPosX;
      event.cameraY = fraction * (view.posY - view.prevPosY) + view.prevPosY;
      event.cameraZ = (view.posZ - view.prevPosZ) * fraction + view.prevPosZ;
      event.timerRenderPartialTicks = partial;
      event.renderWidth = (float)width;
      event.renderHeight = (float)height;
      return event;
   }

   public static strictfp boolean suppressVanillaName(Entity entity) {
      Module module = modules == null ? null : modules.find("name_tags");
      return module instanceof NameTagsModule && ((NameTagsModule)module).suppressVanilla(entity);
   }

   public static strictfp void beginLiving(EntityLivingBase entity) {
      Module module = modules == null ? null : modules.find("chams");
      livingRenders.push(module instanceof ChamsModule && ((ChamsModule)module).begin(entity));
   }

   public static strictfp void endLiving() {
      Boolean selected = livingRenders.poll();
      Module module = modules == null ? null : modules.find("chams");
      if (selected != null && module instanceof ChamsModule) {
         ((ChamsModule)module).end(selected);
      }
   }

   public static strictfp void beforeSetBlock(World world, BlockPos position, IBlockState state) {
      blockEvent(world, 2, position.getX(), position.getY(), position.getZ(), Block.getIdFromBlock(state.getBlock()));
   }

   public static strictfp void afterUnloadChunk(int x, int z) {
      blockEvent(localWorld(), 0, x, 0, z, 0);
   }

   public static strictfp void afterInvalidate(World world, int firstX, int firstZ) {
      blockEvent(world, 1, firstX >> 4, 0, firstZ >> 4, 0);
   }

   private static strictfp World localWorld() {
      return modules == null ? null : modules.context().world();
   }

   private static strictfp void blockEvent(World world, int kind, int x, int y, int z, int identifier) {
      if (modules != null) {
         SemanticEvent event = new SemanticEvent(20);
         RenderEventPayload payload = RenderEventPayload.attach(event);
         payload.regionKind = kind;
         payload.world = world;
         payload.x = x;
         payload.y = y;
         payload.z = z;
         payload.blockId = identifier;

         try {
            modules.dispatch(event);
         } finally {
            RenderEventPayload.detach(event);
         }
      }
   }

   public static strictfp void clear() {
      try {
         if (uiSurface != null) {
            uiSurface.close();
         }
      } finally {
         uiSurface = null;
         uiFrames = new UiFrameDriver(-1.0, 0, false, true);
         RenderAccess.clearProjection();
      }
   }

   public interface GuiFrame {
      void drawRecoveredFrame(float var1);
   }
}
