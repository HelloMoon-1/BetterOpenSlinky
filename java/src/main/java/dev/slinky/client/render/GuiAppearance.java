package dev.slinky.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.slinky.module.EvidenceCatalog;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GuiAppearance {
   public static final String[] TABS = new String[]{"Combat", "Move", "Visual", "Block", "Utility", "Profiles", "Unload"};
   private static final int[] CATEGORIES = new int[]{0, 1, 2, 3, 4, 5};

   public static int category(int tab) {
      return CATEGORIES[tab];
   }

   public static int tab(int category) {
      for (int i = 0; i < CATEGORIES.length; i++) {
         if (CATEGORIES[i] == category) {
            return i;
         }
      }

      return tab(4);
   }

   public static boolean includes(int page, int category) {
      return page == category;
   }

   public static List<Module> entries(ModuleManager manager, int page) {
      List<Module> entries = new ArrayList<>();

      for (Module module : manager.all()) {
         if (includes(page, module.metadataCategory())) {
            entries.add(module);
         }
      }

      Collections.sort(entries, Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
      return entries;
   }

   public static String description(Module module) {
      JsonObject record = EvidenceCatalog.moduleOrNull(module.getName());
      JsonArray observations = record == null ? null : record.getAsJsonArray("descriptor_observations");
      if (observations == null || observations.size() == 0) {
         return module.getDescription();
      }
      JsonElement description = observations.get(0).getAsJsonObject().get("description");
      return description == null || description.isJsonNull() ? module.getDescription() : description.getAsString();
   }

   public static void configureMissing(Module gui) {
      if (gui != null) {
         boolean fresh = true;

         for (Setting<?> setting : gui.getSettings()) {
            fresh &= !setting.isConfigured();
         }

         if (fresh && gui.getBinding() == 0) {
            gui.setBinding(99);
         }

         configure(gui, "scale", "Medium (100%)");
         configure(gui, "accent_color", "1.0,0.35,0.0");
         configure(gui, "allow_input", "false");
         configure(gui, "compact", "false");
         configure(gui, "wide", "false");
      }
   }

   private static void configure(Module gui, String key, String value) {
      Setting<?> setting = gui.findSetting(key);
      if (!setting.isConfigured()) {
         setting.parseAndSet(value);
         setting.saveCurrent();
      }
   }

   private GuiAppearance() {
   }
}
