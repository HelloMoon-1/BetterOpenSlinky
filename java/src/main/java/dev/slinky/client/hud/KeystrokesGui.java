package dev.slinky.client.hud;

import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.RavenHudEditor;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.KeystrokesModule;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public final class KeystrokesGui extends RavenHudEditor {
   private final KeystrokesModule module;
   private GuiButton modButton, colorButton, mouseButton, outlineButton;
   private boolean dragging;
   private int lastX, lastY;

   public KeystrokesGui(ModuleManager manager, KeystrokesModule module) { super(manager); this.module = module; }

   @Override
   protected boolean editorDecoration() { return false; }

   @Override
   public void initGui() {
      super.initGui();
      this.modButton = new GuiButton(0, this.width / 2 - 70, this.height / 2 - 28, 140, 20, "Mod: " + (this.module.requestedEnabled() ? "Enabled" : "Disabled"));
      this.buttonList.add(this.modButton);
      this.colorButton = new GuiButton(1, this.width / 2 - 70, this.height / 2 - 6, 140, 20, "Text color: " + KeystrokesModule.COLOR_NAMES[this.module.colorIndex()]);
      this.buttonList.add(this.colorButton);
      this.mouseButton = new GuiButton(2, this.width / 2 - 70, this.height / 2 + 16, 140, 20, "Show mouse buttons: " + (this.module.mouseButtons() ? "On" : "Off"));
      this.buttonList.add(this.mouseButton);
      this.outlineButton = new GuiButton(3, this.width / 2 - 70, this.height / 2 + 38, 140, 20, "Outline: " + (this.module.outline() ? "On" : "Off"));
      this.buttonList.add(this.outlineButton);
   }

   @Override
   protected void drawEditor(int mx, int my, float partial) {
      RenderAccess.submit(new SemanticEvent(10), this.module.plan(true));
      super.drawEditor(mx, my, partial);
   }

   @Override
   protected void actionPerformed(GuiButton button) {
      if (button == this.modButton) {
         this.module.toggleEnabled();
         this.modButton.displayString = "Mod: " + (this.module.requestedEnabled() ? "Enabled" : "Disabled");
      } else if (button == this.colorButton) {
         this.module.cycleColor();
         this.colorButton.displayString = "Text color: " + KeystrokesModule.COLOR_NAMES[this.module.colorIndex()];
      } else if (button == this.mouseButton) {
         this.module.toggleMouseButtons();
         this.mouseButton.displayString = "Show mouse buttons: " + (this.module.mouseButtons() ? "On" : "Off");
      } else if (button == this.outlineButton) {
         this.module.toggleOutline();
         this.outlineButton.displayString = "Outline: " + (this.module.outline() ? "On" : "Off");
      }
   }

   @Override
   protected void mouseClicked(int mx, int my, int button) throws IOException {
      super.mouseClicked(mx, my, button);
      if (button == 0 || button == 1) KeystrokesClicks.onMouseButton(button, true);
      if (button == 0) {
         int x1 = this.module.x(), y1 = this.module.y();
         int x2 = x1 + 74, y2 = y1 + (this.module.mouseButtons() ? 74 : 50);
         if (mx >= x1 && mx <= x2 && my >= y1 && my <= y2) {
            this.dragging = true; this.lastX = mx; this.lastY = my;
         }
      }
   }

   @Override
   protected void mouseReleased(int mx, int my, int button) {
      super.mouseReleased(mx, my, button);
      if (button == 0 || button == 1) KeystrokesClicks.onMouseButton(button, false);
      this.dragging = false;
   }

   @Override
   protected void mouseClickMove(int mx, int my, int button, long time) {
      super.mouseClickMove(mx, my, button, time);
      if (!this.dragging) return;
      this.module.position(this.module.x() + mx - this.lastX, this.module.y() + my - this.lastY);
      this.lastX = mx; this.lastY = my;
   }

   @Override
   public void onGuiClosed() {
      this.manager.persist();
      super.onGuiClosed();
   }

   /** Register with ClientCommandHandler to retain the donor /keystrokesmod entry point. */
   public static final class Command extends CommandBase {
      private final KeystrokesModule module;
      public Command(KeystrokesModule module) { this.module = module; }
      @Override public String getCommandName() { return "keystrokesmod"; }
      @Override public String getCommandUsage(ICommandSender sender) { return "/keystrokesmod"; }
      @Override public int getRequiredPermissionLevel() { return 0; }
      @Override public void processCommand(ICommandSender sender, String[] args) { this.module.openEditor(); }
   }
}
