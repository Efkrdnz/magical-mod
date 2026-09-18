package com.efkrdnz.magical.client.renderer.space;

/**
 * The four most recent places something went through the wall.
 *
 * <p>A subspace at rest is still, and that is most of the point - the old dome pulsed, spun and
 * glinted without ever meaning anything by it, which is the surest way to make a thing look
 * generated. So the boundary has exactly one moving part, and it moves only when something
 * happens: a ring runs out from wherever a body crossed, and then the wall is quiet again.
 *
 * <p>Four slots, because the ring is a reading and not a fireworks display: a crowd pouring
 * through a domain should tell you roughly where, not paint the whole shell. A new crossing takes
 * the oldest slot, so the four that are showing are always the four most recent.
 */
public final class SubspaceCrossings {

    /** How many rings can run at once. */
    public static final int SLOTS = 4;
    /** How long one takes to run out and fade. */
    public static final int RIPPLE_TICKS = 16;

    private final float[] x = new float[SLOTS];
    private final float[] y = new float[SLOTS];
    private final float[] z = new float[SLOTS];
    private final int[] startTick = new int[SLOTS];
    private boolean[] used = new boolean[SLOTS];

    /** Records a crossing in the direction of a unit vector from the domain's centre. */
    public void note(float nx, float ny, float nz, int tick) {
        int slot = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!used[i]) {
                slot = i;
                break;
            }
            if (startTick[i] < startTick[slot]) {
                slot = i;
            }
        }
        x[slot] = nx;
        y[slot] = ny;
        z[slot] = nz;
        startTick[slot] = tick;
        used[slot] = true;
    }

    /** How far through its run a ring is, 0 at the crossing and 1 when it is spent. */
    public float phase(int slot, float age) {
        if (!used[slot]) {
            return 1.0F;
        }
        float elapsed = (age - startTick[slot]) / RIPPLE_TICKS;
        return elapsed < 0.0F ? 0.0F : Math.min(elapsed, 1.0F);
    }

    public boolean live(int slot, float age) {
        return used[slot] && phase(slot, age) < 1.0F;
    }

    public float x(int slot) {
        return x[slot];
    }

    public float y(int slot) {
        return y[slot];
    }

    public float z(int slot) {
        return z[slot];
    }
}
