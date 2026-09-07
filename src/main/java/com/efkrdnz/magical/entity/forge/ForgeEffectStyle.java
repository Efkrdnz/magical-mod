package com.efkrdnz.magical.entity.forge;

/**
 * The visual vocabulary of forged combat. Every forge visual is one of these drawn by
 * {@code ForgeEffectRenderer} — no vanilla particle is ever used for forged-weapon feedback.
 */
public enum ForgeEffectStyle {
    FIRE_BLOOM,
    FROST_SHARDS,
    STORM_FORK,
    VOID_IMPLOSION,
    RADIANT_CROSS,
    VENOM_DRIP,
    TERRA_SHARDS,
    GALE_SWIRL,
    CHARGE_TELEGRAPH,
    BRAND_MARK,
    SLAM_CRACK;

    private static final ForgeEffectStyle[] VALUES = values();

    public static ForgeEffectStyle byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FIRE_BLOOM;
    }
}
