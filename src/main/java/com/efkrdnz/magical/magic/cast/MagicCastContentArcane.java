package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.arcane.ArcaneExileSkill;
import com.efkrdnz.magical.magic.skill.arcane.ArcaneSnapSkill;
import com.efkrdnz.magical.magic.skill.arcane.EchoesOfPassageSkill;
import com.efkrdnz.magical.magic.skill.arcane.LodestoneSkill;
import com.efkrdnz.magical.magic.skill.arcane.PuppetSigilSkill;
import com.efkrdnz.magical.magic.skill.arcane.RetrogradeSkill;

/** ARCANE base-school skills: handler + behaviour + profile per skill module. */
public final class MagicCastContentArcane {
    private MagicCastContentArcane() {}

    public static void register() {
        new ArcaneSnapSkill().register();
        new LodestoneSkill().register();
        new RetrogradeSkill().register();
        new ArcaneExileSkill().register();
        new PuppetSigilSkill().register();
        new EchoesOfPassageSkill().register();
    }
}
