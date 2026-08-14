package dev.rylry.supersonic.mixin;

import dev.rylry.supersonic.chunk.ChunkAdmissionController;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private int serverViewDistance;

    @Shadow
    public abstract DistanceManager getDistanceManager();

    @Shadow
    protected abstract void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void supersonic$budgetChunkTracking(ServerPlayer player, CallbackInfo callback) {
        int normalRadius = Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
        ChunkTrackingView view = ChunkAdmissionController.get(
            this.level, (ChunkMap)(Object)this, this.getDistanceManager()
        ).trackingView(player, normalRadius, this.serverViewDistance);
        this.applyChunkTrackingView(player, view);
        callback.cancel();
    }
}
