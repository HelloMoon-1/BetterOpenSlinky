package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.BedPlatesRenderer;
import dev.slinky.client.render.BedPlatesWorld;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;

/** Defense information derived exclusively from real, already-loaded client-world blocks. */
public final class BedPlatesModule extends Module {
   private final BedPlatesWorld beds = new BedPlatesWorld();
   private final BooleanSetting showDistance;
   private final NumberSetting range;
   private final NumberSetting scale;

   public BedPlatesModule() {
      super("bed_plates", "Bed Plates", "Shows the actual defense blocks surrounding loaded beds.", ModuleCategory.RENDER);
      this.showDistance = this.option(new BooleanSetting("show_distance", "Show distance", true));
      this.range = this.option(new NumberSetting("range", "Range", 100.0, 16.0, 100.0, 1.0));
      this.scale = this.option(new NumberSetting("scale", "Scale", 2.0, 0.5, 4.0, 0.1));
   }

   private <T, S extends Setting<T>> S option(S setting) {
      setting.set(setting.get());
      setting.saveCurrent();
      return this.add(setting);
   }

   @Override
   public boolean listens(int type) { return type == 7; }

   @Override
   public void onClientTick(ClientContext c) { this.beds.update(c, this.range.doubleValue()); }

   @Override
   public void onEvent(ClientContext c, SemanticEvent event) {
      if (event.type != 7 || event.cancelled) return;
      if (!this.beds.matches(c)) { this.beds.clear(); return; }
      if (c.minecraft().gameSettings.hideGUI) return;
      RenderAccess.Frame frame = RenderAccess.frame(c, event);
      if (frame != null) {
         RenderAccess.submit(event, BedPlatesRenderer.plan(c, frame, this.beds.beds(),
            this.showDistance.isEnabled(), this.scale.floatValue(), this.range.doubleValue()));
      }
   }

   @Override
   protected void onEnable(ClientContext c) { this.beds.clear(); }

   @Override
   protected void onDisable(ClientContext c) { this.beds.clear(); }

   @Override
   public void onWorldUnload(ClientContext c) { this.beds.clear(); }
}
