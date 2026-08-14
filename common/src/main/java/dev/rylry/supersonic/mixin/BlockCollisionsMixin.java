package dev.rylry.supersonic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rylry.supersonic.movement.SupersonicMovement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps player-controlled collision iteration from waiting for chunks. */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin {
    @Shadow @Final private CollisionContext context;

    @WrapOperation(
        method = "getChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;getChunkForCollisions(II)Lnet/minecraft/world/level/BlockGetter;"
        )
    )
    private BlockGetter supersonic$getPlayerCollisionChunkWithoutWaiting(
        CollisionGetter collisionGetter,
        int chunkX,
        int chunkZ,
        Operation<BlockGetter> original
    ) {
        Entity entity = this.context instanceof EntityCollisionContext entityContext
            ? entityContext.getEntity()
            : null;
        if (entity != null
            && SupersonicMovement.isPlayerControlled(entity)
            && collisionGetter instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        }
        return original.call(collisionGetter, chunkX, chunkZ);
    }
}
