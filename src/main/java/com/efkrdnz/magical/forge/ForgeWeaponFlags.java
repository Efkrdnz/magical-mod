package com.efkrdnz.magical.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Turns a forged weapon's modifier ids into the {@link ModifierStack} the strike math reads. Unknown
 * ids (a weapon forged by a newer or older build) are simply skipped rather than failing the strike.
 *
 * <p>This reads the weapon's flat modifier list, which every rune on the weapon shares. It is the
 * whole-weapon view - the fallback for a weapon forged before runes became positional, and the
 * source the guard and proc checks use when they have no single step in hand.
 */
public final class ForgeWeaponFlags {

    private ForgeWeaponFlags() {}

    public static ModifierStack of(ForgedWeapon weapon) {
        List<ForgeModifierKind> kinds = new ArrayList<>();
        for (ResourceLocation id : weapon.modifiers()) {
            ForgeModifiers.get(id).map(ModifierDefinition::kind).ifPresent(kinds::add);
        }
        return ModifierStack.of(kinds);
    }
}
