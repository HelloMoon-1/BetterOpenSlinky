package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.RavenHudDrawing;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/** OpenB4 Radar's fixed position, squared-distance projection and marker geometry. */
public final class RadarModule extends Module {
   public final BooleanSetting showTracerLines;

   public RadarModule() {
      super("radar", "Radar", "Shows nearby players using the OpenB4 radar.", ModuleCategory.RENDER);
      this.showTracerLines = this.add(new BooleanSetting("show_tracer_lines", "Show tracer lines", false));
      this.showTracerLines.set(this.showTracerLines.get());
      this.showTracerLines.saveCurrent();
   }

   @Override
   public int metadataCategory() { return 2; }

   @Override
   public int descriptorFlags() { return 0; }

   @Override
   public boolean listens(int type) {
      return type == 9 || type == 10;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      RenderEventPayload payload = RenderEventPayload.get(event);
      if (event.type == 9 && payload != null) {
         // Rotation/player movement and Overlay visibility must invalidate the cached UI FBO.
         payload.redrawFlags |= 3;
      } else if (event.type == 10 && RavenRender.visible(context.minecraft())) {
         this.render(context.minecraft());
      }
   }

   public void render(Minecraft game) {
      if (game.thePlayer == null || game.theWorld == null) {
         return;
      }
      try (RavenRender.Scope ignored = RavenRender.begin()) {
         RavenHudDrawing.rect(5, 70, 105, 170, 0x7D000000);
         RavenHudDrawing.rect(4, 69, 106, 70, -1);
         RavenHudDrawing.rect(4, 170, 106, 171, -1);
         RavenHudDrawing.rect(4, 70, 5, 170, -1);
         RavenHudDrawing.rect(105, 70, 106, 170, -1);
         RavenHudDrawing.polygon(55.5, 122.0, 5.0, 3, -1);
         int scale = new ScaledResolution(game).getScaleFactor();
         GL11.glEnable(GL11.GL_SCISSOR_TEST);
         GL11.glScissor(5 * scale, game.displayHeight - scale * 170, 100 * scale, 100 * scale);
         for (EntityPlayer player : game.theWorld.playerEntities) {
            // Target AntiBot's relationship bit is the runtime equivalent of BotCheck.isBot.
            if (player == game.thePlayer || player.deathTime != 0 || (RelationshipService.flags(player) & 2) != 0) {
               continue;
            }
            double distanceSquared = player.getDistanceSqToEntity(game.thePlayer);
            if (!(distanceSquared <= 360.0)) {
               continue;
            }
            double dx = player.posX - game.thePlayer.posX;
            double dz = player.posZ - game.thePlayer.posZ;
            double bearing = Math.atan2(dx, dz) * (double)57.29578F;
            double angle = (game.thePlayer.rotationYaw + bearing) % 360.0;
            // Deliberately not sqrt(distanceSquared): this is the donor's actual projection.
            double radius = distanceSquared / 5.0;
            double x = 55.5 - radius * Math.sin(Math.toRadians(angle));
            double y = 122.0 - radius * Math.cos(Math.toRadians(angle));
            if (this.showTracerLines.isEnabled()) {
               RavenHudDrawing.begin();
               GL11.glEnable(GL11.GL_LINE_SMOOTH);
               GL11.glLineWidth(0.5F);
               RavenHudDrawing.color(-1);
               GL11.glBegin(GL11.GL_LINE_LOOP);
               GL11.glVertex2d(55.5, 122.0);
               GL11.glVertex2d(x, y);
               GL11.glEnd();
               GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
            RavenHudDrawing.polygon(x, y, 3.0, 4, 0xFFFF0000);
         }
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }
   }
}
