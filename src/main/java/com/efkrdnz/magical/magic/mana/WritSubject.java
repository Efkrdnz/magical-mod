package com.efkrdnz.magical.magic.mana;

/**
 * Whose magic a writ is about.
 *
 * <p>THEIRS is the ordinary tyranny and MINE the ordinary privilege; ALL is the interesting one,
 * because a wielder who legislates everyone has legislated themselves and the Ledger does not make
 * an exception for the hand that wrote it.
 */
public enum WritSubject {
    ALL,
    MINE,
    THEIRS;

    public String translationKey() {
        return "writ.magical.subject." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
