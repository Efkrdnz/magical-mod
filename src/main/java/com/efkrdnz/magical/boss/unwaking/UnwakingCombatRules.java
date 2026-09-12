package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.MagicalConfig;

/** Encounter-only tuning. Ordinary spell counters and player damage are unaffected. */
public final class UnwakingCombatRules {
    public static final int PARRY_LEAD_TICKS = 8;
    private UnwakingCombatRules() {}

    public static float damageMultiplier() {
        return MagicalConfig.SPEC.isLoaded() ? MagicalConfig.UNWAKING_DAMAGE_MULTIPLIER.get().floatValue() : 20F;
    }
}
