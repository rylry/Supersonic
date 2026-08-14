package dev.rylry.supersonic.movement;

import dev.rylry.supersonic.cache.CoarseHeightCache;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class SupersonicMovement {
  public static boolean isPlayerControlled(Entity entity) {
    if (entity instanceof ServerPlayer) {
      return true;
    }
    for (Entity passenger : entity.getIndirectPassengers()) {
      if (passenger instanceof ServerPlayer) {
        return true;
      }
    }
    return false;
  }

  public static boolean canUseDirectMovement(ServerLevel level, Vec3 start, Vec3 end) {
    int chunkX = SectionPos.blockToSectionCoord(start.x);
    int chunkZ = SectionPos.blockToSectionCoord(start.z);

    int endChunkX = SectionPos.blockToSectionCoord(end.x);
    int endChunkZ = SectionPos.blockToSectionCoord(end.z);

    double dx = end.x - start.x;
    double dz = end.z - start.z;

    int stepX = Integer.signum(endChunkX - chunkX);
    int stepZ = Integer.signum(endChunkZ - chunkZ);

    double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dx);
    double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dz);

    double nextX = stepX > 0 ? SectionPos.sectionToBlockCoord(chunkX + 1)
        : SectionPos.sectionToBlockCoord(chunkX);
    double nextZ = stepZ > 0 ? SectionPos.sectionToBlockCoord(chunkZ + 1)
        : SectionPos.sectionToBlockCoord(chunkZ);

    double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : (nextX - start.x) / dx;
    double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (nextZ - start.z) / dz;

    double tEnter = 0.0;

    while (true) {
      double tExit = Math.min(tMaxX, tMaxZ);
      if (chunkX == endChunkX && chunkZ == endChunkZ) {
        tExit = 1.0;
      }

      double yEnter = Mth.lerp(tEnter, start.y, end.y);
      double yExit = Mth.lerp(tExit, start.y, end.y);

      double lowestY = Math.min(yEnter, yExit);

      ChunkPos pos = new ChunkPos(chunkX, chunkZ);

      int terrainHeight = CoarseHeightCache.get(level, pos);

      if (lowestY <= terrainHeight && level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
        return false;
      }

      if (chunkX == endChunkX && chunkZ == endChunkZ) {
        return true;
      }

      if (tMaxX < tMaxZ) {
        tEnter = tMaxX;
        tMaxX += tDeltaX;
        chunkX += stepX;
      } else {
        tEnter = tMaxZ;
        tMaxZ += tDeltaZ;
        chunkZ += stepZ;
      }
    }
  }
}
