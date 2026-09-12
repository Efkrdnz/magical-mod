package com.efkrdnz.magical.forge;

import java.util.Optional;

import com.efkrdnz.magical.forge.chain.Payload;
import com.efkrdnz.magical.forge.strike.StrikeSpec;

/**
 * Everything the server side of a strike needs and the client never sees: the resolved numbers, the
 * weapon that produced them, and the one target vanilla already hit on this press. Immutable, so a
 * strike cannot drift once it is in the air.
 *
 * <p>The archetype rides along rather than being re-derived at impact, because the stack it comes
 * off is the one in the wielder's hand and by the time the strike lands they may be holding
 * something else. A strike that changes what it is mid-flight because the player swapped weapons is
 * exactly what this record being immutable is for.
 */
public record StrikeLoadout(ForgedWeapon weapon, ElementDefinition element, FormDefinition form, float damage,
        float knockback, float speed, float critChance, float weaponAttack, ModifierStack mods, boolean heavy,
        boolean finisher, boolean echo, int comboIndex, int primaryTargetId, WeaponClass archetype,
        Optional<Payload> payload) {

    /** No primary target: either an echo, or a press that landed no vanilla hit at all. */
    public static final int NO_PRIMARY_TARGET = -1;

    public static StrikeLoadout of(StrikeSpec spec, ForgedWeapon weapon, ElementDefinition element,
            FormDefinition form, float weaponAttack, boolean echo, int primaryTargetId,
            WeaponClass archetype) {
        return of(spec, weapon, element, form, weaponAttack, echo, primaryTargetId, archetype,
                Optional.empty());
    }

    /** As above, carrying a payload this strike will fire when its trigger comes due. */
    public static StrikeLoadout of(StrikeSpec spec, ForgedWeapon weapon, ElementDefinition element,
            FormDefinition form, float weaponAttack, boolean echo, int primaryTargetId,
            WeaponClass archetype, Optional<Payload> payload) {
        return new StrikeLoadout(weapon, element, form, spec.damage(), spec.knockback(), spec.speed(),
                spec.critChance(), weaponAttack, spec.mods(), spec.heavy(), spec.finisher(), echo,
                spec.comboIndex(), echo ? NO_PRIMARY_TARGET : primaryTargetId, archetype, payload);
    }

    public boolean has(ForgeModifierKind kind) {
        return mods.has(kind);
    }

    public int stacks(ForgeModifierKind kind) {
        return mods.stacks(kind);
    }

    public FormFamily family() {
        return form.family();
    }
}
