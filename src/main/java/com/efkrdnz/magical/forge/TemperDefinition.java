package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.forge.strike.TemperStats;

import net.minecraft.resources.ResourceLocation;

public record TemperDefinition(ResourceLocation id, float reachDelta, float widthScale, float speedScale,
        float knockbackScale, float critChance, int comboWindowDelta, int recoveryDelta, int chargeThresholdDelta,
        int stabilityDelta) {

    /**
     * MC-free mirror of this definition, for the pure strike math (drops id and stabilityDelta).
     * Positional: field order must match {@link TemperStats}'s record components exactly, or
     * values transpose silently. Pinned by {@code StatsAdapterContractTest} in {@code forge.strike}.
     */
    public TemperStats stats() {
        return new TemperStats(reachDelta, widthScale, speedScale, knockbackScale, critChance, comboWindowDelta,
                recoveryDelta, chargeThresholdDelta);
    }
}
