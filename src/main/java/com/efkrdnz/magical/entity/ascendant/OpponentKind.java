package com.efkrdnz.magical.entity.ascendant;

/**
 * Which of the two things a {@code MagicOpponentEntity} is.
 *
 * <p>They share an entity type, a renderer and the tower's spawn path, and differ in where their
 * stats and roster come from: a {@link #CLONE} copies the player who spawned it, an
 * {@link #ASCENDANT} reads {@link AscendantTier}.
 *
 * <p>Stored in NBT. An opponent saved before this existed has no tag and loads as {@code CLONE},
 * which is what it was.
 */
public enum OpponentKind {

    /** A copy of the player: their unlocked skills, their tuning, their passives. Difficulty 0-5. */
    CLONE,

    /** An authored enemy with a roster of its own, identical for every player. Difficulty 6-10. */
    ASCENDANT;

    public static OpponentKind byName(String name) {
        for (OpponentKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return CLONE;
    }
}
