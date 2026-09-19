package com.efkrdnz.magical.magic.incantation;

/**
 * Noita's eight action types under our names, in Noita's order (OTHER is CONTROL). Three things the
 * type decides, all ported: which cards make a press "have produced a body" (what spends every
 * card's use), which cards an Impose walks over, and which cards make a payload worth a Latch.
 */
public enum VerseType {
    PROJECTILE,
    STATIC,
    MODIFIER,
    MULTICAST,
    MATERIAL,
    CONTROL,
    UTILITY,
    PASSIVE;

    /** {@code got_projectiles}: a press that played one of these spends the uses of every card in hand. */
    public boolean spawnsBodies() {
        return this == PROJECTILE || this == STATIC || this == MATERIAL;
    }

    /** A card of this type spends its use even on a press that produced no body. */
    public boolean spendsUseAlone() {
        return this == CONTROL || this == UTILITY;
    }

    /** What an Impose scans over on its way to a target. */
    public boolean imposeScans() {
        return this == MODIFIER || this == PASSIVE || this == CONTROL || this == MULTICAST;
    }

    /** What makes a payload worth spawning a Latch for, and what an Impose may target. */
    public boolean payloadWorthy() {
        return this == PROJECTILE || this == STATIC || this == MATERIAL || this == UTILITY;
    }
}
