package dev.rylry.supersonic.mixin;

import dev.rylry.supersonic.chunk.ChunkAdmissionController;
import dev.rylry.supersonic.chunk.TicketTrackerBridge;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ChunkTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
public abstract class FixedPlayerDistanceChunkTrackerMixin implements TicketTrackerBridge {
    @Shadow(aliases = "this$0") @Final private DistanceManager supersonic$outer;

    @Inject(method = "getLevelFromSource", at = @At("RETURN"), cancellable = true)
    private void supersonic$usePlayerRadius(long chunkPos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(ChunkAdmissionController.ticketSourceLevel(
            this.supersonic$outer, chunkPos, callback.getReturnValue(), this
        ));
    }

    @Override
    public void supersonic$refresh(long chunkPos, int sourceLevel) {
        ((ChunkTracker)(Object)this).update(chunkPos, sourceLevel, false);
    }
}
