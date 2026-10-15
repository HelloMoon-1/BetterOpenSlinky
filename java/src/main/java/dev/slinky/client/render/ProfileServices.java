package dev.slinky.client.render;

import dev.slinky.config.ConfigStore;
import dev.slinky.config.ConfigurationFiles;
import dev.slinky.config.RecoveredConfiguration;
import dev.slinky.module.ModuleManager;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.LongSupplier;

public final class ProfileServices {
   private ProfileServices() {
   }

   public interface DirectoryScanner {
      void scan(Path var1, LongSupplier var2, ProfileServices.RecordReceiver var3) throws IOException;
   }

   public static final class Failure extends IOException {
      public final int code;

      public Failure(int code, String message) {
         super(message);
         this.code = code;
      }
   }

   public static final class FilesHost implements ProfileServices.Host {
      private static final long FILETIME_EPOCH = 116444736000000000L;
      private final ModuleManager manager;
      private final Path directory;
      private final ConfigStore configuration;
      private final LongSupplier fileTime;
      private final ProfileServices.DirectoryScanner scanner;
      private List<ProfileServices.Record> rows = Collections.emptyList();

      public FilesHost(ModuleManager manager, Path directory, ConfigStore configuration) {
         this(manager, directory, configuration, () -> System.currentTimeMillis() * 10000L + 116444736000000000L);
      }

      public FilesHost(ModuleManager manager, Path directory, ConfigStore configuration, LongSupplier fileTime) {
         this(manager, directory, configuration, fileTime, ProfileServices.FilesHost::scanDirectory);
      }

      public FilesHost(ModuleManager manager, Path directory, ConfigStore configuration, LongSupplier fileTime, ProfileServices.DirectoryScanner scanner) {
         this.manager = Objects.requireNonNull(manager);
         this.directory = directory.toAbsolutePath().normalize();
         this.configuration = Objects.requireNonNull(configuration);
         this.fileTime = Objects.requireNonNull(fileTime);
         this.scanner = Objects.requireNonNull(scanner);
      }

      private Path path(String name) throws IOException {
         if (name != null && !name.isEmpty()) {
            try {
               Path target = this.directory.resolve(name).normalize();
               if (!this.directory.equals(target.getParent())) {
                  throw new ProfileServices.Failure(1012, "Invalid profile name");
               } else {
                  return target;
               }
            } catch (InvalidPathException var3) {
               throw new ProfileServices.Failure(1006, "Invalid profile filename");
            }
         } else {
            throw new ProfileServices.Failure(1012, "A profile name is required");
         }
      }

      @Override
      public synchronized List<ProfileServices.Record> records() throws IOException {
         Map<String, Integer> retained = new TreeMap<>(ProfileMath::compareNames);

         for (RecoveredConfiguration.ProfileBinding binding : this.configuration.profileBindings()) {
            if (binding.binding != 0 && !retained.containsKey(binding.name)) {
               retained.put(binding.name, binding.binding);
            }
         }

         for (ProfileServices.Record row : this.rows) {
            if (row.binding != 0) {
               retained.put(row.name, row.binding);
            }
         }

         List<ProfileServices.Record> next = new ArrayList<>();
         ensure(this.directory.getParent());
         ensure(this.directory);
         this.scanner.scan(this.directory, this.fileTime, (name, age) -> {
            if (name.getBytes(StandardCharsets.UTF_8).length >= 260) {
               throw new ProfileServices.Failure(1006, "Profile filename exceeds the narrow host buffer");
            } else {
               Integer bindingx = retained.get(name);
               next.add(new ProfileServices.Record(name, age, bindingx == null ? 0 : bindingx));
            }
         });
         next.sort((left, right) -> {
            int order = Long.compare(left.ageSeconds, right.ageSeconds);
            return order != 0 ? order : ProfileMath.compareNames(left.name, right.name);
         });
         // A failed or partial enumeration must leave the last usable snapshot
         // and saved bindings intact. Publish only after the scan succeeds.
         this.rows = next;
         this.saveBindings();
         return this.snapshot();
      }

      @Override
      public synchronized List<ProfileServices.Record> snapshot() {
         return Collections.unmodifiableList(new ArrayList<>(this.rows));
      }

      private static void ensure(Path directory) throws IOException {
         ConfigurationFiles.WidePath path = new ConfigurationFiles.WidePath();
         path.text = ConfigurationFiles.wide(directory.toString());

         try (ConfigurationFiles.NioHost host = new ConfigurationFiles.NioHost(null)) {
            int code = ConfigurationFiles.ensureDirectory(path, host);
            if (code != 0) {
               throw new ProfileServices.Failure(code, "Could not create profile directory");
            }
         }
      }

      private static void scanDirectory(Path directory, LongSupplier fileTime, ProfileServices.RecordReceiver receiver) throws IOException {
         DirectoryStream<Path> files;
         try {
            files = Files.newDirectoryStream(directory);
         } catch (NoSuchFileException var22) {
            return;
         } catch (IOException var23) {
            throw new ProfileServices.Failure(1013, "Could not begin profile enumeration");
         }

         try {
            DirectoryStream<Path> opened = files;
            Throwable var5 = null;

            try {
               long now = fileTime.getAsLong();

               for (Path file : opened) {
                  if (!Files.isDirectory(file)) {
                     long modified = Files.getLastModifiedTime(file).toMillis() * 10000L + 116444736000000000L;
                     receiver.record(file.getFileName().toString(), ProfileMath.ageSeconds(now, modified));
                  }
               }
            } catch (Throwable var24) {
               var5 = var24;
               throw var24;
            } finally {
               if (files != null) {
                  if (var5 != null) {
                     try {
                        opened.close();
                     } catch (Throwable var21) {
                        var5.addSuppressed(var21);
                     }
                  } else {
                     files.close();
                  }
               }
            }
         } catch (DirectoryIteratorException var26) {
            throw new ProfileServices.Failure(1015, "Could not continue profile enumeration");
         }
      }

      @Override
      public void load(String name) throws IOException {
         if (name == null) {
            RecoveredConfiguration.resetProfile(this.manager);
         } else {
            ensure(this.directory.getParent());
            ensure(this.directory);
            Path target = this.path(name);
            if (!Files.isRegularFile(target)) {
               throw new ProfileServices.Failure(1008, "Profile does not exist");
            }

            try {
               new ConfigStore(target).loadProfile(this.manager);
            } catch (ConfigurationFiles.Failure var4) {
               throw new ProfileServices.Failure(var4.code, var4.getMessage());
            } catch (IOException var5) {
               throw new ProfileServices.Failure(5001, var5.getMessage());
            }
         }

         this.manager.configurationSession().profileApplied(name);
      }

      @Override
      public void save(String name, boolean overwrite) throws IOException {
         Path target = this.path(name);

         try {
            new ConfigStore(target).saveProfile(this.manager, overwrite);
         } catch (ConfigurationFiles.Failure var5) {
            throw new ProfileServices.Failure(var5.code, var5.getMessage());
         }

         this.manager.configurationSession().profileApplied(name);
      }

      @Override
      public void delete(String name) throws IOException {
         if (name == null) {
            throw new ProfileServices.Failure(6004, RenderConstants.text(1313637));
         } else {
            try {
               Files.delete(this.path(name));
            } catch (IOException var3) {
               throw new ProfileServices.Failure(1011, "Could not delete profile");
            }
         }
      }

      @Override
      public void explore() throws IOException {
         if (!Desktop.isDesktopSupported()) {
            throw new ProfileServices.Failure(4000, "Directory opener unavailable");
         } else {
            Desktop.getDesktop().open(this.directory.toFile());
         }
      }

      @Override
      public void saveConfiguration() throws IOException {
         try {
            this.configuration.save(this.manager);
         } catch (ConfigurationFiles.Failure var2) {
            throw new ProfileServices.Failure(var2.code, var2.getMessage());
         }
      }

      @Override
      public synchronized void bind(String name, int key) {
         if (key >= 0 && key <= 103) {
            ProfileServices.Record found = null;

            for (ProfileServices.Record row : this.rows) {
               if (ProfileMath.compareNames(row.name, name) == 0) {
                  found = row;
                  break;
               }
            }

            if (found == null) {
               throw new IllegalArgumentException("Profile is absent from the current directory records");
            } else {
               found.binding = key;
               this.saveBindings();
               this.manager.persist();
            }
         } else {
            throw new IllegalArgumentException("Binding ID outside the recovered key table");
         }
      }

      private void saveBindings() {
         List<RecoveredConfiguration.ProfileBinding> bindings = new ArrayList<>();

         for (ProfileServices.Record row : this.rows) {
            if (row.binding != 0) {
               bindings.add(new RecoveredConfiguration.ProfileBinding(row.name, row.binding));
            }
         }

         this.configuration.profileBindings(bindings);
      }

      @Override
      public String current() {
         return this.manager.configurationSession().currentProfile();
      }

      @Override
      public boolean pending() {
         return this.manager.configurationSession().pending();
      }
   }

   public interface Host {
      List<ProfileServices.Record> records() throws IOException;

      default List<ProfileServices.Record> snapshot() {
         throw new UnsupportedOperationException("Profile snapshot service is required after refresh failure");
      }

      void load(String var1) throws IOException;

      void save(String var1, boolean var2) throws IOException;

      void delete(String var1) throws IOException;

      void explore() throws IOException;

      void saveConfiguration() throws IOException;

      void bind(String var1, int var2);

      String current();

      boolean pending();
   }

   public static final class Record {
      public final String name;
      public final String date;
      public final long ageSeconds;
      public int binding;

      public Record(String name, long ageSeconds, int binding) {
         this.name = name;
         this.ageSeconds = ageSeconds;
         this.date = ProfileMath.date(ageSeconds);
         this.binding = binding;
      }
   }

   public interface RecordReceiver {
      void record(String var1, long var2) throws IOException;
   }
}
