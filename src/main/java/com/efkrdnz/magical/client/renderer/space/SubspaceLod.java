package com.efkrdnz.magical.client.renderer.space;

/**
 * How finely the boundary shell is tessellated, which depends on how big it looks and never on
 * how big it is.
 *
 * <p>The silhouette error of an n-gon seen from its own centre is {@code 1 - cos(pi/n)}, and
 * there is no radius in that expression at all: a five-block dome and a sixteen-block dome look
 * identical from the middle, so they cost the same. What does matter is how much of the screen
 * the dome covers, and that is {@code distance / radius} - one ratio, dimensionless, the same
 * number for every domain that looks the same size.
 *
 * <p>The old renderer had the opposite arrangement: 30 bands, 122 ring segments and a fixed
 * count of arcs and ticks, emitted identically whether the dome filled the sky or sat a hundred
 * blocks off as a smudge, and every quad of it distance-sorted per frame.
 */
public final class SubspaceLod {

    /** How many detail rungs there are, nearest first. */
    public static final int RUNGS = 4;

    private static final int[] SEGMENTS = {64, 48, 32, 24};
    private static final int[] RINGS = {32, 24, 16, 12};

    /** Where each rung gives out, as {@code distance / radius}. The last rung has no ceiling. */
    private static final double[] CEILINGS = {2.0, 4.0, 8.0};

    /**
     * How many rungs coarser the far wall is drawn than the near one.
     *
     * <p>Seen from outside, the far wall is read through the near wall, so every pixel of it is
     * already multiplied by an alpha in the low tenths; and the two walls share one silhouette,
     * which the near wall draws at full detail. Nothing about the far wall's own tessellation
     * survives to the screen, so it is drawn at a quarter of the cost.
     */
    private static final int FAR_WALL_COARSER = 2;

    private SubspaceLod() {}

    /** The rung for a dome whose distance is {@code viewRatio} times its own radius. */
    public static int rung(double viewRatio) {
        for (int i = 0; i < CEILINGS.length; i++) {
            if (viewRatio < CEILINGS[i]) {
                return i;
            }
        }
        return RUNGS - 1;
    }

    /**
     * The rung for a dome of this radius at this squared distance.
     *
     * @param distanceToCameraSq squared, because that is what the entity renderer already holds
     */
    public static int rungFor(float radius, double distanceToCameraSq) {
        if (radius <= 0.0F) {
            return RUNGS - 1;
        }
        return rung(Math.sqrt(Math.max(0.0, distanceToCameraSq)) / radius);
    }

    /** Segments round the equator. A multiple of four, so a ring always lands due north. */
    public static int segments(int rung) {
        return SEGMENTS[clamp(rung)];
    }

    /** Rings from pole to pole. Even, so there is always a ring exactly on the horizon. */
    public static int rings(int rung) {
        return RINGS[clamp(rung)];
    }

    /**
     * Quads spent on the membrane itself - one wall from inside, two from outside, the far one
     * coarser.
     */
    public static int membraneQuads(int rung, boolean inside) {
        int near = segments(rung) * rings(rung);
        if (inside) {
            return near;
        }
        int far = clamp(rung + FAR_WALL_COARSER);
        return near + segments(far) * rings(far);
    }

    private static int clamp(int rung) {
        return rung < 0 ? 0 : Math.min(rung, RUNGS - 1);
    }
}
