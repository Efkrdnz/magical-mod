package com.efkrdnz.magical.magic;

import net.minecraft.util.Mth;

public final class SpaceRuleCodec {
    private SpaceRuleCodec() {
    }

    public static int encode(SpaceRuleCategory category, SpaceRuleOperation operation, SpaceTargetGroup target) {
        return (category.ordinal() & 0xFF) << 16 | (operation.ordinal() & 0xFF) << 8 | (target.ordinal() & 0xFF);
    }

    public static SpaceRuleCategory category(int encoded) {
        int ordinal = encoded >> 16 & 0xFF;
        return SpaceRuleCategory.values()[Mth.clamp(ordinal, 0, SpaceRuleCategory.values().length - 1)];
    }

    public static SpaceRuleOperation operation(int encoded) {
        int ordinal = encoded >> 8 & 0xFF;
        return SpaceRuleOperation.values()[Mth.clamp(ordinal, 0, SpaceRuleOperation.values().length - 1)];
    }

    public static SpaceTargetGroup target(int encoded) {
        int ordinal = encoded & 0xFF;
        return SpaceTargetGroup.values()[Mth.clamp(ordinal, 0, SpaceTargetGroup.values().length - 1)];
    }
}
