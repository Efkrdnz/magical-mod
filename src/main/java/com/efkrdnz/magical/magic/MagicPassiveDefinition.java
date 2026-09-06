package com.efkrdnz.magical.magic;

import net.minecraft.resources.ResourceLocation;

public record MagicPassiveDefinition(
        ResourceLocation id,
        boolean curse,
        int maxLevel,
        float reductionPerLevel,
        int requiredProficiencyToDispel,
        int dispelManaCost,
        int color,
        int shopTier,
        int shopCost) {

    public MagicPassiveDefinition(
            ResourceLocation id,
            boolean curse,
            int maxLevel,
            float reductionPerLevel,
            int requiredProficiencyToDispel,
            int dispelManaCost,
            int color) {
        this(id, curse, maxLevel, reductionPerLevel, requiredProficiencyToDispel, dispelManaCost, color, 0, 0);
    }

    public String nameKey() {
        return "passive.magical." + id.getPath();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }
}
