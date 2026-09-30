package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.primordial.CalderaSkill;
import com.efkrdnz.magical.magic.skill.primordial.CycloneSkill;
import com.efkrdnz.magical.magic.skill.primordial.FaultLineSkill;
import com.efkrdnz.magical.magic.skill.primordial.SkyfallSkill;
import com.efkrdnz.magical.magic.skill.primordial.TsunamiSkill;
import com.efkrdnz.magical.magic.skill.primordial.UpheavalSkill;

/** PRIMORDIAL, the -4 layer: six catastrophes the land was already capable of. */
public final class MagicCastContentPrimordial {
    private MagicCastContentPrimordial() {}

    public static void register() {
        new CycloneSkill().register();
        new FaultLineSkill().register();
        new SkyfallSkill().register();
        new CalderaSkill().register();
        new TsunamiSkill().register();
        new UpheavalSkill().register();
    }
}
