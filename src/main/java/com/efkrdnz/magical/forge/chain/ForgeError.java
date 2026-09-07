package com.efkrdnz.magical.forge.chain;

import java.util.Locale;

/** Every reason a forge attempt can be refused, client and server side. */
public enum ForgeError {
    NO_WEAPON,
    NOT_FORGEABLE,
    BAD_PAYLOAD,
    UNRECOGNIZED_GLYPH,
    AMBIGUOUS_GLYPH,
    /**
     * A glyph the client claimed to have kept off the weapon in the slot, which that weapon does
     * not actually carry in that category - or does not carry as many times as was claimed.
     */
    KEPT_GLYPH_MISSING,
    MISSING_GRADE,
    MISSING_ELEMENT,
    MISSING_FORM,
    DUPLICATE_GRADE,
    DUPLICATE_ELEMENT,
    TOO_MANY_FORMS,
    TOO_MANY_MODIFIERS,
    /**
     * A modifier rune drawn more times than it stacks.
     *
     * <p>It used to mean any repeat at all. Runes stack now, so it means only the copy that went
     * past the cap - one that would be charged for and then do nothing. The constant keeps its
     * place in this enum because the wire sends errors by ordinal, and reusing it costs nothing
     * where retiring it would leave a hole.
     */
    DUPLICATE_MODIFIER,
    DUPLICATE_TEMPER,
    SEEKING_NEEDS_PROJECTILE,
    CLASS_REQUIRED,
    DIVINESMITH_REQUIRED,
    DIVINE_REQUIRES_FORGED,
    MATERIAL_CAP,
    COOLDOWN,
    NO_MANA,
    MISFIRE,

    // Appended, never reordered: errors travel the wire as ordinals, so an insert anywhere above
    // would make an older client render the wrong message for every error after it.

    /** Two element runes that do not fuse into anything. */
    FUSION_UNKNOWN_PAIR,
    /** A fusion the smith lacks the class for. The argument is the fusion ordinal. */
    FUSION_LOCKED_CLASS,
    /** A fusion whose sorcery the smith has not learned. The argument is the fusion ordinal. */
    FUSION_LOCKED_SKILL,
    /** A second element rune drawn at a grade that holds only one. */
    FUSION_NEEDS_GRADE;

    /** Translation key of the message shown to the player. */
    public String langKey() {
        return "forge.magical.error." + name().toLowerCase(Locale.ROOT);
    }
}
