package com.efkrdnz.magical.magic.visual;

/** How the layers of a circle rotate relative to each other. */
public enum SpinSignature {
    /** No rotation at all (breathing only). */
    STATIC(0.0F, false),
    SLOW(0.6F, false),
    SINGLE_FAST(2.2F, false),
    ONE_WAY_FAST(2.6F, false),
    COUNTER_SLOW(0.8F, true),
    COUNTER_FAST(2.0F, true),
    /** One sweeping hand: the spin is driven by the caller (sweep bearing). */
    SWEEP(0.0F, false),
    NONE(0.0F, false);

    private final float degPerTick;
    private final boolean alternate;

    SpinSignature(float degPerTick, boolean alternate) {
        this.degPerTick = degPerTick;
        this.alternate = alternate;
    }

    public float degPerTick() {
        return degPerTick;
    }

    /** Alternating spin signs from layer to layer (counter-rotating rings). */
    public boolean alternate() {
        return alternate;
    }
}
