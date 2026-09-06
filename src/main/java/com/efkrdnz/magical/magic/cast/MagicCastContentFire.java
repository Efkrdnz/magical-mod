package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.fire.AshEffigySkill;
import com.efkrdnz.magical.magic.skill.fire.CrucibleSkill;
import com.efkrdnz.magical.magic.skill.fire.MagmaVentSkill;
import com.efkrdnz.magical.magic.skill.fire.SlagRollerSkill;
import com.efkrdnz.magical.magic.skill.fire.SmokestackSkill;
import com.efkrdnz.magical.magic.skill.fire.WildfireSkill;

/** FIRE base-school skills. */
public final class MagicCastContentFire {
    private MagicCastContentFire() {}

    public static void register() {
        new SmokestackSkill().register();
        new SlagRollerSkill().register();
        new WildfireSkill().register();
        new MagmaVentSkill().register();
        new AshEffigySkill().register();
        new CrucibleSkill().register();
    }
}
