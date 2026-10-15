package dev.slinky.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class ItemPolicyService {
   private static final Map<String, Integer> IDENTIFIERS = new LinkedHashMap<>();
   private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

   private ItemPolicyService() {
   }

   public static int identifier(String canonicalName) {
      Integer id = IDENTIFIERS.get(canonicalName);
      return id == null ? -1 : id;
   }

   public static String name(int identifier) {
      return NAMES.get(identifier);
   }

   public static boolean contains(List<String> canonicalNames, int identifier) {
      for (String name : canonicalNames) {
         int resolved = identifier(name);
         if (resolved != -1 && resolved == identifier) {
            return true;
         }
      }

      return false;
   }

   public static List<String> canonicalize(List<String> input) {
      TreeSet<Integer> ids = new TreeSet<>(new Comparator<Integer>() {
         public int compare(Integer a, Integer b) {
            return a - b;
         }
      });

      for (String name : input) {
         int id = identifier(name);
         if (id != -1) {
            ids.add(id);
         }
      }

      List<String> result = new ArrayList<>();

      for (Integer id : ids) {
         result.add(name(id));
      }

      return Collections.unmodifiableList(result);
   }

   static {
      try (Reader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/ItemCatalog.json"), "UTF-8")) {
         for (JsonElement e : new JsonParser().parse(reader).getAsJsonArray()) {
            JsonObject row = e.getAsJsonObject();
            String name = row.get("name").getAsString();
            int id = row.get("id").getAsInt();
            IDENTIFIERS.put(name, id);
            NAMES.put(id, name);
         }

         if (IDENTIFIERS.size() != 385) {
            throw new IllegalStateException("Incomplete recovered item catalog");
         }
      } catch (IOException var17) {
         throw new ExceptionInInitializerError(var17);
      }
   }
}
