package com.efkrdnz.magical.tower;

import net.minecraft.resources.ResourceLocation;

public record DungeonTowerReward(Kind kind, ResourceLocation id, int amount, String labelKey) {
    public enum Kind {
        CLASS_UNLOCK,
        CLASS_EVOLUTION,
        CLASS_SKILL,
        GENERAL_SKILL,
        PASSIVE,
        MAX_MANA,
        MAX_BARRIER,
        PROFICIENCY
    }
}
