package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;

/**
 * How one ability's voxel field looks and moves. This is the whole authoring surface of the
 * framework: a new blood skill writes one of these and registers a painter that hands it over.
 *
 * @param cube        cube edge as a fraction of the field's pitch; below one it leaves a visible
 *                    gap, so the field reads as separate drops rather than a solid extrusion
 * @param cap         cubes this ability may ask for, before the profile's budget class scales it
 * @param burstRadius how far from the caster the blood starts, so it leaves rather than appears
 * @param archHeight  how high the flight arcs; blood thrown across a room should not slide
 * @param shaderCount worley cells across a face, and the one number here that will look wrong for a
 *                    non-obvious reason. The house default on this render type is around forty,
 *                    which samples twenty cells across a face three pixels wide at eight blocks -
 *                    pure static. A voxel wants one or two.
 */
public record VoxelStyle(
        FxKinds.Body kind,
        ColorRole role,
        float cube,
        int cap,
        Timeline timing,
        float burstRadius,
        float archHeight,
        float idleAmp,
        float idleHz,
        int shaderCount,
        int shaderParamB) {

    /**
     * Every duration in ticks.
     *
     * @param launch         one cube's flight, once it has started
     * @param jitter         random delay on top, so the front is ragged rather than a marching line
     * @param materialise    how long a cube takes to finish arriving after it lands
     * @param dissolve       how long the field takes to leave
     * @param dissolveSpread stagger on the way out; the tips drain first
     */
    public record Timeline(float launch, float jitter, float materialise,
            float dissolve, float dissolveSpread) {
    }

    /** The look Blood Manipulation forms with: a thrown sheet that lands hard and drains fast. */
    public static final VoxelStyle STRIKE = new VoxelStyle(
            FxKinds.Body.BLOOD, ColorRole.BASE,
            0.88F, 1200,
            new Timeline(7.0F, 2.0F, 2.5F, 9.0F, 6.0F),
            0.55F, 1.1F,
            0.012F, 0.11F,
            2, 14);

    /** How much of {@link #cap} a profile's budget class is allowed to spend. */
    private static final float[] BUDGET_SCALE = {0.30F, 0.50F, 0.75F, 1.00F};

    public int capFor(int budgetClass) {
        int index = Math.max(0, Math.min(BUDGET_SCALE.length - 1, budgetClass));
        return Math.max(1, Math.round(cap * BUDGET_SCALE[index]));
    }
}
