package com.efkrdnz.magical.magic.primordial;

import java.util.Locale;

/**
 * How hard the land answers a catastrophe.
 *
 * <p>Primordial costs the world: a disaster is woken, not made, and the ground where it breaks
 * decides how bad it gets. Each catastrophe samples one feature of the land - open sky, stone,
 * heat, water, the mass of the ground it tears up - and this turns the count into a factor between
 * {@link #MIN} and {@link #MAX} that scales the catastrophe, and into the one word the caster hears.
 * Pure: the counting is done against a level by {@code PrimordialService}.
 */
public final class Wellspring {
    public static final float MIN = 0.5F;
    public static final float MAX = 1.5F;
    /** Heat-bearing blocks round a caldera at which it answers in full. */
    public static final int HEAT_FULL = 14;
    /** Water blocks round the caster at which a tsunami stands as a full wall. */
    public static final int WATER_FULL = 96;
    /** The Nether is heat all the way down: this much of the fraction before a block is counted. */
    public static final double NETHER_HEAT = 0.4D;
    /** Below this height the rock itself is warm. */
    public static final int DEEP_BELOW = 0;
    public static final double DEEP_HEAT = 0.15D;

    private static final float FAINT_BELOW = 0.8F;
    private static final float STEADY_BELOW = 1.1F;
    private static final float STRONG_BELOW = 1.35F;

    /** The word the caster hears for a reading. */
    public enum Word {
        FAINT,
        STEADY,
        STRONG,
        OVERWHELMING;

        public String key() {
            return "message.magical.primordial.answer." + name().toLowerCase(Locale.ROOT);
        }
    }

    private Wellspring() {}

    /** A fraction of the land that answers, 0..1 (clamped), onto MIN..MAX. */
    public static float of(double fraction) {
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        return (float) (MIN + (MAX - MIN) * f);
    }

    public static double fraction(int part, int whole) {
        if (whole <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, part / (double) whole));
    }

    /** Columns that see the sky out of those sampled. */
    public static float sky(int open, int sampled) {
        return of(fraction(open, sampled));
    }

    /** Stone-like blocks out of those sampled under a path. */
    public static float stone(int stone, int sampled) {
        return of(fraction(stone, sampled));
    }

    /** Heat-bearing blocks near, plus the Nether and depth. */
    public static float heat(int hot, boolean nether, int y) {
        double f = Math.min(1.0D, Math.max(0, hot) / (double) HEAT_FULL);
        if (nether) {
            f += NETHER_HEAT;
        }
        if (y < DEEP_BELOW) {
            f += DEEP_HEAT;
        }
        return of(f);
    }

    /** Water blocks round the caster. */
    public static float water(int water) {
        return of(Math.max(0, water) / (double) WATER_FULL);
    }

    /** Solid breakable blocks out of the cells of a plate. */
    public static float mass(int solid, int cells) {
        return of(fraction(solid, cells));
    }

    public static Word word(float reading) {
        if (reading < FAINT_BELOW) {
            return Word.FAINT;
        }
        if (reading < STEADY_BELOW) {
            return Word.STEADY;
        }
        if (reading < STRONG_BELOW) {
            return Word.STRONG;
        }
        return Word.OVERWHELMING;
    }
}
