package com.efkrdnz.magical.forge;

import net.minecraft.resources.ResourceLocation;

public record ElementDefinition(ResourceLocation id, ForgeElementKind kind, int primaryColor, int secondaryColor,
        int edgeColor, float procBase) {
}
