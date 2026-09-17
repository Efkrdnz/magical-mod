package com.efkrdnz.magical.magic.mana;

/**
 * Whose magic a Weave rule is written about.
 *
 * <p>The whole character of the Authority is in this axis. A wielder who writes only MINE has a
 * private advantage; one who writes only THEIRS has built a trap; one who writes ALL has said
 * something about the world and has to live in it, which is the version the Authority is proud of.
 */
public enum WeaveSubject {
    /** Every caster inside the Weave, the wielder included. */
    ALL,
    /** The wielder alone. */
    MINE,
    /** Everyone except the wielder. */
    THEIRS;

    public String translationKey() {
        return "weave.magical.subject." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
