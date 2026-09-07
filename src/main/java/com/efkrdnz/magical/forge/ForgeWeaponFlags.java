package com.efkrdnz.magical.forge;

import java.util.ArrayList;
import java.util.List;

import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;

import net.minecraft.resources.ResourceLocation;

/**
 * Turns a forged weapon's modifier ids into the bit set the strike math reads. Unknown ids (a
 * weapon forged by a newer or older build) are simply skipped rather than failing the strike.
 */
public final class ForgeWeaponFlags {

    private ForgeWeaponFlags() {}

    public static int of(ForgedWeapon weapon) {
        List<ForgeModifierKind> kinds = new ArrayList<>();
        for (ResourceLocation id : weapon.modifiers()) {
            ForgeModifiers.get(id).map(ModifierDefinition::kind).ifPresent(kinds::add);
        }
        return ForgeStrikeMath.flagsOf(kinds);
    }
}
