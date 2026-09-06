package com.efkrdnz.magical.magic.visual;

/** Tier fixes ESCALATION: radius, layer budget, seals, spin, windup/linger, feedback strength. */
public record TierProfile(
        int tier,
        float radius,
        int layerBudget,
        int sealCount,
        float spinScale,
        int windupTicks,
        int lingerTicks,
        float shakeStrength,
        int hitstopTicks,
        float fovKick,
        boolean throughTerrain,
        int stackCount) {

    public static TierProfile forTier(int tier) {
        return switch (Math.max(0, Math.min(4, tier < 0 ? 4 : tier))) {
            case 0 -> new TierProfile(0, 1.2F, 3, 0, 1.0F, 3, 14, 0.0F, 0, -1.5F, false, 1);
            case 1 -> new TierProfile(1, 1.6F, 5, 0, 1.0F, 5, 18, 0.05F, 0, -2.0F, false, 1);
            case 2 -> new TierProfile(2, 2.0F, 6, 3, 1.1F, 7, 24, 0.12F, 0, -2.5F, false, 1);
            case 3 -> new TierProfile(3, 2.4F, 8, 5, 1.2F, 11, 30, 0.25F, 1, -3.0F, false, 2);
            default -> new TierProfile(4, 3.0F, 12, 7, 1.3F, 20, 40, 0.45F, 2, 6.0F, true, 3);
        };
    }

    public TierProfile withRadius(float value) {
        return new TierProfile(tier, value, layerBudget, sealCount, spinScale, windupTicks, lingerTicks, shakeStrength, hitstopTicks, fovKick, throughTerrain, stackCount);
    }

    public TierProfile withWindup(int ticks) {
        return new TierProfile(tier, radius, layerBudget, sealCount, spinScale, ticks, lingerTicks, shakeStrength, hitstopTicks, fovKick, throughTerrain, stackCount);
    }

    public TierProfile withLinger(int ticks) {
        return new TierProfile(tier, radius, layerBudget, sealCount, spinScale, windupTicks, ticks, shakeStrength, hitstopTicks, fovKick, throughTerrain, stackCount);
    }
}
