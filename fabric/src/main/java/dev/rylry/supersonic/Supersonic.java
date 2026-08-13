package dev.rylry.supersonic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import dev.rylry.supersonic.cache.CoarseHeightCache;

public class Supersonic implements ModInitializer {

    @Override
    public void onInitialize() {

        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            CoarseHeightCache.onChunkLoaded(level, chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            CoarseHeightCache.onChunkUnloaded(level, chunk);
        });

        // Use Fabric to bootstrap the Common mod.
        CommonClass.init();
    }
}
