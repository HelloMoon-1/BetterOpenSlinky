package dev.slinky.client.render;

import dev.slinky.client.ClientContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Client-thread-only, loaded-section scanner. Never asks the provider to load a chunk. */
public final class BedPlatesWorld {
   private static final int MAX_BEDS = 64;
   private static final int CELLS_PER_TICK = 8192;
   private static final int SECTION_VISITS_PER_TICK = 32;
   // Vanilla 1.8 bed metadata: SOUTH, WEST, NORTH, EAST; bit 3 marks the head.
   private static final int[] DX = {0, -1, 0, 1};
   private static final int[] DZ = {1, 0, -1, 0};
   private final Map<Long, Chunk> loaded = new HashMap<>();
   private final List<Chunk> chunks = new ArrayList<>();
   private List<Chunk> previousMembership = Collections.emptyList();
   private final Map<BlockPos, Bed> beds = new LinkedHashMap<>();
   private World world;
   private Object player;
   private int sectionCursor;
   private int tick;
   private int defenseCursor;

   public void clear() {
      this.loaded.clear();
      this.chunks.clear();
      this.beds.clear();
      this.previousMembership = Collections.emptyList();
      this.world = null;
      this.player = null;
      this.sectionCursor = this.tick = this.defenseCursor = 0;
   }

   public boolean matches(ClientContext c) {
      return c.ready() && this.world == c.world() && this.player == c.player();
   }

   public void update(ClientContext c, double range) {
      if (!c.ready()) { this.clear(); return; }
      if (!this.matches(c)) {
         this.clear();
         this.world = c.world();
         this.player = c.player();
      }
      this.tick++;
      this.loaded.clear();
      List<Chunk> nextChunks = new ArrayList<>();
      // Refresh membership before every scan/defense lookup, including unload/reload at the same coordinates.
      for (Chunk chunk : LoadedChunkAccess.loaded(c)) {
         double x = Math.max(chunk.xPosition * 16.0, Math.min(c.player().posX, chunk.xPosition * 16.0 + 16.0));
         double z = Math.max(chunk.zPosition * 16.0, Math.min(c.player().posZ, chunk.zPosition * 16.0 + 16.0));
         double dx = x - c.player().posX, dz = z - c.player().posZ;
         if (dx * dx + dz * dz <= (range + 4.0) * (range + 4.0)) {
            this.loaded.put(chunkKey(chunk.xPosition, chunk.zPosition), chunk);
            nextChunks.add(chunk);
         }
      }
      // Only rebuild/sort the traversal when loaded membership actually changes.
      if (!nextChunks.equals(this.previousMembership)) {
         this.previousMembership = nextChunks;
         this.chunks.clear();
         this.chunks.addAll(nextChunks);
         Collections.sort(this.chunks, Comparator.comparingInt((Chunk v) -> v.xPosition).thenComparingInt(v -> v.zPosition));
         if (this.sectionCursor >= this.chunks.size() * 16) this.sectionCursor = 0;
      }
      Iterator<Bed> iterator = this.beds.values().iterator();
      while (iterator.hasNext()) {
         Bed bed = iterator.next();
         if (!this.valid(bed) || distanceSquared(c, bed) > range * range) iterator.remove();
      }
      if (this.chunks.isEmpty()) { this.sectionCursor = 0; return; }
      int cells = 0;
      int slots = this.chunks.size() * 16;
      for (int visited = 0; visited < Math.min(SECTION_VISITS_PER_TICK, slots) && cells < CELLS_PER_TICK; visited++) {
         int slot = this.sectionCursor++ % slots;
         // Keep cursor bounded indefinitely, including on long-running servers.
         if (this.sectionCursor >= slots) this.sectionCursor = 0;
         Chunk chunk = this.chunks.get(slot / 16);
         ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
         int section = slot % 16;
         if (section >= storage.length || storage[section] == null) continue;
         char[] data = storage[section].getData();
         if (data == null) continue;
         int length = Math.min(4096, data.length);
         cells += length;
         for (int i = 0; i < length; i++) {
            int packed = data[i];
            if ((packed >>> 4) != 26) continue;
            int meta = packed & 15;
            BlockPos pos = new BlockPos((chunk.xPosition << 4) + (i & 15), (section << 4) + (i >>> 8),
               (chunk.zPosition << 4) + ((i >>> 4) & 15));
            int facing = meta & 3;
            BlockPos head = (meta & 8) != 0 ? pos : pos.add(DX[facing], 0, DZ[facing]);
            if (!this.beds.containsKey(head) && this.beds.size() < MAX_BEDS) {
               Bed bed = new Bed(head, head.add(-DX[facing], 0, -DZ[facing]), facing);
               if (this.valid(bed) && distanceSquared(c, bed) <= range * range) this.beds.put(head, bed);
            }
         }
      }
      // Amortize defense scans: at most one bed per tick, and no rescan within one second.
      List<Bed> refresh = new ArrayList<>(this.beds.values());
      for (int i = 0; i < refresh.size(); i++) {
         if (this.defenseCursor >= refresh.size()) this.defenseCursor = 0;
         Bed bed = refresh.get(this.defenseCursor++);
         if (!bed.scanned || this.tick - bed.updated >= 20) {
            this.defenses(bed);
            break;
         }
      }
   }

   private static long chunkKey(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }

   private int packed(BlockPos pos) {
      if (pos.getY() < 0 || pos.getY() >= 256) return 0;
      Chunk chunk = this.loaded.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
      if (chunk == null) return -1; // Unknown is not air, and never triggers a provider lookup.
      ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
      int section = pos.getY() >> 4;
      if (section >= sections.length || sections[section] == null) return 0;
      char[] data = sections[section].getData();
      int index = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
      return data != null && index < data.length ? data[index] : 0;
   }

   private boolean valid(Bed bed) {
      int head = this.packed(bed.head), foot = this.packed(bed.foot);
      return head >= 0 && foot >= 0 && (head >>> 4) == 26 && (foot >>> 4) == 26
         && (head & 8) != 0 && (foot & 8) == 0 && (head & 3) == bed.facing && (foot & 3) == bed.facing;
   }

   public static double distanceSquared(ClientContext c, Bed bed) {
      double dx = c.player().posX - bed.x(), dy = c.player().posY - bed.head.getY(), dz = c.player().posZ - bed.z();
      return dx * dx + dy * dy + dz * dz;
   }

   /** Three above-ground shells around BOTH bed halves, matching the donor's defense-layer approach. */
   private void defenses(Bed bed) {
      bed.items.clear();
      Map<Integer, ItemStack> unique = new LinkedHashMap<>();
      for (int layer = 1; layer <= 3; layer++) {
         Map<Integer, Integer> counts = new HashMap<>();
         Map<Integer, ItemStack> stacks = new HashMap<>();
         int minX = Math.min(bed.head.getX(), bed.foot.getX()) - layer;
         int maxX = Math.max(bed.head.getX(), bed.foot.getX()) + layer;
         int minZ = Math.min(bed.head.getZ(), bed.foot.getZ()) - layer;
         int maxZ = Math.max(bed.head.getZ(), bed.foot.getZ()) + layer;
         for (int y = 0; y <= layer; y++) {
            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  int headX = Math.abs(x - bed.head.getX()), headZ = Math.abs(z - bed.head.getZ());
                  int footX = Math.abs(x - bed.foot.getX()), footZ = Math.abs(z - bed.foot.getZ());
                  int shell = Math.max(y, Math.min(Math.max(headX, headZ), Math.max(footX, footZ)));
                  if (shell == 0) continue;
                  // Donor rule: shell corners/upper edges beyond radial distance belong to the next layer.
                  double radial = Math.min(Math.sqrt(headX * headX + headZ * headZ),
                     Math.sqrt(footX * footX + footZ * footZ)) + y;
                  int targetLayer = radial > shell ? shell + 1 : shell;
                  if (targetLayer != layer) continue;
                  int packed = this.packed(new BlockPos(x, bed.head.getY() + y, z));
                  if (packed < 0 || (packed >>> 4) == 26) continue;
                  Block block = Block.getBlockById(packed >>> 4);
                  if (block == null || block == Blocks.air || !block.getMaterial().isSolid()) continue;
                  Item item = Item.getItemFromBlock(block);
                  if (item == null) continue;
                  IBlockState state = block.getStateFromMeta(packed & 15);
                  int damage = block.damageDropped(state);
                  int key = (Item.getIdFromItem(item) << 16) | (damage & 65535);
                  counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
                  stacks.put(key, new ItemStack(item, 1, damage));
               }
            }
         }
         List<Integer> keys = new ArrayList<>(counts.keySet());
         Collections.sort(keys, (a, b) -> {
            int order = Integer.compare(counts.get(b), counts.get(a));
            return order != 0 ? order : Integer.compare(a, b);
         });
         for (Integer key : keys) if (!unique.containsKey(key)) unique.put(key, stacks.get(key));
      }
      // Actual observed items only; an uncovered bed deliberately has an empty item row.
      for (ItemStack item : unique.values()) {
         if (bed.items.size() == 12) break;
         bed.items.add(item);
      }
      bed.updated = this.tick;
      bed.scanned = true;
   }

   public List<Bed> beds() { return Collections.unmodifiableList(new ArrayList<>(this.beds.values())); }

   public static final class Bed {
      public final BlockPos head, foot;
      private final int facing;
      private final List<ItemStack> items = new ArrayList<>();
      private int updated;
      private boolean scanned;
      private Bed(BlockPos head, BlockPos foot, int facing) { this.head = head; this.foot = foot; this.facing = facing; }
      public double x() { return (this.head.getX() + this.foot.getX()) * 0.5 + 0.5; }
      public double z() { return (this.head.getZ() + this.foot.getZ()) * 0.5 + 0.5; }
      public List<ItemStack> items() { return Collections.unmodifiableList(this.items); }
      public boolean scanned() { return this.scanned; }
   }
}
