package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.dark.EffigySkill;
import com.efkrdnz.magical.magic.skill.dark.LongDebtSkill;
import com.efkrdnz.magical.magic.skill.dark.SeverTheThreadSkill;
import com.efkrdnz.magical.magic.skill.dark.UmbralTenancySkill;

/**
 * DARK, the -2 layer: four skills that cost nothing to cast and are paid for afterwards, forever.
 *
 * <p>Only four are registered here. {@code black_flames} and its family, and
 * {@code abyssal_discharge}, live on the same layer and in the same school but keep their existing
 * profiles in {@code MagicVisualContentKept} and their existing routing - they were built before
 * the school existed, and moving them would be a rewrite rather than a registration.
 */
public final class MagicCastContentDark {
    private MagicCastContentDark() {}

    public static void register() {
        new EffigySkill().register();
        new UmbralTenancySkill().register();
        new LongDebtSkill().register();
        new SeverTheThreadSkill().register();
    }
}
