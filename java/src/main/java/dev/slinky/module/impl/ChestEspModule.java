package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderMath;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;

public final strictfp class ChestEspModule extends EvidenceModule {
   private final List<ChestEspModule.Row> rows = new ArrayList<>();

   public ChestEspModule() {
      super("chest_esp", "Chest ESP", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 0 || t == 6;
   }

   private static strictfp String kind(int id) {
      return id == 54 ? "chest" : (id == 130 ? "ender_chest" : (id == 146 ? "trapped_chest" : (id == 138 ? "beacon" : null)));
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 0) {
         this.rows.clear();
         if (!c.ready()) {
            return;
         }

         double[] self = RenderAccess.position(c.player());
         float range = this.number("range");

         for (TileEntity tile : c.world().loadedTileEntityList) {
            BlockPos pos = tile.getPos();
            int id = Block.getIdFromBlock(c.world().getBlockState(pos).getBlock());
            String kind = kind(id);
            if (kind != null && this.flag("render_" + kind)) {
               double[] p = new double[]{(double)pos.getX(), (double)pos.getY(), (double)pos.getZ()};
               if (!(RenderMath.distanceSquared(p, self) > (double)(range * range))) {
                  this.rows.add(new ChestEspModule.Row(p, id));
                  if (this.rows.size() == 100) {
                     break;
                  }
               }
            }
         }
      } else {
         RenderAccess.submit(e, this.plan(e));
      }
   }

   public strictfp DrawPlan plan(SemanticEvent e) {
      DrawPlan p = new DrawPlan().lineWidth(this.number("line_width"));
      float scale = RenderConstants.f(1383008);

      for (ChestEspModule.Row r : this.rows) {
         double[] relative = RenderMath.subtract(r.position, new double[]{e.cameraX, e.cameraY, e.cameraZ});
         p.push()
            .translate((float)(relative[0] + RenderConstants.d(1383120)), (float)(relative[1] + RenderConstants.d(1383128)), (float)(relative[2] + 0.5))
            .scale(scale, scale, scale);
         if (this.flag("filled")) {
            p.color(this.color("color_" + kind(r.id)), RenderConstants.f(1383012)).graphics(168960);
         }

         p.color(this.color("color_" + kind(r.id)), 1.0F).graphics(169008).pop();
      }

      return p;
   }

   public strictfp List<ChestEspModule.Row> rows() {
      return Collections.unmodifiableList(this.rows);
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.rows.clear();
   }

   public static final strictfp class Row {
      public final double[] position;
      public final int id;

      public Row(double[] p, int id) {
         this.position = (double[])p.clone();
         this.id = id;
      }
   }
}
