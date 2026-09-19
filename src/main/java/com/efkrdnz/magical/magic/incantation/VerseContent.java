package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** The real catalogue, filled by the six verse files in a fixed order so registration order is stable. */
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
        return catalogue;
    }

    public static Verse get(ResourceLocation id) {
        return CATALOGUE.get(id);
    }
}
