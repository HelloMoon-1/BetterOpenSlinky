package dev.slinky.client.hud;

import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RavenHudEditor;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.BridgeInfoModule;
import net.minecraft.client.gui.GuiButton;

public final class BridgeInfoEditor extends RavenHudEditor {
   private static final String HELP = "Edit the HUD position by dragging.";
   private static final String EXAMPLE = "Enemy: Player123-Distance to goal: 17.2-Enemy distance to goal: 16.3-Blocks: 98";
   private final BridgeInfoModule module;
   private int x = 5, y = 70, left, top, right, bottom, dragStartX, dragStartY, dragFromX, dragFromY;
   private boolean dragging;
   private GuiButton resetButton;

   public BridgeInfoEditor(ModuleManager manager, BridgeInfoModule module) {
      super(manager); this.module = module;
   }
   @Override
   public void initGui() {
      super.initGui();
      this.resetButton = new GuiButton(1, this.width - 90, 5, 85, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.x = this.module.x(); this.y = this.module.y();
      this.initPos(this.x, this.y);
   }
   @Override
   protected void drawEditor(int mx, int my, float partial) {
      super.drawEditor(mx, my, partial);
      DrawPlan p = new DrawPlan();
      int ty = this.y;
      for (String line : EXAMPLE.split("-")) {
         p.text(line, this.x, ty, BridgeInfoModule.COLOR, true, this.mc.fontRendererObj.FONT_HEIGHT);
         ty += this.mc.fontRendererObj.FONT_HEIGHT + 2;
      }
      this.left = this.x; this.top = this.y; this.right = this.x + 140; this.bottom = this.y + 41;
      this.module.position(this.x, this.y);
      InfoHudEditor.rainbowText(p, HELP, '-', this.width / 2 - 84, this.height / 2 - 20, 2L, 0L, true, this.mc.fontRendererObj);
      RenderAccess.submit(new SemanticEvent(10), p);
   }
   @Override
   protected void mouseClickMove(int mx, int my, int button, long time) {
      super.mouseClickMove(mx, my, button, time);
      if (button != 0) return;
      if (this.dragging) {
         this.x = this.dragFromX + mx - this.dragStartX; this.y = this.dragFromY + my - this.dragStartY;
      } else if (mx > this.left && mx < this.right && my > this.top && my < this.bottom) {
         this.dragging = true;
         this.dragStartX = mx; this.dragStartY = my; this.dragFromX = this.x; this.dragFromY = this.y;
      }
   }
   @Override
   protected void mouseReleased(int mx, int my, int button) {
      super.mouseReleased(mx, my, button);
      if (button == 0) this.dragging = false;
   }
   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.resetButton) { this.x = 5; this.y = 70; this.module.position(this.x, this.y); }
   }
   @Override
   public void onGuiClosed() {
      this.module.position(this.x, this.y); this.closed(this.x, this.y); super.onGuiClosed();
   }
}
