package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.fusion.BlackOrrerySkill;
import com.efkrdnz.magical.magic.skill.fusion.CinderChariotSkill;
import com.efkrdnz.magical.magic.skill.fusion.DawnwellSkill;
import com.efkrdnz.magical.magic.skill.fusion.FallenSunSkill;
import com.efkrdnz.magical.magic.skill.fusion.ScaldingGeyserSkill;
import com.efkrdnz.magical.magic.skill.fusion.TectonicVerdictSkill;
import com.efkrdnz.magical.magic.skill.fusion.TotalEclipseSkill;

/** The 7 fusion outputs (4 Spell Creator hybrids, 3 Magic Originator ultimates). */
public final class MagicCastContentFusion {
    private MagicCastContentFusion() {}

    public static void register() {
        new ScaldingGeyserSkill().register();
        new DawnwellSkill().register();
        new BlackOrrerySkill().register();
        new CinderChariotSkill().register();
        new FallenSunSkill().register();
        new TotalEclipseSkill().register();
        new TectonicVerdictSkill().register();
    }
}
