package dev.slinky.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slinky.client.ItemPolicyService;
import dev.slinky.inject.Platform;
import dev.slinky.module.EvidenceCatalog;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.RecoveredUiLayout;
import dev.slinky.module.impl.GhostHandModule;
import dev.slinky.module.impl.WeaponsModule;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.ChoiceSetting;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NamedColorListSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.RangeSetting;
import dev.slinky.setting.Setting;
import dev.slinky.setting.StringListSetting;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class InlineModuleSettings {
   private static final JsonObject METADATA = readMetadata();
   private static final Map<String, List<String>> ORDER = readOrder();
   private static final List<String> ITEMS = readItems(false);
   private static final List<String> BLOCKS = readItems(true);
   private final ModuleManager manager;
   private final GuiWidgets widgets;
   private final Map<Setting<?>, InlineModuleSettings.Record> records = new IdentityHashMap<>();
   private final RecoveredGui.Toggle sticks = new RecoveredGui.Toggle();
   private final RecoveredGui.Toggle swordExcluded = new RecoveredGui.Toggle();

   public InlineModuleSettings(ModuleManager manager, GuiPresentation presentation, GuiPresentation.Tooltip tooltip) {
      this.manager = manager;
      this.widgets = new GuiWidgets(presentation, tooltip, (format, values) -> {
         Object[] args = new Object[values.length];

         for (int i = 0; i < values.length; i++) {
            args[i] = values[i];
         }

         return String.format(Locale.ROOT, format.replace("%0.", "%."), args);
      });
   }

   private static JsonObject readMetadata() {
      try (InputStreamReader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/WidgetMetadata.json"), StandardCharsets.UTF_8)) {
         return new JsonParser().parse(reader).getAsJsonObject();
      } catch (IOException var14) {
         throw new ExceptionInInitializerError(var14);
      }
   }

   private static Map<String, List<String>> readOrder() {
      Map<String, List<String>> result = new LinkedHashMap<>();

      try (InputStreamReader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/ModuleUi.json"), StandardCharsets.UTF_8)) {
         for (JsonElement element : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("modules")) {
            JsonObject module = element.getAsJsonObject();
            Map<String, Set<String>> edges = new LinkedHashMap<>();

            for (JsonElement variant : module.getAsJsonArray("variants")) {
               String previous = null;
               Map<String, Integer> counts = new HashMap<>();

               for (JsonElement control : variant.getAsJsonObject().getAsJsonArray("controls")) {
                  JsonObject row = control.getAsJsonObject();
                  String token;
                  if (row.has("section")) {
                     String label = row.get("section").getAsString();
                     int count = counts.containsKey(label) ? counts.get(label) : 0;
                     counts.put(label, count + 1);
                     token = "section:" + count + ":" + label;
                  } else {
                     token = row.get("key").getAsString();
                  }

                  if (!edges.containsKey(token)) {
                     edges.put(token, new LinkedHashSet<>());
                  }

                  if (previous != null) {
                     edges.get(previous).add(token);
                  }

                  previous = token;
               }
            }

            List<String> order = new ArrayList<>();

            while (order.size() < edges.size()) {
               String next = null;

               for (String tokenx : edges.keySet()) {
                  if (!order.contains(tokenx)) {
                     boolean incoming = false;

                     for (Entry<String, Set<String>> edge : edges.entrySet()) {
                        if (!order.contains(edge.getKey()) && edge.getValue().contains(tokenx)) {
                           incoming = true;
                           break;
                        }
                     }

                     if (!incoming) {
                        next = tokenx;
                        break;
                     }
                  }
               }

               if (next == null) {
                  throw new IllegalStateException("Contradictory recovered UI order " + module.get("module").getAsString());
               }

               order.add(next);
            }

            result.put(module.get("module").getAsString(), Collections.unmodifiableList(order));
         }

         return Collections.unmodifiableMap(result);
      } catch (IOException var27) {
         throw new ExceptionInInitializerError(var27);
      }
   }

   private InlineModuleSettings.Record record(Setting<?> setting) {
      InlineModuleSettings.Record record = this.records.get(setting);
      if (record == null) {
         record = new InlineModuleSettings.Record();
         this.records.put(setting, record);
      }

      return record;
   }

   private static String text(JsonObject row, String key, String fallback) {
      JsonElement value = row == null ? null : row.get(key);
      return value != null && !value.isJsonNull() ? value.getAsString() : fallback;
   }

   private static float parameter(JsonObject row, String key) {
      JsonElement value = row == null ? null : row.get(key);
      return value == null || value.isJsonNull() ? 0.0F : value.getAsJsonObject().get("value").getAsFloat();
   }

   private static JsonObject schema(Module module, Setting<?> setting) {
      JsonObject descriptor = EvidenceCatalog.moduleOrNull(module.getName());
      if (descriptor != null) {
         for (JsonElement row : descriptor.getAsJsonArray("settings")) {
            JsonObject value = row.getAsJsonObject();
            if (value.get("key").getAsString().equals(setting.getKey())) {
               return value;
            }
         }
      }
      return null;
   }

   private static JsonObject metadata(Module module, Setting<?> setting) {
      JsonObject descriptor = METADATA.getAsJsonObject(module.getName());
      return descriptor == null ? null : descriptor.getAsJsonObject(setting.getKey());
   }

   private static String numberFormat(NumberSetting setting) {
      int decimals = setting.getStep() > 0.0
         ? Math.min(6, Math.max(0, BigDecimal.valueOf(setting.getStep()).stripTrailingZeros().scale())) : 2;
      return "%." + decimals + "f";
   }

   private static Map<String, RecoveredUiLayout.Control> controls(Module module) {
      Map<String, RecoveredUiLayout.Control> visible = new LinkedHashMap<>();
      Map<String, Integer> counts = new HashMap<>();
      for (RecoveredUiLayout.Control control : RecoveredUiLayout.controls(module)) {
         String token;
         if (control.setting == null) {
            int count = counts.containsKey(control.section) ? counts.get(control.section) : 0;
            counts.put(control.section, count + 1);
            token = "section:" + count + ":" + control.section;
         } else {
            token = control.setting.getKey();
         }
         visible.put(token, control);
      }
      Map<String, RecoveredUiLayout.Control> ordered = new LinkedHashMap<>();
      List<String> recovered = ORDER.get(module.getName());
      if (recovered != null) {
         for (String token : recovered) {
            if (visible.containsKey(token)) {
               ordered.put(token, visible.get(token));
            }
         }
      }
      // New settings use their source declaration order. The layout provider
      // has already applied visibility and the recovered variant gates.
      ordered.putAll(visible);
      return ordered;
   }

   public List<String> render(Module module, RecoveredGui.Runtime state, DrawPlan plan, RecoveredGui.Layout layout, float[] accent, double now) {
      List<String> drawn = new ArrayList<>();

      for (Entry<String, RecoveredUiLayout.Control> entry : controls(module).entrySet()) {
         String token = entry.getKey();
         RecoveredUiLayout.Control next = entry.getValue();

         if (next != null) {
            drawn.add(token);
            if (next.setting == null) {
               this.widgets.section(plan, layout, next.section);
            } else {
               Setting<?> setting = next.setting;
               if (module instanceof WeaponsModule && setting.getKey().equals("extra_items")) {
                  WeaponsModule weapons = (WeaponsModule)module;
                  boolean old = weapons.sticksEnabled();
                  boolean nextValue = this.widgets.checkbox(state, plan, layout, "Sticks", this.sticks, old, accent, now);
                  if (nextValue != old) {
                     weapons.setSticksEnabled(nextValue);
                     this.changed(module, setting);
                  }

                  drawn.add("derived:sticks");
               }

               this.draw(module, setting, state, plan, layout, accent, now);
               if (module instanceof GhostHandModule && setting.getKey().equals("require_mouse_pressed")) {
                  GhostHandModule ghost = (GhostHandModule)module;
                  boolean old = ghost.swordExcluded();
                  boolean nextValue = this.widgets.checkbox(state, plan, layout, "Not holding a sword", this.swordExcluded, old, accent, now);
                  if (nextValue != old) {
                     ghost.setSwordExcluded(nextValue);
                     this.changed(module, module.findSetting("allow_using_sword"));
                  }

                  drawn.add("derived:sword_excluded");
               }
            }
         }
      }

      return Collections.unmodifiableList(drawn);
   }

   private void changed(Module module, Setting<?> setting) {
      module.afterSettingControl(setting.getKey(), this.manager.context());
      this.manager.configurationSession().controlChanged();
      this.manager.persist();
   }

   private void draw(Module module, Setting<?> setting, RecoveredGui.Runtime state, DrawPlan plan, RecoveredGui.Layout layout, float[] accent, double now) {
      JsonObject info = metadata(module, setting);
      JsonObject schema = schema(module, setting);
      String label = text(info, "label", setting.getLabel());
      InlineModuleSettings.Record r = this.record(setting);
      boolean mutated = false;
      if (setting instanceof BooleanSetting) {
         BooleanSetting value = (BooleanSetting)setting;
         boolean next = this.widgets.checkbox(state, plan, layout, label, r.toggle, value.isEnabled(), accent, now);
         if (next != value.isEnabled()) {
            value.set(Boolean.valueOf(next));
            mutated = true;
         }
      } else if (setting instanceof NumberSetting) {
         NumberSetting value = (NumberSetting)setting;
         float next = this.widgets
            .number(
               state,
               plan,
               layout,
               label,
               text(info, "format", schema == null ? numberFormat(value) : null),
               r.lower,
               r.number,
               (float)value.getMinimum(),
               (float)value.getMaximum(),
               schema == null ? (float)value.getStep() : parameter(schema, "step_observation"),
               parameter(schema, "extra_ui_parameter_observation"),
               value.floatValue(),
               accent,
               now
            );
         if (Float.floatToRawIntBits(next) != Float.floatToRawIntBits(value.floatValue())) {
            value.set(Double.valueOf((double)next));
            mutated = true;
         }
      } else if (setting instanceof RangeSetting) {
         RangeSetting value = (RangeSetting)setting;
         RangeSetting.Value old = value.get();
         JsonObject extra = schema == null ? null : schema.getAsJsonObject("extra_ui_parameter_observations");
         float step = extra == null ? 0.0F : parameter(extra, "0x30");
         float scale = extra == null ? 0.0F : parameter(extra, "0x34");
         float[] next = this.widgets
            .range(
               state,
               plan,
               layout,
               label,
               text(info, "format", schema == null ? "%.2f" : null),
               text(info, "pair_format", schema == null ? "%.2f - %.2f" : null),
               r.lower,
               r.upper,
               r.range,
               (float)value.minimum(),
               (float)value.maximum(),
               step,
               scale,
               (float)old.lower,
               (float)old.upper,
               accent,
               now
            );
         if (next[0] != (float)old.lower || next[1] != (float)old.upper) {
            value.set(new RangeSetting.Value((double)next[0], (double)next[1]));
            mutated = true;
         }
      } else if (setting instanceof ChoiceSetting) {
         ChoiceSetting value = (ChoiceSetting)setting;
         r.selected[0] = value.index();
         this.widgets.choice(state, plan, layout, label, r.choice, value.getOptions(), r.selected, info != null && info.has("flag") ? info.get("flag").getAsInt() : 0, accent, now, index -> {
            if (index != value.index()) {
               value.set(Integer.valueOf(index));
               this.changed(module, setting);
            }
         });
         if (r.selected[0] != value.index()) {
            value.set(Integer.valueOf(r.selected[0]));
            mutated = true;
         }
      } else if (setting instanceof ColorSetting) {
         ColorSetting value = (ColorSetting)setting;
         ColorSetting.Value rgb = value.get();
         this.widgets.color(state, plan, layout, label, new float[]{rgb.red, rgb.green, rgb.blue}, nextx -> {
            ColorSetting.Value old = value.get();
            if (!value.isConfigured() || old.red != nextx[0] || old.green != nextx[1] || old.blue != nextx[2]) {
               value.set(new ColorSetting.Value(nextx[0], nextx[1], nextx[2]));
               this.changed(module, setting);
            }
         }, now);
      } else {
         this.widgets
            .list(
               state,
               plan,
               layout,
               label,
               text(info, "title", label),
               filter(module, setting),
               setting instanceof NamedColorListSetting ? 1 : 0,
               this.list(module, setting),
               info != null && info.has("suggestions_endpoint_present") && info.get("suggestions_endpoint_present").getAsBoolean()
                  ? suggestions(module) : null,
               now
            );
      }

      // Opening or drawing a control is not an edit. Some callbacks open an
      // editor or replace the screen, so they must only run after a mutation.
      if (mutated) {
         this.changed(module, setting);
      }
   }

   private static RecoveredGui.Filter filter(Module module, Setting<?> setting) {
      return module.getName().equals("Friends") ? (text, ch) -> {
         if (text.length() < 16 && (ch >= 97 && ch <= 122 || ch >= 65 && ch <= 90 || ch >= 48 && ch <= 57 || ch == 95)) {
            text.append((char)ch);
         }
      } : RecoveredGui::catalogFilter;
   }

   private static List<String> readItems(boolean blocks) {
      try (InputStreamReader reader = new InputStreamReader(Platform.resource("/assets/slinkysemantic/ItemCatalog.json"), StandardCharsets.UTF_8)) {
         List<String> names = new ArrayList<>();

         for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            if (!blocks || item.get("id").getAsInt() < 256) {
               names.add(item.get("name").getAsString());
            }
         }

         return Collections.unmodifiableList(names);
      } catch (IOException var17) {
         throw new ExceptionInInitializerError(var17);
      }
   }

   private static List<String> suggestions(Module module) {
      return module.getName().equals("Block ESP") ? BLOCKS : ITEMS;
   }

   private GuiWidgets.ListData list(final Module module, final Setting<?> setting) {
      return new GuiWidgets.ListData() {
         @Override
         public long size() {
            return setting instanceof NamedColorListSetting
               ? (long)((NamedColorListSetting)setting).get().size()
               : (long)((StringListSetting)setting).get().size();
         }

         @Override
         public String name(long index) {
            return setting instanceof NamedColorListSetting
               ? ((NamedColorListSetting)setting).get().get((int)index).name
               : ((StringListSetting)setting).get().get((int)index);
         }

         @Override
         public float[] color(long index) {
            ColorSetting.Value value = ((NamedColorListSetting)setting).get().get((int)index).color;
            return new float[]{value.red, value.green, value.blue};
         }

         @Override
         public void color(long index, float[] rgb) {
            NamedColorListSetting value = (NamedColorListSetting)setting;
            List<NamedColorListSetting.Entry> rows = new ArrayList<>(value.get());
            ColorSetting.Value old = rows.get((int)index).color;
            if (old.red == rgb[0] && old.green == rgb[1] && old.blue == rgb[2]) {
               return;
            }
            rows.set((int)index, new NamedColorListSetting.Entry(rows.get((int)index).name, new ColorSetting.Value(rgb[0], rgb[1], rgb[2])));
            value.set(rows);
            InlineModuleSettings.this.changed(module, setting);
         }

         @Override
         public void add(String name, float[] rgb) {
            if (setting instanceof NamedColorListSetting) {
               int id = module.getName().equals("Block ESP") ? LoadedChunkAccess.blockIdentifier(name) : ItemPolicyService.identifier(name);
               if (id < 0) {
                  return;
               }

               NamedColorListSetting value = (NamedColorListSetting)setting;
               List<NamedColorListSetting.Entry> rows = new ArrayList<>(value.get());

               for (NamedColorListSetting.Entry row : rows) {
                  if (row.name.equals(name)) {
                     return;
                  }
               }

               rows.add(new NamedColorListSetting.Entry(name, new ColorSetting.Value(rgb[0], rgb[1], rgb[2])));
               rows.sort((a, b) -> Integer.compare(ItemPolicyService.identifier(a.name), ItemPolicyService.identifier(b.name)));
               value.set(rows);
            } else {
               StringListSetting value = (StringListSetting)setting;
               List<String> rows = new ArrayList<>(value.get());
               if (rows.contains(name)) {
                  return;
               }

               rows.add(name);
               value.set(rows);
            }

            InlineModuleSettings.this.changed(module, setting);
         }

         @Override
         public void remove(long index) {
            if (setting instanceof NamedColorListSetting) {
               NamedColorListSetting value = (NamedColorListSetting)setting;
               List<NamedColorListSetting.Entry> rows = new ArrayList<>(value.get());
               rows.remove((int)index);
               value.set(rows);
            } else {
               StringListSetting value = (StringListSetting)setting;
               List<String> rows = new ArrayList<>(value.get());
               rows.remove((int)index);
               value.set(rows);
            }

            InlineModuleSettings.this.changed(module, setting);
         }
      };
   }

   private static final class Record {
      final Object lower = new Object();
      final Object upper = new Object();
      final RecoveredGui.Toggle toggle = new RecoveredGui.Toggle();
      final RecoveredGui.Slider number = new RecoveredGui.Slider();
      final GuiWidgets.Range range = new GuiWidgets.Range();
      final GuiWidgets.Choice choice = new GuiWidgets.Choice();
      final int[] selected = new int[]{0};

      private Record() {
      }
   }
}
