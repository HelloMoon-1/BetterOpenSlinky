package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.GuiModuleList;
import dev.slinky.client.GuiModuleSettings;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hook.RenderHooks;
import dev.slinky.client.render.OpenB4GuiScreen;
import dev.slinky.client.render.ProfileServices;
import dev.slinky.client.render.RecoveredGui;
import dev.slinky.client.render.RecoveredGuiScreen;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.client.render.RenderModuleAccess;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

public final class GuiModule extends EvidenceModule {
   private final ModuleManager manager;
   private final RecoveredGui.Runtime runtime = new RecoveredGui.Runtime();
   private final ChoiceSetting guiStyle;
   private OpenB4GuiScreen clickGui;
   private RecoveredGuiScreen originalGui;
   private boolean unloadRequested;
   private boolean shutdown;
   private boolean changingScreen;
   private boolean closePending;
   private int previousGuiScale = -1;
   private int appliedRavenScale = 2;

   public GuiModule(ModuleManager manager) {
      super("gui", "GUI", ModuleCategory.RENDER);
      this.manager = manager;
      // GUI is global (descriptor flag 16). These must be configured AND have a
      // saved default: ConfigStore.apply restores saved values before decoding.
      this.guiStyle = this.preference(new ChoiceSetting("gui_style", "GUI style", 1, "Original", "Raven / OpenB4"));
      this.preference(new ChoiceSetting("raven_scale", "Scale", 1, "Small", "Normal", "Large"));
      this.preference(new NumberSetting("raven_background_blur", "Background blur", 30.0, 0.0, 100.0, 5.0));
      this.preference(new NumberSetting("raven_scroll_speed", "Scroll speed", 35.0, 10.0, 90.0, 1.0));
      this.booleanPreference("raven_allow_movement", "Allow movement", true);
      this.booleanPreference("raven_allow_rotating", "Allow rotating", true);
      this.booleanPreference("raven_dark_background", "Dark background", true);
      this.booleanPreference("raven_hide_player_model", "Hide player model", false);
      this.booleanPreference("raven_hide_watermark", "Hide watermark", false);
      this.booleanPreference("raven_lite_mode", "Lite mode", false);
      this.booleanPreference("raven_rainbow_outlines", "Rainbow outlines", true);
      this.booleanPreference("raven_save_category_positions", "Save category positions", false);
      this.preference(new StringListSetting("raven_hidden_modules", "Hidden HUD modules", Collections.emptyList()));
      this.preference(new StringListSetting("raven_category_positions", "Category positions", Collections.emptyList()));
      RenderModuleAccess.bindGuiInteraction(this::interactionRequested);
   }

   private <T extends Setting<?>> T preference(T setting) {
      if (!setting.isConfigured()) {
         setting.parseAndSet(setting.format());
      }
      setting.saveCurrent();
      return this.add(setting);
   }

   private void booleanPreference(String key, String label, boolean value) {
      this.preference(new BooleanSetting(key, label, value));
   }

   public RecoveredGui.Runtime runtime() {
      return this.runtime;
   }

   public float scale() {
      return RenderConstants.f(1381616 + this.choice("scale") * 4);
   }

   public ColorSetting.Value accent() {
      return this.color("accent_color");
   }

   public boolean compact() {
      return this.flag("compact");
   }

   public boolean wide() {
      return this.flag("wide");
   }

   public boolean ravenFlag(String key) {
      return this.flag("raven_" + key);
   }

   public float ravenNumber(String key) {
      return this.number("raven_" + key);
   }

   public int ravenScale() {
      return this.choice("raven_scale") + 1;
   }

   public int appliedRavenScale() {
      return this.previousGuiScale < 0 ? this.ravenScale() : this.appliedRavenScale;
   }

   private void applyRavenScale() {
      if (this.previousGuiScale < 0) {
         this.previousGuiScale = this.manager.context().minecraft().gameSettings.guiScale;
      }
      this.appliedRavenScale = this.ravenScale();
      this.manager.context().minecraft().gameSettings.guiScale = this.appliedRavenScale;
   }

   private void restoreRavenScale() {
      if (this.previousGuiScale >= 0) {
         this.manager.context().minecraft().gameSettings.guiScale = this.previousGuiScale;
         this.previousGuiScale = -1;
      }
   }

   public boolean ravenSelected() {
      return this.guiStyle.index() == 1;
   }

   @Override
   public boolean settingVisible(String key) {
      return !"raven_hidden_modules".equals(key) && !"raven_category_positions".equals(key);
   }

   private boolean protectedFromHiding(Module module) {
      return module == null || module == this || "mod_overlay".equals(module.getId())
         || "command_line".equals(module.getId());
   }

   public boolean hidden(Module module) {
      return !this.protectedFromHiding(module)
         && ((StringListSetting)this.findSetting("raven_hidden_modules")).containsIgnoreCase(module.getId());
   }

   public void toggleHidden(Module module) {
      if (this.protectedFromHiding(module)) return;
      StringListSetting setting = (StringListSetting)this.findSetting("raven_hidden_modules");
      List<String> ids = new ArrayList<>(setting.get());
      if (!ids.removeIf(id -> id.equalsIgnoreCase(module.getId()))) {
         ids.add(module.getId());
      }
      setting.set(ids);
      this.manager.persist();
   }

   public boolean interactionRequested() {
      return this.runtime.closeRequested;
   }

   public int redrawFlags(int old) {
      return old | 3;
   }

   public void requestUnload() {
      this.runtime.closeRequested = true;
      this.unloadRequested = true;
   }

   public GuiScreen selectedGui() {
      if (this.ravenSelected()) {
         if (this.clickGui == null) {
            this.clickGui = new OpenB4GuiScreen(this.manager, this);
         }
         return this.clickGui;
      }
      if (this.originalGui == null) {
         this.originalGui = new RecoveredGuiScreen(this.manager, this);
      }
      return this.originalGui;
   }

   /** Selects a real screen, not a temporary embedded "advanced" view. */
   public void selectGui(boolean raven) {
      this.guiStyle.set(raven ? 1 : 0);
      this.manager.persist();
      if (this.isEnabled()) {
         this.openSelectedGui();
      }
   }

   public void openSelectedGui() {
      this.runtime.finishModal(true);
      this.runtime.clearInteraction();
      this.runtime.closeRequested = false;
      this.runtime.start(RenderAccess.now());
      if (this.ravenSelected()) this.applyRavenScale();
      else this.restoreRavenScale();
      GuiScreen screen = this.selectedGui();
      if (screen instanceof OpenB4GuiScreen) {
         ((OpenB4GuiScreen)screen).opened();
      }
      this.showScreen(screen);
   }

   public void returnToGui() {
      if (!this.isEnabled()) {
         this.manager.requestEnabled(this, true);
      }
      this.openSelectedGui();
   }

   public void openEditor(GuiScreen editor) {
      if (editor == null) {
         throw new IllegalArgumentException("HUD editor must not be null");
      }
      this.runtime.finishModal(true);
      this.runtime.clearInteraction();
      this.showScreen(editor);
   }

   public void showOriginalSettings(Module entry) {
      this.runtime.selected = entry.metadataCategory() >= 0 && entry.metadataCategory() < 5 ? entry.metadataCategory() : 2;
      this.runtime.selectedAnimation = dev.slinky.client.render.GuiAppearance.tab(this.runtime.selected);
      this.runtime.scroll = this.runtime.scrollMaximum = 0.0F;
      this.selectGui(false);
      if (this.originalGui != null) {
         this.originalGui.expanded(entry, true);
      }
   }

   public void showProfiles() {
      this.runtime.selected = 5;
      this.runtime.selectedAnimation = dev.slinky.client.render.GuiAppearance.tab(this.runtime.selected);
      this.runtime.scroll = this.runtime.scrollMaximum = 0.0F;
      this.runtime.profileStamp = Double.NEGATIVE_INFINITY;
      this.selectGui(false);
   }

   private void showScreen(GuiScreen screen) {
      this.changingScreen = true;
      try {
         this.manager.context().minecraft().displayGuiScreen(screen);
      } finally {
         this.changingScreen = false;
         this.closePending = false;
      }
   }

   private static boolean ownedScreen(GuiScreen screen) {
      return screen instanceof RenderHooks.GuiFrame || screen instanceof GuiModuleList || screen instanceof GuiModuleSettings;
   }

   @Override
   public void onClientTick(ClientContext c) {
      if (this.unloadRequested) {
         this.unloadRequested = false;
         this.manager.shutdown();
         return;
      }
      // displayGuiScreen calls the OLD screen's onGuiClosed before installing
      // the new screen. Never disable synchronously from that callback: doing so
      // tears down the GUI during a mode/editor transition.
      if (this.closePending) {
         this.closePending = false;
         if (this.isEnabled() && !ownedScreen(c.minecraft().currentScreen)) {
            this.manager.requestEnabled(this, false);
         }
      }
      if (this.isEnabled() && c.minecraft().currentScreen instanceof OpenB4GuiScreen) {
         ((OpenB4GuiScreen)c.minecraft().currentScreen).updateMovement();
      }
   }

   @Override
   public boolean listens(int type) {
      return type == 0 || type == 5 || type == 9 || type == 10 || type == 26;
   }

   @Override
   protected void onEnable(ClientContext c) {
      this.runtime.closeRequested = false;
      this.unloadRequested = this.closePending = false;
      if (Mouse.isGrabbed()) {
         Mouse.setCursorPosition(c.minecraft().displayWidth / 2, c.minecraft().displayHeight / 2);
         Mouse.setGrabbed(false);
      }
      if (!ownedScreen(c.minecraft().currentScreen)) {
         this.openSelectedGui();
      }
   }

   @Override
   protected void onDisable(ClientContext c) {
      this.unloadRequested = this.closePending = false;
      this.runtime.finishModal(true);
      this.runtime.clearInteraction();
      if (this.clickGui != null) {
         this.clickGui.cancelInput();
      }
      this.restoreRavenScale();
      if (!this.changingScreen && ownedScreen(c.minecraft().currentScreen)) {
         this.showScreen(null);
      }
      if (c.ready() && !this.shutdown && c.minecraft().currentScreen == null) {
         restoreGameFocus(c);
      }
      this.saveOnClose();
      RenderAccess.releaseCanvasResources();
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext context) {
      GuiScreen current = context.minecraft().currentScreen;
      if ("gui_style".equals(key) && (current instanceof OpenB4GuiScreen && !this.ravenSelected()
         || current instanceof RecoveredGuiScreen && this.ravenSelected())) {
         this.manager.persist();
         this.openSelectedGui();
         return true;
      }
      return false;
   }

   public void saveOnClose() {
      if (!this.shutdown) {
         try {
            this.manager.input().profiles().saveConfiguration();
         } catch (IOException failure) {
            SemanticEvent error = new SemanticEvent(12);
            RenderEventPayload payload = RenderEventPayload.attach(error);
            payload.errorCode = failure instanceof ProfileServices.Failure ? ((ProfileServices.Failure)failure).code : 1;
            try {
               this.manager.dispatch(error);
            } finally {
               RenderEventPayload.detach(error);
            }
         }
      }
   }

   public long dispatchInput(int kind, int key, boolean pressed, int unchanged, byte[] utf8, int x, int y, float wheel) {
      GuiScreen screen = this.manager.context().minecraft().currentScreen;
      if (this.isEnabled() && screen instanceof OpenB4GuiScreen) {
         return ((OpenB4GuiScreen)screen).dispatchInput(kind, key, pressed, unchanged != 0, utf8, x, y, wheel) ? 1L : 0L;
      }
      if (this.isEnabled() && screen instanceof RecoveredGuiScreen) {
         if (((RecoveredGuiScreen)screen).modeSelectionInput(kind, key, pressed, unchanged != 0, x, y)) {
            return 1L;
         }
         if (this.runtime.input(kind, key, x, y, wheel, pressed, utf8)) {
            return 1L;
         }
      } else if (screen != null) {
         // Native HUD editors own their vanilla callbacks. Feeding those events
         // into RecoveredGui.Runtime steals text, drag and reset-button input.
         return 0L;
      }
      if (kind == 2 && pressed && key != 0) {
         if (unchanged == 0 && key == this.getBinding()) {
            this.manager.requestEnabled(this, !this.isEnabled());
            return 1L;
         }
         if (key == 87 && this.isEnabled()) {
            this.manager.requestEnabled(this, false);
            return 1L;
         }
      }
      return this.isEnabled() && (kind != 2 || !this.flag("allow_input") || Integer.compareUnsigned(key, 5) <= 0) ? 1L : 0L;
   }

   /** Called after displayGuiScreen(null), never from the old screen's close callback. */
   public void finishEditorClose() {
      ClientContext c = this.manager.context();
      if (c.minecraft().currentScreen != null) return;
      this.closePending = false;
      if (this.isEnabled()) this.manager.requestEnabled(this, false);
      if (!this.shutdown) restoreGameFocus(c);
   }

   private static void restoreGameFocus(ClientContext c) {
      if (!c.ready() || c.minecraft().currentScreen != null || !org.lwjgl.opengl.Display.isActive()) return;
      c.minecraft().setIngameFocus();
      // Vanilla skips grabbing when its focus flag is already true; GUI event 0 used to
      // ungrab in the interval between closing the editor and the next client tick.
      if (c.minecraft().inGameHasFocus && !Mouse.isGrabbed()) c.minecraft().mouseHelper.grabMouseCursor();
   }

   public void screenClosed() {
      this.screenClosed(this.manager.context().minecraft().currentScreen);
   }

   public void screenClosed(GuiScreen screen) {
      if (!this.changingScreen && this.isEnabled()) {
         this.closePending = true;
      }
   }

   @Override
   public void onEvent(ClientContext c, SemanticEvent event) {
      RenderEventPayload payload = RenderEventPayload.get(event);
      if (event.type == 9 && payload != null) {
         payload.redrawFlags = this.redrawFlags(payload.redrawFlags);
      } else if (event.type == 26) {
         this.shutdown = true;
         this.restoreRavenScale();
         if (this.clickGui != null) {
            this.clickGui.releaseResources();
         }
         RenderAccess.releaseCanvasResources();
         this.runtime.finishModal(true);
         this.runtime.clearInteraction();
      } else if (event.type == 0 && ownedScreen(c.minecraft().currentScreen) && Mouse.isGrabbed()) {
         Mouse.setGrabbed(false);
      }
      // ForgeUiSurface invokes GuiFrame exactly once, after module HUD drawing.
   }

   @Override
   public void onWorldUnload(ClientContext c) {
      this.restoreRavenScale();
      if (this.clickGui != null) {
         this.clickGui.releaseResources();
      }
      this.runtime.finishModal(true);
      this.runtime.clearInteraction();
      RenderAccess.releaseCanvasResources();
   }
}
