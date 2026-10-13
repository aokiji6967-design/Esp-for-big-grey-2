package dev.esp.logic;

import dev.esp.config.RenderSettings;
import dev.esp.config.SelectionMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds selected blocks around the player without ever blocking a frame.
 *
 * <p>A pass walks the chunk columns that intersect the range, nearest first, and examines a fixed
 * budget of positions per tick. Two details make it work in practice:
 *
 * <ul>
 *   <li><b>The pass keeps its progress.</b> Walking does not restart the sweep. The queue is
 *       rebuilt around the player's new chunk, but columns that are already done stay done, and the
 *       column being scanned keeps its exact cursor. Otherwise a player who keeps moving would
 *       never get past the first few chunks, which is why distant selections used to appear only
 *       once the player stood still.
 *   <li><b>Empty chunk sections are skipped for free.</b> An all-air section costs nothing, so in a
 *       normal world a full sweep of the whole render distance is cheap even though it covers every
 *       position in range.
 * </ul>
 *
 * <p>Results carry over between passes, {@link #tick} re-checks every published position before the
 * sweep runs, so a block that is mined, replaced or deselected loses its box on the next tick
 * instead of waiting for the sweep to happen to visit that coordinate again.
 *
 * <p>Positions are absolute, so the published list stays valid while the next pass runs.
 */
public final class BlockScanner {
    private static final int CHUNK_WIDTH = 16;
    private static final int SECTION_WIDTH = 16;
    private static final int CELLS_PER_SECTION = SECTION_WIDTH * SECTION_WIDTH * SECTION_WIDTH;

    /** One chunk column to visit, ordered by distance from the player. */
    private record Column(int x, int z) {}

    private final Long2ObjectMap<EspTarget> found = new Long2ObjectOpenHashMap<>();
    private final List<EspTarget> pending = new ArrayList<>();

    /** Columns this pass has finished, so rebuilding the queue never re-sweeps them. */
    private final LongSet visited = new LongOpenHashSet();

    private List<EspTarget> snapshot = Collections.emptyList();
    private List<Column> queue = List.of();
    private int queueIndex;

    /** The world the current results belong to; a change means nothing found is still valid. */
    private ClientWorld worldRef;

    /** The column being scanned, and how far into it the sweep has got. */
    private WorldChunk current;
    private ChunkSection[] sections;
    private int sectionIndex;
    private int cellIndex;

    private int bottomY;
    private double passStartX;
    private double passStartZ;
    private double lastRange = -1.0;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private boolean dirty = true;

    /** Forces the next tick to rebuild the sweep, used when the selection changes. */
    public void invalidate() {
        dirty = true;
    }

    public void reset() {
        dirty = true;
        pending.clear();
        found.clear();
        visited.clear();
        queue = List.of();
        queueIndex = 0;
        current = null;
        sections = null;
        sectionIndex = 0;
        cellIndex = 0;
        snapshot = Collections.emptyList();
        worldRef = null;
        lastRange = -1.0;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

    public List<EspTarget> snapshot() {
        return snapshot;
    }

    public void tick(
        ClientWorld world, Vec3d playerPos, RenderSettings settings,
        SelectionMap<Block> selected, double range
    ) {
        // A different world (dimension change, respawn, new server) invalidates every position.
        if (world != worldRef) {
            reset();
            worldRef = world;
        }

        if (range != lastRange) {
            lastRange = range;
            dirty = true;
        }

        int playerChunkX = chunkOf(playerPos.x);
        int playerChunkZ = chunkOf(playerPos.z);
        boolean movedChunk = playerChunkX != lastChunkX || playerChunkZ != lastChunkZ;

        if (dirty) {
            startPass(world, playerPos, range);
        } else if (queueIndex >= queue.size()) {
            // The previous pass is done; begin the next one so changes are picked up.
            startPass(world, playerPos, range);
        } else if (movedChunk) {
            refreshQueue(playerPos, range);
        } else if (movedFromPassStart(playerPos) > range) {
            // The swept area no longer covers the player, so the bookkeeping is worthless.
            startPass(world, playerPos, range);
        }

        lastChunkX = playerChunkX;
        lastChunkZ = playerChunkZ;

        // Cheap every tick: at most one lookup per published target, so no box outlives its block.
        verifyFound(world, selected, range, playerPos.x, playerPos.y, playerPos.z);

        sweep(world, settings, selected, range, playerPos);

        publish();
    }

    private void startPass(ClientWorld world, Vec3d playerPos, double range) {
        dirty = false;
        bottomY = world.getBottomY();
        passStartX = playerPos.x;
        passStartZ = playerPos.z;
        visited.clear();
        queue = buildColumns(playerPos, range);
        queueIndex = 0;
        current = null;
        sections = null;
        sectionIndex = 0;
        cellIndex = 0;
    }

    /**
     * Rebuilds the queue around the player without losing progress: finished columns stay finished,
     * and the column being scanned stays first so its cursor remains valid.
     */
    private void refreshQueue(Vec3d playerPos, double range) {
        Column inFlight = current != null && queueIndex < queue.size() ? queue.get(queueIndex) : null;

        List<Column> rebuilt = new ArrayList<>();

        if (inFlight != null) rebuilt.add(inFlight);

        for (Column column : buildColumns(playerPos, range)) {
            if (visited.contains(chunkKey(column.x(), column.z()))) continue;
            if (inFlight != null && column.x() == inFlight.x() && column.z() == inFlight.z()) continue;

            rebuilt.add(column);
        }

        queue = rebuilt;
        queueIndex = 0;
    }

    private void sweep(
        ClientWorld world, RenderSettings settings, SelectionMap<Block> selected,
        double range, Vec3d playerPos
    ) {
        if (queueIndex >= queue.size()) return;

        int budget = Math.max(1024, settings.blockScanBudget());
        int maxTargets = settings.maxTargets();
        double rangeSq = range * range;

        // Sections are rejected by palette first, so the budget is only ever spent on sections that
        // actually contain something selected. Solid stone and empty air cost a palette lookup
        // instead of 4096 block lookups, which is what makes a full sweep finish in a frame or two.
        Predicate<BlockState> relevant = state -> !state.isAir() && selected.isEnabled(state.getBlock());

        double px = playerPos.x;
        double py = playerPos.y;
        double pz = playerPos.z;

        int steps = 0;

        while (steps < budget && queueIndex < queue.size()) {
            Column column = queue.get(queueIndex);

            if (current == null) {
                // Never read through the world: an unloaded chunk would not be safe there. The
                // chunk object itself stays readable even if it unloads while being scanned.
                Chunk chunk = world.getChunk(column.x(), column.z(), ChunkStatus.FULL, false);
                sections = null;
                sectionIndex = 0;
                cellIndex = 0;

                if (!(chunk instanceof WorldChunk worldChunk)) {
                    // Not loaded yet. Leave it unvisited so the next queue refresh retries it once
                    // the client has the chunk, instead of writing the column off for the pass.
                    queueIndex++;
                    continue;
                }

                current = worldChunk;
                sections = worldChunk.getSectionArray();
            }

            if (sections == null || sectionIndex >= sections.length) {
                visited.add(chunkKey(column.x(), column.z()));
                current = null;
                sections = null;
                sectionIndex = 0;
                cellIndex = 0;
                queueIndex++;
                continue;
            }

            ChunkSection section = sections[sectionIndex];

            // Free skips: an all-air section, and any section whose palette holds none of the
            // selected blocks. In a normal world that is almost every section.
            if (section == null || section.isEmpty() || !section.hasAny(relevant)) {
                sectionIndex++;
                cellIndex = 0;
                continue;
            }

            while (steps < budget && cellIndex < CELLS_PER_SECTION) {
                scanCell(section, column, sectionIndex, cellIndex, selected, rangeSq, px, py, pz, maxTargets);

                cellIndex++;
                steps++;
            }

            if (cellIndex >= CELLS_PER_SECTION) {
                sectionIndex++;
                cellIndex = 0;
            }
        }
    }

    private void scanCell(
        ChunkSection section, Column column, int sectionIdx, int cell,
        SelectionMap<Block> selected, double rangeSq, double px, double py, double pz, int maxTargets
    ) {
        int localY = cell >> 8;
        int localZ = (cell >> 4) & 15;
        int localX = cell & 15;

        int blockX = column.x() * CHUNK_WIDTH + localX;
        int blockZ = column.z() * CHUNK_WIDTH + localZ;
        int y = bottomY + sectionIdx * SECTION_WIDTH + localY;

        double dx = blockX + 0.5 - px;
        double dy = y + 0.5 - py;
        double dz = blockZ + 0.5 - pz;

        if (dx * dx + dy * dy + dz * dz > rangeSq) return;

        long key = BlockPos.asLong(blockX, y, blockZ);
        if (found.containsKey(key)) return;

        BlockState state = section.getBlockState(localX, localY, localZ);
        if (state.isAir() || !selected.isEnabled(state.getBlock())) return;

        // At the cap the sweep stops adding; scan order is nearest first, so whatever is already
        // listed is closer than anything found from here on.
        if (pending.size() >= maxTargets) return;

        EspTarget target = EspTarget.ofBlock(new BlockPos(blockX, y, blockZ), state.getBlock());

        found.put(key, target);
        pending.add(target);
    }

    /**
     * Re-checks every published position, so a box disappears as soon as its block does. The cost is
     * one lookup per published target, not per scanned position, which is why it can run every tick.
     */
    private void verifyFound(
        ClientWorld world, SelectionMap<Block> selected, double range,
        double px, double py, double pz
    ) {
        if (found.isEmpty()) return;

        // The slack keeps the boundary from churning while the player walks.
        double keepSq = (range + CHUNK_WIDTH) * (range + CHUNK_WIDTH);
        Set<EspTarget> retired = new HashSet<>();

        var iterator = found.long2ObjectEntrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            EspTarget target = entry.getValue();

            if (target.distanceSq(px, py, pz) > keepSq) {
                iterator.remove();
                retired.add(target);
                continue;
            }

            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            Chunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);

            BlockState state = chunk == null ? null : chunk.getBlockState(pos);

            if (state == null || state.isAir() || !selected.isEnabled(state.getBlock())) {
                iterator.remove();
                retired.add(target);
            }
        }

        if (!retired.isEmpty()) pending.removeAll(retired);
    }

    private void publish() {
        snapshot = pending.isEmpty() ? Collections.emptyList() : List.copyOf(pending);
    }

    private double movedFromPassStart(Vec3d playerPos) {
        double dx = playerPos.x - passStartX;
        double dz = playerPos.z - passStartZ;

        return Math.sqrt(dx * dx + dz * dz);
    }

    /** @return every chunk column that can hold a position inside the range, nearest first */
    private List<Column> buildColumns(Vec3d playerPos, double range) {
        int radius = (int) Math.ceil(range / (double) CHUNK_WIDTH);
        int playerChunkX = chunkOf(playerPos.x);
        int playerChunkZ = chunkOf(playerPos.z);

        double rangeSq = range * range;
        List<Column> result = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            double chunkMinX = cx * (double) CHUNK_WIDTH;
            double chunkMaxX = chunkMinX + CHUNK_WIDTH;

            double dx = Math.max(Math.max(chunkMinX - playerPos.x, 0.0), playerPos.x - chunkMaxX);
            if (dx * dx > rangeSq) continue;

            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                double chunkMinZ = cz * (double) CHUNK_WIDTH;
                double chunkMaxZ = chunkMinZ + CHUNK_WIDTH;

                double dz = Math.max(Math.max(chunkMinZ - playerPos.z, 0.0), playerPos.z - chunkMaxZ);

                if (dx * dx + dz * dz <= rangeSq) result.add(new Column(cx, cz));
            }
        }

        double anchorX = playerPos.x;
        double anchorZ = playerPos.z;

        result.sort(Comparator.comparingDouble(column -> {
            double centerX = column.x() * (double) CHUNK_WIDTH + 8.0 - anchorX;
            double centerZ = column.z() * (double) CHUNK_WIDTH + 8.0 - anchorZ;

            return centerX * centerX + centerZ * centerZ;
        }));

        return result;
    }

    private static int chunkOf(double coordinate) {
        return (int) Math.floor(coordinate / (double) CHUNK_WIDTH);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
