package com.efkrdnz.magical.forge;

import java.util.List;
import java.util.Optional;

import com.efkrdnz.magical.forge.fusion.ForgeFusion;

import net.minecraft.resources.ResourceLocation;

/**
 * The bridge between the Minecraft-free {@link ForgeFusion} table and the resource locations the
 * rest of the forge speaks in. Mirrors what {@code ForgeSpecials} does for Arts.
 */
public final class ForgeFusions {

    private ForgeFusions() {}

    /** The fusion that produced this element, or empty for one of the eight drawn elements. */
    public static Optional<ForgeFusion> byElement(ResourceLocation elementId) {
        return isLocal(elementId) ? ForgeFusion.byResult(elementId.getPath()) : Optional.empty();
    }

    /**
     * The element glyphs a weapon carrying this element was forged from.
     *
     * <p>A fused element has no glyph of its own, so reforging one needs the pair back: the strip
     * shows two element cells and the grammar fuses them again on submit. A drawn element is its
     * own single component.
     */
    public static List<String> componentsOf(ResourceLocation elementId) {
        if (!isLocal(elementId)) {
            return List.of();
        }
        return ForgeFusion.byResult(elementId.getPath())
                .map(ForgeFusion::components)
                .orElseGet(() -> List.of(elementId.getPath()));
    }

    private static boolean isLocal(ResourceLocation elementId) {
        return ForgeIds.id(elementId.getPath()).equals(elementId);
    }
}
