package dev.slinky.client.render;

import dev.slinky.client.SemanticEvent;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.ModOverlayModule;
import java.io.IOException;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

public final class HudPositionEditor extends RavenHudEditor {
   private static final String HELP = "Edit the HUD position by dragging.";
   private final String[] example = {"This is an", "example", "HUD"};
   private final ModOverlayModule hud;
   private int x;
   private int y;
   private int left;
   private int top;
   private int right;
   private int bottom;
   private boolean dragging;
   private int dragStartX;
   private int dragStartY;
   private int dragFromX;
   private int dragFromY;
   private GuiButton resetButton;
   private GuiButton alignButton;
   private ScreenshotModuleList.Layout screenshotPreview;
   private float previewAnchorX;
   private float previewAnchorY;
   private float emptyScale;

   public HudPositionEditor(ModuleManager manager, ModOverlayModule hud) {
      super(manager);
      this.hud = hud;
   }

   @Override
   public void initGui() {
      super.initGui();
      this.buttonList.clear();
      this.resetButton = new GuiButton(1, this.width - 90, this.height - 25, 85, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.alignButton = new GuiButton(2, this.width - 90, this.height - 47, 85, 20, "Align: " + (this.hud.alignRight() ? "Right" : "Left"));
      this.buttonList.add(this.alignButton);
      if (this.hud.screenshotStyle()) {
         this.alignButton.displayString = "Right (fixed)";
         this.alignButton.enabled = false;
      }
      this.dragging = false;
      this.screenshotPreview = null;
      this.x = this.hud.positionX();
      this.y = this.hud.positionY();
      this.left = this.x;
      this.top = this.y;
      this.initPos(this.x, this.y);
   }

   @Override
   protected boolean editorDecoration() { return !this.hud.screenshotStyle(); }

   @Override
   protected void drawEditor(int mx, int my, float partial) {
      if (this.hud.screenshotStyle()) {
         RenderAccess.submit(new SemanticEvent(10), new DrawPlan().color(0, 0, 0, 0.28F)
            .rect(0, 0, this.width, this.height, 0));
         this.drawScreenshot();
         // Screenshot preview, help and buttons all use the same smooth atlas, never per-char vanilla draws.
         this.drawScreenshotControls(mx, my);
         return;
      } else {
         super.drawEditor(mx, my, partial);
         this.drawExample();
         this.left = this.x;
         this.top = this.y;
         this.hud.position(this.left, this.top);
      }
      int tx = this.width / 2 - 84;
      int ty = this.height / 2 - 20;
      long offset = 0L;
      for (int i = 0; i < HELP.length(); i++) {
         char character = HELP.charAt(i);
         this.fontRendererObj.drawString(String.valueOf(character), tx, ty, OpenB4Theme.rainbow(2L, offset), true);
         tx += this.fontRendererObj.getCharWidth(character);
         if (character != ' ') { offset -= 90L; }
      }
   }

   private void drawScreenshotControls(int mx, int my) {
      DrawPlan plan = new DrawPlan().canvas();
      try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
         for (GuiButton button : this.buttonList) DripLiteStyle.button(plan, button, 85, mx, my);
         plan.canvasText("Drip Lite ArrayList - drag to move, Esc to save.", this.width / 2.0F, this.height / 2.0F,
            0xFFF2F2F5, DripLiteStyle.FONT, 18, 1);
      }
      RenderAccess.submit(new SemanticEvent(10), plan);
   }

   private void updateScreenshotPreview() {
      this.screenshotPreview = this.hud.screenshotPreview(this.width, this.height, this.x, this.y);
      if (!this.screenshotPreview.empty()) {
         this.left = (int)Math.floor(this.screenshotPreview.left);
         this.top = (int)Math.floor(this.screenshotPreview.top);
         this.right = (int)Math.ceil(this.screenshotPreview.right);
         this.bottom = (int)Math.ceil(this.screenshotPreview.bottom);
         this.previewAnchorX = this.screenshotPreview.anchorX;
         this.previewAnchorY = this.screenshotPreview.anchorY;
      } else {
         // Editor-only handle when there are no drawable rows; never invent enabled modules.
         this.emptyScale = Math.min(this.hud.hudScale(), Math.min(this.width / 72.0F, this.height / 23.0F));
         float s = this.emptyScale;
         float edge = Math.max(68.0F * s, Math.min(this.width - 4.0F * s, this.x + 4.0F * s));
         float panelTop = Math.max(4.0F * s, Math.min(this.height - 19.0F * s, this.y - 3.0F * s));
         this.left = (int)Math.floor(edge - 64.0F * s);
         this.right = (int)Math.ceil(edge);
         this.top = (int)Math.floor(panelTop);
         this.bottom = (int)Math.ceil(panelTop + 15.0F * s);
         this.previewAnchorX = edge - 4.0F * s;
         this.previewAnchorY = panelTop + 3.0F * s;
      }
   }

   private void drawScreenshot() {
      this.updateScreenshotPreview();
      RenderAccess.submit(new SemanticEvent(10), this.screenshotPreview.plan);
      if (this.screenshotPreview.empty()) {
         DrawPlan handle = new DrawPlan().canvas().color(0.75F, 0.45F, 0.8F, 0.8F)
            .roundedStroke(this.left, this.top, this.right - this.left, this.bottom - this.top, 3.0F * this.emptyScale);
         try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
            handle.push().translate(this.left, this.top, 0).scale(this.emptyScale, this.emptyScale, 1);
            handle.canvasText("Drag HUD", 4, 7.5F, DripLiteStyle.SECONDARY, 11.0F, 17, 1);
            handle.pop();
         }
         RenderAccess.submit(new SemanticEvent(10), handle);
      }
   }

   private boolean screenshotHit(int mx, int my) {
      return this.screenshotPreview.empty()
         ? mx >= this.left && mx <= this.right && my >= this.top && my <= this.bottom
         : this.screenshotPreview.contains(mx, my);
   }

   @Override
   protected void mouseClicked(int mx, int my, int button) throws IOException {
      super.mouseClicked(mx, my, button);
      if (!this.hud.screenshotStyle() || button != 0) { return; }
      // Buttons win over the preview, including the disabled fixed-alignment control.
      for (GuiButton control : this.buttonList) {
         // Both controls are created above at 85x20; MC 1.8.9 keeps their dimensions protected.
         if (control.visible && mx >= control.xPosition && mx < control.xPosition + 85
            && my >= control.yPosition && my < control.yPosition + 20) { return; }
      }
      this.updateScreenshotPreview();
      if (this.screenshotHit(mx, my)) {
         this.dragging = true;
         this.dragStartX = mx;
         this.dragStartY = my;
         // Begin at the fitted screen anchor, not a possibly off-screen saved position.
         this.dragFromX = Math.round(this.previewAnchorX);
         this.dragFromY = Math.round(this.previewAnchorY);
      }
   }

   private void drawExample() {
      FontRenderer font = this.mc.fontRendererObj;
      float scale = this.hud.hudScale();
      int height = Math.round((font.FONT_HEIGHT + 2) * scale);
      int width = 0;
      for (String line : this.example) { width = Math.max(width, Math.round(font.getStringWidth(line) * scale)); }
      this.right = this.left + width;
      this.bottom = this.top + height * this.example.length;
      float fx = this.left / scale;
      float fy = this.top / scale;
      GlStateManager.pushMatrix();
      try {
         GlStateManager.scale(scale, scale, 1.0F);
         double offset = 0.0;
         for (String line : this.example) {
            float lx = this.hud.alignRight() ? fx - font.getStringWidth(line) : fx;
            font.drawString(line, lx, fy, OpenB4Theme.getRGB(this.hud.theme(), offset), true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            offset -= OpenB4Theme.offset() * (this.hud.theme() == 1 ? 240.0 : 1.0);
            fy += font.FONT_HEIGHT + 2;
         }
      } finally {
         GlStateManager.popMatrix();
      }
   }

   @Override
   protected void mouseClickMove(int mx, int my, int button, long time) {
      super.mouseClickMove(mx, my, button, time);
      if (this.hud.screenshotStyle()) {
         if (button != 0 || !this.dragging || mx == this.dragStartX && my == this.dragStartY) { return; }
         this.x = this.dragFromX + mx - this.dragStartX;
         this.y = this.dragFromY + my - this.dragStartY;
         this.updateScreenshotPreview();
         this.x = Math.round(this.previewAnchorX);
         this.y = Math.round(this.previewAnchorY);
         this.hud.position(this.x, this.y);
         // Rebase at bounds so reversing direction responds immediately without an overshoot dead zone.
         this.dragFromX = this.x;
         this.dragFromY = this.y;
         this.dragStartX = mx;
         this.dragStartY = my;
         return;
      }
      if (this.hud.alignRight()) { mx += 50; }
      if (button != 0) { return; }
      if (this.dragging) {
         this.x = this.dragFromX + mx - this.dragStartX;
         this.y = this.dragFromY + my - this.dragStartY;
      } else if (mx > this.left && mx < this.right && my > this.top && my < this.bottom) {
         this.dragging = true;
         this.dragStartX = mx;
         this.dragStartY = my;
         this.dragFromX = this.x;
         this.dragFromY = this.y;
      }
   }

   @Override
   protected void mouseReleased(int mx, int my, int button) {
      super.mouseReleased(mx, my, button);
      if (button == 0) { this.dragging = false; }
   }

   @Override
   protected void actionPerformed(GuiButton button) throws IOException {
      if (button == this.resetButton) {
         this.dragging = false;
         if (this.hud.screenshotStyle()) {
            this.hud.resetScreenshotPosition();
            this.x = this.hud.positionX();
            this.y = this.hud.positionY();
            this.updateScreenshotPreview();
            this.manager.persist();
         } else {
            this.x = this.hud.alignRight() ? this.width - 5 : 5;
            this.y = 70;
            this.hud.position(this.x, this.y);
         }
      } else if (button == this.alignButton) {
         this.hud.toggleAlignment();
         this.alignButton.displayString = "Align: " + (this.hud.alignRight() ? "Right" : "Left");
         this.manager.persist();
      }
   }

   @Override
   public void onGuiClosed() {
      this.hud.position(this.x, this.y);
      this.closed(this.x, this.y);
      super.onGuiClosed();
   }
}
