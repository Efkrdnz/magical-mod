package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.classes.AnvilFallSkill;
import com.efkrdnz.magical.magic.skill.classes.ArcaneGraspSkill;
import com.efkrdnz.magical.magic.skill.classes.ArcanumHareSkill;
import com.efkrdnz.magical.magic.skill.classes.BloodshoutSkill;
import com.efkrdnz.magical.magic.skill.classes.ContagionSkill;
import com.efkrdnz.magical.magic.skill.classes.DawnhammerSkill;
import com.efkrdnz.magical.magic.skill.classes.GildedChainSkill;
import com.efkrdnz.magical.magic.skill.classes.HailVolleySkill;
import com.efkrdnz.magical.magic.skill.classes.HeatedIronSkill;
import com.efkrdnz.magical.magic.skill.classes.IronChargeSkill;
import com.efkrdnz.magical.magic.skill.classes.LeadShotSkill;
import com.efkrdnz.magical.magic.skill.classes.LeechCutSkill;
import com.efkrdnz.magical.magic.skill.classes.LivingBulwarkSkill;
import com.efkrdnz.magical.magic.skill.classes.ManaBloomSkill;
import com.efkrdnz.magical.magic.skill.classes.SightLineSkill;
import com.efkrdnz.magical.magic.skill.classes.SigilForgeSkill;
import com.efkrdnz.magical.magic.skill.classes.SomnolentDraughtSkill;
import com.efkrdnz.magical.magic.skill.classes.SpiritWolfSkill;
import com.efkrdnz.magical.magic.skill.classes.SunderGripSkill;
import com.efkrdnz.magical.magic.skill.classes.UnstableCompoundSkill;
import com.efkrdnz.magical.magic.skill.classes.VialBreakSkill;
import com.efkrdnz.magical.magic.skill.classes.WarHornSkill;

/** The 22 class-reward skills. */
public final class MagicCastContentClass {
    private MagicCastContentClass() {}

    public static void register() {
        new HeatedIronSkill().register();
        new DawnhammerSkill().register();
        new GildedChainSkill().register();
        new IronChargeSkill().register();
        new SunderGripSkill().register();
        new BloodshoutSkill().register();
        new LivingBulwarkSkill().register();
        new HailVolleySkill().register();
        new SpiritWolfSkill().register();
        new LeadShotSkill().register();
        new ArcaneGraspSkill().register();
        new ArcanumHareSkill().register();
        new LeechCutSkill().register();
        new SomnolentDraughtSkill().register();
        new ContagionSkill().register();
        new UnstableCompoundSkill().register();
        new AnvilFallSkill().register();
        new SigilForgeSkill().register();
        new WarHornSkill().register();
        new SightLineSkill().register();
        new ManaBloomSkill().register();
        new VialBreakSkill().register();
    }
}
