package dev.slinky.client;

import dev.slinky.client.render.ProfileServices;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.config.ConfigStore;
import dev.slinky.config.RecoveredConfiguration;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.FriendsModule;
import dev.slinky.module.impl.GuiModule;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InputRouter implements InputQuery.Host {
   private final ModuleManager modules;
   private final BindingInput keys = new BindingInput();
   private final InputQuery query = new InputQuery(this.keys);
   private ConfigStore configuration;
   private ProfileServices.Host profiles;

   public InputRouter(ModuleManager modules) {
      this.modules = modules;
   }

   public void profiles(ConfigStore configuration, ProfileServices.Host profiles) {
      this.configuration = Objects.requireNonNull(configuration);
      this.profiles = Objects.requireNonNull(profiles);
   }

   public ProfileServices.Host profiles() {
      if (this.profiles == null) {
         throw new IllegalStateException("Profile file service has not been installed");
      } else {
         return this.profiles;
      }
   }

   public int input(InputQuery.Request request) {
      try {
         return this.query.query(request, this);
      } catch (IllegalArgumentException var3) {
         this.report(var3);
         return 0;
      }
   }

   @Override
   public boolean environmentAvailable() {
      return this.modules.context().minecraft() != null;
   }

   @Override
   public int dispatch(InputQuery.Request request, boolean unchanged) {
      Module candidate = this.modules.find("GUI");
      return !(candidate instanceof GuiModule)
         ? 0
         : (int)((GuiModule)candidate)
            .dispatchInput(request.type, request.key, request.requested == 1, unchanged ? 1 : 0, request.utf8, request.x, request.y, request.wheel);
   }

   @Override
   public boolean ready() {
      return this.modules.context().minecraft().inGameHasFocus;
   }

   @Override
   public boolean guiEnabled() {
      Module gui = this.modules.find("GUI");
      return gui != null && gui.isEnabled();
   }

   @Override
   public boolean friendsEnabled() {
      Module friends = this.modules.find("Friends");
      return friends != null && friends.isEnabled();
   }

   @Override
   public void friendAction() {
      Module friends = this.modules.find("Friends");
      if (friends instanceof FriendsModule) {
         ((FriendsModule)friends).middleClickAction(this.modules.context(), this.modules);
      }
   }

   @Override
   public boolean moduleBindings(int key, byte requested) {
      boolean accepted = this.keys.dispatch(this.modules, key, requested == 1);
      if (accepted) {
         this.modules.persist();
      }

      return accepted;
   }

   @Override
   public void profileBindings(int key, byte requested) {
      if (requested == 1) {
         if (this.configuration == null) {
            throw new IllegalStateException("Configuration service has not been installed");
         } else {
            for (RecoveredConfiguration.ProfileBinding binding : this.configuration.profileBindings()) {
               if (binding.binding == key) {
                  try {
                     this.profiles().load(binding.name);
                     SemanticEvent event = new SemanticEvent(11);
                     RenderEventPayload payload = RenderEventPayload.attach(event);
                     payload.action = 0;
                     payload.profileName = binding.name;

                     try {
                        this.modules.dispatch(event);
                     } finally {
                        RenderEventPayload.detach(event);
                     }
                  } catch (IOException var11) {
                     this.report(var11);
                  }
               }
            }
         }
      }
   }

   public void releaseLostHolds() {
      this.keys.releaseLostHolds(this.modules);
   }

   public void clear() {
      this.keys.releaseAll(this.modules);
      this.keys.clear();
   }

   private void report(Exception failure) {
      SemanticEvent event = new SemanticEvent(12);
      RenderEventPayload payload = RenderEventPayload.attach(event);
      payload.errorCode = failure instanceof ProfileServices.Failure ? ((ProfileServices.Failure)failure).code : 1;
      payload.message = failure.getMessage();

      try {
         this.modules.dispatch(event);
      } finally {
         RenderEventPayload.detach(event);
      }

      Logger.getLogger("SlinkySemantic").log(Level.WARNING, "Input action could not be applied", (Throwable)failure);
   }
}
