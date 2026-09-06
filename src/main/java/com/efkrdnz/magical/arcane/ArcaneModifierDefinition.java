package com.efkrdnz.magical.arcane;

import net.minecraft.resources.ResourceLocation;

public record ArcaneModifierDefinition(
        ResourceLocation id,
        String displayName,
        ModifierEffect effect,
        int manaDelta,
        int stabilityDelta) {
}
