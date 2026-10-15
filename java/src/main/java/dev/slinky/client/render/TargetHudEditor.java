package dev.slinky.client.render;

import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.TargetHudModule;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.EntityLivingBase;

/** OpenB4 TargetHudEditor: the same help line, drag bounds and reset button. */
public final class TargetHudEditor extends RavenHudEditor {
   private static final String HELP = "Edit the HUD position by dragging.";
   private final TargetHudModule hud;
   private String text = "";
   private double ratio;
   private boolean dragging;
   private int dragStartX;
   private int dragStartY;
   private int dragFromX;
   private int dragFromY;
   private GuiButton resetButton;

   public TargetHudEditor(ModuleManager manager, TargetHudModule hud) {
      super(manager);
      this.hud = hud;
   }

   @Override
   public void initGui() {
      super.initGui();
      this.buttonList.clear();
      this.resetButton = new GuiButton(1, this.width - 90, this.height - 25, 85, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.initPos(this.hud.offsetX(), this.hud.offsetY());
      this.updatePreview();
   }

   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.resetButton) {
         this.hud.resetPosition();
      }
   }

   @Override
   public void updateScreen() {
      this.updatePreview();
      super.updateScreen();
   }

   private void updatePreview() {
      EntityLivingBase entity = this.hud.previewEntity(this.mc);
      if (entity == null) {
         this.text = "";
         this.ratio = 0.0;
         return;
      }
      this.ratio = entity.getMaxHealth() > 0.0F ? Math.min(entity.getHealth() / entity.getMaxHealth(), 1.0F) : 0.0;
      this.text = this.hud.text(this.mc, entity.getDisplayName().getUnformattedText(), entity.getHealth(), this.ratio);
   }

   @Override
   protected void drawEditor(int mouseX, int mouseY, float partial) {
      super.drawEditor(mouseX, mouseY, partial);
      // Donor Mode.Disabled still previews Modern in its position editor.
      this.hud.renderText(this.mc, this.text, this.ratio, 255);
      int x = this.width / 2 - 84;
      int y = this.height / 2 - 20;
      long offset = 0L;
      net.minecraft.client.renderer.GlStateManager.enableTexture2D();
      for (int i = 0; i < HELP.length(); i++) {
         char character = HELP.charAt(i);
         this.mc.fontRendererObj.drawString(String.valueOf(character), x, y, OpenB4Theme.rainbow(2L, offset), true);
         x += this.mc.fontRendererObj.getCharWidth(character);
         if (character != ' ') {
            offset -= 90L;
         }
      }
   }

   @Override
   protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
      super.mouseClickMove(mouseX, mouseY, button, time);
      if (button != 0) {
         return;
      }
      int width = this.mc.fontRendererObj.getStringWidth(this.text) + 8;
      int x = this.width / 2 - width / 2 + this.hud.offsetX();
      int y = this.height / 2 + this.hud.offsetY();
      int left = x - 8, top = y - 8, right = x + width;
      int bottom = y + this.mc.fontRendererObj.FONT_HEIGHT + 12 + 8;
      if (this.dragging) {
         this.hud.position(this.dragFromX + mouseX - this.dragStartX, this.dragFromY + mouseY - this.dragStartY);
      } else if (mouseX > left && mouseX < right && mouseY > top && mouseY < bottom) {
         this.dragging = true;
         this.dragStartX = mouseX;
         this.dragStartY = mouseY;
         this.dragFromX = this.hud.offsetX();
         this.dragFromY = this.hud.offsetY();
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      super.mouseReleased(mouseX, mouseY, button);
      if (button == 0) {
         this.dragging = false;
      }
   }

   @Override
   public void onGuiClosed() {
      try {
         this.closed(this.hud.offsetX(), this.hud.offsetY());
         this.hud.releaseResources();
      } finally {
         super.onGuiClosed();
      }
   }
}
