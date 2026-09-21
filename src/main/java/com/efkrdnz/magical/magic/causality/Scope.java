package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * Who an {@link Effect} lands on. The doc's <b>Redirect</b>, expressed as a property of the effect
 * rather than as a modifier, because every effect needs one and an effect with nowhere to land is
 * not a thing the board should be able to draw.
 *
 * <p>The weights are the progression the doc asks for, in four numbers: yourself and whoever you
 * are already tangled with are free, the nearest body costs a point of thought, a marked one costs
 * two and an anchor, and the whole field around you costs four. A Weave that only ever touches its
 * owner is cheap enough to run six chains; one that reaches for everything can afford two.
 */
public enum Scope {

    /** The wielder. */
    SELF(0, false),
    /** The other party in the event - whoever hit me, whoever I hit, whoever died. */
    OTHER(0, false),
    /** The nearest living thing to me, the wielder excepted. */
    NEAREST(1, false),
    /** Whatever wears the mark. */
    MARKED(2, true),
    /** Every living thing within {@link #FIELD_RADIUS} blocks of me, the wielder excepted. */
    FIELD(4, false);

    /** How far {@link #FIELD} and {@link #NEAREST} reach. */
    public static final double FIELD_RADIUS = 8.0D;
    public static final double NEAREST_RADIUS = 16.0D;

    private final int weight;
    private final boolean anchored;

    Scope(int weight, boolean anchored) {
        this.weight = weight;
        this.anchored = anchored;
    }

    public int weight() {
        return weight;
    }

    public boolean needsAnchor() {
        return anchored;
    }

    /** True when the scope names one body rather than a crowd; {@link Modifier#SHARED} wants a crowd. */
    public boolean single() {
        return this != FIELD;
    }

    public String translationKey() {
        return "scope.magical." + name().toLowerCase(Locale.ROOT);
    }
}
