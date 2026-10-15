package dev.slinky.client.hud;

import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RavenHudEditor;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.SprintModule;
import net.minecraft.client.gui.GuiButton;

public final class SprintTextEditor extends RavenHudEditor {
   private static final String HELP = "Edit the HUD position by dragging.";
   private final SprintModule module;
   private int x, y, x2, y2, targetX = 5, targetY = 5;
   private int dragStartX, dragStartY, dragFromX, dragFromY;
   private boolean dragging;
   private GuiButton resetButton;

   public SprintTextEditor(ModuleManager manager, SprintModule module) { super(manager); this.module = module; }

   @Override
   public void initGui() {
      super.initGui();
      this.resetButton = new GuiButton(1, this.width - 90, this.height - 25, 85, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.x = this.targetX = this.module.textX(); this.y = this.targetY = this.module.textY();
      this.initPos(this.targetX, this.targetY);
   }

   @Override
   protected void drawEditor(int mx, int my, float partial) {
      super.drawEditor(mx, my, partial);
      this.x = this.targetX; this.y = this.targetY;
      this.x2 = this.x + this.mc.fontRendererObj.getStringWidth(this.module.text());
      this.y2 = this.y + this.mc.fontRendererObj.FONT_HEIGHT;
      DrawPlan p = new DrawPlan().text(this.module.text(), this.x, this.y, -1, true, this.mc.fontRendererObj.FONT_HEIGHT);
      this.module.textPosition(this.x, this.y);
      InfoHudEditor.rainbowText(p, HELP, '-', this.width / 2 - 84, this.height / 2 - 20, 2L, 0L, true, this.mc.fontRendererObj);
      RenderAccess.submit(new SemanticEvent(10), p);
   }

   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.resetButton) {
         this.targetX = 5; this.targetY = 5; this.module.textPosition(5, 5);
      }
   }

   @Override
   protected void mouseReleased(int mx, int my, int button) {
      super.mouseReleased(mx, my, button);
      if (button == 0) this.dragging = false;
   }

   @Override
   protected void mouseClickMove(int mx, int my, int button, long time) {
      super.mouseClickMove(mx, my, button, time);
      if (button != 0) return;
      if (this.dragging) {
         this.targetX = this.dragFromX + mx - this.dragStartX; this.targetY = this.dragFromY + my - this.dragStartY;
      } else if (mx > this.x && mx < this.x2 && my > this.y && my < this.y2) {
         this.dragging = true;
         this.dragStartX = mx; this.dragStartY = my; this.dragFromX = this.targetX; this.dragFromY = this.targetY;
      }
   }

   @Override
   public void onGuiClosed() {
      this.module.textPosition(this.targetX, this.targetY);
      this.closed(this.targetX, this.targetY);
      super.onGuiClosed();
   }
}
