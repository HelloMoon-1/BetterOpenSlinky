package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hook.GlStateScope;
import dev.slinky.client.render.HudCombatTarget;
import dev.slinky.client.render.OpenB4Theme;
import dev.slinky.client.render.RavenHudDrawing;
import dev.slinky.client.render.RavenHudEffects;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.client.render.TargetHudEditor;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;

/** OpenB4's actual text/bar TargetHUD; no invented portrait/equipment layout. */
public final class TargetHudModule extends Module {
   public enum Mode { Disabled, Modern, Legacy }

   public final ChoiceSetting mode;
   public final ChoiceSetting theme;
   public final BooleanSetting editPosition;
   public final BooleanSetting renderEsp;
   public final BooleanSetting showDifference;
   public final BooleanSetting showHealth;
   public final BooleanSetting showWinLoss;
   public final BooleanSetting traditionalHealthColor;
   private final NumberSetting positionX;
   private final NumberSetting positionY;
   private final ModuleManager manager;
   private final RavenHudEffects effects = new RavenHudEffects();
   private double shown;
   private double target = -1.0;
   private double from = -1.0;
   private long changedAt = -1L;
   private boolean espSuppressed;

   public TargetHudModule(ModuleManager manager) {
      super("target_hud", "TargetHUD", "OpenB4 target HUD for the last locally attacked entity.", ModuleCategory.RENDER);
      this.manager = manager;
      this.mode = this.option(new ChoiceSetting("mode", "Mode", 1, "Disabled", "Modern", "Legacy"));
      this.theme = this.option(new ChoiceSetting("theme", "Theme", 0, OpenB4Theme.NAMES));
      this.editPosition = this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.renderEsp = this.option(new BooleanSetting("render_esp", "Render ESP", false));
      this.showDifference = this.option(new BooleanSetting("show_difference", "Show difference", false));
      this.showHealth = this.option(new BooleanSetting("show_health", "Show health", true));
      this.showWinLoss = this.option(new BooleanSetting("show_win_loss", "Show win/loss", true));
      this.traditionalHealthColor = this.option(new BooleanSetting("traditional_health_color", "Traditional health color", false));
      // Donor Profile.posX/posY become hidden persisted settings, not new visible controls.
      this.positionX = this.option(new NumberSetting("position_x", "Position X", 70, -1000000, 1000000, 1));
      this.positionY = this.option(new NumberSetting("position_y", "Position Y", 40, -1000000, 1000000, 1));
   }

   private <V, S extends Setting<V>> S option(S setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      return this.add(setting);
   }

   @Override
   public int metadataCategory() { return 2; }

   @Override
   public int descriptorFlags() { return 0; }

   @Override
   public boolean settingVisible(String key) {
      return !"position_x".equals(key) && !"position_y".equals(key);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if ("edit_position".equals(key) && this.editPosition.isEnabled()) {
         this.editPosition.set(false);
         this.openEditor();
         return true;
      }
      return false;
   }

   public void openEditor() {
      GuiModule gui = (GuiModule)this.manager.find("gui");
      TargetHudEditor editor = new TargetHudEditor(this.manager, this);
      if (gui != null) {
         gui.openEditor(editor);
      } else {
         this.manager.context().minecraft().displayGuiScreen(editor);
      }
   }

   public int offsetX() { return this.positionX.intValue(); }
   public int offsetY() { return this.positionY.intValue(); }

   public void position(int x, int y) {
      this.positionX.set((double)Math.max(-1000000, Math.min(1000000, x)));
      this.positionY.set((double)Math.max(-1000000, Math.min(1000000, y)));
   }

   public void resetPosition() {
      this.position(70, 40);
   }

   /** Optional runtime adapter for an independently rendered backtrack ghost. */
   public void setEspSuppressed(boolean value) {
      this.espSuppressed = value;
   }

   @Override
   public boolean listens(int type) {
      return type == 6 || type == 9 || type == 10 || type == 26;
   }

   @Override
   public void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 26) {
         this.releaseResources();
      } else if (event.type == 9) {
         RenderEventPayload payload = RenderEventPayload.get(event);
         if (payload != null) {
            // Cached frames must also be cleared when an overlay is hidden or a target expires.
            payload.redrawFlags |= 3;
         }
      } else if (event.type == 6) {
         this.renderEsp(context.minecraft(), event);
      } else if (event.type == 10 && this.mode.index() != Mode.Disabled.ordinal() && RavenRender.visible(context.minecraft())) {
         HudCombatTarget.Snapshot snapshot = HudCombatTarget.snapshot(context.minecraft());
         if (snapshot != null) {
            try (RavenRender.Scope ignored = RavenRender.begin()) {
               this.renderText(context.minecraft(), this.text(context.minecraft(), snapshot.name, snapshot.health, snapshot.ratio),
                  snapshot.ratio, snapshot.alpha);
            }
         }
      }
   }

   public String text(Minecraft game, String name, float health, double ratio) {
      StringBuilder text = new StringBuilder(name == null ? "" : name);
      if (this.showHealth.isEnabled()) {
         text.append(' ').append(health(ratio, health));
      }
      float own = game.thePlayer == null ? 0.0F : game.thePlayer.getHealth() + game.thePlayer.getAbsorptionAmount();
      if (this.showDifference.isEnabled()) {
         text.append(' ').append(healthDifference(own - health, !this.showHealth.isEnabled()));
      }
      if (this.showWinLoss.isEnabled() && game.thePlayer != null) {
         boolean win = ratio <= own / game.thePlayer.getMaxHealth();
         text.append(' ').append('§').append(win ? "aW" : "cL");
      }
      String result = text.toString();
      int length = result.length();
      if (length < 14) {
         String padding = "          ".substring(0, (16 - length + 1) / 2);
         result = padding + result + padding;
      }
      return result;
   }

   private static String health(double ratio, double value) {
      String prefix = ratio < 0.3 ? "§c" : ratio < 0.5 ? "§6" : ratio < 0.7 ? "§e" : "§a";
      if (RavenRender.healthAsHearts()) {
         value /= 2.0;
      }
      return prefix + number(value) + (RavenRender.heartSymbol() ? "§c❤" : "");
   }

   private static String healthDifference(float difference, boolean heart) {
      String prefix = difference >= 8.0F ? "§2" : difference > 2.0F ? "§a" : difference > -2.0F ? "§e"
         : difference > -6.0F ? "§6" : "§c";
      if (RavenRender.healthAsHearts()) {
         difference /= 2.0F;
      }
      return prefix + (difference >= 0.0F ? "+" : "") + number(difference)
         + (heart && RavenRender.heartSymbol() ? "§c❤" : "");
   }

   private static String number(double value) {
      double rounded = Math.round(value * 10.0) / 10.0;
      return rounded == Math.floor(rounded) ? Long.toString((long)rounded) : Double.toString(rounded);
   }

   private static int healthColor(double ratio) {
      return ratio < 0.3 ? -55770 : ratio < 0.5 ? -12250 : ratio < 0.7 ? -218 : -14221530;
   }

   /** Donor editor uses raw health (not absorption) and the attacked living entity or self. */
   public EntityLivingBase previewEntity(Minecraft game) {
      HudCombatTarget.Snapshot snapshot = HudCombatTarget.snapshot(game);
      return snapshot != null && snapshot.entity instanceof EntityLivingBase ? (EntityLivingBase)snapshot.entity : game.thePlayer;
   }

   /** Called inside RavenRender.begin(), including by the native-coordinate editor. */
   public void renderText(Minecraft game, String text, double ratio, int alpha) {
      if (alpha <= 0 || game.fontRendererObj == null) {
         return;
      }
      ratio = Double.isFinite(ratio) ? Math.max(0.0, Math.min(1.0, ratio)) : 0.0;
      ScaledResolution resolution = new ScaledResolution(game);
      int width = game.fontRendererObj.getStringWidth(text) + 8;
      int x = resolution.getScaledWidth() / 2 - width / 2 + this.offsetX();
      int y = resolution.getScaledHeight() / 2 + this.offsetY();
      int left = x - 8, top = y - 8, right = x + width;
      int barY = y + game.fontRendererObj.FONT_HEIGHT - 1 + 8;
      int backgroundAlpha = Math.min(alpha, 100), barAlpha = Math.min(alpha, 210);
      int[] colors = OpenB4Theme.getRGBArr(this.theme.index());
      int barLeft = left + 6, barRight = right - 6;
      boolean legacy = this.mode.index() == Mode.Legacy.ordinal();
      boolean falling = this.target < this.from;
      this.animate(ratio, legacy);
      double displayed = Math.max(0.0, Math.min(1.0, this.shown));
      int first, second, trailFirst, trailSecond;
      if (this.traditionalHealthColor.isEnabled()) {
         int color = healthColor(displayed);
         first = second = RavenHudDrawing.alpha(color, barAlpha);
         trailFirst = trailSecond = RavenHudDrawing.alpha(color, backgroundAlpha);
      } else {
         double front = legacy || !falling ? displayed : this.target;
         first = RavenHudDrawing.alpha(colors[0], barAlpha);
         second = RavenHudDrawing.alpha(RavenHudDrawing.blend(colors[0], colors[1], 1.0 - front), barAlpha);
         trailFirst = RavenHudDrawing.alpha(colors[0], backgroundAlpha);
         trailSecond = RavenHudDrawing.alpha(RavenHudDrawing.blend(colors[0], colors[1], 1.0 - displayed), backgroundAlpha);
      }
      if (legacy) {
         RavenHudDrawing.roundedBorder(left, top, right, barY + 13, 10.0F,
            RavenHudDrawing.alpha(0, backgroundAlpha), RavenHudDrawing.alpha(colors[0], alpha), RavenHudDrawing.alpha(colors[1], alpha));
      } else {
         this.effects.render(game, left, top, right, barY + 13, barAlpha);
      }
      if (displayed < 1.0) {
         RavenHudDrawing.rounded(barLeft, barY, barRight, barY + 5, 4.0F, RavenHudDrawing.alpha(0, backgroundAlpha));
      }
      if (legacy || !falling) {
         RavenHudDrawing.roundedGradient(barLeft, barY, (float)(barLeft + (barRight - barLeft) * displayed), barY + 5, 4.0F, first, second);
      } else {
         if (this.target < 1.0) {
            RavenHudDrawing.roundedGradient(barLeft, barY, (float)(barLeft + (barRight - barLeft) * displayed), barY + 5, 4.0F, trailFirst, trailSecond);
         }
         RavenHudDrawing.roundedGradient(barLeft, barY, (float)(barLeft + (barRight - barLeft) * this.target), barY + 5, 4.0F, first, second);
      }
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
      game.fontRendererObj.drawString(text, x, y, RavenHudDrawing.alpha(0xDCDCDC, Math.max(4, alpha)), true);
      GlStateManager.resetColor();
   }

   private void animate(double ratio, boolean legacy) {
      if (this.target == -1.0) {
         this.target = ratio;
         return;
      }
      long now = System.currentTimeMillis();
      long age = now - this.changedAt;
      long duration = legacy ? 125L : this.target < this.from ? 800L : 500L;
      if (ratio != this.target) {
         this.from = age > duration ? this.target : this.shown;
         this.target = ratio;
         this.changedAt = now;
      } else if (age <= duration) {
         double time = Math.min(1.0, (double)age / duration);
         if (!legacy) {
            time = 1.0F - (float)Math.pow(1.0 - time, 5.0);
         }
         this.shown = this.from + (this.target - this.from) * time;
      } else {
         this.shown = ratio;
      }
   }

   private void renderEsp(Minecraft game, SemanticEvent event) {
      if (!this.renderEsp.isEnabled() || this.espSuppressed || game.theWorld == null || game.thePlayer == null) {
         return;
      }
      HudCombatTarget.Snapshot snapshot = HudCombatTarget.snapshot(game);
      if (snapshot == null || !(snapshot.entity instanceof EntityLivingBase)) {
         return;
      }
      Entity entity = snapshot.entity;
      double partial = event.timerRenderPartialTicks;
      double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial - event.cameraX;
      double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial - event.cameraY;
      double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial - event.cameraZ;
      AxisAlignedBB box = entity.getEntityBoundingBox().expand(0.1, 0.1, 0.1).offset(x - entity.posX, y - entity.posY, z - entity.posZ);
      try (GlStateScope ignored = GlStateScope.open()) {
         GlStateManager.disableDepth();
         GlStateManager.depthMask(false);
         RavenHudDrawing.filledBox(box, OpenB4Theme.getRGB(this.theme.index(), 0.0), 0.37F);
      }
   }

   public void releaseResources() {
      this.effects.close();
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.changedAt = -1L;
      this.from = this.target = -1.0;
      this.releaseResources();
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      HudCombatTarget.clear();
      this.espSuppressed = false;
      this.changedAt = -1L;
      this.from = this.target = -1.0;
      this.releaseResources();
   }
}
