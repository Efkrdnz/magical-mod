package com.efkrdnz.magical.arcane;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

public record ArcaneRuneDefinition(
        ResourceLocation id,
        String displayName,
        Affinity affinity,
        ChatFormatting color,
        int baseCost,
        int baseStability,
        float basePower) {
}
