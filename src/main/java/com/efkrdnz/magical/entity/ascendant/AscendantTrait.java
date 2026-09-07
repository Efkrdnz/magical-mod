package com.efkrdnz.magical.entity.ascendant;

import java.util.Set;

/**
 * The sins an Ascendant carries, as boss behaviour rather than as player passives.
 *
 * <p>The seven sin passives exist in the registry, but their behaviour lives in {@code
 * magic/passive/}, which is {@code ServerPlayer}-typed at every entry point. A mob cannot be pushed
 * through that pipeline. It does not need to be: the opponent already honours a few passives in its
 * own hooks - Wrath's knockback on melee, the resistance reductions on incoming damage - and these
 * follow the same pattern.
 *
 * <p>So these are not the player's passives reused. They are the same ideas, expressed where a mob
 * can actually act on them.
 */
public enum AscendantTrait {

    /** Anger sharpens it: melee damage climbs as its health falls. */
    WRATH,

    /** It eats the casting: landing a spell heals it a share of what the spell cost. */
    GLUTTONY,

    /** Too proud to be interrupted: the first hit of each burst simply does not land. */
    PRIDE,

    /** Its presence drags: touching you in melee slows you. */
    SLOTH,

    /** It wants what you have, and takes a spell out of your own book as it loses ground. */
    ENVY,

    /** It takes the means as well as the life: landing a spell drains your pool. */
    GREED;

    /** Nothing, for the tiers that fight on their stats alone. */
    public static final Set<AscendantTrait> NONE = Set.of();

    /** From tier 7: it gets angry, and it feeds. */
    public static final Set<AscendantTrait> SIN_EATER = Set.of(WRATH, GLUTTONY);

    /** From tier 8: it also refuses the opening hit, and it drags. */
    public static final Set<AscendantTrait> FALLEN = Set.of(WRATH, GLUTTONY, PRIDE, SLOTH);

    /** From tier 9: it starts stealing out of your book. */
    public static final Set<AscendantTrait> BLACK_FLAME = Set.of(WRATH, GLUTTONY, PRIDE, SLOTH, ENVY);

    /** Tier 10 carries all of it. */
    public static final Set<AscendantTrait> AUTHORITY =
            Set.of(WRATH, GLUTTONY, PRIDE, SLOTH, ENVY, GREED);
}
