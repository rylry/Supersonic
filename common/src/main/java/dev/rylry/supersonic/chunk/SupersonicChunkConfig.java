package dev.rylry.supersonic.chunk;

import dev.rylry.supersonic.Constants;
import dev.rylry.supersonic.platform.Services;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public record SupersonicChunkConfig(
    double refillPerTick,
    double capacity,
    int residentCost,
    int generatedCost,
    int generationCost,
    int growthDelayTicks,
    int radiusGrowthStep,
    double fastPlayerThreshold,
    double directMovementMinSpeed,
    int movementPacketLimit,
    Map<String, Integer> dimensionHeights
) {
    private static final String HEIGHT_PREFIX = "height.";
    private static final String NETHER = "minecraft:the_nether";
    private static volatile SupersonicChunkConfig INSTANCE = defaults();

    public static SupersonicChunkConfig get() {
        return INSTANCE;
    }

    public static void load() {
        Path path = Services.PLATFORM.getConfigDirectory().resolve("supersonic-chunks.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException exception) {
                Constants.LOG.warn("Could not read {}; using safe defaults", path, exception);
            }
        }

        SupersonicChunkConfig defaults = defaults();
        INSTANCE = new SupersonicChunkConfig(
            positiveDouble(properties, "refill-per-tick", defaults.refillPerTick),
            positiveDouble(properties, "capacity", defaults.capacity),
            nonNegativeInt(properties, "resident-cost", defaults.residentCost),
            nonNegativeInt(properties, "generated-cost", defaults.generatedCost),
            nonNegativeInt(properties, "generation-cost", defaults.generationCost),
            positiveInt(properties, "growth-delay-ticks", defaults.growthDelayTicks),
            positiveInt(properties, "radius-growth-step", defaults.radiusGrowthStep),
            nonNegativeDouble(properties, "fast-player-threshold", defaults.fastPlayerThreshold),
            nonNegativeDouble(properties, "direct-movement-min-speed", defaults.directMovementMinSpeed),
            positiveInt(properties, "movement-packet-limit", defaults.movementPacketLimit),
            dimensionHeights(properties, defaults.dimensionHeights)
        );

        boolean writeConfig = !Files.exists(path);
        writeConfig |= putIfMissing(properties, "refill-per-tick", INSTANCE.refillPerTick);
        writeConfig |= putIfMissing(properties, "capacity", INSTANCE.capacity);
        writeConfig |= putIfMissing(properties, "resident-cost", INSTANCE.residentCost);
        writeConfig |= putIfMissing(properties, "generated-cost", INSTANCE.generatedCost);
        writeConfig |= putIfMissing(properties, "generation-cost", INSTANCE.generationCost);
        writeConfig |= putIfMissing(properties, "growth-delay-ticks", INSTANCE.growthDelayTicks);
        writeConfig |= putIfMissing(properties, "radius-growth-step", INSTANCE.radiusGrowthStep);
        writeConfig |= putIfMissing(properties, "fast-player-threshold", INSTANCE.fastPlayerThreshold);
        writeConfig |= putIfMissing(properties, "direct-movement-min-speed", INSTANCE.directMovementMinSpeed);
        writeConfig |= putIfMissing(properties, "movement-packet-limit", INSTANCE.movementPacketLimit);
        for (Map.Entry<String, Integer> entry : INSTANCE.dimensionHeights.entrySet()) {
            writeConfig |= putIfMissing(properties, HEIGHT_PREFIX + entry.getKey(), entry.getValue());
        }
        if (writeConfig) {
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "Supersonic chunk admission budget");
                }
            } catch (IOException exception) {
                Constants.LOG.warn("Could not write default config to {}", path, exception);
            }
        }
    }

    private static boolean putIfMissing(Properties properties, String key, Object value) {
        if (properties.containsKey(key)) {
            return false;
        }
        properties.setProperty(key, value.toString());
        return true;
    }

    public int cost(ChunkState state) {
        return switch (state) {
            case RESIDENT -> this.residentCost;
            case GENERATED_ON_DISK -> this.generatedCost;
            case UNGENERATED -> this.generationCost;
        };
    }

    public int height(String dimension, int fallback) {
        return this.dimensionHeights.getOrDefault(dimension, fallback);
    }

    private static SupersonicChunkConfig defaults() {
        return new SupersonicChunkConfig(
            22.5, 45.0, 0, 1, 7, 5, 1, 1.0, 0.0, 30, Map.of(NETHER, 128)
        );
    }

    private static Map<String, Integer> dimensionHeights(
        Properties properties,
        Map<String, Integer> defaults
    ) {
        Map<String, Integer> heights = new HashMap<>(defaults);
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(HEIGHT_PREFIX) || key.length() == HEIGHT_PREFIX.length()) {
                continue;
            }

            String dimension = key.substring(HEIGHT_PREFIX.length());
            int fallback = heights.getOrDefault(dimension, Integer.MIN_VALUE);
            try {
                heights.put(dimension, Integer.parseInt(properties.getProperty(key)));
            } catch (NumberFormatException exception) {
                if (fallback == Integer.MIN_VALUE) {
                    heights.remove(dimension);
                }
            }
        }
        return Map.copyOf(heights);
    }

    private static double positiveDouble(Properties properties, String key, double fallback) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
            return Double.isFinite(value) && value > 0.0 ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double nonNegativeDouble(Properties properties, String key, double fallback) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
            return Double.isFinite(value) && value >= 0.0 ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int nonNegativeInt(Properties properties, String key, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int positiveInt(Properties properties, String key, int fallback) {
        return Math.max(1, nonNegativeInt(properties, key, fallback));
    }
}
