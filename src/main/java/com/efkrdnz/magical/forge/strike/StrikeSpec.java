package com.efkrdnz.magical.forge.strike;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.ModifierStack;

/**
 * The single resolved value a strike is built from: everything the strike entity, its renderer,
 * and the echo scheduler need, with no further lookups against form/temper/weapon data.
 */
public record StrikeSpec(FormFamily family, boolean heavy, boolean finisher, int comboIndex, float damage,
        float reach, float halfWidth, float arcDegrees, float speed, int lifeTicks, float knockback,
        float critChance, int recoveryTicks, ModifierStack mods, float chargeFraction) {

    public boolean has(ForgeModifierKind kind) {
        return mods.has(kind);
    }

    public int stacks(ForgeModifierKind kind) {
        return mods.stacks(kind);
    }

    /** This strike with its damage scaled, for a press that splits its force across several forms. */
    public StrikeSpec withDamageScale(float scale) {
        return scale == 1f ? this : new StrikeSpec(family, heavy, finisher, comboIndex, damage * scale, reach,
                halfWidth, arcDegrees, speed, lifeTicks, knockback, critChance, recoveryTicks, mods,
                chargeFraction);
    }

    /** The delayed echo repeat of this strike: scaled-down damage, echo itself cannot re-echo. */
    public StrikeSpec asEcho() {
        return new StrikeSpec(family, heavy, finisher, comboIndex, damage * ForgeStrikeMath.ECHO_SCALE, reach,
                halfWidth, arcDegrees, speed, lifeTicks, knockback, critChance, recoveryTicks,
                mods.without(ForgeModifierKind.ECHO), chargeFraction);
    }
}
