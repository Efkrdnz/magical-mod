package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.StrikeSpec;

/**
 * Everything the server side of a strike needs and the client never sees: the resolved numbers, the
 * weapon that produced them, and the one target vanilla already hit on this press. Immutable, so a
 * strike cannot drift once it is in the air.
 */
public record StrikeLoadout(ForgedWeapon weapon, ElementDefinition element, FormDefinition form, float damage,
        float knockback, float speed, float critChance, float weaponAttack, int modifierFlags, boolean heavy,
        boolean finisher, boolean echo, int comboIndex, int primaryTargetId) {

    /** No primary target: either an echo, or a press that landed no vanilla hit at all. */
    public static final int NO_PRIMARY_TARGET = -1;

    public static StrikeLoadout of(StrikeSpec spec, ForgedWeapon weapon, ElementDefinition element,
            FormDefinition form, float weaponAttack, boolean echo, int primaryTargetId) {
        return new StrikeLoadout(weapon, element, form, spec.damage(), spec.knockback(), spec.speed(),
                spec.critChance(), weaponAttack, spec.modifierFlags(), spec.heavy(), spec.finisher(), echo,
                spec.comboIndex(), echo ? NO_PRIMARY_TARGET : primaryTargetId);
    }

    public boolean has(ForgeModifierKind kind) {
        return ForgeStrikeMath.hasFlag(modifierFlags, kind);
    }

    public FormFamily family() {
        return form.family();
    }
}
