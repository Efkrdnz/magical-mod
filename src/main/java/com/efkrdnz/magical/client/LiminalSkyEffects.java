package com.efkrdnz.magical.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Sky effects for The Kept Grounds: a liminal overcast that never resolves.
 * No sun, no moon, no stars, no clouds—only a flat sourceless light and an
 * indistinct horizon. The fog is uniform and desaturated, matching mown grass
 * under perpetual cloud. Distance washes out so you cannot judge how far away
 * a structure truly is.
 *
 * This is the visual anchor of the liminal horror aesthetic: brightness without
 * source, scale without reference, and the oppressive sense that the plain is
 * maintained but nobody is here.
 */
public final class LiminalSkyEffects extends DimensionSpecialEffects {
    /**
     * Pale desaturated grey-green fog color: off-white with a faint grass undertone.
     * Chosen to evoke an overcast afternoon and to make the horizon indistinct—
     * sky and grass nearly meet in the same value.
     */
    private static final Vec3 LIMINAL_FOG = new Vec3(0.78D, 0.80D, 0.76D);

    public LiminalSkyEffects() {
        // SkyType.NONE: no sun, moon, stars, or clouds. The only visual is fog.
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        // Return the flat liminal fog color regardless of brightness.
        // Chromos allows the color to vary with environmental effects (palette overrides).
        // Liminal does not: the overcast is uniform, unchanging, and ambient. The fog
        // does not grow darker in shadow or lighter in sun—there is no sun.
        // This flatness is what sells the liminal aesthetic: no directional reference,
        // no time of day, no natural lighting hierarchy.
        return LIMINAL_FOG;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        // Return true so fog genuinely washes out distance in all directions.
        // Vanilla returns false for most dimensions, allowing you to see to the horizon.
        // In the Kept Grounds, distance is an illusion—you cannot trust what you see
        // far away. The castle draws no closer no matter how long you walk toward it.
        // Fog that thickens with distance reinforces this: the horizon dissolves.
        return true;
    }
}
