package dev.rylry.supersonic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rylry.supersonic.movement.SupersonicMovement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the fluid interaction "loaded" test genuinely nonblocking. */
@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionMixin {
    private static final ThreadLocal<Boolean> SUPERSONIC_NONBLOCKING =
        ThreadLocal.withInitial(() -> false);

    @WrapOperation(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityFluidInteraction;hasFluidAndLoaded(Lnet/minecraft/world/level/Level;IIIIII)Z"
        )
    )
    private boolean supersonic$scopeNonblockingFluidLookup(
        Level level,
        int x0,
        int y0,
        int z0,
        int x1,
        int y1,
        int z1,
        Operation<Boolean> original,
        @Local(argsOnly = true) Entity entity
    ) {
        if (!SupersonicMovement.isPlayerControlled(entity)) {
            return original.call(level, x0, y0, z0, x1, y1, z1);
        }

        SUPERSONIC_NONBLOCKING.set(true);
        try {
            return original.call(level, x0, y0, z0, x1, y1, z1);
        } finally {
            SUPERSONIC_NONBLOCKING.remove();
        }
    }

    @WrapOperation(
        method = "hasFluidAndLoaded",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"
        )
    )
    private static ChunkAccess supersonic$getFluidChunkWithoutWaiting(
        Level level,
        int chunkX,
        int chunkZ,
        ChunkStatus status,
        boolean create,
        Operation<ChunkAccess> original
    ) {
        if (SUPERSONIC_NONBLOCKING.get() && level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        }
        return original.call(level, chunkX, chunkZ, status, create);
    }
}
