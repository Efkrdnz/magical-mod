package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * An event the wielder is watching for. The left-hand side of {@code when X happens}.
 *
 * <p>A Cause is <b>not</b> a thing the wielder does. Every other Authority is pressed - Chaos
 * burdens, Mana recites, Space writes a law - and this one is the machine that runs when nobody is
 * pressing anything. That is the whole of its character: the wielder's work happens at the board,
 * hours before the fight, and what they spend in the fight is having been right.
 *
 * <p>Each carries a {@link #weight}, which is what the board charges to hold it, and whether it
 * {@link #needsAnchor() reaches outside the wielder}. The doc's rule is here in the numbers and
 * nowhere else: your own causality is cheap, somebody else's costs three times as much, and the
 * only door to somebody else's is the {@link Effect#BRAND anchor}.
 *
 * <p>{@link #carriesMagnitude()} says whether the event has a size to it. A cause without one
 * starts its chain at zero, so an {@link Effect} that spends a magnitude reads the ledger instead -
 * which is exactly how {@link #DECREE} turns into a weapon.
 */
public enum Cause {

    /** Something hurt me. Magnitude is the damage as it stood when the Weave was asked. */
    HURT(1, false, true),
    /** I hurt something. Magnitude is the damage dealt. */
    STRIKE(1, false, true),
    /** Something died to me. Magnitude is the dead thing's maximum health. */
    SLAY(1, false, true),
    /** I was healed. Magnitude is the healing. */
    MENDED(1, false, true),
    /** I cast a skill. Magnitude is what it cost in mana. */
    INVOKE(1, false, true),
    /** A hit emptied my barrier. Magnitude is what got through it. */
    BREAK(2, false, true),
    /** I pressed Decree. The one cause the wielder fires by hand, and it carries nothing. */
    DECREE(1, false, false),
    /** The ledger rose to {@code param} or past it. The cause a chain feeds itself through. */
    BRIM(2, false, false),
    /** Every {@code param} ticks. The heartbeat, and the only cause that needs nothing to happen. */
    TOLL(2, false, false),
    /** Something living came within {@code param} blocks of me. A tripwire. */
    NEAR(2, false, false),
    /** The marked took damage, from anyone. Magnitude is that damage. */
    MARKED_HURT(3, true, true),
    /** The marked hurt something. Magnitude is that damage. */
    MARKED_STRIKES(3, true, true),
    /** The marked died. Magnitude is its maximum health. */
    MARKED_FALLS(3, true, true);

    /** Causes whose {@code param} is a tick interval rather than a threshold, and its bounds. */
    public static final int MIN_INTERVAL = 20;
    public static final int MAX_INTERVAL = 600;
    /** {@link #NEAR}'s reach, in blocks. */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 24;
    /** {@link #BRIM}'s threshold, in ledger. */
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = Ledger.MAX;

    private final int weight;
    private final boolean anchored;
    private final boolean magnitude;

    Cause(int weight, boolean anchored, boolean magnitude) {
        this.weight = weight;
        this.anchored = anchored;
        this.magnitude = magnitude;
    }

    public int weight() {
        return weight;
    }

    /** True for the three that watch somebody else. Nothing in the Weave may hold one without a Mark. */
    public boolean needsAnchor() {
        return anchored;
    }

    /** True when the event has a size. A chain from one that has not starts at zero. */
    public boolean carriesMagnitude() {
        return magnitude;
    }

    /** True when the wielder sets a number on this cause. */
    public boolean takesParam() {
        return this == BRIM || this == TOLL || this == NEAR;
    }

    public int minParam() {
        return switch (this) {
            case BRIM -> MIN_THRESHOLD;
            case TOLL -> MIN_INTERVAL;
            case NEAR -> MIN_RANGE;
            default -> 0;
        };
    }

    public int maxParam() {
        return switch (this) {
            case BRIM -> MAX_THRESHOLD;
            case TOLL -> MAX_INTERVAL;
            case NEAR -> MAX_RANGE;
            default -> 0;
        };
    }

    public int defaultParam() {
        return switch (this) {
            case BRIM -> 20;
            case TOLL -> 100;
            case NEAR -> 6;
            default -> 0;
        };
    }

    /**
     * True when the consequence this cause carries can still be rewritten.
     *
     * <p>{@link Effect#ERASE}, {@link Effect#RETURN}, {@link Effect#PASS} and {@link Effect#STORE}
     * all reach back into the event that fired them, so they mean nothing on a cause whose event is
     * already over - a death has happened, a heartbeat never had a consequence at all. On those the
     * board says so and the engine drops the action rather than pretending it landed.
     */
    public boolean rewritable() {
        return this == HURT || this == STRIKE || this == MENDED || this == BREAK
                || this == MARKED_HURT || this == MARKED_STRIKES;
    }

    public String translationKey() {
        return "cause.magical." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
