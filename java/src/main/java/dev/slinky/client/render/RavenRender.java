package dev.slinky.client.render;

import dev.slinky.client.hook.GlStateScope;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.RavenSettingsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

public final class RavenRender {
   private static ModuleManager manager;

   private RavenRender() {
   }

   public static void bind(ModuleManager value) {
      manager = value;
      OpenB4Theme.bind(value);
   }

   private static RavenSettingsModule settings() {
      return manager != null && manager.find("raven_settings") instanceof RavenSettingsModule
         ? (RavenSettingsModule)manager.find("raven_settings") : null;
   }

   public static boolean chatAsInventory() {
      RavenSettingsModule settings = settings();
      return settings != null && settings.chatAsInventory.isEnabled();
   }

   public static int background() {
      RavenSettingsModule settings = settings();
      return settings == null ? 2 : settings.background.index();
   }

   public static boolean healthAsHearts() {
      RavenSettingsModule settings = settings();
      return settings != null && settings.healthAsHearts.isEnabled();
   }

   public static boolean heartSymbol() {
      RavenSettingsModule settings = settings();
      return settings != null && settings.heartSymbol.isEnabled();
   }

   public static boolean visible(Minecraft game) {
      return game.thePlayer != null && game.theWorld != null && !game.gameSettings.showDebugInfo
         && (game.currentScreen == null || !chatAsInventory() && game.currentScreen instanceof GuiChat);
   }

   public static Scope begin() {
      return new Scope(Minecraft.getMinecraft());
   }

   public static final class Scope implements AutoCloseable {
      private final GlStateScope state;
      private final int matrix;
      private boolean projection;
      private boolean model;
      private boolean texture;
      private boolean closed;

      private Scope(Minecraft game) {
         this.matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
         this.state = GlStateScope.open();
         try {
            ScaledResolution resolution = new ScaledResolution(game);
            GL11.glViewport(0, 0, game.displayWidth, game.displayHeight);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            this.texture = true;
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            this.projection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0, 1000.0, 3000.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            this.model = true;
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         } catch (RuntimeException | Error error) {
            this.close();
            throw error;
         }
      }

      @Override
      public void close() {
         if (!this.closed) {
            this.closed = true;
            try {
               if (this.model) {
                  GL11.glMatrixMode(GL11.GL_MODELVIEW);
                  GL11.glPopMatrix();
               }
               if (this.projection) {
                  GL11.glMatrixMode(GL11.GL_PROJECTION);
                  GL11.glPopMatrix();
               }
               if (this.texture) {
                  GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                  GL11.glMatrixMode(GL11.GL_TEXTURE);
                  GL11.glPopMatrix();
               }
               GL11.glMatrixMode(this.matrix);
            } finally {
               this.state.close();
            }
         }
      }
   }
}
