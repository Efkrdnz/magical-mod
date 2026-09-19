package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** Ids without touching {@code MagicalMod}, so the package stays free of NeoForge. */
public final class VerseIds {

    public static final String NAMESPACE = "magical";

    private VerseIds() {
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static ResourceLocation body(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "body/" + path);
    }
}
