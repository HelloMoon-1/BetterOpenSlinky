package dev.slinky.client.render;

import dev.slinky.client.ClientContext;
import dev.slinky.client.ItemPolicyService;
import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;

public final class LoadedChunkAccess {
   private static final Field LIST = ReflectionHelper.findField(ChunkProviderClient.class, "chunkListing", "field_73237_c");

   private LoadedChunkAccess() {
   }

   public static List<Chunk> loaded(ClientContext c) {
      try {
         return new ArrayList<>((List)LIST.get(c.world().getChunkProvider()));
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static int blockIdentifier(String name) {
      int id = ItemPolicyService.identifier(name);
      if (id >= 0 && id < 256 && Block.getBlockById(id) != null) {
         return id;
      } else {
         Block block = Block.getBlockFromName(name);
         return block == null ? -1 : Block.getIdFromBlock(block);
      }
   }
}
