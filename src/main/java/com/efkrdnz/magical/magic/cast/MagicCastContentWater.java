package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.water.DelugeJetSkill;
import com.efkrdnz.magical.magic.skill.water.DrowningBellSkill;
import com.efkrdnz.magical.magic.skill.water.IceRampartSkill;
import com.efkrdnz.magical.magic.skill.water.LeviathanCoilSkill;
import com.efkrdnz.magical.magic.skill.water.RimeSnapSkill;
import com.efkrdnz.magical.magic.skill.water.RipCurrentSkill;

/** WATER base-school skills. */
public final class MagicCastContentWater {
    private MagicCastContentWater() {}

    public static void register() {
        new RipCurrentSkill().register();
        new RimeSnapSkill().register();
        new DrowningBellSkill().register();
        new IceRampartSkill().register();
        new DelugeJetSkill().register();
        new LeviathanCoilSkill().register();
    }
}
