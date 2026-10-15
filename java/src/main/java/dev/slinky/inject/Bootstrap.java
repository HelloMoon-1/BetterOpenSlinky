package dev.slinky.inject;

import com.google.common.util.concurrent.ListenableFuture;
import dev.slinky.SlinkyMod;
import dev.slinky.client.ClientEventBridge;
import dev.slinky.client.hook.GameHooks;
import dev.slinky.inject.transform.SlinkyPatches;
import dev.slinky.inject.transform.TransformerEngine;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

public final class Bootstrap {
   private static final String INJECTED_PROPERTY = "slinky.injected";

   private Bootstrap() {
   }

   public static void start(String namespace, String payloadPath) throws Throwable {
      synchronized (Bootstrap.class) {
         Field managerField = GameHooks.class.getDeclaredField("modules");
         managerField.setAccessible(true);
         Object previousManager = managerField.get(null);
         if (previousManager != null) {
            System.setProperty("slinky.patches.installed", "true");
            Field shutdownField = previousManager.getClass().getDeclaredField("shutdown");
            shutdownField.setAccessible(true);
            if (!((AtomicBoolean)shutdownField.get(previousManager)).get()) {
               Platform.log("Slinky is already running in this process; ignoring second injection");
               return;
            }
            ClientEventBridge.install(null);
            GameHooks.install(null);
            Platform.log("Previous Slinky session was unloaded; starting a new session");
         }
         System.clearProperty("slinky.injected");

         try {
            System.setProperty("slinky.injected", "true");
            Platform.initialize(Platform.Namespace.of(namespace), payloadPath, true);

            try (InputStream mappings = Platform.openResource("/dev/slinky/inject/mappings.txt")) {
               if (mappings == null && Platform.getNamespace() != Platform.Namespace.NAMED) {
                  throw new IllegalStateException("Payload for " + namespace + " is missing " + "/dev/slinky/inject/mappings.txt");
               }

               Mappings.load(mappings);
            }

            Platform.log("Runtime: " + Platform.describe() + ", " + Mappings.size() + " mappings, payload " + payloadPath);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
               throw new IllegalStateException("Minecraft has not been created yet");
            }
            ListenableFuture<Object> task = mc.addScheduledTask(new Runnable() {
               @Override
               public void run() {
                  Bootstrap.initializeOnClientThread();
               }
            });
            task.get(2L, TimeUnit.MINUTES);
         } catch (Throwable failure) {
            System.clearProperty("slinky.injected");
            throw failure;
         }
      }
   }

   private static void initializeOnClientThread() {
      SlinkyMod mod = new SlinkyMod();

      try {
         mod.preInitialize();
         mod.initialize();
         if (System.getProperty("slinky.patches.installed") == null) {
            TransformerEngine transformer = new TransformerEngine();
            transformer.applyAll(SlinkyPatches.create(Platform.isForge()));
            if (transformer.getFailed() != 0) {
               throw new IllegalStateException("Failed to install " + transformer.getFailed() + " Slinky class patches");
            }
            System.setProperty("slinky.patches.installed", "true");
         } else {
            Platform.log("Reusing the installed Slinky game hooks");
         }
         Platform.log("Slinky Semantic 0.5.0-settings initialized with " + mod.modules().all().size() + " modules");
      } catch (Throwable var2) {
         try {
            if (mod.modules() != null) {
               mod.modules().shutdown();
            }
         } catch (Throwable cleanupFailure) {
            var2.addSuppressed(cleanupFailure);
         } finally {
            System.clearProperty("slinky.injected");
         }
         Platform.log("Slinky initialization failed", var2);
         throw var2 instanceof RuntimeException ? (RuntimeException)var2 : new RuntimeException(var2);
      }
   }
}
