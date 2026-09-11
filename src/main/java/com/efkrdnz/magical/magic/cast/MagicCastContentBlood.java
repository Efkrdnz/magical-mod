package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.blood.BloodManipulationSkill;
import com.efkrdnz.magical.magic.skill.blood.CrimsonTitheSkill;
import com.efkrdnz.magical.magic.skill.blood.ExsanguinateSkill;
import com.efkrdnz.magical.magic.skill.blood.HemorrhageSkill;
import com.efkrdnz.magical.magic.skill.blood.ScarletLanceSkill;
import com.efkrdnz.magical.magic.skill.blood.SecondHeartSkill;
import com.efkrdnz.magical.magic.skill.blood.VeinWalkSkill;

/** BLOOD, the -1 layer: seven skills paid out of the Vessel first and the body second. */
public final class MagicCastContentBlood {
    private MagicCastContentBlood() {}

    public static void register() {
        new CrimsonTitheSkill().register();
        new HemorrhageSkill().register();
        new ScarletLanceSkill().register();
        new SecondHeartSkill().register();
        new VeinWalkSkill().register();
        new ExsanguinateSkill().register();
        new BloodManipulationSkill().register();
    }
}
