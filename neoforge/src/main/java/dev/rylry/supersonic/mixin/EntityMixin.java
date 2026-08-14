package dev.rylry.supersonic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rylry.supersonic.movement.SupersonicMovement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
  // NeoForge appends a synchronous getChunk call to setPosRaw. Player movement
  // uses a nonblocking lookup so positioning cannot bypass chunk admission.
  // The return value of NeoForge's ensure-load call is discarded.
  @WrapOperation(
      method = "setPosRaw",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
  private LevelChunk supersonic$avoidSynchronousPlayerChunkLoad(
      Level level, int chunkX, int chunkZ, Operation<LevelChunk> original) {
    Entity entity = (Entity)(Object)this;
    if (level instanceof ServerLevel serverLevel && SupersonicMovement.isPlayerControlled(entity)) {
      return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
    }
    return original.call(level, chunkX, chunkZ);
  }
}
