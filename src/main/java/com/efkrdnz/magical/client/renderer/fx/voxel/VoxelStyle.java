package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.visual.FxKinds;

/**
 * How one ability's voxel field looks and moves. This is the whole authoring surface of the
 * framework: a new blood skill writes one of these and registers a painter that hands it over.
 *
 * @param rgb         the flat colour the field draws in. Explicit rather than a palette role: the
 *                    school ramp's bright and hot slots are derived by shifting blue hardest, which
 *                    carries blood toward pink - right for a rune band, wrong for liquid
 * @param cube        cube edge as a fraction of the field's pitch. At or just above one the cubes
 *                    touch and the field reads as a body of liquid; below one it separates into
 *                    visible drops with gaps between them
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
        int rgb,
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
            FxKinds.Body.BLOOD, 0xF23B47,
            1.05F, 1800,
            new Timeline(7.0F, 2.0F, 2.5F, 9.0F, 6.0F),
            0.55F, 1.1F,
            0.012F, 0.11F,
            2, 14);

    /**
     * The look of harvested blood: a pool at a corpse that lifts and streams into the player.
     *
     * <p>The timeline's words mean slightly different things here, because there is no synced
     * shape and the server picks the flight. {@code launch} is the floor under one cube's flight
     * once the jitter has been taken off the stream's; {@code jitter} is how ragged the front is;
     * {@code materialise} is how long the pool takes to well up out of the corpse;
     * {@code dissolve} is both how long a cube takes to shrink into the chest and how long the pool
     * takes to dry, with {@code dissolveSpread} staggering the drying rim-first. {@code burstRadius}
     * is the pool's radius. {@code BloodHarvestRules} pins the flight and drying windows these have
     * to fit inside, and a test holds the two together.
     */
    public static final VoxelStyle HARVEST = new VoxelStyle(
            FxKinds.Body.BLOOD, 0xF23B47,
            1.0F, 96,
            new Timeline(6.0F, 6.0F, 6.0F, 4.0F, 4.0F),
            0.55F, 0.9F,
            0.012F, 0.09F,
            2, 14);

    /**
     * The look of a Crimson Spear: a short straight field that gathers at the hand in a handful of
     * ticks, flies as one body, and shatters at the impact through its own dissolve. A small burst
     * and a low arch, because it forms at a fist rather than out of a whole caster.
     */
    public static final VoxelStyle SPEAR = new VoxelStyle(
            FxKinds.Body.BLOOD, 0xE8303C,
            1.1F, 220,
            new Timeline(4.0F, 1.0F, 2.0F, 5.0F, 3.0F),
            0.25F, 0.15F,
            0.006F, 0.13F,
            2, 14);

    /**
     * The look of Coagulate: a ring of blood that sets slowly round the caster and stands. Its
     * erosion is driven by the synced integrity rather than by age, so the dissolve here is only
     * how the last of it drains at the end. Capped so that a shell and a full Manipulation field
     * together stay inside the frame budget.
     */
    public static final VoxelStyle SHELL = new VoxelStyle(
            FxKinds.Body.BLOOD, 0xC81E2E,
            1.05F, 600,
            new Timeline(8.0F, 4.0F, 5.0F, 10.0F, 6.0F),
            0.45F, 0.5F,
            0.008F, 0.07F,
            2, 14);

    /** How much of {@link #cap} a profile's budget class is allowed to spend. */
    private static final float[] BUDGET_SCALE = {0.30F, 0.50F, 0.75F, 1.00F};

    public int capFor(int budgetClass) {
        int index = Math.max(0, Math.min(BUDGET_SCALE.length - 1, budgetClass));
        return Math.max(1, Math.round(cap * BUDGET_SCALE[index]));
    }
}
