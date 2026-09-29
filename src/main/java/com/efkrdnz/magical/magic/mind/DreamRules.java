package com.efkrdnz.magical.magic.mind;

import java.util.Set;

/**
 * Every number the Dream has, and the shape of the world it happens in. Pure: no Minecraft, so every
 * rule is pinned on an exact value in {@code DreamRulesTest}.
 */
public final class DreamRules {
    /** Lull takes only a viewer who believes some live element of yours this much. */
    public static final float LULL_BELIEF = Belief.SURE;
    public static final int MOB_SLEEP_TICKS = 600;
    public static final int DREAM_TICKS = 1200;
    /** What a dream leaves a dreamer it would have killed. */
    public static final float ONE_HEART = 2.0F;
    public static final int LULL_MANA = 60;

    /** Plots start a million blocks out, far from anything else in any level they are laid out in. */
    public static final int PLOT_BASE = 1_000_000;
    public static final int PLOT_SPACING = 256;
    public static final int PLOTS_PER_ROW = 64;
    public static final int PLOT_Y = 100;
    public static final int PLOT_HALF = 32;
    public static final int PLOT_BELOW = 16;
    public static final int PLOT_ABOVE = 48;
    /** The starter floor under a new plot's arrival: five by five. */
    public static final int PLATFORM_HALF = 2;
    public static final int MAX_FIGMENTS = 32;
    /** How far from the eye a build edit in your own dream may reach. */
    public static final double EDIT_REACH = 8.0;

    /** A way out of the dream, or a thing whose death is worth more than the dream. */
    public static final Set<String> NEVER_DREAMED = Set.of(
            "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway", "minecraft:end_portal_frame",
            "minecraft:wither", "minecraft:ender_dragon");

    private DreamRules() {}

    /** The block a plot is measured from: its arrival floor is one below. */
    public static Offset origin(int plot) {
        return new Offset(PLOT_BASE + Math.floorMod(plot, PLOTS_PER_ROW) * PLOT_SPACING, PLOT_Y,
                PLOT_BASE + Math.floorDiv(plot, PLOTS_PER_ROW) * PLOT_SPACING);
    }

    public static boolean inside(int plot, double x, double y, double z) {
        Offset o = origin(plot);
        return x >= o.dx() - PLOT_HALF && x < o.dx() + PLOT_HALF + 1
                && z >= o.dz() - PLOT_HALF && z < o.dz() + PLOT_HALF + 1
                && y >= o.dy() - PLOT_BELOW && y <= o.dy() + PLOT_ABOVE;
    }

    /** The plot whose cell of the grid this column falls in, or -1 outside the grid. */
    public static int plotAt(double x, double z) {
        double half = PLOT_SPACING / 2.0;
        int col = (int) Math.floor((x - PLOT_BASE + half) / PLOT_SPACING);
        int row = (int) Math.floor((z - PLOT_BASE + half) / PLOT_SPACING);
        if (col < 0 || row < 0 || col >= PLOTS_PER_ROW) {
            return -1;
        }
        return row * PLOTS_PER_ROW + col;
    }

    /** Whether a blow wakes the dreamer: it would leave them one heart or less. */
    public static boolean wakes(float health, float incoming) {
        return incoming >= health - ONE_HEART;
    }

    /** What of a blow actually lands in a dream: never more than leaves one heart. */
    public static float dealt(float health, float incoming) {
        return Math.min(incoming, Math.max(0.0F, health - ONE_HEART));
    }

    public static boolean refused(String id) {
        return NEVER_DREAMED.contains(id);
    }
}
