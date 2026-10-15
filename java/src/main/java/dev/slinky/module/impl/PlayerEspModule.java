package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.StatusText;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.client.render.PlayerEspGlowRenderer;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.ColorSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.player.EntityPlayer;

public final strictfp class PlayerEspModule extends EvidenceModule {
   private final List<PlayerEspModule.Row> rows = new ArrayList<>();
   private PlayerEspGlowRenderer glow = new PlayerEspGlowRenderer();
   private boolean silhouetteRendered;
   private boolean glowActive;
   private List<PlayerEspGlowRenderer.Target> frameTargets = Collections.emptyList();

   public PlayerEspModule() {
      super("player_esp", "Player ESP", ModuleCategory.RENDER);
      // Keep the recovered mode indices/UI gates intact; glow is an independent style.
      this.add(new ChoiceSetting("render_style", "Render style", 0, "Original", "Glow"));
      this.add(new NumberSetting("glow_smoothness", "Glow smoothness", 65.0, 0.0, 100.0, 1.0));
      this.add(new ChoiceSetting("glow_colors", "Glow colors", 0, "Teams / Friends", "Original colors"));
      this.add(new NumberSetting("glow_players", "Glow player limit", 12.0, 4.0, 24.0, 1.0));
   }

   @Override
   public boolean settingVisible(String key) {
      if (key.startsWith("glow_")) return this.choice("render_style") == 1;
      return super.settingVisible(key);
   }

   @Override
   public strictfp boolean listens(int type) {
      return type == 6 || type == 7 || type == 18 || type == 26;
   }

   public strictfp List<PlayerEspModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 6) {
         this.frameTargets = Collections.emptyList();
         this.silhouetteRendered = false;
      }
      if ((e.type == 6 || e.type == 7) && e.cancelled) { return; }
      if (this.choice("render_style") == 0 && this.glowActive) { this.releaseGlow(c); }
      if (e.type == 26) {
         this.clear(c);
      } else if (e.type == 18) {
         this.update(c);
      } else if (this.choice("render_style") == 1 && c.ready() && !e.cancelled) {
         if (e.type == 6) {
            this.glowActive = true;
            this.frameTargets = this.glowTargets(c, e);
            this.silhouetteRendered = this.glow.render(c.minecraft(), this.frameTargets,
               e.timerRenderPartialTicks, e.cameraX, e.cameraY, e.cameraZ, this.number("glow_smoothness") / 100.0F);
         } else if (e.type == 7) {
            RenderAccess.Frame frame = RenderAccess.frame(c, e);
            if (frame != null) {
               if (!this.silhouetteRendered) {
                  RenderAccess.submit(e, PlayerEspGlowRenderer.boxFallback(frame, this.frameTargets, e.timerRenderPartialTicks, this.number("glow_smoothness") / 100.0F));
               }
               if (this.flag("show_health_bar")) {
                  RenderAccess.submit(e, this.screenPlan(frame, e.timerRenderPartialTicks, false));
               }
            }
            this.silhouetteRendered = false;
            this.frameTargets = Collections.emptyList();
         }
      } else if (e.type == 6 && this.choice("mode") == 1 && c.ready()) {
         RenderAccess.submit(e, this.worldPlan(c, e));
      } else if (e.type == 7 && this.choice("mode") == 0) {
         RenderAccess.Frame frame = RenderAccess.frame(c, e);
         if (frame != null) {
            RenderAccess.submit(e, this.screenPlan(frame, e.timerRenderPartialTicks));
         }
      }
   }

   private strictfp void update(ClientContext c) {
      if (c.ready()) {
         this.rows.clear();
         double[] self = RenderAccess.position(c.player());
         int mask = (this.flag("hide_friendlies") ? 5 : 0) + 2;
         float radius = this.number("range");

         for (EntityPlayer player : c.world().playerEntities) {
            int flags = RelationshipService.flags(player);
            if (player != c.player() && (flags & mask) == 0 && !player.isDead) {
               double[] p = RenderAccess.position(player);
               double distance = RenderMath.distanceSquared(p, self);
               if (!(distance > (double)(radius * radius))) {
                  boolean target = this.flag("override_target_color") && player == GameHooks.target();
                  ColorSetting.Value color = target
                     ? this.color("color_target")
                     : (this.choice("color_mode") == 0 ? RenderAccess.nameColor(player) : this.color((flags & 5) != 0 ? "color_friendlies" : "color_enemies"));
                  float extra = RenderMath.max(0.0F, player.getAbsorptionAmount());
                  float total = player.getMaxHealth() + extra;
                  float max = RenderConstants.f(1378848);
                  float health = total == 0.0F ? 0.0F : RenderMath.clamp(0.0F, max, (player.getHealth() + extra) / total);
                  float absorb = player.getHealth() == 0.0F ? 0.0F : RenderMath.clamp(0.0F, max, extra / player.getHealth());
                  this.rows.add(new PlayerEspModule.Row(player, RenderAccess.previous(player), p, color, (float)distance, player.hurtTime, health, absorb, target));
                  if (this.rows.size() == 100) {
                     break;
                  }
               }
            }
         }

         Collections.sort(this.rows, (a, b) -> RenderMath.compareDistance(a.distance, b.distance));
      } else {
         this.clear(c);
      }
   }

   private List<PlayerEspGlowRenderer.Target> glowTargets(ClientContext c, SemanticEvent event) {
      float partial = event.timerRenderPartialTicks;
      // Construct ONLY during event 6: event 7 has already replaced the world projection.
      Frustum frustum = new Frustum();
      frustum.setPosition(event.cameraX, event.cameraY, event.cameraZ);
      List<PlayerEspGlowRenderer.Target> targets = new ArrayList<>();
      int mask = (this.flag("hide_friendlies") ? 5 : 0) + 2;
      double rangeSquared = (double)this.number("range") * this.number("range");
      // Existing rows are farthest-first. Recheck live filters because they can change between ticks.
      int limit = Math.min(PlayerEspGlowRenderer.MAX_TARGETS, (int)this.number("glow_players"));
      for (int i = this.rows.size() - 1; i >= 0 && targets.size() < limit; i--) {
         Row row = this.rows.get(i);
         EntityPlayer player = row.entity;
         if (player == null || player == c.player() || player == c.minecraft().getRenderViewEntity()
            || player.isDead || player.worldObj != c.world() || (RelationshipService.flags(player) & mask) != 0
            || player.getDistanceSqToEntity(c.player()) > rangeSquared) { continue; }
         double remaining = 1.0 - partial;
         if (!frustum.isBoundingBoxInFrustum(player.getEntityBoundingBox().offset(
            (player.prevPosX - player.posX) * remaining, (player.prevPosY - player.posY) * remaining,
            (player.prevPosZ - player.posZ) * remaining).expand(0.5, 0.5, 0.5))) continue;
         ColorSetting.Value base = row.color;
         if (this.choice("glow_colors") == 0) {
            int flags = RelationshipService.flags(player);
            if (this.flag("override_target_color") && player == GameHooks.target()) {
               base = this.color("color_target");
            } else if ((flags & 4) != 0) {
               base = this.color("color_friendlies");
            } else {
               ColorSetting.Value team = RenderAccess.nameColor(player);
               boolean colored = team.red != 1.0F || team.green != 1.0F || team.blue != 1.0F;
               base = colored ? team : this.color((flags & 1) != 0 ? "color_friendlies" : "color_enemies");
            }
         }
         targets.add(new PlayerEspGlowRenderer.Target(player, this.hurt(row, partial, true, base)));
      }
      return targets;
   }

   public String glowLimitation() { return this.glow.limitation(); }

   private strictfp ColorSetting.Value hurt(PlayerEspModule.Row row, float partial, boolean world) {
      return this.hurt(row, partial, world, row.color);
   }

   private ColorSetting.Value hurt(PlayerEspModule.Row row, float partial, boolean world, ColorSetting.Value base) {
      if (row.hurt > 0 && this.flag("hurt_effect")) {
         float t = RenderMath.min(RenderConstants.f(world ? 1378800 : 1378824), (float)row.hurt - partial) * RenderConstants.f(world ? 1378804 : 1378828);
         return RenderMath.blend(base, new ColorSetting.Value(RenderConstants.f(world ? 1378792 : 1378832), 0.0F, 0.0F), t);
      } else {
         return base;
      }
   }

   public strictfp DrawPlan screenPlan(RenderAccess.Frame frame, float partial) {
      return this.screenPlan(frame, partial, true);
   }

   private strictfp DrawPlan screenPlan(RenderAccess.Frame frame, float partial, boolean boxes) {
      DrawPlan plan = new DrawPlan().lineWidth(this.number("line_width"));

      for (PlayerEspModule.Row row : this.rows) {
         double[] relative = RenderMath.subtract(RenderMath.lerp(row.previous, row.position, (double)partial), frame.camera);
         float minx = 0.0F;
         float maxx = 0.0F;
         float miny = 0.0F;
         float maxy = 0.0F;
         boolean valid = true;

         for (int i = 0; i < 8; i++) {
            double[] corner = new double[]{
               RenderConstants.d(1378976 + i * 24), RenderConstants.d(1378984 + i * 24), i == 0 ? -0.4 : RenderConstants.d(1378992 + i * 24)
            };
            RenderMath.Projection p = frame.project(RenderMath.add(relative, corner));
            if (p == null) {
               valid = false;
               break;
            }

            if (i == 0) {
               maxx = p.x;
               minx = p.x;
               maxy = p.y;
               miny = p.y;
            } else {
               if (minx > p.x) {
                  minx = p.x;
               } else if (p.x > maxx) {
                  maxx = p.x;
               }

               if (miny > p.y) {
                  miny = p.y;
               } else if (p.y > maxy) {
                  maxy = p.y;
               }
            }
         }

         if (valid && RenderMath.visibleBounds(minx, miny, maxx, maxy, frame.width, frame.height)) {
            ColorSetting.Value color = this.hurt(row, partial, false);
            if (boxes && (this.flag("show_2d_box") || row.target)) {
               plan.push().translate(minx, miny, 0.0F).scale(maxx - minx, maxy - miny, RenderConstants.f(1378832));
               if (this.flag("filled")) {
                  plan.color(color, RenderConstants.f(1378836)).graphics(169120);
               }

               plan.color(color, 1.0F).graphics(169168).pop();
            }

            if (this.flag("show_health_bar")) {
               float x = minx - this.number("line_width") + RenderConstants.f(1378840) + RenderConstants.f(1378844);
               healthBar(plan, row, x, miny, maxy);
            }
         }
      }

      return plan;
   }

   public static strictfp void healthBar(DrawPlan p, PlayerEspModule.Row row, float x, float lower, float upper) {
      float middle = RenderMath.lerp(upper, lower, row.health);
      p.color(0.0F, 0.0F, 0.0F, 1.0F).line(x, lower, x, middle);
      if (row.absorption > 0.0F) {
         float split = RenderMath.lerp(middle, upper, row.absorption);
         p.color(RenderConstants.f(1378852), RenderConstants.f(1378856), 0.0F, 1.0F).line(x, middle, x, split);
         middle = split;
      }

      float peak = RenderConstants.f(1378852);
      p.color(RenderMath.lerp(peak, 0.0F, row.health), RenderMath.lerp(0.0F, peak, row.health), 0.0F, 1.0F).line(x, middle, x, upper);
   }

   private strictfp DrawPlan worldPlan(ClientContext c, SemanticEvent e) {
      DrawPlan p = new DrawPlan().lineWidth(this.number("line_width"));
      float sy = RenderConstants.f(1378792);
      float sx = RenderConstants.f(1378796);
      float yaw = Float.intBitsToFloat(Float.floatToRawIntBits(c.minecraft().getRenderViewEntity().rotationYaw) ^ RenderConstants.i(1378768));

      for (PlayerEspModule.Row row : this.rows) {
         double[] relative = RenderMath.subtract(
            RenderMath.lerp(row.previous, row.position, (double)e.timerRenderPartialTicks), new double[]{e.cameraX, e.cameraY, e.cameraZ}
         );
         p.push().translate((float)relative[0], (float)(relative[1] + RenderConstants.d(1378784)), (float)relative[2]).scale(sx, sy, sx);
         if (this.flag("show_2d_box") || row.target) {
            ColorSetting.Value color = this.hurt(row, e.timerRenderPartialTicks, true);
            if (this.flag("filled")) {
               p.color(color, RenderConstants.f(1378808)).graphics(168960);
            }

            p.color(color, 1.0F).graphics(169008);
         }

         if (this.flag("show_health_bar")) {
            p.rotate(yaw, 0.0F, sy, 0.0F);
            healthBar(p, row, RenderConstants.f(1378816), sy, RenderConstants.f(1378820));
         }

         p.pop();
      }

      return p;
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.clear(c);
   }

   @Override
   protected void onDisable(ClientContext c) {
      this.clear(c);
   }

   private void clear(ClientContext c) {
      this.rows.clear();
      this.releaseGlow(c);
   }

   private void releaseGlow(ClientContext c) {
      this.frameTargets = Collections.emptyList();
      this.silhouetteRendered = false;
      if (!this.glowActive) { return; }
      final PlayerEspGlowRenderer retired = this.glow;
      this.glow = new PlayerEspGlowRenderer();
      this.silhouetteRendered = false;
      this.glowActive = false;
      // Detach first: a queued cleanup must never delete a newly enabled renderer's resources.
      c.minecraft().addScheduledTask(new Runnable() {
         @Override public void run() { retired.close(); }
      });
   }

   @Override
   public strictfp String statusText() {
      if (this.choice("render_style") == 1) {
         return this.glow.limitation() == null ? "Glow" : "Glow (box fallback)";
      }
      if (!this.findSetting("mode").isConfigured()) {
         return null;
      } else {
         int index = this.choice("mode");
         return StatusText.label(this.getName(), index);
      }
   }

   public static final strictfp class Row {
      public final EntityPlayer entity;
      public final double[] previous;
      public final double[] position;
      public final ColorSetting.Value color;
      public final float distance;
      public final float health;
      public final float absorption;
      public final int hurt;
      public final boolean target;

      public Row(
         double[] previous, double[] position, ColorSetting.Value color, float distance, int hurt, float health, float absorption, boolean target
      ) {
         this(null, previous, position, color, distance, hurt, health, absorption, target);
      }

      private Row(EntityPlayer entity, double[] previous, double[] position, ColorSetting.Value color,
         float distance, int hurt, float health, float absorption, boolean target) {
         this.entity = entity;
         this.previous = (double[])previous.clone();
         this.position = (double[])position.clone();
         this.color = color;
         this.distance = distance;
         this.hurt = hurt;
         this.health = health;
         this.absorption = absorption;
         this.target = target;
      }
   }
}
