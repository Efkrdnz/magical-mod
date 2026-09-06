package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.voidschool.AntithesisSkill;
import com.efkrdnz.magical.magic.skill.voidschool.FallenFirmamentSkill;
import com.efkrdnz.magical.magic.skill.voidschool.GravemoonsSkill;
import com.efkrdnz.magical.magic.skill.voidschool.GulletOfTheDeepSkill;
import com.efkrdnz.magical.magic.skill.voidschool.HollowMawSkill;
import com.efkrdnz.magical.magic.skill.voidschool.HushwingSkill;

/** VOID base-school skills. */
public final class MagicCastContentVoid {
    private MagicCastContentVoid() {}

    public static void register() {
        new HollowMawSkill().register();
        new HushwingSkill().register();
        new GravemoonsSkill().register();
        new AntithesisSkill().register();
        new GulletOfTheDeepSkill().register();
        new FallenFirmamentSkill().register();
    }
}
