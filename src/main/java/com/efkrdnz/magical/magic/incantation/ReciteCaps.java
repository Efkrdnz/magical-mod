package com.efkrdnz.magical.magic.incantation;

/**
 * Noita runs its draw on one player's frame; this runs on a server tick for every wielder, so it is
 * bounded. Past a cap the recite frays: drawing stops, what was planned still fires.
 */
public final class ReciteCaps {

    public static final int MAX_VERSES = 20;
    public static final int MIN_BREATH = 1;
    public static final int MAX_BREATH = 8;
    public static final int MAX_STEPS = 1024;
    public static final int MAX_BODIES = 64;
    public static final int MAX_DEPTH = 4;
    /** Noita's own {@code recursion_limit}. */
    public static final int RECURSION_LIMIT = 2;

    private ReciteCaps() {
    }
}
