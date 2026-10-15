package dev.slinky.inject;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;

public final class Platform {
   private static Platform.Namespace namespace = Platform.Namespace.NAMED;
   private static String payloadPath;
   private static boolean nativesAvailable;
   private static Boolean forge;
   private static Boolean lunar;
   private static Boolean badlion;

   private Platform() {
   }

   static void initialize(Platform.Namespace runtimeNamespace, String jarPath, boolean natives) {
      namespace = runtimeNamespace;
      payloadPath = jarPath;
      nativesAvailable = natives;
   }

   public static Platform.Namespace getNamespace() {
      return namespace;
   }

   public static String getPayloadPath() {
      return payloadPath;
   }

   public static ClassLoader getGameClassLoader() {
      ClassLoader loader = Minecraft.class.getClassLoader();
      return loader != null ? loader : ClassLoader.getSystemClassLoader();
   }

   public static boolean isForge() {
      if (forge == null) {
         forge = isClassPresent("net.minecraftforge.common.ForgeVersion");
      }

      return forge;
   }

   public static boolean isLunar() {
      if (lunar == null) {
         lunar = isClassPresent("com.moonsworth.lunar.genesis.Genesis")
            || isClassPresent("com.moonsworth.lunar.client.Lunar")
            || System.getProperty("java.class.path", "").toLowerCase().contains("lunar");
      }

      return lunar;
   }

   public static boolean isBadlion() {
      if (badlion == null) {
         badlion = isClassPresent("net.badlion.client.Wrapper")
            || isClassPresent("net.badlion.client.Main")
            || System.getProperty("java.class.path", "").toLowerCase().contains("badlion")
            || System.getProperty("sun.java.command", "").toLowerCase().contains("badlion");
      }

      return badlion;
   }

   public static boolean hasOptiFine() {
      return isClassPresent("optifine.OptiFineTweaker") || isClassPresent("Config") || isClassPresent("net.optifine.Config");
   }

   public static String describe() {
      String loader = isForge() ? "Forge" : (isBadlion() ? "Badlion" : (isLunar() ? "Lunar" : "Vanilla"));
      if (hasOptiFine() && !isForge()) {
         loader = loader + "+OptiFine";
      }

      return loader + " (" + namespace.id + " names)";
   }

   public static boolean isClassPresent(String name) {
      try {
         Class.forName(name, false, getGameClassLoader());
         return true;
      } catch (Throwable var2) {
         return false;
      }
   }

   public static InputStream openResource(String path) throws IOException {
      if (payloadPath != null) {
         final JarFile jar = new JarFile(payloadPath);
         JarEntry entry = jar.getJarEntry(path.startsWith("/") ? path.substring(1) : path);
         if (entry == null) {
            jar.close();
            return Platform.class.getResourceAsStream(path);
         } else {
            return new FilterInputStream(jar.getInputStream(entry)) {
               @Override
               public void close() throws IOException {
                  try {
                     super.close();
                  } finally {
                     jar.close();
                  }
               }
            };
         }
      } else {
         return Platform.class.getResourceAsStream(path);
      }
   }

   public static InputStream resource(String path) {
      try {
         return openResource(path);
      } catch (IOException var2) {
         return null;
      }
   }

   public static void log(String message) {
      if (nativesAvailable) {
         try {
            NativeBridge.log(message);
            return;
         } catch (UnsatisfiedLinkError var2) {
            nativesAvailable = false;
         }
      }

      System.out.println("[Slinky] " + message);
   }

   public static void log(String context, Throwable throwable) {
      log(context + ": " + throwable);
      int depth = 0;

      for (Throwable current = throwable; current != null && depth < 6; depth++) {
         if (current != throwable) {
            log("Caused by: " + current);
         }

         StackTraceElement[] trace = current.getStackTrace();

         for (int i = 0; i < trace.length && i < 12; i++) {
            log("    at " + trace[i]);
         }

         current = current.getCause();
      }
   }

   public static enum Namespace {
      NAMED("named"),
      SRG("srg"),
      OFFICIAL("official");

      public final String id;

      private Namespace(String id) {
         this.id = id;
      }

      public static Platform.Namespace of(String id) {
         for (Platform.Namespace namespace : values()) {
            if (namespace.id.equalsIgnoreCase(id)) {
               return namespace;
            }
         }

         throw new IllegalArgumentException("Unknown namespace " + id);
      }
   }
}
