package dev.rylry.supersonic.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.rylry.supersonic.Constants;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class CoarseHeightCache {

  private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<HeightRegion>> DIMENSIONS = new ConcurrentHashMap<>();

  private static HeightRegion region(ServerLevel level, ChunkPos pos) {
    int regionX = pos.getRegionX();
    int regionZ = pos.getRegionZ();
    long regionKey = ChunkPos.pack(regionX, regionZ);

    int levelMaxY = level.getMinY() + level.getHeight();

    return DIMENSIONS.computeIfAbsent(level.dimension(), key -> {
      Constants.LOG.info("Creating new height cache for dimension {} of height {}",
          level.dimension().identifier(), levelMaxY);
      return new Long2ObjectOpenHashMap<>();
    }).computeIfAbsent(regionKey, key -> new HeightRegion(levelMaxY));

  }

  public static void put(ServerLevel level, ChunkPos pos, int maxY) {
    region(level, pos).put(pos, maxY);
  }

  public static int get(ServerLevel level, ChunkPos pos) {
    return region(level, pos).get(pos);
  }

  public static void updateFromChunk(ServerLevel level, LevelChunk chunk) {
    int maxY = level.getMinY();
    Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        maxY = Math.max(maxY, heightmap.getHighestTaken(x, z));
      }
    }
    put(level, chunk.getPos(), maxY);
  }

  public static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
    updateFromChunk(level, chunk);
  }

  public static void onChunkUnloaded(ServerLevel level, LevelChunk chunk) {
    updateFromChunk(level, chunk);
  }
}
