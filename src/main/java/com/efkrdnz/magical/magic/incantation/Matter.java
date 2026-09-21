package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;

/**
 * What a material verse puts in the world. The school is the colour its body is drawn in, the
 * lifetime is how long the keeper lets it stand before the block it replaced comes back. Pure:
 * which block it is belongs to the entity side ({@code VerseMatter}), the way a look's painter does.
 */
public enum Matter {
    WATER(MagicSchool.WATER, 240),
    LAVA(MagicSchool.FIRE, 200),
    FLAME(MagicSchool.FIRE, 240),
    STONE(MagicSchool.PRIMORDIAL, 400),
    GLASS(MagicSchool.ARCANE, 400),
    ICE(MagicSchool.WATER, 300),
    EARTH(MagicSchool.PRIMORDIAL, 400);

    private final MagicSchool school;
    private final int lifetimeTicks;

    Matter(MagicSchool school, int lifetimeTicks) {
        this.school = school;
        this.lifetimeTicks = lifetimeTicks;
    }

    public MagicSchool school() {
        return school;
    }

    /** How long a laid block stands before the keeper takes it back. */
    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    /** A fluid flows from where it is laid; a solid stays put. */
    public boolean isFluid() {
        return this == WATER || this == LAVA;
    }
}
