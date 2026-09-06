package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.light.CleansingRaySkill;
import com.efkrdnz.magical.magic.skill.light.GlintSkill;
import com.efkrdnz.magical.magic.skill.light.HeavensGazeSkill;
import com.efkrdnz.magical.magic.skill.light.LanternBrandSkill;
import com.efkrdnz.magical.magic.skill.light.PrismCascadeSkill;
import com.efkrdnz.magical.magic.skill.light.RevelationSkill;

/** LIGHT base-school skills. */
public final class MagicCastContentLight {
    private MagicCastContentLight() {}

    public static void register() {
        new GlintSkill().register();
        new LanternBrandSkill().register();
        new RevelationSkill().register();
        new CleansingRaySkill().register();
        new PrismCascadeSkill().register();
        new HeavensGazeSkill().register();
    }
}
