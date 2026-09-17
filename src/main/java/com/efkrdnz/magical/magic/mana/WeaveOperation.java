package com.efkrdnz.magical.magic.mana;

/**
 * What a Weave rule does to the aspect it is written on.
 *
 * <p>Deliberately the same six shapes for every aspect, unlike Space, whose fifty-seven operations
 * each belong to exactly one category. Magic has fewer dials than physics and they all turn the
 * same way, so the grammar is a clean product rather than a table of special cases - which also
 * means a player who has learned one aspect has learned all six.
 */
public enum WeaveOperation {
    /** More of it. Costs triple, cooldowns run long, effects outstay their welcome. */
    RAISE(3.0F),
    /** Less of it. Costs halve, cooldowns run short. */
    LOWER(0.5F),
    /** None of it. Free casts, no cooldown, or - on SCHOOL - no magic whatsoever. */
    ZERO(0.0F),
    /** Backwards. Mana flows the wrong way, a heal is a wound, a ward is an opening. */
    INVERT(-1.0F),
    /** Frozen where it stands. Nothing may raise, lower or zero it while the lock holds. */
    LOCK(1.0F),
    /** The rule lifts and the aspect behaves as it does everywhere else in the world. */
    RESTORE(1.0F);

    private final float factor;

    WeaveOperation(float factor) {
        this.factor = factor;
    }

    /** The multiplier this operation applies to a numeric aspect such as COST or COOLDOWN. */
    public float factor() {
        return factor;
    }

    /** True when the rule erases rather than stores, the way a CLEAR does on a subspace. */
    public boolean clears() {
        return this == RESTORE;
    }

    public String translationKey() {
        return "weave.magical.operation." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
