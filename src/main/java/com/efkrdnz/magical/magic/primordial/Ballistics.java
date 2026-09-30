package com.efkrdnz.magical.magic.primordial;

/**
 * Lobs and falls. Pure.
 *
 * <p>A lob is integrated the way {@code ThrownSpellEntity} integrates it with no drag: move by the
 * velocity, then take {@code gravity} off its height. After {@code ticks} such steps a body started
 * at {@code v} has risen {@code ticks * vy - gravity * ticks * (ticks - 1) / 2}, which is what
 * {@link #lob} solves, so a bomb lands on its mark rather than near it.
 */
public final class Ballistics {
    public static final int MIN_FLIGHT = 12;
    public static final int MAX_FLIGHT = 45;
    private static final double FLIGHT_PER_BLOCK = 1.1D;
    /** A falling star speeds up: progress along its slant is time to this power. */
    public static final double FALL_ACCELERATION = 1.7D;

    private Ballistics() {}

    public static double[] lob(double dx, double dy, double dz, double gravity, int ticks) {
        int t = Math.max(1, ticks);
        double vy = (dy + gravity * t * (t - 1) / 2.0D) / t;
        return new double[] {dx / t, vy, dz / t};
    }

    /** How long a lob over a horizontal distance stays in the air. */
    public static int flightTicks(double horizontal) {
        long t = Math.round(MIN_FLIGHT + horizontal * FLIGHT_PER_BLOCK);
        return (int) Math.max(MIN_FLIGHT, Math.min(MAX_FLIGHT, t));
    }

    /** How far along its slant a falling star is at a fraction of its warning, 0..1. */
    public static double fall(double fraction) {
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        return Math.pow(f, FALL_ACCELERATION);
    }
}
