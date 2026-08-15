package dev.rylry.supersonic.chunk;

import dev.rylry.supersonic.Constants;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

public final class ChunkAdmissionController {
    private static final Map<ChunkMap, ChunkAdmissionController> BY_MAP = new WeakHashMap<>();
    private static final Map<DistanceManager, ChunkAdmissionController> BY_DISTANCE_MANAGER = new WeakHashMap<>();
    private static final Map<Object, GlobalBudget> BY_SERVER = new WeakHashMap<>();

    private final ServerLevel level;
    private final ChunkMap chunkMap;
    private final Path regionFolder;
    private final GlobalBudget budget;
    private final Map<ServerPlayer, PlayerState> players = new IdentityHashMap<>();
    private final Map<Long, Integer> outstanding = new HashMap<>();
    private final Map<Long, byte[]> regionHeaders = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int slowPlayersRemaining;
    private int fastPlayersRemaining;
    private int slowReserve;
    private int serverViewDistance;
    private TicketTrackerBridge ticketTracker;

    private ChunkAdmissionController(ServerLevel level, ChunkMap chunkMap, DistanceManager distanceManager) {
        this.level = level;
        this.chunkMap = chunkMap;
        Path worldFolder = level.getServer().getWorldPath(LevelResource.ROOT);
        this.regionFolder = DimensionType.getStorageFolder(level.dimension(), worldFolder).resolve("region");
        this.budget = BY_SERVER.computeIfAbsent(level.getServer(), ignored -> new GlobalBudget());
        BY_DISTANCE_MANAGER.put(distanceManager, this);
    }

    public static synchronized ChunkAdmissionController get(
        ServerLevel level, ChunkMap chunkMap, DistanceManager distanceManager
    ) {
        return BY_MAP.computeIfAbsent(chunkMap, ignored -> new ChunkAdmissionController(level, chunkMap, distanceManager));
    }

    public static synchronized int ticketSourceLevel(DistanceManager manager, long chunkPos, int vanillaLevel, Object tracker) {
        ChunkAdmissionController controller = BY_DISTANCE_MANAGER.get(manager);
        if (controller == null || vanillaLevel != 0 || !(tracker instanceof TicketTrackerBridge bridge)) {
            return vanillaLevel;
        }
        controller.ticketTracker = bridge;
        int radius = controller.radiusAt(new ChunkPos(chunkPos));
        if (radius < 0) {
            return vanillaLevel;
        }
        return radius == 0
            ? controller.serverViewDistance + 1
            : Math.max(0, controller.serverViewDistance - radius);
    }

    public ChunkTrackingView trackingView(ServerPlayer player, int normalRadius, int serverViewDistance) {
        this.serverViewDistance = serverViewDistance;
        beginTick(serverViewDistance);
        ChunkPos center = player.chunkPosition();
        PlayerState state = this.players.computeIfAbsent(player, ignored -> new PlayerState(normalRadius));
        ChunkTrackingView previous = player.getChunkTrackingView();
        int maximum = maximumRadius(state, normalRadius);

        double globallyAvailable = this.budget.available();
        double tierAvailable = state.fast
            ? Math.max(0.0, globallyAvailable - this.slowReserve)
            : globallyAvailable;
        int tierPlayersRemaining = state.fast ? this.fastPlayersRemaining : this.slowPlayersRemaining;
        double fairShare = tierPlayersRemaining <= 1 ? tierAvailable : tierAvailable / tierPlayersRemaining;
        int chosen = maximum;
        int chosenCost = estimate(previous, center, chosen);
        while (chosen > 0 && chosenCost > fairShare) {
            chosenCost = estimate(previous, center, --chosen);
        }
        if (chosenCost > tierAvailable) {
            chosen = 0;
        }

        ChunkTrackingView next = view(center, chosen);
        admit(previous, next);
        if (chosen > state.radius) {
            state.lastGrowthTick = this.tick;
        }
        boolean changedAtSameCenter = state.center != null && state.center.equals(center) && state.radius != chosen;
        if (state.center != null && !state.center.equals(center)) {
            state.recentSourceRadii.put(state.center.toLong(), new SourceRadius(state.radius, this.tick + 200));
        }
        state.center = center;
        state.radius = chosen;
        if (changedAtSameCenter && this.ticketTracker != null) {
            int sourceLevel = chosen == 0 ? serverViewDistance + 1 : Math.max(0, serverViewDistance - chosen);
            this.ticketTracker.supersonic$refresh(center.toLong(), sourceLevel);
        }
        if (state.fast) {
            this.fastPlayersRemaining = Math.max(0, this.fastPlayersRemaining - 1);
        } else {
            this.slowReserve = Math.max(0, this.slowReserve - state.reservedCost);
            state.reservedCost = 0;
            this.slowPlayersRemaining = Math.max(0, this.slowPlayersRemaining - 1);
        }
        state.lastPosition = player.position();
        state.lastObservedTick = this.tick;
        return next;
    }

    private void beginTick(int serverViewDistance) {
        long currentTick = this.level.getGameTime();
        if (currentTick == this.tick) {
            return;
        }
        this.tick = currentTick;
        this.regionHeaders.clear();
        this.players.keySet().removeIf(player -> player.isRemoved() || player.level() != this.level);
        for (PlayerState state : this.players.values()) {
            state.recentSourceRadii.values().removeIf(source -> source.expiresAtTick <= this.tick);
        }

        var iterator = this.outstanding.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            ChunkPos pos = new ChunkPos(entry.getKey());
            if (this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                this.budget.complete(entry.getValue());
                iterator.remove();
            }
        }
        this.budget.refill(this.level.getServer().getTickCount());

        this.slowPlayersRemaining = 0;
        this.fastPlayersRemaining = 0;
        this.slowReserve = 0;
        for (ServerPlayer player : this.level.players()) {
            int normalRadius = Math.clamp(player.requestedViewDistance(), 2, serverViewDistance);
            PlayerState state = this.players.computeIfAbsent(player, ignored -> new PlayerState(normalRadius));
            state.fast = isFast(player, state);
            state.reservedCost = 0;
            if (state.fast) {
                this.fastPlayersRemaining++;
            } else {
                int maximum = maximumRadius(state, normalRadius);
                state.reservedCost = estimate(player.getChunkTrackingView(), player.chunkPosition(), maximum);
                this.slowReserve += state.reservedCost;
                this.slowPlayersRemaining++;
            }
        }
    }

    private boolean isFast(ServerPlayer player, PlayerState state) {
        if (state.lastPosition == null || state.lastObservedTick >= this.tick) {
            return false;
        }
        long elapsedTicks = this.tick - state.lastObservedTick;
        Vec3 movement = player.position().subtract(state.lastPosition);
        double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z) / elapsedTicks;
        return horizontalSpeed > SupersonicChunkConfig.get().fastPlayerThreshold();
    }

    private int maximumRadius(PlayerState state, int normalRadius) {
        // Keep load shedding active until movement slows instead of admitting a
        // large isolated batch during a brief recovery in the tick-time average.
        if (state.radius == 0) {
            return 0;
        }
        if (state.radius >= normalRadius) {
            return normalRadius;
        }
        if (this.tick - state.lastGrowthTick < SupersonicChunkConfig.get().growthDelayTicks()) {
            return state.radius;
        }
        return Math.min(normalRadius, state.radius + SupersonicChunkConfig.get().radiusGrowthStep());
    }

    private int estimate(ChunkTrackingView previous, ChunkPos center, int radius) {
        ChunkTrackingView candidate = view(center, radius);
        int[] cost = {0};
        ChunkTrackingView.difference(previous, candidate, pos -> cost[0] += cost(pos), ignored -> {});
        return cost[0];
    }

    private void admit(ChunkTrackingView previous, ChunkTrackingView next) {
        Set<Long> admitted = new HashSet<>();
        ChunkTrackingView.difference(previous, next, pos -> admitted.add(pos.toLong()), ignored -> {});
        for (long packed : admitted) {
            if (!this.outstanding.containsKey(packed)) {
                int cost = cost(new ChunkPos(packed));
                if (cost > 0) {
                    this.outstanding.put(packed, cost);
                    this.budget.admit(cost);
                }
            }
        }
    }

    private int cost(ChunkPos pos) {
        if (this.outstanding.containsKey(pos.toLong()) || this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
            return 0;
        }
        return SupersonicChunkConfig.get().cost(classify(pos));
    }

    private ChunkState classify(ChunkPos pos) {
        if (this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
            return ChunkState.RESIDENT;
        }
        return generatedOnDisk(pos) ? ChunkState.GENERATED_ON_DISK : ChunkState.UNGENERATED;
    }

    private boolean generatedOnDisk(ChunkPos pos) {
        long regionKey = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        byte[] header = this.regionHeaders.computeIfAbsent(regionKey, ignored -> readRegionHeader(pos));
        int index = (pos.getRegionLocalX() + pos.getRegionLocalZ() * 32) * Integer.BYTES;
        return header.length == 4096 && (header[index] != 0 || header[index + 1] != 0 || header[index + 2] != 0 || header[index + 3] != 0);
    }

    private byte[] readRegionHeader(ChunkPos pos) {
        Path path = this.regionFolder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        if (!Files.isRegularFile(path)) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Read only the location table. No chunk payload is loaded.
            }
            return buffer.position() == 4096 ? buffer.array() : new byte[0];
        } catch (IOException exception) {
            Constants.LOG.debug("Could not inspect region header {}", path, exception);
            return new byte[0];
        }
    }

    private int radiusAt(ChunkPos center) {
        int radius = -1;
        for (PlayerState state : this.players.values()) {
            if (center.equals(state.center)) {
                radius = Math.max(radius, state.radius);
            }
            SourceRadius recent = state.recentSourceRadii.get(center.toLong());
            if (recent != null) {
                radius = Math.max(radius, recent.radius);
            }
        }
        return radius;
    }

    private static ChunkTrackingView view(ChunkPos center, int radius) {
        return radius == 0 ? ChunkTrackingView.EMPTY : ChunkTrackingView.of(center, radius);
    }

    private static final class PlayerState {
        private PlayerState(int initialRadius) {
            this.radius = initialRadius;
        }

        private ChunkPos center;
        private final Map<Long, SourceRadius> recentSourceRadii = new HashMap<>();
        private int radius;
        private long lastGrowthTick = Long.MIN_VALUE / 2;
        private Vec3 lastPosition;
        private long lastObservedTick = Long.MIN_VALUE;
        private boolean fast;
        private int reservedCost;
    }

    private record SourceRadius(int radius, long expiresAtTick) {}

    private static final class GlobalBudget {
        private double balance = SupersonicChunkConfig.get().capacity();
        private int outstanding;
        private int lastTick = Integer.MIN_VALUE;

        private double available() {
            return Math.max(0.0, this.balance - this.outstanding);
        }

        private void admit(int cost) {
            this.outstanding += cost;
        }

        private void complete(int cost) {
            this.outstanding = Math.max(0, this.outstanding - cost);
            this.balance = Math.max(0.0, this.balance - cost);
        }

        private void refill(int tick) {
            if (tick == this.lastTick) {
                return;
            }
            this.lastTick = tick;
            SupersonicChunkConfig config = SupersonicChunkConfig.get();
            this.balance = Math.min(config.capacity(), this.balance + config.refillPerTick());
        }
    }
}
