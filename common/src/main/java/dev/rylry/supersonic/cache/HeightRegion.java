package dev.rylry.supersonic.cache;

import java.util.Arrays;

import net.minecraft.world.level.ChunkPos;

public class HeightRegion {
  public final int FALLBACK;
  private final int[] heights = new int[32 * 32];

  public HeightRegion(int fallback) {
    FALLBACK = fallback;
    Arrays.fill(heights, FALLBACK);
  }

  public void put(ChunkPos pos, int height) {
    heights[index(pos)] = height;
  }

  public int get(ChunkPos pos) {
    return heights[index(pos)];
  }

  private static int index(ChunkPos pos) {
    int localX = pos.getRegionLocalX();
    int localZ = pos.getRegionLocalZ();
    return (localZ << 5) | localX;
  }
}
