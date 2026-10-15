package dev.slinky.client;

import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public final class GuiModuleSettings extends GuiScreen {
   private final ModuleManager modules;
   private final Module module;
   private GuiTextField value;
   private int page;
   private int selected = -1;
   private String message = "";

   public GuiModuleSettings(ModuleManager modules, Module module) {
      this.modules = modules;
      this.module = module;
   }

   public void initGui() {
      this.buttonList.clear();
      List<Setting<?>> visible = this.module.visibleSettings();
      int rows = Math.max(1, (this.height - 120) / 24);
      this.page = Math.max(0, Math.min(this.page, Math.max(0, (visible.size() - 1) / rows)));

      for (int i = this.page * rows; i < Math.min(visible.size(), (this.page + 1) * rows); i++) {
         Setting<?> setting = visible.get(i);
         this.buttonList
            .add(
               new GuiButton(
                  100 + i,
                  this.width / 2 - 170,
                  35 + (i - this.page * rows) * 24,
                  340,
                  20,
                  setting.getLabel() + " = " + (setting.isConfigured() ? setting.format() : "Choose a value")
               )
            );
      }

      this.value = new GuiTextField(1, this.fontRendererObj, this.width / 2 - 170, this.height - 64, 270, 20);
      this.value.setMaxStringLength(8192);
      if (this.selected >= 0 && this.selected < visible.size()) {
         this.value.setText(visible.get(this.selected).format());
      }

      this.buttonList.add(new GuiButton(1, this.width / 2 + 104, this.height - 65, 65, 20, "Save"));
      this.buttonList.add(new GuiButton(2, this.width / 2 - 170, this.height - 36, 60, 20, "Back"));
      this.buttonList.add(new GuiButton(3, this.width / 2 + 40, this.height - 36, 60, 20, "Previous"));
      this.buttonList.add(new GuiButton(4, this.width / 2 + 110, this.height - 36, 60, 20, "Next"));
   }

   protected void actionPerformed(GuiButton button) throws IOException {
      if (button.id >= 100) {
         this.selected = button.id - 100;
         this.value.setText(this.module.visibleSettings().get(this.selected).format());
         this.value.setFocused(true);
      } else if (button.id == 1 && this.selected >= 0) {
         try {
            Setting<?> edited = this.module.visibleSettings().get(this.selected);
            edited.parseAndSet(this.value.getText());
            this.module.afterSettingControl(edited.getKey(), this.modules.context());
            this.modules.persist();
            this.message = "Saved";
            this.initGui();
            this.modules.configurationSession().controlChanged();
         } catch (IllegalArgumentException var3) {
            this.message = var3.getMessage();
         }
      } else if (button.id == 2) {
         this.mc.displayGuiScreen(new GuiModuleList(this.modules));
      } else if (button.id == 3) {
         this.page--;
         this.selected = -1;
         this.initGui();
      } else if (button.id == 4) {
         this.page++;
         this.selected = -1;
         this.initGui();
      }
   }

   protected void keyTyped(char character, int key) throws IOException {
      if (!this.value.textboxKeyTyped(character, key)) {
         super.keyTyped(character, key);
      }
   }

   protected void mouseClicked(int x, int y, int button) throws IOException {
      super.mouseClicked(x, y, button);
      this.value.mouseClicked(x, y, button);
   }

   public void drawScreen(int x, int y, float partial) {
      this.drawDefaultBackground();
      this.drawCenteredString(this.fontRendererObj, this.module.getName(), this.width / 2, 12, 16777215);
      List<Setting<?>> visible = this.module.visibleSettings();
      boolean changed = false;

      for (GuiButton button : this.buttonList) {
         button.drawButton(this.mc, x, y);
         if (button.id >= 100 && button.id - 100 < visible.size()) {
            changed = this.module.afterSettingControl(visible.get(button.id - 100).getKey(), this.modules.context()) || changed;
         }
      }

      if (changed) {
         this.modules.persist();

         for (GuiButton buttonx : this.buttonList) {
            if (buttonx.id >= 100 && buttonx.id - 100 < visible.size()) {
               Setting<?> setting = visible.get(buttonx.id - 100);
               buttonx.displayString = setting.getLabel() + " = " + (setting.isConfigured() ? setting.format() : "Choose a value");
            }
         }
      }

      this.value.drawTextBox();
      this.drawString(this.fontRendererObj, this.message, 12, this.height - 90, 16777215);
   }

   public boolean doesGuiPauseGame() {
      return false;
   }
}
