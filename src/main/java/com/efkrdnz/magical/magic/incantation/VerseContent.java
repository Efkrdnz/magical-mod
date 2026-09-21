package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/**
 * The real catalogue, filled by the eight verse files in a fixed order so registration order is
 * stable. The materials and the passives came last and register last, so nothing that was already
 * on the Grimoire's grid moved when they arrived.
 */
public final class VerseContent {

    public static final VerseCatalogue CATALOGUE = build();

    private VerseContent() {
    }

    private static VerseCatalogue build() {
        VerseCatalogue catalogue = new VerseCatalogue();
        ProjectileVerses.register(catalogue);
        StaticVerses.register(catalogue);
        ModifierVerses.register(catalogue);
        MulticastVerses.register(catalogue);
        UtilityVerses.register(catalogue);
        ControlVerses.register(catalogue);
        MaterialVerses.register(catalogue);
        PassiveVerses.register(catalogue);
        return catalogue;
    }

    public static Verse get(ResourceLocation id) {
        return CATALOGUE.get(id);
    }
}
