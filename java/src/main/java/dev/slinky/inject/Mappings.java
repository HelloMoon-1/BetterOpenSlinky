package dev.slinky.inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Mappings {
   public static final String RESOURCE = "/dev/slinky/inject/mappings.txt";
   private static final Map<String, String> classes = new HashMap<>();
   private static final Map<String, String> classesReverse = new HashMap<>();
   private static final Map<String, String> fields = new HashMap<>();
   private static final Map<String, String> methods = new HashMap<>();
   private static final Map<String, String> methodsByParameters = new HashMap<>();
   private static boolean identity = true;
   private static Mappings.HierarchyProvider hierarchy = Mappings::runtimeSupertypes;

   private Mappings() {
   }

   public static void load(InputStream stream) throws IOException {
      classes.clear();
      classesReverse.clear();
      fields.clear();
      methods.clear();
      methodsByParameters.clear();
      identity = stream == null;
      if (stream != null) {
         String line;
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            while ((line = reader.readLine()) != null) {
               String[] columns = line.split("\t");
               String var5 = columns[0];
               switch (var5) {
                  case "CL":
                     classes.put(columns[1], columns[2]);
                     classesReverse.put(columns[2], columns[1]);
                     break;
                  case "FD":
                     fields.put(columns[1] + '.' + columns[2], columns[3]);
                     break;
                  case "MD":
                     methods.put(columns[1] + '.' + columns[2] + columns[3], columns[4]);
                     methodsByParameters.put(columns[1] + '.' + columns[2] + columns[3].substring(0, columns[3].indexOf(41) + 1), columns[4]);
               }
            }
         }
      }
   }

   public static void setHierarchyProvider(Mappings.HierarchyProvider provider) {
      hierarchy = provider;
   }

   public static boolean isIdentity() {
      return identity;
   }

   public static int size() {
      return classes.size() + fields.size() + methods.size();
   }

   public static String mapClass(String namedInternalName) {
      if (identity) {
         return namedInternalName;
      } else {
         String mapped = classes.get(namedInternalName);
         if (mapped != null) {
            return mapped;
         } else {
            int inner = namedInternalName.lastIndexOf(36);
            if (inner > 0) {
               String outer = classes.get(namedInternalName.substring(0, inner));
               if (outer != null) {
                  return outer + namedInternalName.substring(inner);
               }
            }

            return namedInternalName;
         }
      }
   }

   public static String unmapClass(String runtimeInternalName) {
      if (identity) {
         return runtimeInternalName;
      } else {
         String named = classesReverse.get(runtimeInternalName);
         return named != null ? named : runtimeInternalName;
      }
   }

   public static String mapClassBinary(String namedBinaryName) {
      return mapClass(namedBinaryName.replace('.', '/')).replace('/', '.');
   }

   public static String getNamedName(Class<?> runtimeClass) {
      return unmapClass(runtimeClass.getName().replace('.', '/')).replace('/', '.');
   }

   public static String mapDescriptor(String namedDescriptor) {
      if (identity) {
         return namedDescriptor;
      } else {
         StringBuilder builder = new StringBuilder(namedDescriptor.length());
         int index = 0;

         while (index < namedDescriptor.length()) {
            char c = namedDescriptor.charAt(index);
            if (c == 'L') {
               int end = namedDescriptor.indexOf(59, index);
               builder.append('L').append(mapClass(namedDescriptor.substring(index + 1, end))).append(';');
               index = end + 1;
            } else {
               builder.append(c);
               index++;
            }
         }

         return builder.toString();
      }
   }

   public static String mapField(String namedOwner, String namedName) {
      if (identity) {
         return namedName;
      } else {
         String direct = fields.get(namedOwner + '.' + namedName);
         if (direct != null) {
            return direct;
         } else {
            for (String type : walkSupertypes(namedOwner)) {
               String mapped = fields.get(type + '.' + namedName);
               if (mapped != null) {
                  return mapped;
               }
            }

            return namedName;
         }
      }
   }

   public static String mapMethod(String namedOwner, String namedName, String namedDescriptor) {
      if (!identity && !namedName.startsWith("<")) {
         String direct = methods.get(namedOwner + '.' + namedName + namedDescriptor);
         if (direct != null) {
            return direct;
         } else {
            for (String type : walkSupertypes(namedOwner)) {
               String mapped = methods.get(type + '.' + namedName + namedDescriptor);
               if (mapped != null) {
                  return mapped;
               }
            }

            return namedName;
         }
      } else {
         return namedName;
      }
   }

   public static String mapMethodByParameters(String namedOwner, String namedName, String namedParameters) {
      if (!identity && !namedName.startsWith("<")) {
         String direct = methodsByParameters.get(namedOwner + '.' + namedName + namedParameters);
         if (direct != null) {
            return direct;
         } else {
            for (String type : walkSupertypes(namedOwner)) {
               String mapped = methodsByParameters.get(type + '.' + namedName + namedParameters);
               if (mapped != null) {
                  return mapped;
               }
            }

            return namedName;
         }
      } else {
         return namedName;
      }
   }

   public static String unmapDescriptor(String runtimeDescriptor) {
      if (identity) {
         return runtimeDescriptor;
      } else {
         StringBuilder builder = new StringBuilder(runtimeDescriptor.length());
         int index = 0;

         while (index < runtimeDescriptor.length()) {
            char c = runtimeDescriptor.charAt(index);
            if (c == 'L') {
               int end = runtimeDescriptor.indexOf(59, index);
               builder.append('L').append(unmapClass(runtimeDescriptor.substring(index + 1, end))).append(';');
               index = end + 1;
            } else {
               builder.append(c);
               index++;
            }
         }

         return builder.toString();
      }
   }

   private static Iterable<String> walkSupertypes(String namedOwner) {
      Set<String> visited = new HashSet<>();
      Deque<String> queue = new ArrayDeque<>();
      List<String> result = new ArrayList<>();
      queue.add(mapClass(namedOwner));

      while (!queue.isEmpty()) {
         String runtime = queue.poll();
         if (visited.add(runtime)) {
            String[] supertypes = hierarchy.getSupertypes(runtime);
            if (supertypes != null) {
               for (String supertype : supertypes) {
                  if (supertype != null && !visited.contains(supertype)) {
                     result.add(unmapClass(supertype));
                     queue.add(supertype);
                  }
               }
            }
         }
      }

      return result;
   }

   private static String[] runtimeSupertypes(String runtimeInternalName) {
      try {
         Class<?> type = Class.forName(runtimeInternalName.replace('/', '.'), false, Platform.getGameClassLoader());
         Class<?>[] interfaces = type.getInterfaces();
         String[] result = new String[interfaces.length + 1];
         result[0] = type.getSuperclass() == null ? null : type.getSuperclass().getName().replace('.', '/');

         for (int i = 0; i < interfaces.length; i++) {
            result[i + 1] = interfaces[i].getName().replace('.', '/');
         }

         return result;
      } catch (Throwable var5) {
         return null;
      }
   }

   public interface HierarchyProvider {
      String[] getSupertypes(String var1);
   }
}
