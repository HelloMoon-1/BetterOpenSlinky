package dev.slinky.client.hud;

import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.OpenB4Theme;
import dev.slinky.client.render.RavenHudEditor;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.InfoHudModule;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;

public final class InfoHudEditor extends RavenHudEditor {
   private static final String HELP = "Edit the HUD position by dragging.";
   private final String[] example = {"This is an", "example", "HUD"};
   private final InfoHudModule module;
   private GuiButton resetButton;
   private int x, y, dragStartX, dragStartY, dragFromX, dragFromY, left, right, top, bottom;
   private boolean dragging;

   public InfoHudEditor(ModuleManager manager, InfoHudModule module) {
      super(manager);
      this.module = module;
   }

   @Override
   public void initGui() {
      super.initGui();
      this.resetButton = new GuiButton(1, this.width - 90, this.height - 25, 85, 20, "Reset position");
      this.buttonList.add(this.resetButton);
      this.x = this.module.x();
      this.y = this.module.y();
      this.initPos(this.x, this.y);
   }

   @Override
   protected void drawEditor(int mx, int my, float partial) {
      super.drawEditor(mx, my, partial);
      FontRenderer fr = this.mc.fontRendererObj;
      int w = 0;
      for (String s : this.example) w = Math.max(w, fr.getStringWidth(s));
      int x1 = this.x - 5, x2 = this.x + w + 5;
      int y1 = this.y - 5, y2 = this.y + (this.example.length - 1) * 12 + fr.FONT_HEIGHT + 5;
      this.left = x1; this.top = y1; this.right = x2; this.bottom = y2;
      int background = RavenRender.background();
      if (background == 2) {
         int screen = new ScaledResolution(this.mc).getScaledWidth();
         if (x1 <= 0) x1 = -3;
         if (x2 > screen) x2 = screen + 3;
      }
      DrawPlan p = new DrawPlan();
      InfoHud.background(p, x1, y1, x2 - 0.5F, y2 - 1.5F, background);
      int oy = 0;
      for (String s : this.example) {
         p.text(s, this.x, this.y + oy, -1, background == 0, fr.FONT_HEIGHT);
         oy += 12;
      }
      this.module.position(this.x, this.y);
      rainbowText(p, HELP, '-', this.width / 2 - 84, this.height / 2 - 20, 2L, 0L, true, fr);
      RenderAccess.submit(new SemanticEvent(10), p);
   }

   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.resetButton) {
         this.module.resetPosition();
         this.x = this.module.x(); this.y = this.module.y();
      }
   }

   @Override
   protected void mouseClickMove(int mx, int my, int button, long time) {
      super.mouseClickMove(mx, my, button, time);
      if (button != 0) return;
      if (this.dragging) {
         this.x = this.dragFromX + mx - this.dragStartX;
         this.y = this.dragFromY + my - this.dragStartY;
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
   public void onGuiClosed() {
      this.module.position(this.x, this.y);
      this.closed(this.x, this.y);
      super.onGuiClosed();
   }

   public static void rainbowText(DrawPlan p, String text, char newline, int x, int y, long speed, long lineOffset, boolean shadow, FontRenderer fr) {
      int startX = x, line = 0;
      long offset = 0L;
      for (int i = 0; i < text.length(); ++i) {
         char c = text.charAt(i);
         if (c == newline) {
            x = startX;
            y += fr.FONT_HEIGHT + 5;
            offset = lineOffset * ++line;
            continue;
         }
         p.text(String.valueOf(c), x, y, OpenB4Theme.rainbow(speed, offset), shadow, fr.FONT_HEIGHT);
         x += fr.getCharWidth(c);
         if (c != ' ') offset -= 90L;
      }
   }
}
