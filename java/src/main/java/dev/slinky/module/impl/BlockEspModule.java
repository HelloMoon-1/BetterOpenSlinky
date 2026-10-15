package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.numeric.NumericServices;
import dev.slinky.client.render.BlockMesh;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.LoadedChunkAccess;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.client.render.RenderConstants;
import dev.slinky.client.render.RenderEventPayload;
import dev.slinky.module.EvidenceModule;
import dev.slinky.module.ModuleCategory;
import dev.slinky.setting.ColorSetting;
import dev.slinky.setting.NamedColorListSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public final strictfp class BlockEspModule extends EvidenceModule {
   private final List<BlockMesh.Region> regions = new ArrayList<>();
   private final SortedMap<Integer, ColorSetting.Value> policy = new TreeMap<>((a, b) -> a - b);
   private boolean recollect;
   private boolean dirty;

   public BlockEspModule() {
      super("block_esp", "Block ESP", ModuleCategory.RENDER);
   }

   @Override
   public strictfp boolean listens(int t) {
      return t == 0 || t == 5 || t == 6 || t == 20 || t == 26;
   }

   private strictfp void refreshPolicy() {
      this.policy.clear();

      for (NamedColorListSetting.Entry e : ((NamedColorListSetting)this.findSetting("allowed_blocks")).get()) {
         int id = LoadedChunkAccess.blockIdentifier(e.name);
         if (id != -1 && !this.policy.containsKey(Integer.valueOf(id))) {
            this.policy.put(Integer.valueOf(id), e.color);
         }
      }
   }

   @Override
   public strictfp boolean afterSettingControl(String key, ClientContext c) {
      if (!key.equals("allowed_blocks")) {
         return false;
      } else {
         this.release();
         this.refreshPolicy();
         this.recollect = true;
         return true;
      }
   }

   @Override
   protected strictfp void onEnable(ClientContext c) {
      this.refreshPolicy();
   }

   @Override
   protected strictfp void onDisable(ClientContext c) {
      this.release();
   }

   @Override
   public strictfp void onEvent(ClientContext c, SemanticEvent e) {
      RenderEventPayload payload = RenderEventPayload.get(e);
      if (e.type == 20 && payload != null) {
         this.regionChanged(payload.world, payload.regionKind, payload.x, payload.y, payload.z, payload.blockId);
      } else if (e.type == 26) {
         this.release();
         this.policy.clear();
      } else if (e.type == 0) {
         if (c.inputReady() && this.dirty) {
            for (BlockMesh.Region region : this.regions) {
               BlockMesh.rebuild(region);
            }

            this.dirty = false;
         }

         if (!c.ready()) {
            this.release();
         } else if (this.recollect) {
            this.release();
            this.collect(c);
            this.recollect = false;
         }
      } else if (e.type == 5) {
         if (e.module == this) {
            if (e.enabled) {
               this.refreshPolicy();
               if (c.ready()) {
                  this.collect(c);
               }
            } else {
               this.release();
            }
         }
      } else if (e.type == 6) {
         RenderAccess.submit(e, this.plan(e));
      }
   }

   public strictfp void regionChanged(ClientContext c, int kind, int x, int y, int z, int blockId) {
      this.regionChanged(kind == 1 && c != null && c.minecraft() != null && c.ready() ? c.world() : null, kind, x, y, z, blockId);
   }

   public strictfp void regionChanged(World world, int kind, int x, int y, int z, int blockId) {
      if (this.isEnabled()) {
         if (Integer.compareUnsigned(kind, 2) < 0) {
            this.remove(x, z);
            if (kind == 1 && world != null) {
               this.scan(world.getChunkFromChunkCoords(x, z));
            }
         } else if (kind == 2) {
            int cx = x >> 4;
            int cz = z >> 4;
            BlockMesh.Region region = this.find(cx, cz);
            ColorSetting.Value color = this.policy.get(Integer.valueOf(blockId));
            if (region == null) {
               if (color == null) {
                  return;
               }

               region = new BlockMesh.Region(cx, cz);
               this.insert(region);
            }

            BlockMesh.change(region, x - (cx << 4), y, z - (cz << 4), blockId, color);
            if (region.blocks.isEmpty()) {
               this.remove(cx, cz);
            }
         }
      }
   }

   private strictfp BlockMesh.Region find(int x, int z) {
      for (BlockMesh.Region r : this.regions) {
         if (r.x == x && r.z == z) {
            return r;
         }
      }

      return null;
   }

   private strictfp void insert(BlockMesh.Region region) {
      int i = 0;

      while (i < this.regions.size() && BlockMesh.compareRegion(region.x, region.z, this.regions.get(i).x, this.regions.get(i).z) > 0) {
         i++;
      }

      this.regions.add(i, region);
   }

   private strictfp void remove(int x, int z) {
      BlockMesh.Region r = this.find(x, z);
      if (r != null) {
         r.release();
         this.regions.remove(r);
      }
   }

   private strictfp void collect(ClientContext c) {
      for (Chunk chunk : LoadedChunkAccess.loaded(c)) {
         this.scan(chunk);
      }
   }

   private strictfp void scan(Chunk chunk) {
      this.remove(chunk.xPosition, chunk.zPosition);
      BlockMesh.Region region = null;
      ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

      for (int section = 0; section < 16; section++) {
         if (sections[section] != null) {
            List<BlockMesh.Position> found = BlockMesh.scanPacked(section, sections[section].getData(), this.policy);
            if (!found.isEmpty()) {
               if (region == null) {
                  region = new BlockMesh.Region(chunk.xPosition, chunk.zPosition);
                  this.insert(region);
               }

               region.blocks.addAll(found);
            }
         }
      }

      if (region != null) {
         BlockMesh.rebuild(region);
      }
   }

   public strictfp DrawPlan plan(SemanticEvent e) {
      DrawPlan p = new DrawPlan().lineWidth(this.number("line_width"));
      long limit = BlockMesh.renderLimit(this.flag("should_limit_block_render"), this.number("block_render_limit"));
      double scale = RenderConstants.d(1392976);
      double cx = e.cameraX * scale;
      double cz = scale * e.cameraZ;
      double midpoint = RenderConstants.d(1392992);

      for (BlockMesh.Region region : this.regions) {
         if (!(NumericServices.hypot(cx - ((double)region.x + midpoint), cz - ((double)region.z + midpoint)) > (double)this.number("range"))) {
            p.push().translate((float)((double)(region.x << 4) - e.cameraX), (float)(-e.cameraY), (float)((double)(region.z << 4) - e.cameraZ));
            long amount = Long.compareUnsigned((long)region.indices.length, limit) > 0 ? limit : (long)region.indices.length;

            for (int i = 0; (long)(i + 1) < amount; i += 2) {
               int a = region.indices[i] & '\uffff';
               int b = region.indices[i + 1] & '\uffff';
               int color = region.colors[a];
               p.color((float)(color >>> 24 & 0xFF) / 255.0F, (float)(color >>> 16 & 0xFF) / 255.0F, (float)(color >>> 8 & 0xFF) / 255.0F, 1.0F)
                  .vertices(
                     1,
                     3,
                     region.vertices[a * 3],
                     region.vertices[a * 3 + 1],
                     region.vertices[a * 3 + 2],
                     region.vertices[b * 3],
                     region.vertices[b * 3 + 1],
                     region.vertices[b * 3 + 2]
                  );
            }

            p.pop();
         }
      }

      return p;
   }

   public strictfp List<BlockMesh.Region> regions() {
      return Collections.unmodifiableList(this.regions);
   }

   private strictfp void release() {
      for (BlockMesh.Region r : this.regions) {
         r.release();
      }

      this.regions.clear();
   }

   @Override
   public strictfp void onWorldUnload(ClientContext c) {
      this.release();
      this.recollect = true;
   }
}
