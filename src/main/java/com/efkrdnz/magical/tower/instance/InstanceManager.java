package com.efkrdnz.magical.tower.instance;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.tower.archetype.PlacementSink;
import com.efkrdnz.magical.tower.archetype.TowerArchetype;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Owns the lifecycle of every live plot: allocate, generate, release, wipe, reclaim.
 *
 * <p>This layer knows nothing about towers, progression, rewards, or the player's magic. It hands
 * out places for a run to happen and guarantees they get cleaned up. Gameplay sits above it.
 *
 * <p>State here is intentionally <b>not</b> persisted. An instance lasts minutes, so a restart
 * during one should abandon it rather than resurrect it. What must survive a restart is the
 * player's tower progress, and that already lives in {@code PlayerMagicState.towerClears}. The
 * cost of not persisting is that blocks from an in-flight run outlive the restart with nothing
 * tracking them, which is what {@link #wipeOrphans} is for.
 */
public final class InstanceManager {
    /** Ticks a plot may sit empty before it is reclaimed. Ten minutes. */
    private static final long IDLE_EXPIRY_TICKS = 20L * 60L * 10L;

    /** How often the sweep runs. Cleanup is not urgent; checking every tick would be waste. */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    /**
     * How many blocks to drain (set to air) per sweep. Large dungeons are drained over many sweeps
     * rather than in a single stall.
     */
    private static final int DRAIN_BUDGET_BLOCKS_PER_SWEEP = 60_000;

    private static final Map<TowerInstance.Key, TowerInstance> LIVE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Queue<Integer>> RECLAIMED = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, AtomicInteger> NEXT_INDEX = new ConcurrentHashMap<>();

    private static int sweepCountdown = SWEEP_INTERVAL_TICKS;

    private InstanceManager() {}

    /**
     * Claims a plot and builds its first floor.
     *
     * @return the new instance, or null if the archetype's dimension is missing or its grid is full
     */
    public static TowerInstance allocate(MinecraftServer server, TowerArchetype archetype, UUID owner) {
        ServerLevel level = server.getLevel(archetype.dimension());
        if (level == null) {
            MagicalMod.LOGGER.warn("Tower archetype {} has no loaded dimension {}",
                    archetype.id(), archetype.dimension().location());
            return null;
        }

        Integer plotIndex = claimPlotIndex(archetype.id());
        if (plotIndex == null) {
            MagicalMod.LOGGER.warn("Tower archetype {} has no free plots", archetype.id());
            return null;
        }

        TowerInstance.Key key = new TowerInstance.Key(archetype.id(), plotIndex);
        TowerInstance instance = new TowerInstance(key, archetype, owner, level.getGameTime());
        LIVE.put(key, instance);
        generate(level, instance);
        return instance;
    }

    private static Integer claimPlotIndex(ResourceLocation archetypeId) {
        Integer recycled = RECLAIMED
                .computeIfAbsent(archetypeId, ignored -> new ConcurrentLinkedQueue<>())
                .poll();
        if (recycled != null) {
            return recycled;
        }
        int next = NEXT_INDEX.computeIfAbsent(archetypeId, ignored -> new AtomicInteger()).getAndIncrement();
        return next < PlotGrid.MAX_PLOTS ? next : null;
    }

    /** Builds, or rebuilds, the instance's current floor. */
    public static void generate(ServerLevel level, TowerInstance instance) {
        TowerArchetype archetype = instance.archetype();
        BlockPos origin = instance.origin();
        AABB footprint = archetype.generator().footprint(origin);

        if (PlotGrid.escapesPlot(footprint, instance.key().plotIndex(), archetype.floorY())) {
            MagicalMod.LOGGER.error("Generator for {} reports a footprint outside its plot; refusing to generate",
                    archetype.id());
            return;
        }

        RandomSource random = RandomSource.create(
                instance.key().plotIndex() * 341873128712L + instance.floor() * 132897987541L);

        // Create a sink that both writes the block and records the placement for later cleanup.
        PlacementSink sink = new PlacementSink() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                level.setBlock(pos, state, 3);
                instance.recordPlacement(pos);
            }
        };

        archetype.generator().generate(level, origin, instance.floor(), random, sink);
    }

    public static TowerInstance get(TowerInstance.Key key) {
        return key == null ? null : LIVE.get(key);
    }

    /** The active instance this player owns, or null. One run per player at a time. */
    public static TowerInstance ownedBy(UUID playerId) {
        for (TowerInstance instance : LIVE.values()) {
            if (instance.isActive() && instance.owner().equals(playerId)) {
                return instance;
            }
        }
        return null;
    }

    public static Collection<TowerInstance> all() {
        return Collections.unmodifiableCollection(LIVE.values());
    }

    public static int liveCount() {
        return LIVE.size();
    }

    /** Marks an instance finished. Its plot is wiped and reclaimed on a later sweep. */
    public static void release(TowerInstance.Key key) {
        TowerInstance instance = LIVE.get(key);
        if (instance != null) {
            instance.release();
        }
    }

    /**
     * Runs expiry and cleanup. Call once per server tick; it rate-limits itself.
     *
     * <p>Three things happen here and only here:
     * <ol>
     *   <li>Instances nobody has occupied for {@link #IDLE_EXPIRY_TICKS} are released.
     *   <li>Released instances' recorded placements are drained back to air across ticks, budgeted
     *       so large dungeons do not stall the server.
     *   <li>Once an instance is fully drained, its plot index is reclaimed.
     * </ol>
     * This is what stops abandoned runs accumulating forever.
     */
    public static void tick(MinecraftServer server) {
        if (--sweepCountdown > 0) {
            return;
        }
        sweepCountdown = SWEEP_INTERVAL_TICKS;

        List<TowerInstance> toRemove = new ArrayList<>();
        for (TowerInstance instance : LIVE.values()) {
            ServerLevel level = server.getLevel(instance.archetype().dimension());
            if (level == null) {
                toRemove.add(instance);
                continue;
            }
            if (instance.isActive()) {
                if (isOccupied(level, instance)) {
                    instance.markOccupied(level.getGameTime());
                } else if (level.getGameTime() - instance.lastOccupiedGameTime() > IDLE_EXPIRY_TICKS) {
                    instance.release();
                }
            }
            // Drain placements if this instance is released.
            if (!instance.isActive()) {
                int drained = drainPlacements(level, instance, DRAIN_BUDGET_BLOCKS_PER_SWEEP);
                // Only remove once fully drained.
                if (instance.recordedPlacements().isEmpty()) {
                    toRemove.add(instance);
                }
            }
        }

        for (TowerInstance instance : toRemove) {
            LIVE.remove(instance.key());
            RECLAIMED.computeIfAbsent(instance.key().archetypeId(), ignored -> new ConcurrentLinkedQueue<>())
                    .offer(instance.key().plotIndex());
        }
    }

    private static boolean isOccupied(ServerLevel level, TowerInstance instance) {
        AABB plot = PlotGrid.bounds(instance.key().plotIndex(), instance.archetype().floorY());
        return level.players().stream().anyMatch(player -> plot.contains(player.position()));
    }

    /**
     * Drains recorded placements from an instance, setting them to air up to a budget per sweep.
     *
     * @return the number of blocks drained in this sweep
     */
    private static int drainPlacements(ServerLevel level, TowerInstance instance, int budget) {
        Set<Long> placements = instance.recordedPlacements();
        int drained = 0;
        // Drain up to budget blocks from the set. Concurrent iteration is safe with ConcurrentHashMap.
        var iterator = placements.iterator();
        while (iterator.hasNext() && drained < budget) {
            long packedPos = iterator.next();
            BlockPos pos = BlockPos.of(packedPos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            iterator.remove();
            drained++;
        }
        return drained;
    }

    private static void clearBox(ServerLevel level, AABB box, String label) {
        BlockPos.betweenClosedStream(
                        (int) box.minX, (int) box.minY, (int) box.minZ,
                        (int) box.maxX, (int) box.maxY, (int) box.maxZ)
                .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
    }

    /**
     * Clears leftovers that no live instance is tracking, for one archetype.
     *
     * <p>Instance state is not persisted, so a crash mid-run leaves blocks behind that the sweep
     * cannot see. This is deliberately a manual operation rather than something that runs at
     * startup: it is destructive, and only an operator can know it is safe to run.
     *
     * <p>Orphans have no recorded placements, so cleanup is bounded by the generator's footprint.
     * This is safe because the footprint is an advisory sanity bound, not the unlimited generator
     * output: a well-written generator respects its own footprint.
     *
     * @return how many plots were cleared
     */
    public static int wipeOrphans(MinecraftServer server, TowerArchetype archetype) {
        ServerLevel level = server.getLevel(archetype.dimension());
        if (level == null) {
            return 0;
        }
        int wiped = 0;
        for (int index = 0; index < PlotGrid.MAX_PLOTS; index++) {
            if (LIVE.containsKey(new TowerInstance.Key(archetype.id(), index))) {
                continue;
            }
            BlockPos origin = PlotGrid.origin(index, archetype.floorY());
            if (level.isLoaded(origin) && !level.getBlockState(origin).isAir()) {
                clearBox(level, archetype.generator().footprint(origin), archetype.id() + "#" + index);
                wiped++;
            }
        }
        return wiped;
    }

    /** Drops all tracking without touching the world. For server shutdown only. */
    public static void clear() {
        LIVE.clear();
        RECLAIMED.clear();
        NEXT_INDEX.clear();
    }
}
