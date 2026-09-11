package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.blood.BloodFieldData;

/**
 * A field of voxels, baked once and then read every frame.
 *
 * <p>Nothing here changes while the field is alive. Positions are recomputed from scratch each
 * frame out of these arrays and the clock, which is what makes the motion smooth between ticks
 * without any interpolation: there is no previous state to interpolate from, only a function.
 *
 * <p>Targets are canvas-local {@code u, w, v}. Rotation is applied per frame rather than baked in,
 * so a field that follows the caster's view can do it at frame rate.
 */
public final class VoxelField {

    /** Canvas-local u, w, v per voxel. */
    final float[] targets;

    /** Distance from the middle, normalized to 0..1. Drives formation order in both directions. */
    final float[] rank;

    /**
     * A stable per-voxel value in 0..1, uncorrelated with position.
     *
     * <p>Distance LOD keeps the voxels whose key falls under the threshold, so backing away shrinks
     * the kept set monotonically and never reshuffles it. Keying on position instead would thin the
     * field in visible bands; keying on the frame would make it boil.
     */
    final float[] lodKey;

    final int count;

    /** Furthest the field reaches from the caster, in blocks. */
    final float reach;

    final BloodFieldData source;

    VoxelField(float[] targets, float[] rank, float[] lodKey, int count, float reach,
            BloodFieldData source) {
        this.targets = targets;
        this.rank = rank;
        this.lodKey = lodKey;
        this.count = count;
        this.reach = reach;
        this.source = source;
    }

    public int count() {
        return count;
    }

    public float reach() {
        return reach;
    }

    public BloodFieldData source() {
        return source;
    }
}
