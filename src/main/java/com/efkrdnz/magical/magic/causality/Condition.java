package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * A question asked between a {@link Cause} and an {@link Effect}. The middle of the board.
 *
 * <p>A condition passes a signal along or kills the branch, and it never changes what the signal
 * carries. That restraint is deliberate: it makes a chain readable left to right as one sentence,
 * and it means the only thing that can alter a magnitude is an effect or a {@link Modifier}, which
 * is what keeps the conservation rule checkable in one place.
 *
 * <p>{@link #ONCE_PER} is the odd one and the important one. It is the only condition with memory,
 * and it is the wielder's own governor: a chain that would otherwise fire on every tick of a fight
 * can be held to once every two seconds by the person who built it. Almost every strong Weave has
 * one, which is the intended lesson - the Authority hands you the rope and expects you to tie your
 * own knot in it.
 */
public enum Condition {

    /** The other party in the event is the marked. The doc's own worked example turns on this. */
    FROM_MARKED(2, true, false),
    /** The consequence came off a fist or a blade. */
    IS_MELEE(1, false, false),
    /** The consequence came off something that flew. */
    IS_PROJECTILE(1, false, false),
    /** The consequence was magic, of any school. */
    IS_MAGIC(1, false, false),
    /** The consequence was fire, lava or burning. */
    IS_FIRE(1, false, false),
    /** The signal is carrying at least {@code param}. */
    ABOVE(1, false, true),
    /** The signal is carrying less than {@code param}. */
    BELOW(1, false, true),
    /** My health is under {@code param} percent. */
    HURT_UNDER(1, false, true),
    /** My health is over {@code param} percent. */
    HALE_OVER(1, false, true),
    /** The ledger holds at least {@code param}. */
    LEDGER_OVER(1, false, true),
    /** The other party is within {@code param} blocks of me. */
    WITHIN(1, false, true),
    /** I am crouching. The one condition the wielder can answer in the middle of a fight. */
    CROUCHED(1, false, false),
    /** Paradox is under {@code param}. A Weave that knows when to stop arguing. */
    SETTLED(1, false, true),
    /** A mark exists and the thing wearing it is alive. */
    MARKED_LIVES(2, true, false),
    /** This node passes at most once every {@code param} ticks. The governor. */
    ONCE_PER(1, false, true);

    public static final int MIN_PARAM = 1;
    public static final int MAX_PARAM = 600;

    private final int weight;
    private final boolean anchored;
    private final boolean param;

    Condition(int weight, boolean anchored, boolean param) {
        this.weight = weight;
        this.anchored = anchored;
        this.param = param;
    }

    public int weight() {
        return weight;
    }

    public boolean needsAnchor() {
        return anchored;
    }

    public boolean takesParam() {
        return param;
    }

    /** True for the one condition that remembers when it last passed, so the engine keeps its clock. */
    public boolean hasMemory() {
        return this == ONCE_PER;
    }

    public int minParam() {
        return switch (this) {
            case ONCE_PER -> Cause.MIN_INTERVAL;
            case HURT_UNDER, HALE_OVER, SETTLED -> 1;
            default -> MIN_PARAM;
        };
    }

    public int maxParam() {
        return switch (this) {
            case ONCE_PER -> MAX_PARAM;
            case HURT_UNDER, HALE_OVER, SETTLED -> 100;
            case LEDGER_OVER -> Ledger.MAX;
            case WITHIN -> Cause.MAX_RANGE;
            default -> MAX_PARAM;
        };
    }

    public int defaultParam() {
        return switch (this) {
            case ABOVE -> 10;
            case BELOW -> 10;
            case HURT_UNDER -> 40;
            case HALE_OVER -> 60;
            case LEDGER_OVER -> 20;
            case WITHIN -> 8;
            case SETTLED -> 40;
            case ONCE_PER -> 40;
            default -> 0;
        };
    }

    /**
     * True when the question is about the event rather than about the world.
     *
     * <p>A source-kind question on a cause that carries no source - a heartbeat, a decree - can
     * never be true, so the board marks the chain dead rather than letting the wielder build a
     * machine that silently never runs.
     */
    public boolean asksAboutTheEvent() {
        return this == IS_MELEE || this == IS_PROJECTILE || this == IS_MAGIC || this == IS_FIRE
                || this == FROM_MARKED || this == WITHIN;
    }

    public String translationKey() {
        return "condition.magical." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
