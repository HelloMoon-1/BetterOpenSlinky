package dev.slinky.module;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ClientEventBridge;
import dev.slinky.client.InputRouter;
import dev.slinky.client.ModuleActionService;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.rotation.RotationService;
import dev.slinky.client.placement.PlacementSelection;
import dev.slinky.config.ConfigurationSession;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
   private final Map<String, Module> byId = new LinkedHashMap<>();
   private final ClientContext context;
   private final InputRouter input;
   private final ConfigurationSession configurationSession = new ConfigurationSession();
   private final AtomicBoolean shutdown = new AtomicBoolean();
   private boolean pendingPersistence;
   private boolean inputTickDelivered;
   private final ModuleStateQueue<Module> stateChanges = new ModuleStateQueue<>(new ModuleStateQueue.Lifecycle<Module>() {
      public boolean enabled(Module module) {
         return module.isEnabled();
      }

      public boolean cancelDisable(Module module) {
         SemanticEvent event = new SemanticEvent(4);
         event.module = module;
         ModuleManager.this.dispatch(event);
         return event.cancelled;
      }

      public void mark(Module module, boolean enabled) {
         module.markEnabled(enabled);
      }

      public void notifyChange(Module module, boolean enabled) {
         ModuleManager.this.dispatchLifecycle(module, enabled);
         if (!enabled) {
            ModuleActionService.release(module);
         }
      }

      @Override
      public void beforeDisableBatch() {
         ModuleManager.this.disablingListeners = ModuleManager.this.selectedListeners(5);
      }
   });
   private List<Module> disablingListeners;
   private Runnable persistence = new Runnable() {
      @Override
      public void run() {
      }
   };

   public ModuleManager() {
      this(new ClientContext(Minecraft.getMinecraft()));
   }

   public ModuleManager(ClientContext context) {
      this.context = context;
      this.input = new InputRouter(this);
      context.setGuiEnabled(() -> {
         Module gui = this.find("GUI");
         return gui != null && gui.isEnabled();
      });
   }

   public void register(Module module) {
      String id = normalize(module.getId());
      if (this.byId.containsKey(id)) {
         throw new IllegalArgumentException("duplicate module id " + id);
      } else {
         this.byId.put(id, module);
      }
   }

   public void setPersistence(Runnable persistence) {
      this.persistence = persistence;
   }

   public synchronized void persist() {
      if (!this.shutdown.get()) {
         if (this.stateChanges.pending()) {
            this.pendingPersistence = true;
         } else {
            this.persistence.run();
         }
      }
   }

   public List<Module> all() {
      return Collections.unmodifiableList(new ArrayList<>(this.byId.values()));
   }

   public List<Module> inCategory(ModuleCategory category) {
      List<Module> result = new ArrayList<>();

      for (Module module : this.byId.values()) {
         if (module.getCategory() == category) {
            result.add(module);
         }
      }

      return result;
   }

   public Module find(String name) {
      String normalized = normalize(name);
      Module direct = this.byId.get(normalized);
      if (direct != null) {
         return direct;
      } else {
         for (Module module : this.byId.values()) {
            if (normalize(module.getName()).equals(normalized)) {
               return module;
            }
         }

         return null;
      }
   }

   public void toggle(Module module) {
      this.setEnabled(module, !this.requestedEnabled(module), true);
   }

   public boolean requestedEnabled(Module module) {
      return this.stateChanges.requestedEnabled(module);
   }

   public void setBinding(Module module, int binding) {
      if (this.find(module.getId()) != module) {
         throw new IllegalArgumentException("Binding owner is not registered");
      } else {
         module.setBinding(binding);
         SemanticEvent event = new SemanticEvent(24);
         event.module = module;
         this.dispatch(event);
      }
   }

   public void setEnabled(Module module, boolean enabled, boolean save) {
      if (!this.shutdown.get()) {
         this.requestEnabled(module, enabled);
         if (save) {
            this.persist();
         }
      }
   }

   private void validateSettings(Module module, boolean enabled) {
      if (enabled && module instanceof EvidenceModule) {
         for (Setting<?> setting : module.getSettings()) {
            if (!setting.isConfigured()) {
               throw new IllegalArgumentException("Choose " + module.getName() + "." + setting.getKey() + " before enabling");
            }
         }
      }
   }

   public boolean requestEnabled(Module module, boolean enabled) {
      if (this.shutdown.get()) {
         return false;
      } else {
         this.validateSettings(module, enabled);
         return this.stateChanges.request(module, enabled);
      }
   }

   public void applyPendingChanges() {
      try {
         this.stateChanges.apply();
      } finally {
         this.disablingListeners = null;
      }

      synchronized (this) {
         if (this.pendingPersistence && !this.shutdown.get()) {
            this.pendingPersistence = false;
            this.persistence.run();
         }
      }
   }

   private void dispatchLifecycle(Module subject, boolean enabled) {
      SemanticEvent event = new SemanticEvent(5);
      event.module = subject;
      event.enabled = enabled;
      List<Module> selected = (List<Module>)(enabled ? this.selectedListeners(5) : new ArrayList<>(this.disablingListeners));
      if (!selected.contains(subject)) {
         selected.add(subject);
      }

      this.sortListeners(selected, 5);

      for (Module module : selected) {
         if (module == subject) {
            module.notifyLifecycle(enabled, this.context);
         }

         if (module.listens(5)) {
            module.onEvent(this.context, event);
         }
      }
   }

   public void clientTick(boolean start) {
      if (start) {
         this.inputTickDelivered = false;
         this.input.releaseLostHolds();
         this.applyPendingChanges();
         PlacementSelection.tick();
      } else {
         PlacementSelection.finishTick();
         this.applyPendingChanges();
      }
      for (Module module : this.byId.values()) {
         if (module.isEnabled()) {
            if (start) {
               module.onClientTickStart(this.context);
            } else {
               module.onClientTick(this.context);
            }
         }
      }
   }

   public void clientInputTick() {
      if (this.inputTickDelivered || this.shutdown.get()) {
         return;
      }
      this.inputTickDelivered = true;
      // Vanilla has consumed this tick's manual hotbar/use input. End Bridge's
      // temporary selection before consumables try to acquire the shared lease.
      PlacementSelection.finishTick();
      this.applyPendingChanges();
      for (Module module : this.byId.values()) {
         if (module.isEnabled() && this.requestedEnabled(module)) {
            module.onClientInputTick(this.context);
         }
      }
   }

   public void worldUnload() {
      this.inputTickDelivered = true;
      this.stateChanges.clear();
      this.input.clear();

      try {
         for (Module module : this.byId.values()) {
            module.onWorldUnload(this.context);
         }
      } finally {
         RelationshipService.clear();
         try {
            GameHooks.clear();
         } finally {
            try {
               RotationService.reset(this.context);
            } finally {
               ModuleActionService.clear();
            }
         }
      }
   }

   public void dispatch(SemanticEvent event) {
      for (Module module : this.selectedListeners(event.type)) {
         module.onEvent(this.context, event);
      }
   }

   public InputRouter input() {
      return this.input;
   }

   public ConfigurationSession configurationSession() {
      return this.configurationSession;
   }

   private List<Module> selectedListeners(int type) {
      List<Module> selected = new ArrayList<>();

      for (Module module : this.byId.values()) {
         if (module.isEnabled() && module.listens(type)) {
            selected.add(module);
         }
      }

      this.sortListeners(selected, type);
      return selected;
   }

   private void sortListeners(List<Module> selected, final int type) {
      Collections.sort(selected, new Comparator<Module>() {
         public int compare(Module first, Module second) {
            return EvidenceCatalog.priority(second.getName(), type) - EvidenceCatalog.priority(first.getName(), type);
         }
      });
   }

   public synchronized void persistOnExit() {
      if (!this.shutdown.get()) {
         this.persistence.run();
      }
   }

   public void shutdown() {
      if (this.shutdown.compareAndSet(false, true)) {
         try {
            synchronized (this) {
               this.persistence.run();
               this.pendingPersistence = false;
            }

            this.stateChanges.clear();

            for (Module module : this.byId.values()) {
               if (module.listens(26)) {
                  module.onEvent(this.context, new SemanticEvent(26));
               }
            }

            for (Module modulex : this.byId.values()) {
               this.stateChanges.request(modulex, false);
            }

            this.applyPendingChanges();
            List<Module> enabled = new ArrayList<>();
            this.disablingListeners = this.selectedListeners(5);

            for (Module modulex : this.byId.values()) {
               if (modulex.isEnabled()) {
                  enabled.add(modulex);
               }
            }

            for (Module modulexx : enabled) {
               modulexx.markEnabled(false);
            }

            try {
               for (Module modulexx : enabled) {
                  this.dispatchLifecycle(modulexx, false);
               }
            } finally {
               this.disablingListeners = null;
            }

         } finally {
            // A failed save or module callback must still leave the old session inert.
            for (Module module : this.byId.values()) {
               module.markEnabled(false);
            }
            this.pendingPersistence = false;
            this.disablingListeners = null;
            this.stateChanges.clear();
            try {
               this.worldUnload();
            } finally {
               try {
                  ClientEventBridge.install(null);
               } finally {
                  try {
                     GameHooks.install(null);
                  } finally {
                     System.clearProperty("slinky.injected");
                  }
               }
            }
         }
      }
   }

   public void renderTick(boolean start) {
      for (Module module : this.byId.values()) {
         if (module.isEnabled()) {
            if (start) {
               module.onRenderTickStart(this.context);
            } else {
               module.onRenderTick(this.context);
            }
         }
      }
   }

   public ClientContext context() {
      return this.context;
   }

   private static String normalize(String value) {
      return value.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }
}
