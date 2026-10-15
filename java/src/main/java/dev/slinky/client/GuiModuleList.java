package dev.slinky.client;

import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class GuiModuleList extends GuiScreen {
   private final ModuleManager modules;
   private ModuleCategory selected = ModuleCategory.COMBAT;

   public GuiModuleList(ModuleManager modules) {
      this.modules = modules;
   }

   public void initGui() {
      this.buttonList.clear();
      int categoryWidth = Math.max(62, this.width / ModuleCategory.values().length);
      int categoryId = 1000;

      for (ModuleCategory category : ModuleCategory.values()) {
         this.buttonList.add(new GuiButton(categoryId++, category.ordinal() * categoryWidth, 20, categoryWidth, 20, category.getDisplayName()));
      }

      List<Module> visible = this.modules.inCategory(this.selected);
      int y = 58;

      for (int index = 0; index < visible.size(); index++) {
         Module module = visible.get(index);
         String state = module.isEnabled() ? "ON  " : "OFF ";
         this.buttonList.add(new GuiButton(2000 + index, this.width / 2 - 120, y, 240, 20, state + module.getName()));
         y += 24;
      }
   }

   protected void actionPerformed(GuiButton button) throws IOException {
      if (button.id >= 1000 && button.id < 1000 + ModuleCategory.values().length) {
         this.selected = ModuleCategory.values()[button.id - 1000];
         this.initGui();
      } else {
         if (button.id >= 2000) {
            List<Module> visible = this.modules.inCategory(this.selected);
            int index = button.id - 2000;
            if (index >= 0 && index < visible.size()) {
               try {
                  this.modules.toggle(visible.get(index));
               } catch (IllegalArgumentException var5) {
                  this.mc.displayGuiScreen(new GuiModuleSettings(this.modules, visible.get(index)));
                  return;
               }

               this.initGui();
            }
         }
      }
   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      this.drawCenteredString(this.fontRendererObj, "Slinky Semantic 1.8.9", this.width / 2, 6, 16777215);
      super.drawScreen(mouseX, mouseY, partialTicks);
      this.drawCenteredString(this.fontRendererObj, "Right-click a module to edit its settings", this.width / 2, this.height - 18, 10526880);
   }

   public boolean doesGuiPauseGame() {
      return false;
   }

   protected void mouseClicked(int x, int y, int button) throws IOException {
      if (button == 1) {
         for (GuiButton entry : this.buttonList) {
            if (entry.id >= 2000 && entry.mousePressed(this.mc, x, y)) {
               int index = entry.id - 2000;
               this.mc.displayGuiScreen(new GuiModuleSettings(this.modules, this.modules.inCategory(this.selected).get(index)));
               return;
            }
         }
      }

      super.mouseClicked(x, y, button);
   }
}
