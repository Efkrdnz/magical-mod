package com.efkrdnz.magical.arcane;

import net.minecraft.resources.ResourceLocation;

public record ArcaneShapeDefinition(
        ResourceLocation id,
        String displayName,
        ArcaneShape shape,
        int baseCost,
        int baseStability,
        float baseRange,
        int baseDurationTicks) {
}
