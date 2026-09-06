package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.ChronosEnvironmentService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Sky for Chronos End: no sun, no moon, no stars - nothing but a faint sepia afterglow.
 * The paradox monuments rendered by ChronosEndRenderer are the only lights left.
 * When a boss palette override is active, the fog follows its scenery color so the
 * horizon matches the repainted sky.
 */
public final class ChronosSkyEffects extends DimensionSpecialEffects {
    private static final Vec3 BASE_FOG = new Vec3(0.055D, 0.04D, 0.022D);

    public ChronosSkyEffects() {
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level != null ? minecraft.level.getGameTime() : 0.0D;
        float level = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_COLOR_PALETTE, now);
        if (level <= 0.0F) {
            return BASE_FOG;
        }
        int palette = Math.round(ChronosClientEnvironment.strength(ChronosEnvironmentService.EFFECT_COLOR_PALETTE));
        Vec3 target = switch (palette) {
            case ChronosEnvironmentService.PALETTE_BLACK_WHITE -> Vec3.ZERO;
            case ChronosEnvironmentService.PALETTE_WHITE_BLACK -> new Vec3(0.90D, 0.90D, 0.91D);
            case ChronosEnvironmentService.PALETTE_WHITE_GOLD -> new Vec3(0.92D, 0.89D, 0.82D);
            default -> BASE_FOG;
        };
        return BASE_FOG.lerp(target, level);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }
}
