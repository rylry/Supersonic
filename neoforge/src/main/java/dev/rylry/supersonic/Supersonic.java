package dev.rylry.supersonic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import dev.rylry.supersonic.cache.CoarseHeightCache;

@Mod(Constants.MOD_ID)
public class Supersonic {

    public Supersonic(IEventBus eventBus) {

        NeoForge.EVENT_BUS.addListener(this::onChunkLoaded);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnloaded);

        CommonClass.init();

    }

    private void onChunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        CoarseHeightCache.onChunkLoaded(level, chunk);
    }

    private void onChunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        CoarseHeightCache.onChunkUnloaded(level, chunk);
    }
}
