package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.forge.strike.FormStats;

import net.minecraft.resources.ResourceLocation;

public record FormDefinition(ResourceLocation id, FormFamily family, float lightScale, float heavyScale, float reach,
        float halfWidth, float arcDegrees, float speed, int lifeTicks, float knockback, int recoveryTicks) {

    /**
     * MC-free mirror of this definition, for the pure strike math. Positional: field order must
     * match {@link FormStats}'s record components exactly, or values transpose silently. Pinned
     * by {@code StatsAdapterContractTest} in {@code forge.strike}.
     */
    public FormStats stats() {
        return new FormStats(family, lightScale, heavyScale, reach, halfWidth, arcDegrees, speed, lifeTicks,
                knockback, recoveryTicks);
    }
}
