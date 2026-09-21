package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * What happens. The right-hand side of {@code then Y happens}, and where the price is decided.
 *
 * <p><b>Paradox is the price of breaking conservation, and that one rule is the whole balance of
 * the Authority.</b> Moving a consequence somewhere else - {@link #PASS}, {@link #RETURN},
 * {@link #STORE} - keeps the books straight and costs almost nothing but mana: the world still
 * receives what it was owed, only not where it expected. Making a consequence stop existing
 * ({@link #ERASE}) or making one out of nothing ({@link #ECHO}, {@link Modifier#TURNED}) is a lie
 * told to reality, and reality charges for it by the point - see {@link #paradoxPerPoint()}.
 *
 * <p>So the clever build is not the one with the strongest effect on it. It is the one that never
 * needs to erase anything, because it arranged for the consequence to have somewhere else to go.
 * That is the whole lesson the Authority is trying to teach, and it is taught by arithmetic rather
 * than by a tooltip.
 *
 * <p>{@link #rewrites()} marks the ones that reach back into the event that fired them. Those only
 * work on a {@link Cause#rewritable() rewritable} cause and are dropped otherwise - a death cannot
 * be un-dealt, and a heartbeat never had a consequence to move.
 */
public enum Effect {

    /** Take {@code param} percent of the signal into the ledger. The consequence loses exactly that. */
    STORE(2, true, 0.0F),
    /** Spend up to {@code param} of the ledger as harm on the scope. */
    SPEND(3, false, 0.0F),
    /** Spend up to {@code param} of the ledger as healing on the scope. */
    MEND(3, false, 0.0F),
    /** Spend up to {@code param} of the ledger as barrier on the scope. */
    WARD(3, false, 0.0F),
    /** The consequence does not happen. The most unnatural thing on the board, and priced like it. */
    ERASE(5, true, 0.9F),
    /** The consequence happens to whoever authored it instead. Conserved, so nearly free of paradox. */
    RETURN(4, true, 0.08F),
    /** The consequence happens to the scope instead. Conserved. The Transfer of the design doc. */
    PASS(4, true, 0.08F),
    /** The consequence happens a second time, where it landed. Made from nothing, so it is dear. */
    ECHO(4, true, 0.5F),
    /** Put the mark on the scope. A Weave that anchors its own targets without the wielder aiming. */
    BRAND(3, false, 0.0F),
    /** The wielder appears behind the scope. */
    STEP(4, false, 0.12F),
    /** The scope is dragged to the wielder. */
    HAUL(4, false, 0.12F),
    /** The scope cannot move for {@code param} ticks. */
    BIND(3, false, 0.05F),
    /** The scope burns for {@code param} ticks. */
    KINDLE(2, false, 0.0F),
    /**
     * The cause and effect of the scope come apart: the next {@code param} consequences it authors
     * do not land at all. The Authority pointed at another Authority, and the dearest thing here.
     */
    SEVER(5, false, 0.6F),
    /** {@code param} mana back to the wielder. The valve that lets a long Weave pay for itself. */
    SIGH(2, false, 0.0F);

    public static final int MIN_PARAM = 1;
    public static final int MAX_PARAM = 100;

    private final int weight;
    private final boolean rewrites;
    private final float paradoxPerPoint;

    Effect(int weight, boolean rewrites, float paradoxPerPoint) {
        this.weight = weight;
        this.rewrites = rewrites;
        this.paradoxPerPoint = paradoxPerPoint;
    }

    public int weight() {
        return weight;
    }

    /**
     * True when this effect reaches back into the event that fired it rather than doing something
     * new. Only meaningful on a {@link Cause#rewritable() rewritable} cause.
     */
    public boolean rewrites() {
        return rewrites;
    }

    /**
     * Paradox charged per point of consequence this effect moved or invented.
     *
     * <p>Zero for the ones that only spend what the wielder had already banked - those were paid
     * for when they were stored. A twelfth for the ones that move a consequence somewhere legal.
     * Nine tenths for erasing one, which is why a wielder who erases a twenty-damage hit twice has
     * put themselves most of the way to a collapse and a wielder who passed it on has not.
     */
    public float paradoxPerPoint() {
        return paradoxPerPoint;
    }

    /** True when the number on this effect is a share of the signal rather than a flat amount. */
    public boolean paramIsPercent() {
        return this == STORE;
    }

    /** True when this effect takes its magnitude out of the ledger rather than off the signal. */
    public boolean spendsLedger() {
        return this == SPEND || this == MEND || this == WARD;
    }

    public boolean takesParam() {
        return this != RETURN && this != ECHO && this != STEP && this != HAUL;
    }

    public int minParam() {
        return switch (this) {
            case STORE -> 5;
            default -> MIN_PARAM;
        };
    }

    public int maxParam() {
        return switch (this) {
            case STORE -> 100;
            case SPEND, MEND, WARD -> Ledger.MAX;
            case BIND, KINDLE -> 200;
            case SEVER -> 5;
            case SIGH -> 40;
            default -> MAX_PARAM;
        };
    }

    public int defaultParam() {
        return switch (this) {
            case STORE -> 50;
            case SPEND -> 20;
            case MEND -> 10;
            case WARD -> 15;
            case ERASE -> 1;
            case BIND -> 40;
            case KINDLE -> 60;
            case SEVER -> 1;
            case SIGH -> 10;
            default -> 0;
        };
    }

    public String translationKey() {
        return "effect.magical." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
