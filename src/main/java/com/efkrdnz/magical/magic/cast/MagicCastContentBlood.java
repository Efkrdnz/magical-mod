package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.blood.BloodManipulationSkill;
import com.efkrdnz.magical.magic.skill.blood.BloodRiteSkill;
import com.efkrdnz.magical.magic.skill.blood.CoagulateSkill;
import com.efkrdnz.magical.magic.skill.blood.CrimsonSpearSkill;
import com.efkrdnz.magical.magic.skill.blood.OpenVeinSkill;
import com.efkrdnz.magical.magic.skill.blood.VeinWalkSkill;

/**
 * BLOOD, the -1 layer: six skills paid out of the Vessel first and the body second, every one of
 * them a way of moving blood between the Vessel, the ground and a body.
 */
public final class MagicCastContentBlood {
    private MagicCastContentBlood() {}

    public static void register() {
        new BloodManipulationSkill().register();
        new VeinWalkSkill().register();
        new OpenVeinSkill().register();
        new CrimsonSpearSkill().register();
        new CoagulateSkill().register();
        new BloodRiteSkill().register();
    }
}
