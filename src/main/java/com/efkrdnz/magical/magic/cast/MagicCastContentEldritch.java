package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.eldritch.GraspOfTheDeepSkill;
import com.efkrdnz.magical.magic.skill.eldritch.UnblinkingEyeSkill;

/** ELDRITCH, the -5 layer: six calls to the deep, paid in mana and in being noticed. */
public final class MagicCastContentEldritch {
    private MagicCastContentEldritch() {}

    public static void register() {
        new GraspOfTheDeepSkill().register();
        new UnblinkingEyeSkill().register();
    }
}
