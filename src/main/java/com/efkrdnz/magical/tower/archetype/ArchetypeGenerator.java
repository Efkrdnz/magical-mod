package com.efkrdnz.magical.tower.archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/**
 * Builds the contents of one floor inside one plot.
 *
 * <p>This is the framework's main extension point. A new kind of dungeon is a new implementation
 * of this interface plus a {@link TowerArchetype} registration — no framework code changes.
 *
 * <p>Implementations run on the logical server only, and must confine every block they write to
 * the box reported by {@link #footprint}. The instance manager wipes exactly that box when the
 * plot is released, so anything written outside it leaks and is never cleaned up.
 */
public interface ArchetypeGenerator {
    /**
     * Writes one floor into the world.
     *
     * @param level  the archetype's dimension
     * @param origin the plot's origin block, at the plot's floor height
     * @param floor  1-based floor number within the run, so one generator can scale with depth
     * @param random seeded per instance and floor, so a regenerated plot is reproducible
     * @param sink   receives all placements; the instance manager can only track what the sink records
     */
    void generate(ServerLevel level, BlockPos origin, int floor, RandomSource random, PlacementSink sink);

    /**
     * An advisory bounding box for a plot at {@code origin}.
     *
     * <p>The instance manager uses this for an escapesPlot sanity check to catch configuration
     * errors early. It is no longer the cleanup boundary — cleanup is now driven by recorded
     * placements tracked through {@link PlacementSink}.
     *
     * <p>Kept separate from {@link #generate} so the manager can validate a plot without running
     * the generator.
     */
    AABB footprint(BlockPos origin);

    /**
     * Where a player entering this floor should be placed. Defaults to one block above the plot
     * origin, which is correct for any generator that treats the origin as floor level.
     */
    default BlockPos entryPoint(BlockPos origin, int floor) {
        return origin.above();
    }
}
