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
    DUPLICATE_MODIFIER,
    DUPLICATE_TEMPER,
    SEEKING_NEEDS_PROJECTILE,
    CLASS_REQUIRED,
    DIVINESMITH_REQUIRED,
    DIVINE_REQUIRES_FORGED,
    MATERIAL_CAP,
    COOLDOWN,
    NO_MANA,
    MISFIRE;

    /** Translation key of the message shown to the player. */
    public String langKey() {
        return "forge.magical.error." + name().toLowerCase(Locale.ROOT);
    }
}
