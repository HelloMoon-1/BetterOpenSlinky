package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.NotificationRenderer;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.NotificationQueue;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.client.render.RenderModuleAccess;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.util.Locale;

public final strictfp class NotificationsModule extends EvidenceModule {
   private final ModuleManager manager;
   private final NotificationQueue queue = new NotificationQueue();
   private boolean handshake;

   public NotificationsModule(ModuleManager manager) {
      super("notifications", "Notifications", ModuleCategory.RENDER);
      this.manager = manager;
      // Recovered booleans all start disabled, which would leave the HUD silent even when the
      // module is on. Restore the captured startup state instead of an empty category filter.
      this.defaultOn("show_combat", true);
      this.defaultOn("show_move", true);
      this.defaultOn("show_block", true);
      this.defaultOn("show_visual", true);
      this.defaultOn("show_utility", true);
      this.defaultOn("hide_hold_notifs", true);
      this.defaultOn("hide_bind_only", true);
      this.defaultOn("hide_ingame", false);
   }

   /** Applies a captured startup value once, and keeps it as the profile fallback baseline. */
   private void defaultOn(String key, boolean value) {
      Setting<?> setting = this.findSetting(key);
      if (setting.isConfigured()) return;
      setting.parseAndSet(Boolean.toString(value));
      setting.saveCurrent();
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 5 || t == 9 || t == 10 || t == 11 || t == 12 || t == 19 || t == 26;
   }

   public strictfp NotificationQueue queue() {
      return this.queue;
   }

   public strictfp boolean clearHandshake() {
      boolean old = this.handshake;
      this.handshake = false;
      return old;
   }

   private strictfp boolean category(Module module) {
      switch (RenderModuleAccess.category(module)) {
         case 0:
            return this.flag("show_combat");
         case 1:
            return this.flag("show_move");
         case 2:
            return this.flag("show_visual");
         case 3:
            return this.flag("show_block");
         case 4:
            return this.flag("show_utility");
         default:
            return true;
      }
   }

   public strictfp void stateChanged(Module module, boolean enabled) {
      if (module == this) {
         if (!enabled) {
            this.handshake = false;
            this.queue.clearAll();
         } else if (!this.handshake) {
            this.queue.clearAll();
            this.handshake = true;
            Module gui = this.manager.find("gui");
            String binding = gui == null ? "" : RenderModuleAccess.keyName(gui.getBinding());
            this.queue.append(1, String.format(Locale.ROOT, RenderConstants.text(1384194), binding));
         }
      } else if (this.isEnabled() && (RenderModuleAccess.observedFlags(module) & 16) == 0
         && ((RenderModuleAccess.observedFlags(module) & 2) == 0 || !this.flag("hide_bind_only"))
         && (!module.isHoldBinding() || !this.flag("hide_hold_notifs"))
         && this.category(module)) {
         this.queue.state(module.getName(), enabled);
      }
   }

   public strictfp int advance(ClientContext c, float elapsed, int flags) {
      Module gui = this.manager.find("gui");
      boolean hidden = this.flag("hide_ingame") && (gui == null || !gui.isEnabled());
      int next = this.queue.advance(elapsed, flags, hidden);
      // The lifetime bar moves even while the queue's entrance/exit opacity is steady.
      return !hidden && !this.queue.messages().isEmpty() ? next | 2 : next;
   }

   public strictfp void configAction(int kind, String label) {
      if (kind != 2) {
         String action = RenderConstants.pointerText(1384336 + 8 * kind);
         this.queue.append(0, action + " " + (label == null ? RenderConstants.text(1384368) : label));
      }
   }

   public strictfp void message(int code, String message) {
      this.queue.append(2, message == null ? RenderConstants.text(1384424).replace("%d", Integer.toString(code)) : message);
   }

   public strictfp void friendChanged(boolean added) {
      this.queue.append(1, added ? "Friend added" : "Friend removed");
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      RenderEventPayload payload = RenderEventPayload.get(e);
      if (e.type == 11 && payload != null) {
         this.configAction(payload.action, payload.profileName);
      } else if (e.type == 12 && payload != null) {
         this.message(payload.errorCode, payload.message);
      } else if (e.type == 19 && payload != null) {
         this.friendChanged(payload.friendAdded);
      } else if (e.type == 5 && e.module != null) {
         this.stateChanged(e.module, e.enabled);
      } else if (e.type == 9 && payload != null) {
         payload.redrawFlags = this.advance(c, (float)e.elapsedSeconds, payload.redrawFlags);
      } else if (e.type == 26) {
         this.onDisable(c);
      } else if (e.type == 10 && (payload == null || payload.guiDraw == null)) {
         this.renderOverlay(c, e);
      }
   }

   public void renderOverlay(ClientContext c, SemanticEvent event) {
      Module gui = this.manager.find("gui");
      if (this.isEnabled() && (!this.flag("hide_ingame") || gui != null && gui.isEnabled())) {
         RenderAccess.submit(event, this.plan(c, event.renderWidth, event.renderHeight));
      }
   }

   public strictfp DrawPlan plan(ClientContext c) {
      return this.plan(c, c.minecraft().displayWidth, c.minecraft().displayHeight);
   }

   private DrawPlan plan(ClientContext c, float width, float height) {
      // Event 10 is projected in framebuffer pixels, not ScaledResolution units.
      float surfaceWidth = Float.isFinite(width) && width > 0.0F ? width : c.minecraft().displayWidth;
      float surfaceHeight = Float.isFinite(height) && height > 0.0F ? height : c.minecraft().displayHeight;
      Module gui = this.manager.find("gui");
      float scale = gui instanceof GuiModule && gui.findSetting("scale").isConfigured() ? ((GuiModule)gui).scale() : 1.0F;
      return NotificationRenderer.plan(this.queue.messages(), surfaceWidth, surfaceHeight, scale);
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.stateChanged(this, true);
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.handshake = false;
      this.queue.clearAll();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.handshake = false;
      this.queue.clearAll();
   }
}
