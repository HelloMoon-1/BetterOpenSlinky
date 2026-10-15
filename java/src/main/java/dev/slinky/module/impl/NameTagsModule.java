package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.RelationshipService;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RecoveredText;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

public final strictfp class NameTagsModule extends EvidenceModule {
   private final List<NameTagsModule.Row> rows = new ArrayList<>();
   private final short[] enchantId = new short[25];
   private final short[] enchantLevel = new short[25];

   public NameTagsModule() {
      super("name_tags", "Name Tags", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 5 || t == 7 || t == 18;
   }

   public strictfp boolean suppressVanilla(Entity entity) {
      return this.isEnabled() && entity instanceof EntityPlayer && (RelationshipService.flags(entity) & 2) == 0;
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 5) {
         if (e.module == this && !e.enabled) {
            this.rows.clear();
         }
      } else if (e.type == 18) {
         this.update(c);
      } else if (c.ready()) {
         RenderAccess.Frame frame = RenderAccess.frame(c, e);
         if (frame != null) {
            RenderAccess.submit(e, this.plan(frame, e.timerRenderPartialTicks, RenderAccess.fov(c, e.timerRenderPartialTicks), RenderAccess.textHeight(c)));
         }
      }
   }

   private strictfp void update(ClientContext c) {
      this.rows.clear();
      if (c.ready()) {
         double[] self = RenderAccess.position(c.player());
         double[] previousSelf = RenderAccess.previous(c.player());
         float range = this.number("range");

         for (EntityPlayer player : c.world().playerEntities) {
            boolean own = player == c.player();
            int flags = RelationshipService.flags(player);
            if ((!own || this.flag("show_own_name_tag") && c.minecraft().gameSettings.thirdPersonView != 0) && (flags & 2) == 0 && !player.isDead) {
               double[] position = RenderAccess.position(player);
               double distance = RenderMath.distanceSquared(position, self);
               if (!(distance > (double)(range * range))) {
                  NameTagsModule.Row row = new NameTagsModule.Row();
                  row.label = this.label(
                     player.getName(), player.getDisplayName().getFormattedText(), flags, own, player.getHealth(), player.getAbsorptionAmount(), distance
                  );
                  if (row.label == null) {
                     break;
                  }

                  row.player = player;
                  row.position = position;
                  row.previous = RenderAccess.previous(player);
                  row.width = (int)RenderAccess.width(c, row.label);
                  row.distance = (float)StrictMath.sqrt((double)((float)distance));
                  row.previousDistance = (float)StrictMath.sqrt(RenderMath.distanceSquared(previousSelf, row.previous));

                  for (int slot = 0; slot < 5; slot++) {
                     ItemStack stack = player.getEquipmentInSlot(slot);
                     row.equipment[slot].present = stack != null;
                     if (stack != null) {
                        row.equipmentCount++;
                     }

                     if (this.flag("show_enchants") && stack != null) {
                        this.enchants(stack, row.equipment[slot].enchantments);
                     }
                  }

                  this.rows.add(row);
                  if (this.rows.size() == 100) {
                     break;
                  }
               }
            }
         }

         Collections.sort(this.rows, (a, b) -> RenderMath.compareDistance(a.distance, b.distance));
      }
   }

   public strictfp String label(String plain, String display, int flags, boolean own, float health, float absorb, double distance) {
      if (this.flag("use_display_name") && !this.flag("show_health") && !this.flag("show_distance")) {
         return display;
      } else {
         RecoveredText text = new RecoveredText(128);
         boolean ok = true;
         if (this.flag("show_health")) {
            health = RenderMath.clamp(0.0F, RenderConstants.f(1390364), health);
            absorb = RenderMath.clamp(0.0F, RenderConstants.f(1390368), absorb);
            char color = (char)(RenderConstants.f(1390372) > health ? 101 : 97);
            if (RenderConstants.f(1390376) > health) {
               color = 'c';
            }

            int precision = this.choice("health_fmt") == 1 ? 1 : 0;
            if (precision == 1) {
               health *= RenderConstants.f(1390380);
               absorb *= RenderConstants.f(1390380);
            }

            ok = text.formatResult(nativeHealth(color, precision, health));
            if (ok && absorb > 0.0F) {
               ok = text.formatResult(nativeAbsorption(precision, absorb));
            }

            if (ok) {
               ok = text.append(RenderConstants.text(1390585));
            }
         }

         if (ok) {
            if (this.flag("use_display_name")) {
               ok = text.append(display);
            } else {
               ok = text.append(RenderConstants.text(1390593)) && text.appendByte((flags & 5) != 0 ? 97 : 99);
               byte[] name = plain.getBytes(StandardCharsets.UTF_8);
               int limit = Math.min(plain.length(), 64);

               for (int i = 0; ok && i < limit && i < name.length; i++) {
                  if ((name[i] & 255) == 194) {
                     i += 2;
                  } else {
                     ok = text.appendByte(name[i]);
                  }
               }
            }
         }

         if (ok && this.flag("show_distance") && !own) {
            double root = StrictMath.sqrt(distance);
            float shortDistance = (float)root;
            char colorx = (char)(RenderConstants.f(1390384) > shortDistance ? 101 : 97);
            if (RenderConstants.f(1390388) > shortDistance) {
               colorx = 'c';
            }

            ok = text.formatResult(String.format(Locale.ROOT, RenderConstants.text(1390596), Integer.valueOf(colorx), root));
         }

         String result = ok ? text.finish() : null;
         return result == null ? (this.flag("use_display_name") ? display : plain) : result;
      }
   }

   private static strictfp String nativeHealth(char color, int precision, float value) {
      return RenderConstants.text(1390567)
         .replace("%c", String.valueOf(color))
         .replace("%.*f", String.format(Locale.ROOT, "%." + precision + "f", (double)value));
   }

   private static strictfp String nativeAbsorption(int precision, float value) {
      return RenderConstants.text(1390576).replace("%.*f", String.format(Locale.ROOT, "%." + precision + "f", (double)value));
   }

   private strictfp void enchants(ItemStack stack, List<String> output) {
      NBTTagList tags = stack.getEnchantmentTagList();
      if (tags != null) {
         int count = tags.tagCount();
         int limit = Math.min(count, 25);

         for (int i = 0; i < limit; i++) {
            int id = tags.getCompoundTagAt(i).getShort("id") & '\uffff';
            if (id <= 62 && (2301620820134395392L >>> id & 1L) == 0L) {
               int level = tags.getCompoundTagAt(i).getShort("lvl");
               this.enchantId[i] = (short)id;
               this.enchantLevel[i] = (short)Math.max(0, Math.min(level, 99));
            }
         }

         List<Integer> indices = new ArrayList<>();

         for (int ix = 0; ix < limit; ix++) {
            indices.add(ix);
         }

         Collections.sort(indices, (a, b) -> RenderConstants.i(1391296 + 4 * this.enchantId[a]) - RenderConstants.i(1391296 + 4 * this.enchantId[b]));
         short[] ids = (short[])this.enchantId.clone();
         short[] levels = (short[])this.enchantLevel.clone();

         for (int ix = 0; ix < limit; ix++) {
            this.enchantId[ix] = ids[indices.get(ix)];
            this.enchantLevel[ix] = levels[indices.get(ix)];
         }

         for (int ix = 0; ix < Math.min(count, 4); ix++) {
            int id = this.enchantId[ix] & '\uffff';
            String abbr = id <= 62 ? RenderConstants.pointerText(1390704 + 8 * id) : null;
            String label = String.format(Locale.ROOT, RenderConstants.text(1390692), abbr, Integer.valueOf(this.enchantLevel[ix]));
            if (label.length() > 5) {
               label = label.substring(0, 5);
            }

            output.add(label);
         }
      }
   }

   public strictfp DrawPlan plan(RenderAccess.Frame frame, float partial, float fov, float fontHeight) {
      return this.plan(frame, partial, fov, fontHeight, (row, slot) -> row.player.getEquipmentInSlot(slot));
   }

   public strictfp DrawPlan plan(RenderAccess.Frame frame, float partial, float fov, float fontHeight, NameTagsModule.EquipmentHost equipmentHost) {
      DrawPlan p = new DrawPlan().op("itemDepth", RenderConstants.f(1390288));
      float fovScale = RenderConstants.f(1390292) / fov;
      float one = RenderConstants.f(1390312);
      float half = RenderConstants.f(1390336);
      float height = RenderConstants.f(1390352);
      float pad = RenderConstants.f(1390356);
      double center = RenderConstants.d(1390320);

      for (NameTagsModule.Row row : this.rows) {
         double[] pos = RenderMath.lerp(row.previous, row.position, (double)partial);
         pos[1] += RenderConstants.d(1390296);
         RenderMath.Projection projected = frame.project(RenderMath.subtract(pos, frame.camera));
         if (projected != null) {
            float factor = projected.reciprocal * RenderConstants.f(1390308);
            if (this.flag("distance_scaling")) {
               factor = RenderMath.max(factor, one);
            }

            float size = this.number("scale") * fovScale * factor;
            float left = (float)((double)(-row.width) * center + RenderConstants.d(1390328));
            p.push().translate(projected.x, projected.y, projected.reciprocal * RenderConstants.f(1390304)).scale(size, size, size).disable(2929);
            if (this.flag("background")) {
               float right = (float)((double)row.width * center + RenderConstants.d(1390344));
               p.color(0.0F, 0.0F, 0.0F, half).vertices(5, 2, left, 0.0F, right, 0.0F, left, height, right, height);
            }

            p.text(row.label, left + pad, -10.0F, -1, this.flag("text_shadow"), fontHeight);
            if (this.flag("show_equipment")) {
               p.enable(2929).scale(one, one, RenderConstants.f(1390360));
               int x = -(row.equipmentCount << 3);

               for (int slot = 0; slot < row.equipment.length; slot++) {
                  NameTagsModule.Equipment gear = row.equipment[slot];
                  if (gear.present) {
                     ItemStack current = equipmentHost.current(row, slot);
                     if (current != null) {
                        p.item(current, (float)x, -32.0F);
                        x += 16;
                     }
                  }
               }

               p.disable(3008).scale(half, half, half);
               if (this.flag("show_enchants")) {
                  x = -(row.equipmentCount << 3);

                  for (NameTagsModule.Equipment gear : row.equipment) {
                     if (gear.present) {
                        int y = -64;

                        for (String label : gear.enchantments) {
                           p.text(label, (float)(x + x), (float)y, -1, true, fontHeight);
                           y += 8;
                        }

                        x += 16;
                     }
                  }
               }
            }

            p.pop();
         }
      }

      return p;
   }

   public strictfp List<NameTagsModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.rows.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.rows.clear();
   }

   public static final class Equipment {
      public boolean present;
      public final List<String> enchantments = new ArrayList<>();
   }

   public interface EquipmentHost {
      ItemStack current(NameTagsModule.Row var1, int var2);
   }

   public static final strictfp class Row {
      public double[] previous;
      public double[] position;
      public EntityPlayer player;
      public String label;
      public int width;
      public int equipmentCount;
      public float distance;
      public float previousDistance;
      public final NameTagsModule.Equipment[] equipment = new NameTagsModule.Equipment[5];

      public Row() {
         for (int i = 0; i < 5; i++) {
            this.equipment[i] = new NameTagsModule.Equipment();
         }
      }
   }
}
