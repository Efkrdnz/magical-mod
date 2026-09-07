package com.efkrdnz.magical.forge;

import net.minecraft.resources.ResourceLocation;

public record ModifierDefinition(ResourceLocation id, ForgeModifierKind kind, float magnitude, int manaDelta,
        int stabilityDelta) {
}
