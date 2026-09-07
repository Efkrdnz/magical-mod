package com.efkrdnz.magical.forge;

public enum ForgeModifierKind {
    ECHO, PIERCE, SEEKING, LEECH, BINDING, REACH, HASTE, BRAND, SHATTER, GUARD;

    private static final int DEFAULT_MAX_STACKS = 3;
    private static final int GUARD_MAX_STACKS = 2;

    /**
     * How many copies of this rune one strike may carry.
     *
     * <p>GUARD stops one lower than the rest on purpose. It is the only modifier that reduces
     * incoming damage rather than increasing outgoing, and the charge guard is already the forge's
     * most abusable surface - stacking a defensive multiplier three deep is the wrong thing to make
     * stronger.
     */
    public int maxStacks() {
        return this == GUARD ? GUARD_MAX_STACKS : DEFAULT_MAX_STACKS;
    }
}
