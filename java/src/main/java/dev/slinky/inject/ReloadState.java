package dev.slinky.inject;

/** Holds reload bookkeeping without changing the schema of already loaded classes. */
public final class ReloadState {
   private static Thread shutdownHook;

   private ReloadState() {
   }

   public static synchronized void replaceHook(Thread hook) {
      Runtime runtime = Runtime.getRuntime();
      Thread previous = shutdownHook;
      boolean removed = previous != null && runtime.removeShutdownHook(previous);
      try {
         runtime.addShutdownHook(hook);
         shutdownHook = hook;
      } catch (RuntimeException failure) {
         if (removed) {
            try {
               runtime.addShutdownHook(previous);
            } catch (RuntimeException restoreFailure) {
               failure.addSuppressed(restoreFailure);
            }
         }
         throw failure;
      }
   }
}
