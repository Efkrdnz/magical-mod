package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.sword.BelowSkill;
import com.efkrdnz.magical.magic.skill.sword.CallTheBladeSkill;
import com.efkrdnz.magical.magic.skill.sword.LooseSkill;
import com.efkrdnz.magical.magic.skill.sword.OneBladeSkill;
import com.efkrdnz.magical.magic.skill.sword.SwordStanceSkill;
import com.efkrdnz.magical.magic.skill.sword.TheKeelSkill;

/**
 * SWORD, the -3 layer: six verbs over one Array, and not one of them is a new mechanism.
 *
 * <p>Every one is a move of the frame or a pure projection of the authored bearings - write a
 * station, read the shape back, unbind the origin, take the forward half, reflect the pitch and
 * take the lower half, drive the scale to zero. That is why the kit ships six actives with no
 * per-blade state machine and no mode enum anywhere in it: a blade is a bearing and a bit.
 *
 * <p>Registration order is the order the design reads them in, and it is also the order the
 * frame-side allocation follows - a skill's {@code frame(n)} must be unique <em>within</em>
 * {@code MagicSchool.SWORD} or {@code VisualProfiles.validate} answers a hard collision, and the
 * school was empty, so the six took 3, 4, 5, 6, 7 and 8 in this order. There is no
 * {@code stamps(...)} layer in any of these six but Call the Blade's: the school's stamp is
 * {@code StampId.EDGE} at atlas cell 32 and {@code STAMP_BAND}'s {@code paramB} field is five
 * bits wide, which is why {@code SchoolMaterial.SWORD} bands with {@code TICK_BAND} instead.
 */
public final class MagicCastContentSword {

    private MagicCastContentSword() {}

    public static void register() {
        new CallTheBladeSkill().register();
        new SwordStanceSkill().register();
        new TheKeelSkill().register();
        new LooseSkill().register();
        new BelowSkill().register();
        new OneBladeSkill().register();
    }
}
