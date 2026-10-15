package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import dev.slinky.setting.ColorSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.Map.Entry;

public final strictfp class BlockMesh {
   private BlockMesh() {
   }

   public static strictfp int compareRegion(int x, int z, int otherX, int otherZ) {
      int difference = x - otherX;
      return difference == 0 ? z - otherZ : difference;
   }

   public static strictfp List<BlockMesh.Position> scanByte(int section, byte[] ids, SortedMap<Integer, ColorSetting.Value> policy) {
      List<BlockMesh.Position> result = new ArrayList<>();

      for (Entry<Integer, ColorSetting.Value> entry : policy.entrySet()) {
         for (int group = 0; group < 256; group++) {
            for (int bit = 0; bit < 16; bit++) {
               if (ids[group * 16 + bit] == (byte)entry.getKey().intValue()) {
                  result.add(new BlockMesh.Position(bit, (group >> 4) + section * 16, group & 15, entry.getKey(), entry.getValue()));
               }
            }
         }
      }

      return result;
   }

   public static strictfp List<BlockMesh.Position> scanPacked(int section, char[] ids, SortedMap<Integer, ColorSetting.Value> policy) {
      List<BlockMesh.Position> result = new ArrayList<>();

      for (Entry<Integer, ColorSetting.Value> entry : policy.entrySet()) {
         for (int group = 0; group < 512; group++) {
            for (int bit = 0; bit < 8; bit++) {
               if ((short)ids[group * 8 + bit] >> 4 == (short)entry.getKey().intValue()) {
                  result.add(new BlockMesh.Position(((group & 1) << 3) + bit, (group >> 5) + section * 16, group >> 1 & 15, entry.getKey(), entry.getValue()));
               }
            }
         }
      }

      return result;
   }

   public static strictfp void rebuild(BlockMesh.Region region) {
      float scale = RenderConstants.f(1393000);
      region.vertices = new float[region.blocks.size() * 8 * 3];
      region.colors = new int[region.blocks.size() * 8];
      region.indices = new short[region.blocks.size() * 24];
      int vertex = 0;
      int index = 0;

      for (BlockMesh.Position p : region.blocks) {
         int first = vertex;
         int red = RecoveredMath.truncateInt(p.color.red * scale) & 0xFF;
         int green = RecoveredMath.truncateInt(p.color.green * scale) & 0xFF;
         int blue = RecoveredMath.truncateInt(p.color.blue * scale) & 0xFF;
         int color = red << 24 | green << 16 | blue << 8 | 0xFF;

         for (int corner = 0; corner < 8; corner++) {
            region.vertices[vertex * 3] = RenderConstants.f(1393072 + corner * 12) + (float)p.x;
            region.vertices[vertex * 3 + 1] = RenderConstants.f(1393076 + corner * 12) + (float)p.y;
            region.vertices[vertex * 3 + 2] = RenderConstants.f(1393080 + corner * 12) + (float)p.z;
            region.colors[vertex++] = color;
         }

         for (int n = 0; n < 24; n++) {
            region.indices[index++] = (short)(RenderConstants.u16(1393168 + n * 2) + (first & 65535));
         }
      }
   }

   public static strictfp long renderLimit(boolean limited, float value) {
      if (!limited) {
         return 4096L;
      } else {
         float amount = value * RenderConstants.f(1392984);
         long first = RecoveredMath.truncateLong(amount);
         long second = RecoveredMath.truncateLong(amount - RenderConstants.f(1392988));
         return first | second & (first < 0L ? -1L : 0L);
      }
   }

   public static strictfp void change(BlockMesh.Region region, int x, int y, int z, int id, ColorSetting.Value policy) {
      for (int i = 0; i < region.blocks.size(); i++) {
         BlockMesh.Position p = region.blocks.get(i);
         if (p.x == (x & 0xFF) && p.y == (y & 0xFF) && p.z == (z & 0xFF)) {
            if (policy == null) {
               region.blocks.remove(i);
            } else {
               region.blocks.set(i, new BlockMesh.Position(x, y, z, id, policy));
            }

            rebuild(region);
            return;
         }
      }

      if (policy != null) {
         region.blocks.add(new BlockMesh.Position(x, y, z, id, policy));
         rebuild(region);
      }
   }

   public static final strictfp class Position {
      public final int x;
      public final int y;
      public final int z;
      public final int id;
      public ColorSetting.Value color;

      public Position(int x, int y, int z, int id, ColorSetting.Value color) {
         this.x = x & 0xFF;
         this.y = y & 0xFF;
         this.z = z & 0xFF;
         this.id = id;
         this.color = color;
      }
   }

   public static final strictfp class Region {
      public final int x;
      public final int z;
      public final List<BlockMesh.Position> blocks = new ArrayList<>();
      public float[] vertices = new float[0];
      public int[] colors = new int[0];
      public short[] indices = new short[0];

      public Region(int x, int z) {
         this.x = x;
         this.z = z;
      }

      public strictfp void release() {
         this.blocks.clear();
         this.vertices = new float[0];
         this.colors = new int[0];
         this.indices = new short[0];
      }
   }
}
