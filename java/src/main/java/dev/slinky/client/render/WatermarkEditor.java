package dev.slinky.client.render;

import dev.slinky.client.SemanticEvent;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.WatermarkModule;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;

/** Native GuiFrame editor: previews the real watermark even while its module is disabled. */
public final class WatermarkEditor extends RavenHudEditor {
   private final WatermarkModule module;
   private GuiButton resetButton;
   private int x, y, grabX, grabY;
   private boolean dragging;

   public WatermarkEditor(ModuleManager manager, WatermarkModule module) {
      super(manager);
      this.module = module;
   }

   @Override
   public void initGui() {
      super.initGui();
      this.buttonList.clear();
      this.resetButton = new GuiButton(1, this.width - 95, this.height - 25, 90, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.x = this.module.clampX(this.module.x(), this.width);
      this.y = this.module.clampY(this.module.y(), this.height);
      this.initPos(this.module.x(), this.module.y());
      this.dragging = false;
   }

   @Override
   protected boolean editorDecoration() { return false; }

   @Override
   protected void drawEditor(int mouseX, int mouseY, float partial) {
      RenderAccess.submit(new SemanticEvent(10), new DrawPlan().color(0, 0, 0, 0.28F).rect(0, 0, this.width, this.height, 0));
      RenderAccess.submit(new SemanticEvent(10), WatermarkRenderer.plan(
         this.x, this.y, this.module.scale(), this.module.backgroundOpacity()));
      DrawPlan p = new DrawPlan().canvas();
      try (CanvasFonts.GuiFaceScope face = DripLiteStyle.face()) {
         DripLiteStyle.button(p, this.resetButton, 90, mouseX, mouseY);
         p.canvasText("Drip Lite Watermark - drag to move, Esc to save.", this.width / 2.0F,
            this.height / 2.0F, 0xFFF2F2F5, DripLiteStyle.FONT, 18, 1);
      }
      RenderAccess.submit(new SemanticEvent(10), p);
   }

   @Override
   protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.resetButton.visible && mouseX >= this.resetButton.xPosition && mouseX < this.resetButton.xPosition + 90
         && mouseY >= this.resetButton.yPosition && mouseY < this.resetButton.yPosition + 20) return;
      if (button == 0 && mouseX >= this.x && mouseX < this.x + this.module.width()
         && mouseY >= this.y && mouseY < this.y + this.module.height()) {
         this.dragging = true;
         this.grabX = mouseX - this.x;
         this.grabY = mouseY - this.y;
      }
   }

   @Override
   protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
      super.mouseClickMove(mouseX, mouseY, button, elapsed);
      if (button == 0 && this.dragging) {
         this.x = this.module.clampX(mouseX - this.grabX, this.width);
         this.y = this.module.clampY(mouseY - this.grabY, this.height);
         this.module.position(this.x, this.y);
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      super.mouseReleased(mouseX, mouseY, button);
      if (button == 0) this.dragging = false;
   }

   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.resetButton) {
         this.module.resetPosition();
         this.x = this.module.clampX(this.module.x(), this.width);
         this.y = this.module.clampY(this.module.y(), this.height);
         this.module.position(this.x, this.y);
         this.dragging = false;
      }
   }

   @Override
   public void onGuiClosed() {
      this.module.position(this.x, this.y);
      this.closed(this.x, this.y);
      super.onGuiClosed();
   }
}
