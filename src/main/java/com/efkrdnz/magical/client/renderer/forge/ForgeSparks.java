package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.forge.visual.ForgeMatter;

import net.minecraft.util.Mth;

/**
 * A forged strike's seeded numbers: how big its shower is, and the per-particle values every
 * client derives from the strike's seed.
 *
 * <p>The shower itself used to be drawn here as geometry - camera-facing slivers flown along a
 * formula, three ticks and gone. It is real particles now ({@code ForgeMatterEmitter}), which fly
 * on and land after the blade is gone; what is left is the arithmetic both halves share. The grade
 * still shows twice over: a divine blade throws a shower and a crude one throws a handful.
 */
public final class ForgeSparks {

    /** How far a spark may fly from the lip, as a fraction of the arc's own radius. */
    private static final float REACH = 0.34f;
    private static final float SLOWEST = 0.45f;
    private static final float FASTEST = 1.0f;

    /** The last fraction of a strike's life in which a new spark is still struck. */
    private static final float LAST_BIRTH = 0.45f;

    /** Salts, so one seed gives every spark several independent numbers. */
    private static final int BIRTH = 0;
    private static final int SPEED = 3;

    private ForgeSparks() {}

    /**
     * How many sparks a strike off a weapon of this grade throws.
     *
     * <p>An ordinal rather than the enum, for the same reason {@link ForgeWeaponLook#emission}
     * takes one: it arrives over the wire, and an ordinal this build does not recognise has to draw
     * something rather than nothing.
     */
    public static int count(int gradeOrdinal) {
        return ForgeMatter.shower(gradeOrdinal);
    }

    /** One of a spark's own numbers, in {@code [0, 1)} and the same on every client and frame. */
    public static float unit(int seed, int index, int salt) {
        int h = seed * 0x9E3779B9 + index * 0x85EBCA6B + salt * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    /**
     * How far through its flight a spark struck at {@code birth} is. Zero until the blade reaches
     * it, one when the strike ends: nothing is left hanging in the air after the cut is gone.
     */
    public static float life(float progress, float birth) {
        float struck = Mth.clamp(birth, 0.0f, 1.0f);
        float span = 1.0f - struck;
        if (span <= 1.0E-4f) {
            return Mth.clamp(progress, 0.0f, 1.0f) >= 1.0f ? 1.0f : 0.0f;
        }
        return Mth.clamp((progress - struck) / span, 0.0f, 1.0f);
    }

    /** How far out spark {@code index} has flown, as a fraction of the arc's radius. */
    public static float reach(int seed, int index, float progress) {
        return flown(life(progress, unit(seed, index, BIRTH) * LAST_BIRTH), unit(seed, index, SPEED));
    }

    /**
     * How far a spark of this speed has flown at this point in its life, as a fraction of radius.
     *
     * <p>Eased out: a spark leaves fast and slows, which is what makes it read as thrown rather
     * than as a line being extruded.
     */
    private static float flown(float life, float speed) {
        float t = Mth.clamp(life, 0.0f, 1.0f);
        return REACH * Mth.lerp(Mth.clamp(speed, 0.0f, 1.0f), SLOWEST, FASTEST) * (1.0f - (1.0f - t) * (1.0f - t));
    }
}
