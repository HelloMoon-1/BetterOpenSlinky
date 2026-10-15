package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.numeric.DoubleTrigonometry;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.client.render.RavenRender;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

public final strictfp class IndicatorsModule extends EvidenceModule {
   private final List<IndicatorsModule.Row> rows = new ArrayList<>();
   private final List<Entity> tracked = new ArrayList<>();
   private final Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
   private final BooleanSetting renderArrows;
   private final BooleanSetting renderPearls;
   private final BooleanSetting renderFireballs;
   private final ChoiceSetting arrowShape;
   private final NumberSetting circleRadius;
   private final BooleanSetting itemColors;
   private final BooleanSetting renderItem;
   private final BooleanSetting onlyThreats;
   private final ItemStack arrowItem = new ItemStack(Items.arrow);
   private final ItemStack pearlItem = new ItemStack(Items.ender_pearl);
   private final ItemStack fireballItem = new ItemStack(Items.fire_charge);

   public IndicatorsModule() {
      super("indicators", "Indicators", ModuleCategory.RENDER);
      // Keep recovered settings/default loading and public plan/Row API intact.
      // The live renderer and visible controls use the donor's independent options.
      this.renderArrows = this.preference(new BooleanSetting("render_arrows", "Render arrows", true));
      this.renderPearls = this.preference(new BooleanSetting("render_ender_pearls", "Render ender pearls", true));
      this.renderFireballs = this.preference(new BooleanSetting("render_fireballs", "Render fireballs", true));
      this.arrowShape = this.preference(new ChoiceSetting("arrow_shape", "Arrow", 0, "Caret", "Greater than", "Triangle"));
      this.circleRadius = this.preference(new NumberSetting("circle_radius", "Circle radius", 50.0, 30.0, 200.0, 5.0));
      this.itemColors = this.preference(new BooleanSetting("item_colors", "Item colors", true));
      this.renderItem = this.preference(new BooleanSetting("render_item", "Render item", true));
      this.onlyThreats = this.preference(new BooleanSetting("render_only_threats", "Render only threats", false));
   }

   private static void configure(Setting<?> setting) {
      setting.parseAndSet(setting.format());
      setting.saveCurrent();
   }

   private <T extends Setting<?>> T preference(T setting) {
      configure(setting);
      return this.add(setting);
   }

   @Override
   public boolean settingVisible(String key) {
      return key.equals("render_arrows") || key.equals("render_ender_pearls") || key.equals("render_fireballs")
         || key.equals("arrow_shape") || key.equals("circle_radius") || key.equals("item_colors")
         || key.equals("render_item") || key.equals("render_only_threats");
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 5 || t == 7 || t == 18;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 5) {
         if (e.module == this && !e.enabled) {
            this.clear();
         }
      } else if (e.type == 18) {
         this.update(c);
         this.updateTracked(c);
      } else if (c.ready() && RavenRender.visible(c.minecraft()) && !e.cancelled) {
         RenderAccess.Frame frame = RenderAccess.frame(c, e);
         if (frame != null) {
            this.renderDonor(c, frame, e.timerRenderPartialTicks);
         }
      }
   }

   private strictfp void update(ClientContext c) {
      this.rows.clear();
      if (c.ready()) {
         double[] self = RenderAccess.position(c.player());

         for (Entity entity : c.world().loadedEntityList) {
            int type = entity instanceof EntityEnderPearl ? 14 : (entity instanceof EntityFireball ? 12 : (entity instanceof EntityArrow ? 10 : 0));
            if (type != 0
               && this.flag(type == 14 ? "show_pearl" : (type == 12 ? "show_fireball" : "show_arrow"))
               && (type != 10 || !RenderAccess.arrowInGround((EntityArrow)entity))) {
               double distance = RenderMath.distanceSquared(RenderAccess.position(entity), self);
               if (!this.flag("require_coming_closer") || !(distance > RenderMath.distanceSquared(RenderAccess.previous(entity), self))) {
                  distance = StrictMath.sqrt(distance);
                  String label = String.format(Locale.ROOT, RenderConstants.text(1392808), distance);
                  if (label.length() > 9) {
                     label = label.substring(0, 9);
                  }

                  int color = distance > RenderConstants.d(1392680) ? -256 : -65536;
                  if (distance > RenderConstants.d(1392688)) {
                     color = -1;
                  }

                  this.rows.add(new IndicatorsModule.Row(entity, type, label, (int)RenderAccess.width(c, label), color));
                  if (this.rows.size() == 10) {
                     break;
                  }
               }
            }
         }
      }
   }

   private void updateTracked(ClientContext c) {
      if (!c.ready()) {
         this.tracked.clear();
         this.seen.clear();
         return;
      }
      Set<Entity> loaded = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
      loaded.addAll(c.world().loadedEntityList);
      this.seen.retainAll(loaded);
      this.tracked.removeIf(entity -> entity.isDead || !loaded.contains(entity));
      for (Entity entity : c.world().loadedEntityList) {
         if (!this.seen.add(entity) || entity.isDead) {
            continue;
         }
         double distance = c.player().getDistanceSqToEntity(entity);
         if (entity instanceof EntityArrow) {
            if (this.renderArrows.isEnabled() && distance > 49.0) {
               this.tracked.add(entity);
            }
         } else if (entity instanceof EntityEnderPearl) {
            // Use actual projectile ownership where available, never an invented
            // throw timer. Unresolved remote ownership remains unknown.
            if (this.renderPearls.isEnabled() && (((EntityEnderPearl)entity).getThrower() != c.player() || distance > 8.0)) {
               this.tracked.add(entity);
            }
         } else if (entity instanceof EntityLargeFireball && this.renderFireballs.isEnabled()
            && (((EntityLargeFireball)entity).shootingEntity != c.player() || distance > 8.0)) {
            this.tracked.add(entity);
         }
      }
   }

   private boolean fireballThreat(ClientContext c, Entity entity) {
      double x = entity.posX;
      double y = entity.posY;
      double z = entity.posZ;
      double mx = x - entity.lastTickPosX;
      double my = y - entity.lastTickPosY;
      double mz = z - entity.lastTickPosZ;
      if (mx == 0.0 && my == 0.0 && mz == 0.0) {
         return false;
      }
      for (int i = 0; i < 400; i++) {
         if (c.player().getDistanceSq(x, y, z) <= 49.0) {
            return true;
         }
         Block block = c.world().getBlockState(new BlockPos(x, y, z)).getBlock();
         if (!(block instanceof BlockAir) && !(block instanceof BlockLiquid) && !(block instanceof BlockFire)) {
            break;
         }
         x += mx * 0.5;
         y += my * 0.5;
         z += mz * 0.5;
      }
      return false;
   }

   private void renderDonor(ClientContext c, RenderAccess.Frame frame, float partial) {
      Minecraft mc = c.minecraft();
      ScaledResolution sr = new ScaledResolution(mc);
      try (RavenRender.Scope scope = RavenRender.begin()) {
         for (Entity entity : this.tracked) {
            int kind;
            if (entity instanceof EntityArrow) {
               if (!this.renderArrows.isEnabled() || RenderAccess.arrowInGround((EntityArrow)entity)) {
                  continue;
               }
               kind = 0;
            } else if (entity instanceof EntityEnderPearl) {
               if (!this.renderPearls.isEnabled()) {
                  continue;
               }
               kind = 1;
            } else {
               if (!this.renderFireballs.isEnabled() || this.onlyThreats.isEnabled() && !this.fireballThreat(c, entity)) {
                  continue;
               }
               kind = 2;
            }
            double[] projected = project(frame, entity, partial, sr.getScaleFactor());
            if (projected == null) {
               continue;
            }
            double cx = sr.getScaledWidth() / 2.0;
            double cy = sr.getScaledHeight() / 2.0;
            double dx = projected[0] - cx;
            double dy = projected[1] - cy;
            boolean inFront = projected[2] < 1.0003684;
            if (!inFront) {
               dx = -dx;
               dy = -dy;
            }
            double radius = this.circleRadius.doubleValue() + (this.renderItem.isEnabled() ? 20.0 : 0.0);
            if (inFront && Math.hypot(dx, dy) < radius + 15.0) {
               continue;
            }
            double angle = Math.atan2(dx, dy);
            float rotation = (float)(Math.atan2(dy, dx) * 57.29578F + 90.0);
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            int color = this.itemColors.isEnabled() ? (kind == 1 ? 0xFFD200FF : kind == 2 ? 0xFFFF9600 : -1) : -1;
            GlStateManager.pushMatrix();
            try {
               GlStateManager.translate(cx + radius * sin, cy + radius * cos, 0.0);
               GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
               this.drawArrow(mc, color);
            } finally {
               GlStateManager.popMatrix();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.pushMatrix();
            try {
               GlStateManager.translate(cx + (radius - 13.0) * sin, cy + (radius - 13.0) * cos, 0.0);
               GlStateManager.scale(0.8, 0.8, 0.8);
               String distance = (int)mc.thePlayer.getDistanceToEntity(entity) + "m";
               mc.fontRendererObj.drawString(distance, -mc.fontRendererObj.getStringWidth(distance) / 2.0F, -4.0F, -1, true);
            } finally {
               GlStateManager.popMatrix();
            }
            if (this.renderItem.isEnabled()) {
               GlStateManager.pushMatrix();
               try {
                  double offset = kind == 0 ? 26.0 : 29.0;
                  GlStateManager.translate(cx + (radius - offset) * sin, cy + (radius - offset) * cos, 0.0);
                  if (kind == 0) {
                     GlStateManager.rotate(rotation - 45.0F, 0.0F, 0.0F, 1.0F);
                     mc.getRenderItem().renderItemIntoGUI(this.arrowItem, -12, -4);
                  } else {
                     mc.getRenderItem().renderItemIntoGUI(kind == 1 ? this.pearlItem : this.fireballItem, -8, -9);
                  }
               } finally {
                  GlStateManager.popMatrix();
               }
            }
         }
      }
   }

   private void drawArrow(Minecraft mc, int color) {
      if (this.arrowShape.index() == 1) {
         GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
         GlStateManager.scale(1.5, 1.5, 1.5);
         mc.fontRendererObj.drawString(">", -2.0F, -4.0F, color, false);
         return;
      }
      GlStateManager.enableBlend();
      GlStateManager.disableTexture2D();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.color((color >>> 16 & 255) / 255.0F, (color >>> 8 & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
      if (this.arrowShape.index() == 0) {
         double a = Math.toRadians(35.0);
         GL11.glEnable(GL11.GL_LINE_SMOOTH);
         GL11.glLineWidth(3.0F);
         GL11.glBegin(GL11.GL_LINE_STRIP);
         GL11.glVertex2d(Math.sin(-a) * 9.0, Math.cos(-a) * 9.0 - 5.0);
         GL11.glVertex2d(0.0, -5.0);
         GL11.glVertex2d(Math.sin(a) * 9.0, Math.cos(a) * 9.0 - 5.0);
         GL11.glEnd();
         GL11.glDisable(GL11.GL_LINE_SMOOTH);
      } else {
         GL11.glBegin(GL11.GL_TRIANGLE_FAN);
         for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2.0 * i / 3.0 + Math.PI;
            GL11.glVertex2d(Math.sin(angle) * 5.0, Math.cos(angle) * 5.0);
         }
         GL11.glEnd();
      }
      GlStateManager.enableTexture2D();
      GlStateManager.disableBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   // Unlike Frame.project, donor indicators need points behind the camera and
   // outside the depth range. Do not clamp those points to the visible screen.
   private static double[] project(RenderAccess.Frame frame, Entity entity, float partial, int scale) {
      double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial - frame.camera[0];
      double y = entity.height / 2.0 + entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial - frame.camera[1];
      double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial - frame.camera[2];
      float[] m = frame.model;
      double[] view = new double[4];
      for (int i = 0; i < 4; i++) {
         view[i] = m[i] * x + m[i + 4] * y + m[i + 8] * z + m[i + 12];
      }
      float[] p = frame.projection;
      double[] clip = new double[4];
      for (int i = 0; i < 4; i++) {
         clip[i] = p[i] * view[0] + p[i + 4] * view[1] + p[i + 8] * view[2] + p[i + 12] * view[3];
      }
      if (clip[3] == 0.0) {
         return null;
      }
      double sx = (clip[0] / clip[3] * 0.5 + 0.5) * frame.width / scale;
      double sy = (0.5 - clip[1] / clip[3] * 0.5) * frame.height / scale;
      double depth = clip[2] / clip[3] * 0.5 + 0.5;
      return Double.isFinite(sx) && Double.isFinite(sy) && Double.isFinite(depth) ? new double[]{sx, sy, depth} : null;
   }

   public strictfp DrawPlan plan(RenderAccess.Frame frame, float partial, float yaw, double clock) {
      float one = RenderConstants.f(1392612);
      double phase = clock * RenderConstants.d(1392616);
      double half = RenderConstants.d(1392624);
      float alpha = (float)(DoubleTrigonometry.sine(phase) * half + half);
      DrawPlan p = new DrawPlan()
         .push()
         .translate(frame.width * RenderConstants.f(1392608), frame.height * RenderConstants.f(1392608), 0.0F)
         .lineWidth(one)
         .enable(2848);

      for (IndicatorsModule.Row r : this.rows) {
         double x = (double)RenderMath.lerp((float)r.px, (float)r.x, partial) - frame.camera[0];
         double z = (double)RenderMath.lerp((float)r.pz, (float)r.z, partial) - frame.camera[2];
         float angle = PointersModule.angle(x, z, yaw, 1392632, 1392640, 1392648);
         float size = this.number("scale");
         p.push()
            .rotate(angle, 0.0F, 0.0F, one)
            .translate(0.0F, Float.intBitsToFloat(Float.floatToRawIntBits(this.number("radius")) ^ RenderConstants.i(1392656)), 0.0F)
            .scale(size, size, size)
            .push()
            .rotate(Float.intBitsToFloat(Float.floatToRawIntBits(angle) ^ RenderConstants.i(1392656)), 0.0F, 0.0F, one)
            .text(r.label, (float)((double)(-r.width) * half), -5.0F, r.color, true, 9.0F)
            .pop()
            .translate(0.0F, RenderConstants.f(1392672), 0.0F)
            .scale(RenderConstants.f(1392676), RenderConstants.f(1392676), RenderConstants.f(1392676));
         int type = r.type - 10;
         p.color(RenderConstants.f(1392912 + 4 * type), RenderConstants.f(1392932 + 4 * type), RenderConstants.f(1392952 + 4 * type), alpha)
            .graphics(169280)
            .color(0.0F, 0.0F, 0.0F, alpha)
            .graphics(169328)
            .pop();
      }

      return p.pop();
   }

   public strictfp List<IndicatorsModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   private void clear() {
      this.rows.clear();
      this.tracked.clear();
      this.seen.clear();
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.clear();
   }

   public static final strictfp class Row {
      public final double x;
      public final double z;
      public final double px;
      public final double pz;
      public final int type;
      public final int color;
      public final int width;
      public final String label;

      public Row(Entity e, int type, String label, int width, int color) {
         this(e.posX, e.posZ, e.prevPosX, e.prevPosZ, type, label, width, color);
      }

      public Row(double x, double z, double px, double pz, int type, String label, int width, int color) {
         this.x = x;
         this.z = z;
         this.px = px;
         this.pz = pz;
         this.type = type;
         this.label = label;
         this.width = width;
         this.color = color;
      }
   }
}
