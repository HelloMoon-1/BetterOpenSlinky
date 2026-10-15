package dev.slinky.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public final class ConfigurationFiles {
   private ConfigurationFiles() {
   }

   public static int initializePath(ConfigurationFiles.WidePath path, ConfigurationFiles.Host host) {
      char[] base = host.environment();
      if (base != null && base[0] != 0) {
         int length = length(base);
         char[] copy = host.allocateWide((long)length + 1L);
         if (copy == null) {
            return 1005;
         } else {
            System.arraycopy(base, 0, copy, 0, length);
            path.text = copy;
            return 0;
         }
      } else {
         return 1004;
      }
   }

   public static int appendPath(ConfigurationFiles.WidePath path, byte[] component, ConfigurationFiles.Host host) {
      int oldLength = length(path.text);
      int componentLength = RecoveredJsonRuntime.stringLength(component, 0);
      char[] next = host.allocateWide(261L);
      if (next == null) {
         return 1005;
      } else {
         long capacity = (long)(oldLength + componentLength + 2);
         host.formatPath(next, capacity, path.text, component);
         host.release(path.text);
         path.text = next;
         return 0;
      }
   }

   public static void parentPath(ConfigurationFiles.WidePath path) {
      int offset = length(path.text);
      int destination = 0;

      do {
         if (path.text[offset] == '\\') {
            destination = offset;
            break;
         }
      } while (--offset != 0);

      path.text[destination] = 0;
   }

   public static int ensureDirectory(ConfigurationFiles.WidePath path, ConfigurationFiles.Host host) {
      return host.createDirectory(path.text) == 0 && host.lastError() != 183 ? 1014 : 0;
   }

   public static int deletePath(ConfigurationFiles.WidePath path, ConfigurationFiles.Host host) {
      return host.deleteFile(path.text) != 0 ? 0 : 1011;
   }

   public static int readText(ConfigurationFiles.WidePath path, ConfigurationFiles.Text output, ConfigurationFiles.Host host) {
      Object handle = host.open(path.text, Integer.MIN_VALUE, 1, 3);
      if (handle == null) {
         return 1008;
      } else {
         int size = host.size(handle);
         if (size == -1) {
            host.close(handle);
            return 1007;
         } else {
            byte[] buffer = host.allocateBytes(Integer.toUnsignedLong(size + 1));
            if (buffer == null) {
               return 1005;
            } else {
               long length;
               int result;
               length = Integer.toUnsignedLong(size);
               long total = 0L;
               result = 0;
               label39:
               if (size != 0) {
                  ConfigurationFiles.Count read = new ConfigurationFiles.Count();

                  while (host.read(handle, buffer, total, size - (int)total, read)) {
                     if (read.value == 0) {
                        result = 1018;
                        break label39;
                     }

                     total += Integer.toUnsignedLong(read.value);
                     if (Long.compareUnsigned(total, length) >= 0) {
                        break label39;
                     }
                  }

                  result = 1009;
               }

               if (result != 0) {
                  host.release(buffer);
               } else {
                  if (length >= (long)buffer.length) {
                     throw new IllegalArgumentException("File service returned unbacked storage");
                  }

                  buffer[(int)length] = 0;
                  output.bytes = buffer;
               }

               host.close(handle);
               return result;
            }
         }
      }
   }

   public static int writeText(ConfigurationFiles.WidePath path, byte[] text, int replaceByte, int incomingCount, ConfigurationFiles.Host host) {
      Object handle = host.open(path.text, 1073741824, 0, (replaceByte & 0xFF) + 1);
      if (handle == null) {
         return host.lastError() == 80 ? 1012 : 1008;
      } else {
         long length = (long)RecoveredJsonRuntime.stringLength(text, 0);
         long total = 0L;
         ConfigurationFiles.Count written = new ConfigurationFiles.Count();
         written.value = incomingCount;
         int result = 0;

         while (Long.compareUnsigned(total, length) < 0) {
            boolean success = host.write(handle, text, total, (int)length - (int)total, written);
            total += Integer.toUnsignedLong(written.value);
            if (!success) {
               result = 1010;
               break;
            }
         }

         host.close(handle);
         return result;
      }
   }

   public static void releasePath(ConfigurationFiles.WidePath path, ConfigurationFiles.Host host) {
      host.release(path.text);
      path.text = null;
   }

   public static int length(char[] text) {
      for (int index = 0; index < text.length; index++) {
         if (text[index] == 0) {
            return index;
         }
      }

      throw new IllegalArgumentException("Wide path lacks terminator");
   }

   public static char[] wide(String text) {
      return Arrays.copyOf(text.toCharArray(), text.length() + 1);
   }

   public static final class Count {
      public int value;
   }

   public static final class Failure extends IOException {
      public final int code;

      public Failure(int code) {
         super("Configuration file service returned " + code);
         this.code = code;
      }
   }

   public interface Host {
      char[] environment();

      char[] allocateWide(long var1);

      byte[] allocateBytes(long var1);

      void release(Object var1);

      int formatPath(char[] var1, long var2, char[] var4, byte[] var5);

      int createDirectory(char[] var1);

      int deleteFile(char[] var1);

      int lastError();

      Object open(char[] var1, int var2, int var3, int var4);

      int size(Object var1);

      boolean read(Object var1, byte[] var2, long var3, int var5, ConfigurationFiles.Count var6);

      boolean write(Object var1, byte[] var2, long var3, int var5, ConfigurationFiles.Count var6);

      void close(Object var1);
   }

   public static final class NioHost implements ConfigurationFiles.Host, AutoCloseable {
      private final Path environment;
      private final Set<FileChannel> opened = Collections.newSetFromMap(new IdentityHashMap<>());
      private int error;

      public NioHost(Path environment) {
         this.environment = environment;
      }

      @Override
      public char[] environment() {
         return this.environment == null ? null : ConfigurationFiles.wide(this.environment.toString());
      }

      @Override
      public char[] allocateWide(long count) {
         return count >= 0L && count <= 2147483639L ? new char[(int)count] : null;
      }

      @Override
      public byte[] allocateBytes(long count) {
         return count >= 0L && count <= 2147483639L ? new byte[(int)count] : null;
      }

      @Override
      public void release(Object allocation) {
      }

      @Override
      public int formatPath(char[] destination, long capacity, char[] base, byte[] component) {
         String result = new String(base, 0, ConfigurationFiles.length(base)) + "\\" + RecoveredJsonRuntime.utf8CString(component);
         if (capacity > 0L && capacity <= (long)destination.length && (long)result.length() < capacity) {
            result.getChars(0, result.length(), destination, 0);
            destination[result.length()] = 0;
            return result.length();
         } else {
            destination[0] = 0;
            return -1;
         }
      }

      private Path path(char[] text) {
         return Paths.get(new String(text, 0, ConfigurationFiles.length(text)));
      }

      @Override
      public int lastError() {
         return this.error;
      }

      private void failure(Exception failed) {
         this.error = failed instanceof FileAlreadyExistsException ? 80 : (failed instanceof NoSuchFileException ? 2 : 5);
      }

      @Override
      public int createDirectory(char[] path) {
         try {
            Files.createDirectory(this.path(path));
            return 1;
         } catch (FileAlreadyExistsException var3) {
            this.error = 183;
            return 0;
         } catch (InvalidPathException | IOException var4) {
            this.failure(var4);
            return 0;
         }
      }

      @Override
      public int deleteFile(char[] path) {
         try {
            Files.delete(this.path(path));
            return 1;
         } catch (InvalidPathException | IOException var3) {
            this.failure(var3);
            return 0;
         }
      }

      @Override
      public Object open(char[] path, int access, int sharing, int creation) {
         try {
            Set<OpenOption> options = new HashSet<>();
            options.add(access == Integer.MIN_VALUE ? StandardOpenOption.READ : StandardOpenOption.WRITE);
            if (creation == 1) {
               options.add(StandardOpenOption.CREATE_NEW);
            } else if (creation == 2) {
               options.add(StandardOpenOption.CREATE);
               options.add(StandardOpenOption.TRUNCATE_EXISTING);
            } else if (creation != 3) {
               throw new IOException("Unsupported file creation disposition");
            }

            FileChannel channel = FileChannel.open(this.path(path), options);
            this.opened.add(channel);
            return channel;
         } catch (InvalidPathException | IOException var7) {
            this.failure(var7);
            return null;
         }
      }

      @Override
      public int size(Object handle) {
         try {
            return (int)((FileChannel)handle).size();
         } catch (IOException var3) {
            this.failure(var3);
            return -1;
         }
      }

      private ByteBuffer buffer(byte[] bytes, long offset, int requested) {
         long count = Integer.toUnsignedLong(requested);
         if (offset >= 0L && offset <= (long)bytes.length && count <= (long)bytes.length - offset) {
            return ByteBuffer.wrap(bytes, (int)offset, (int)count);
         } else {
            throw new IllegalArgumentException("Unbacked file transfer range");
         }
      }

      @Override
      public boolean read(Object handle, byte[] destination, long offset, int requested, ConfigurationFiles.Count count) {
         try {
            int read = ((FileChannel)handle).read(this.buffer(destination, offset, requested));
            count.value = Math.max(read, 0);
            return true;
         } catch (IOException var8) {
            this.failure(var8);
            return false;
         }
      }

      @Override
      public boolean write(Object handle, byte[] source, long offset, int requested, ConfigurationFiles.Count count) {
         try {
            count.value = ((FileChannel)handle).write(this.buffer(source, offset, requested));
            return true;
         } catch (IOException var8) {
            this.failure(var8);
            return false;
         }
      }

      @Override
      public void close(Object handle) {
         FileChannel channel = (FileChannel)handle;

         try {
            channel.close();
         } catch (IOException var7) {
            this.failure(var7);
         } finally {
            this.opened.remove(channel);
         }
      }

      @Override
      public void close() {
         for (FileChannel channel : new ArrayList<>(this.opened)) {
            this.close(channel);
         }
      }
   }

   public static final class Text {
      public byte[] bytes;
   }

   public static final class WidePath {
      public char[] text;
   }
}
