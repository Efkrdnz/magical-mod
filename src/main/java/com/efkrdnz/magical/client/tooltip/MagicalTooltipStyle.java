package com.efkrdnz.magical.client.tooltip;

import java.util.Optional;

import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Which stacks get the magical tooltip, and what colour it burns.
 *
 * <p>Shared by the panel that draws behind the box and the lines written into it, so the two can
 * never disagree about whether a given stack is one of ours - a shader panel under a plain vanilla
 * tooltip, or a magical frame around nothing, would both look like a bug.
 */
public final class MagicalTooltipStyle {

    /** Arcane violet, for a weapon that leans on no element in particular. */
    public static final int DEFAULT_ACCENT = 0x9B6BFF;

    private MagicalTooltipStyle() {}

    /** Catalogue weapons, and anything the forge has already been taken to. */
    public static boolean shows(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (ForgeMaterials.catalogue(stack).isPresent() || ForgedWeapons.isForged(stack));
    }

    /**
     * What the panel glows.
     *
     * <p>What is actually inscribed beats what the weapon was made for: a frost blade someone has
     * since filled with fire should read as fire, because that is what it will do when swung.
     */
    public static int accent(ItemStack stack) {
        return element(stack)
                .flatMap(ForgeElements::get)
                .map(ElementDefinition::primaryColor)
                .orElse(DEFAULT_ACCENT);
    }

    private static Optional<ResourceLocation> element(ItemStack stack) {
        return ForgedWeapons.get(stack)
                .map(ForgedWeapon::element)
                .or(() -> ForgeMaterials.catalogue(stack).flatMap(WeaponDefinition::themeElement));
    }
}
