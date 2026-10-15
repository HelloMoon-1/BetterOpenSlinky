package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ItemPolicyService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NamedColorListSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final strictfp class ItemEspModule extends EvidenceModule {
   private final List<ItemEspModule.Row> rows = new ArrayList<>();
   private final SortedMap<Integer, ColorSetting.Value> policy = new TreeMap<>((a, b) -> a - b);

   public ItemEspModule() {
      super("item_esp", "Item ESP", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 6 || t == 18 || t == 26;
   }

   private strictfp void refreshPolicy() {
      this.policy.clear();

      for (NamedColorListSetting.Entry e : ((NamedColorListSetting)this.findSetting("allowed_items")).get()) {
         int id = ItemPolicyService.identifier(e.name);
         if (id != -1 && !this.policy.containsKey(Integer.valueOf(id))) {
            this.policy.put(Integer.valueOf(id), e.color);
         }
      }
   }

   @Override
   public strictfp boolean afterSettingControl(String key, ClientContext c) {
      if (key.equals("allowed_items")) {
         this.refreshPolicy();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 26) {
         this.policy.clear();
         this.rows.clear();
      } else if (e.type == 18) {
         this.update(c);
      } else if (c.ready()) {
         RenderAccess.submit(e, this.plan(c, e));
      }
   }

   private strictfp void update(ClientContext c) {
      this.rows.clear();
      this.refreshPolicy();
      if (c.ready()) {
         double[] self = RenderAccess.position(c.player());
         float range = this.number("range");

         for (Entity raw : c.world().loadedEntityList) {
            if (raw instanceof EntityItem && !raw.isDead) {
               EntityItem entity = (EntityItem)raw;
               double[] position = RenderAccess.position(entity);
               if (!(RenderMath.distanceSquared(position, self) > (double)(range * range))) {
                  ItemStack stack = entity.getEntityItem();
                  if (stack != null && stack.stackSize > 0 && stack.getItem() != null) {
                     int id = Item.getIdFromItem(stack.getItem());
                     ColorSetting.Value color = this.policy.get(Integer.valueOf(id));
                     if (color == null) {
                        if (!this.flag("highlight_all")) {
                           continue;
                        }

                        color = this.color("default_color");
                     }

                     this.rows.add(new ItemEspModule.Row(RenderAccess.previous(entity), position, color, id, stack.stackSize));
                     if (this.rows.size() == 100) {
                        break;
                     }
                  }
               }
            }
         }

         if (this.flag("show_count")) {
            mergeCounts(this.rows, RenderConstants.d(1393504));

            for (ItemEspModule.Row row : this.rows) {
               if (row.label != null) {
                  row.width = (int)RenderAccess.width(c, row.label);
               }
            }
         }
      }
   }

   public static strictfp void mergeCounts(List<ItemEspModule.Row> rows, double radiusSquared) {
      for (int i = 0; i < rows.size(); i++) {
         ItemEspModule.Row row = rows.get(i);
         if (row.quantity != 0) {
            long total = (long)row.quantity;

            for (int j = 0; j < rows.size(); j++) {
               if (i != j) {
                  ItemEspModule.Row other = rows.get(j);
                  if (row.id == other.id && RenderMath.distanceSquared(row.position, other.position) < radiusSquared) {
                     total += (long)other.quantity;
                     other.quantity = 0;
                  }
               }
            }

            row.quantity = (int)Math.min(total, 99L);
            row.label = total > 1L ? RenderConstants.text(1393611).replace("%u", Integer.toUnsignedString(row.quantity)) : null;
         }
      }
   }

   public strictfp DrawPlan plan(ClientContext c, SemanticEvent e) {
      DrawPlan p = new DrawPlan();
      float size = RenderConstants.f(1393448);
      double offset = RenderConstants.d(1393440);

      for (ItemEspModule.Row row : this.rows) {
         double[] pos = RenderMath.subtract(
            RenderMath.lerp(row.previous, row.position, (double)e.timerRenderPartialTicks), new double[]{e.cameraX, e.cameraY, e.cameraZ}
         );
         p.push()
            .translate((float)pos[0], (float)(pos[1] + offset), (float)pos[2])
            .scale(size, size, size)
            .color(row.color, RenderConstants.f(1393452))
            .graphics(168960)
            .pop();
      }

      if (this.flag("show_count")) {
         float pitch = c.minecraft().getRenderViewEntity().rotationPitch;
         float yaw = Float.intBitsToFloat(Float.floatToRawIntBits(c.minecraft().getRenderViewEntity().rotationYaw) ^ RenderConstants.i(1393472));
         float one = RenderConstants.f(1393456);
         float textScale = RenderConstants.f(1393488);

         for (ItemEspModule.Row row : this.rows) {
            if (row.label != null) {
               double[] pos = RenderMath.subtract(
                  RenderMath.lerp(row.previous, row.position, (double)e.timerRenderPartialTicks), new double[]{e.cameraX, e.cameraY, e.cameraZ}
               );
               p.push()
                  .translate((float)pos[0], (float)(pos[1] + offset), (float)pos[2])
                  .rotate(yaw, 0.0F, one, 0.0F)
                  .rotate(pitch, one, 0.0F, 0.0F)
                  .scale(textScale, textScale, textScale)
                  .text(row.label, (float)((double)(-row.width) * RenderConstants.d(1393496)), -4.0F, -1, true, RenderAccess.textHeight(c))
                  .pop();
            }
         }
      }

      return p;
   }

   public strictfp List<ItemEspModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.rows.clear();
   }

   public static final strictfp class Row {
      public final double[] previous;
      public final double[] position;
      public final ColorSetting.Value color;
      public final int id;
      public int quantity;
      public String label;
      public int width;

      public Row(double[] previous, double[] position, ColorSetting.Value color, int id, int quantity) {
         this.previous = (double[])previous.clone();
         this.position = (double[])position.clone();
         this.color = color;
         this.id = id;
         this.quantity = quantity & 0xFF;
      }
   }
}
