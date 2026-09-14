package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.eldritch.GraspOfTheDeepSkill;

/** ELDRITCH, the -5 layer: six calls to the deep, paid in mana and in being noticed. */
public final class MagicCastContentEldritch {
    private MagicCastContentEldritch() {}

    public static void register() {
        new GraspOfTheDeepSkill().register();
    }
}
