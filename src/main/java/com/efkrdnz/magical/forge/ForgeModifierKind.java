package com.efkrdnz.magical.forge;

/**
 * Every modifier rune a strike can carry.
 *
 * <p>Ordinal is load-bearing: {@link ModifierStack} packs four bits per constant into one long, so
 * this enum may never exceed sixteen constants and a constant may never be reordered without
 * invalidating every forged weapon in every save. Append only.
 */
public enum ForgeModifierKind {
    ECHO, PIERCE, SEEKING, LEECH, BINDING, REACH, HASTE, BRAND, SHATTER, GUARD,
    CHORUS, TITHE, CARRY;

    private static final int DEFAULT_MAX_STACKS = 3;
    private static final int GUARD_MAX_STACKS = 2;
    /** CHORUS multiplies how many times a strike lands, so it stops where GUARD does. */
    private static final int CHORUS_MAX_STACKS = 2;

    /**
     * How many copies of this rune one strike may carry.
     *
     * <p>GUARD stops one lower than the rest on purpose. It is the only modifier that reduces
     * incoming damage rather than increasing outgoing, and the charge guard is already the forge's
     * most abusable surface - stacking a defensive multiplier three deep is the wrong thing to make
     * stronger.
     */
    public int maxStacks() {
        return switch (this) {
            case GUARD -> GUARD_MAX_STACKS;
            case CHORUS -> CHORUS_MAX_STACKS;
            default -> DEFAULT_MAX_STACKS;
        };
    }
}
