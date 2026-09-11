package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.client.renderer.fx.voxel.BloodVoxels;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelStyle;

/**
 * The blood school's painters. One line per ability that forms out of voxels.
 *
 * <p>Every ability gets its own painter id even when it shares a style, because the authoring lint
 * that catches two skills wearing the same look skips the check entirely for custom silhouettes.
 * Two abilities registered under one id would collide in silence, and the second would simply never
 * be drawn.
 */
public final class BloodPainters {
    private BloodPainters() {}

    public static void register() {
        CustomPainters.register("blood_manipulation", (ctx, profile, silhouette) ->
                BloodVoxels.paint(ctx, profile, silhouette, VoxelStyle.STRIKE));
    }
}
