package com.efkrdnz.magical.magic.mana;

/**
 * The six things about magic itself that the Authority of Mana may legislate inside its Weave.
 *
 * <p>Space writes laws about how a body moves. Mana writes laws about how a spell behaves, which is
 * a smaller vocabulary and a far heavier one: a subspace can make you fall upward, a Weave can make
 * your whole school stop existing.
 */
public enum WeaveAspect {
    /** What a cast costs the caster. */
    COST,
    /** How long a caster waits before the same skill answers again. */
    COOLDOWN,
    /** How long what a cast produced lasts once it exists. */
    DURATION,
    /** Whether a school may function at all. The extreme setting here is anti-magic. */
    SCHOOL,
    /** Which way loose mana moves - toward the wielder, or away from them. */
    FLOW,
    /** Whether a cast produces anything at all, or resolves into nothing. */
    MANIFESTATION;

    public String translationKey() {
        return "weave.magical.aspect." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
