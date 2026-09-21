package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * How a causal relationship behaves, as distinct from what it is.
 *
 * <p>A modifier hangs on an {@link Effect} node - at most {@link #MAX_PER_NODE} of them - and every
 * one is a tool rather than a spell, which is the doc asks for. None of them does anything on its
 * own; each changes the shape of a relationship that was already there.
 *
 * <p>{@link #LESSER} is the only one that is free, and it is the one that makes a Weave smaller.
 * That is on purpose: the board is a budget, and a wielder who cannot afford a chain should be able
 * to buy it back by asking for less rather than by deleting an idea.
 */
public enum Modifier {

    /** The effect happens {@link #DELAY_TICKS} ticks later instead of now. */
    AFTER(1, 0.0F),
    /** The effect happens twice, the second a beat behind the first. */
    TWICE(2, 0.25F),
    /** Half again as much. */
    GREATER(2, 0.15F),
    /** Three fifths as much, and it costs nothing to ask. */
    LESSER(0, 0.0F),
    /** Harm becomes mending and mending harm. The sign of a consequence is nobody to set. */
    TURNED(3, 0.6F),
    /** Split evenly among every valid target rather than landing whole. Wants a crowd to split among. */
    SHARED(2, 0.0F);

    /** How many a single effect may wear. Two is enough to combine and few enough to read. */
    public static final int MAX_PER_NODE = 2;

    /** The delay {@link #AFTER} imposes. Long enough to be a plan, short enough to be a fight. */
    public static final int DELAY_TICKS = 40;

    /** How far behind the first the second copy of a {@link #TWICE} lands. */
    public static final int TWICE_GAP = 6;

    public static final float GREATER_SCALE = 1.5F;
    public static final float LESSER_SCALE = 0.6F;

    private final int weight;
    private final float paradoxPerPoint;

    Modifier(int weight, float paradoxPerPoint) {
        this.weight = weight;
        this.paradoxPerPoint = paradoxPerPoint;
    }

    public int weight() {
        return weight;
    }

    /** Charged on top of the effect own share, per point, for the ones that invent consequence. */
    public float paradoxPerPoint() {
        return paradoxPerPoint;
    }

    /** What this modifier does to the magnitude. One for the ones that do not touch it. */
    public float scale() {
        return switch (this) {
            case GREATER -> GREATER_SCALE;
            case LESSER -> LESSER_SCALE;
            default -> 1.0F;
        };
    }

    public String translationKey() {
        return "modifier.magical." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
