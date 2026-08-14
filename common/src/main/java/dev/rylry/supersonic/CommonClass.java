package dev.rylry.supersonic;

import dev.rylry.supersonic.chunk.SupersonicChunkConfig;

public class CommonClass {

    public static void init() {
        SupersonicChunkConfig.load();
        Constants.LOG.info("Supersonic loaded");
    }
}
