package com.efkrdnz.magical.tower.instance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Where plots sit inside an archetype dimension.
 *
 * <p>Because dimensions cannot be created at runtime, concurrent dungeon runs share one dimension
 * and are kept apart by distance instead. A plot index maps to a cell on a square grid; the grid
 * is spaced widely enough that no run will ever see, hear, or damage another.
 *
 * <p>Pure arithmetic, no state. {@link InstanceManager} decides which indices are in use.
 */
public final class PlotGrid {
    /**
     * Distance between plot origins, in blocks. Generous on purpose: it has to exceed the largest
     * blast, projectile, or sound a dungeon can produce, plus render distance, or one run bleeds
     * into its neighbour.
     */
    public static final int PLOT_SPACING = 1024;

    /** Plots per row before wrapping to the next row. */
    public static final int GRID_WIDTH = 64;

    /**
     * Plot indices available before coordinates get uncomfortably large. At the values above the
     * far corner sits about 64k blocks out, well inside the world border.
     */
    public static final int MAX_PLOTS = GRID_WIDTH * GRID_WIDTH;

    private PlotGrid() {}

    /** The origin block of a plot, at the archetype's floor height. */
    public static BlockPos origin(int plotIndex, int floorY) {
        return new BlockPos(
                (plotIndex % GRID_WIDTH) * PLOT_SPACING,
                floorY,
                (plotIndex / GRID_WIDTH) * PLOT_SPACING);
    }

    /**
     * The region a plot owns, independent of what any generator actually built in it.
     *
     * <p>Used as the cleanup bound when a plot is released without a generator on hand, and as the
     * sanity check that a generator's own footprint stays inside its plot.
     */
    public static AABB bounds(int plotIndex, int floorY) {
        BlockPos origin = origin(plotIndex, floorY);
        int half = PLOT_SPACING / 2;
        return new AABB(
                origin.getX() - half, origin.getY() - 64, origin.getZ() - half,
                origin.getX() + half, origin.getY() + 192, origin.getZ() + half);
    }

    /** True if a generator's footprint would spill outside the plot it was given. */
    public static boolean escapesPlot(AABB footprint, int plotIndex, int floorY) {
        AABB plot = bounds(plotIndex, floorY);
        return footprint.minX < plot.minX || footprint.maxX > plot.maxX
                || footprint.minY < plot.minY || footprint.maxY > plot.maxY
                || footprint.minZ < plot.minZ || footprint.maxZ > plot.maxZ;
    }
}
