package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.spatial.CompressionSkill;
import com.efkrdnz.magical.magic.skill.spatial.CreaseFoldSkill;
import com.efkrdnz.magical.magic.skill.spatial.DiasporaSkill;
import com.efkrdnz.magical.magic.skill.spatial.RigidFrameSkill;
import com.efkrdnz.magical.magic.skill.spatial.ShortreachSkill;
import com.efkrdnz.magical.magic.skill.spatial.TranspositionSkill;

/** SPATIAL base-school skills. */
public final class MagicCastContentSpatial {
    private MagicCastContentSpatial() {}

    public static void register() {
        new TranspositionSkill().register();
        new ShortreachSkill().register();
        new CompressionSkill().register();
        new RigidFrameSkill().register();
        new CreaseFoldSkill().register();
        new DiasporaSkill().register();
    }
}
