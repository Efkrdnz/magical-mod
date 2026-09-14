package com.efkrdnz.magical.magic.passive;

/**
 * The client's memory of when each drop of a fed pool left the body.
 *
 * <p>A bleeding pool grows a bite at a time, and the blood of a bite is seen leaving the body at
 * that bite: the cubes the pool did not have a tick ago are born now, a little apart, and fall.
 * A birth never moves once set. What a pool already holds at first sight has already landed -
 * unless it is being fed at that moment, in which case it is the gush just seen.
 *
 * <p>Kept on the entity rather than derived from synced ticks, because the client's own age for
 * an entity starts at zero whenever it first comes into range, so no server tick can be compared
 * to it; what the client can trust is what it sees change.
 */
public final class BloodBleedBirths {

    /** Ticks between one drop of a gush and the next, so a bite leaves as a spurt rather than a lump. */
    public static final float STAGGER = 0.1F;

    private final float[] bornAt = new float[BloodHarvestRules.MAX_CUBES];
    private int known;
    private boolean seen;

    /** The pool is worth {@code cubes} cubes at {@code now}; whichever it did not have are born. */
    public void observe(int cubes, float now, boolean feeding) {
        boolean first = !seen;
        seen = true;
        int target = Math.min(Math.max(0, cubes), bornAt.length);
        if (target <= known) {
            return;
        }
        for (int i = known; i < target; i++) {
            bornAt[i] = first && !feeding ? Float.NEGATIVE_INFINITY : now + (i - known) * STAGGER;
        }
        known = target;
    }

    /** The tick the drop left the body; never, for one the pool has not shed. */
    public float bornAt(int index) {
        return index >= 0 && index < known ? bornAt[index] : Float.POSITIVE_INFINITY;
    }

    public int known() {
        return known;
    }
}
