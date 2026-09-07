package com.efkrdnz.magical.forge.strike;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.FormFamily;

/**
 * The single resolved value a strike is built from: everything the strike entity, its renderer,
 * and the echo scheduler need, with no further lookups against form/temper/weapon data.
 */
public record StrikeSpec(FormFamily family, boolean heavy, boolean finisher, int comboIndex, float damage,
        float reach, float halfWidth, float arcDegrees, float speed, int lifeTicks, float knockback,
        float critChance, int recoveryTicks, int modifierFlags, float chargeFraction) {

    public boolean has(ForgeModifierKind kind) {
        return ForgeStrikeMath.hasFlag(modifierFlags, kind);
    }

    /** The delayed echo repeat of this strike: scaled-down damage, echo itself cannot re-echo. */
    public StrikeSpec asEcho() {
        int flagsWithoutEcho = modifierFlags & ~ForgeModifierKind.ECHO.flag();
        return new StrikeSpec(family, heavy, finisher, comboIndex, damage * ForgeStrikeMath.ECHO_SCALE, reach,
                halfWidth, arcDegrees, speed, lifeTicks, knockback, critChance, recoveryTicks, flagsWithoutEcho,
                chargeFraction);
    }
}
