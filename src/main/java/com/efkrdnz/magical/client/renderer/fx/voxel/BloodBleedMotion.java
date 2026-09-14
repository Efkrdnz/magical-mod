package com.efkrdnz.magical.client.renderer.fx.voxel;

import net.minecraft.util.Mth;

/**
 * Blood leaving a body for the ground: the harvest flight run backwards.
 *
 * <p>A pool that is fed - Open Vein's victim, the Rite's caster - is drawn drop by drop. Each cube
 * of the disc is born inside the body at its own tick, spread evenly over the feed the server
 * planned, and falls to its own pool spot along the bezier the harvest lifts it by, reversed in
 * time: born small in the body, whole as it lands, and exactly where the pooled branch then draws
 * it. Nothing here is stored between frames, so it cannot fall out of step.
 */
public final class BloodBleedMotion {

    /** Ticks a drop takes from the body to the ground. */
    public static final float FALL_TICKS = 8.0F;

    private BloodBleedMotion() {
    }

    /** The tick drop {@code index} of {@code cubes} leaves the body: evenly over the planned feed. */
    public static float bornAt(int index, int cubes, float feedTicks) {
        return Math.max(0.0F, feedTicks) * index / Math.max(1, cubes);
    }

    /** Whether the drop has been born by {@code age}, given that the feed stopped at {@code feedEnd}. */
    public static boolean born(float bornAt, float age, float feedEnd) {
        return bornAt <= age && bornAt <= feedEnd;
    }

    /** Fall progress in 0..1: nothing before birth, landed after {@link #FALL_TICKS}. */
    public static float fallProgress(float bornAt, float age) {
        return Mth.clamp((age - bornAt) / FALL_TICKS, 0.0F, 1.0F);
    }

    /**
     * Where a falling drop is, written into {@code out}: the harvest flight from the pool spot to
     * the chest, read at {@code 1 - progress}. It starts spread through the body and ends on the
     * spot to the last digit, so the pooled branch takes over without a seam - and the easing runs
     * the other way, slow out of the body and fast onto the ground, which is how blood falls.
     */
    public static void fall(VoxelStyle style, int index, int seed, float progress,
            float spotX, float spotY, float spotZ,
            float chestX, float chestY, float chestZ, float[] out) {
        BloodHarvestMotion.fly(style, index, seed, 1.0F - progress, spotX, spotY, spotZ,
                chestX, chestY, chestZ, out);
    }

    /**
     * How far gone a falling drop is, 0 whole to 1 nothing: whole once landed, nothing at birth,
     * and growing out of the body over the first {@code dissolve} ticks of its fall.
     */
    public static float forming(VoxelStyle.Timeline timing, float progress, float fallTicks) {
        return BloodHarvestMotion.entering(timing, 1.0F - progress, fallTicks);
    }
}
