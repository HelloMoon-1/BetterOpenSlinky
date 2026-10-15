package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.KeyState;
import dev.slinky.client.SemanticEvent;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.client.hud.InfoHud;
import dev.slinky.client.hud.SprintTextEditor;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.OpenB4Theme;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
import java.util.Collections;

public final class SprintModule extends EvidenceModule {
   private static final String DEFAULT_TEXT = "[Sprint (Toggled)]";
   private boolean lastState;
   private ModuleManager hudManager;
   private final BooleanSetting editTextPosition, displayText, rainbow;
   private final NumberSetting hudX, hudY;
   private final StringListSetting hudText;

   public SprintModule() {
      this(null);
   }

   public SprintModule(ModuleManager manager) {
      super("sprint", "Sprint", ModuleCategory.MOVEMENT);
      this.hudManager = manager;
      this.editTextPosition = this.hudOption(new BooleanSetting("edit_text_position", "Edit text position", false));
      this.displayText = this.hudOption(new BooleanSetting("display_text", "Display text", false));
      this.rainbow = this.hudOption(new BooleanSetting("rainbow", "Rainbow", false));
      this.hudX = this.hudOption(new NumberSetting("hud_x", "Text position X", 5, -100000, 100000, 1));
      this.hudY = this.hudOption(new NumberSetting("hud_y", "Text position Y", 5, -100000, 100000, 1));
      this.hudText = this.hudOption(new StringListSetting("hud_text", "Sprint text", Collections.singletonList(DEFAULT_TEXT)));
   }

   private <T, S extends Setting<T>> S hudOption(S setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      return this.add(setting);
   }

   public void attachHudManager(ModuleManager manager) { this.hudManager = manager; }
   public int textX() { return this.hudX.intValue(); }
   public int textY() { return this.hudY.intValue(); }
   public void textPosition(int x, int y) { this.hudX.set((double)x); this.hudY.set((double)y); }
   public String text() { return InfoHud.color(this.hudText.get().isEmpty() ? DEFAULT_TEXT : this.hudText.get().get(0)); }
   public String storedText() { return this.text().replace('§', '&'); }
   public void setText(String text) {
      this.hudText.set(Collections.singletonList(InfoHud.color(text)));
      if (this.hudManager != null) this.hudManager.persist();
   }

   public void openEditor() {
      if (this.hudManager == null) throw new IllegalStateException("Sprint HUD requires attachHudManager(manager)");
      SprintTextEditor screen = new SprintTextEditor(this.hudManager, this);
      Module gui = this.hudManager.find("gui");
      if (gui instanceof GuiModule) ((GuiModule)gui).openEditor(screen);
      else this.hudManager.context().minecraft().displayGuiScreen(screen);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      if (key.equals("edit_text_position") && this.editTextPosition.isEnabled()) {
         this.editTextPosition.set(false);
         this.openEditor();
         return true;
      }
      return false;
   }

   @Override
   public boolean settingVisible(String key) {
      return !key.equals("hud_x") && !key.equals("hud_y") && !key.equals("hud_text");
   }

   @Override
   public void onRenderTick(ClientContext context) {
      if (!this.displayText.isEnabled() || !RavenRender.visible(context.minecraft())) return;
      try (RavenRender.Scope scope = RavenRender.begin()) {
         DrawPlan p = new DrawPlan().text(this.text(), this.textX(), this.textY(), this.rainbow.isEnabled() ? OpenB4Theme.rainbow(2L, 0L) : -1, true, context.minecraft().fontRendererObj.FONT_HEIGHT);
         RenderAccess.submit(new SemanticEvent(10), p);
      }
   }

   @Override
   public boolean listens(int type) {
      return type == 17 || type == 21;
   }

   @Override
   public strictfp void onEvent(ClientContext context, SemanticEvent event) {
      if (event.type == 17) {
         boolean active = context.minecraft().currentScreen == null || this.flag("while_in_gui");
         KeyState.set(context.minecraft().gameSettings.keyBindSprint, active);
         if (context.player() != null) {
            if (!active) {
               if (this.lastState) {
                  context.player().setSprinting(false);
               }

               return;
            }

            if (context.player().isSprinting()) {
               context.player().setSprinting(true);
            }
         }

         this.lastState = active;
      } else if (context.ready()) {
         if (event.forward == 0.0F && event.sideways == 0.0F) {
            return;
         }

         boolean using = context.player().isUsingItem();
         if (using && !this.flag("while_using_item")) {
            return;
         }

         if (event.forward < 0.0F) {
            if (!this.flag("while_backwards")) {
               return;
            }
         } else if (event.forward == 0.0F && event.sideways != 0.0F) {
            if (!this.flag("while_sideways")) {
               return;
            }
         } else if (!using) {
            return;
         }

         event.factor = (float)((double)event.factor * 1.3);
         context.player().setSprinting(true);
      }
   }

   private void restore(ClientContext context) {
      KeyState.restore(context.minecraft().gameSettings.keyBindSprint);
      this.lastState = false;
   }

   @Override
   protected void onDisable(ClientContext context) {
      this.restore(context);
   }

   @Override
   public void onWorldUnload(ClientContext context) {
      this.restore(context);
   }
}
