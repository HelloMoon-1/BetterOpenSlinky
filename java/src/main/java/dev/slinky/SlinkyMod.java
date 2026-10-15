package dev.slinky;

import dev.slinky.client.ClientEventBridge;
import dev.slinky.client.SlinkyCommand;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.client.render.GuiAppearance;
import dev.slinky.client.render.ProfileServices;
import dev.slinky.config.ConfigStore;
import dev.slinky.config.InitialSettings;
import dev.slinky.inject.ReloadState;
import dev.slinky.module.CoreModules;
import dev.slinky.module.ModuleManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SlinkyMod {
   public static final String MOD_ID = "slinkysemantic";
   public static final String NAME = "Slinky Semantic";
   public static final String VERSION = "0.5.0-settings";
   private ModuleManager modules;
   private ConfigStore config;
   private final Logger logger = LogManager.getLogger("Slinky Semantic");

   public void preInitialize() {
      this.modules = new ModuleManager();
      CoreModules.register(this.modules);
      GuiAppearance.configureMissing(this.modules.find("GUI"));
      InitialSettings.applyMissing(this.modules);
      GameHooks.install(this.modules);
      Path base = slinkyDirectory();
      Path configPath = base.resolve("slinky-semantic.json");

      try {
         Files.createDirectories(base);
      } catch (IOException var5) {
         this.logger.error("Could not create the configuration directory", var5);
      }

      this.config = new ConfigStore(configPath);
      this.modules.input().profiles(this.config, new ProfileServices.FilesHost(this.modules, base.resolve("profiles"), this.config));
      this.modules.setPersistence(new Runnable() {
         @Override
         public void run() {
            SlinkyMod.this.saveConfiguration();
         }
      });

      try {
         this.config.load(this.modules);
      } catch (IOException var4) {
         this.logger.error("Could not load Slinky configuration", var4);
      }
   }

   public void initialize() {
      GuiAppearance.configureMissing(this.modules.find("GUI"));
      KeyBinding openGui = new KeyBinding("key.slinkysemantic.open", 54, "key.categories.slinkysemantic");
      ClientEventBridge.install(new ClientEventBridge(this.modules, openGui, new SlinkyCommand(this.modules)));
      this.modules.persist();
      Thread hook = new Thread(new Runnable() {
         @Override
         public void run() {
            SlinkyMod.this.modules.persistOnExit();
         }
      }, "slinky-config-save");
      ReloadState.replaceHook(hook);
   }

   public ModuleManager modules() {
      return this.modules;
   }

   public static Path slinkyDirectory() {
      String home = System.getProperty("user.home");
      return home != null && !home.isEmpty() ? Paths.get(home).resolve(".slinky") : Minecraft.getMinecraft().mcDataDir.toPath().resolve("config");
   }

   private void saveConfiguration() {
      try {
         this.config.save(this.modules);
      } catch (IOException var2) {
         this.logger.error("Could not save Slinky configuration", var2);
      }
   }
}
