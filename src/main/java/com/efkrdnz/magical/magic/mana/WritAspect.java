package com.efkrdnz.magical.magic.mana;

/**
 * The six things about a spell an Authority over Mana may legislate.
 *
 * <p>Every one of them is read in {@code MagicSinService.adjustStatsBeforeCast}, the single place
 * every cast in the mod resolves its numbers, which is why all six bite rather than two. A writ
 * names an aspect, an operation and whose magic it is about; nothing here means anything on its own.
 */
public enum WritAspect {
    /** What the cast is billed. */
    COST,
    /** How long the caster waits before it may be cast again. */
    COOLDOWN,
    /** How hard it lands. */
    POTENCY,
    /** How far and how wide it reaches. */
    REACH,
    /** How long what it leaves behind stays. */
    DURATION,
    /** Whether it happens at all. ZERO here is anti-magic, and it is one cell of an ordinary menu. */
    MANIFESTATION;

    public String translationKey() {
        return "writ.magical.aspect." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
