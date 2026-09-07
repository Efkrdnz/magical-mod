package com.efkrdnz.magical.entity.ascendant;

/**
 * How a tier fights, which is mostly a question of where it wants to stand.
 *
 * <p>The blades in {@link AscendantLoadout} already differ in kind rather than degree - a chain
 * lightning axe, a leeching duelist's sword, an artillery piece that forks detonating waves - and
 * stance is what makes that difference visible. A weapon-forward tier hovering out of reach would be
 * wasting its own inscription; an artillery tier standing still would be wasting yours.
 */
public enum AscendantStance {

    /** Opens at range, closes to swing. Mixes freely. */
    SKIRMISHER(6.0D, false),

    /** Wants the ground and the duel. Takes off only to break out of a melee chain. */
    DUELIST(3.0D, false),

    /** Holds the air and kites the whole fight. Its weapon detonates where it lands anyway. */
    ARTILLERY(9.0D, true),

    /** Dives: flies to close distance fast, lands to swing, takes off again. */
    EXECUTIONER(5.0D, false),

    /** Flies freely, and answers being grounded rather than accepting it. */
    AUTHORITY(7.0D, true);

    private final double preferredAltitude;
    private final boolean holdsAir;

    AscendantStance(double preferredAltitude, boolean holdsAir) {
        this.preferredAltitude = preferredAltitude;
        this.holdsAir = holdsAir;
    }

    /** Blocks above the target this stance settles at, once it has a reason to be airborne. */
    public double preferredAltitude() {
        return preferredAltitude;
    }

    /**
     * Whether this stance wants the air by default.
     *
     * <p>A stance that does not still takes off for a concrete reason - being meleed, needing range,
     * having no path to you. Wanting the air is a preference, never a licence to hover.
     */
    public boolean holdsAir() {
        return holdsAir;
    }
}
