package com.efkrdnz.magical.client.renderer.fx;

import net.minecraft.util.Mth;

/**
 * Per-frame FX budget: caps quads per budget class, demotes trails, particles and circle layers
 * in a fixed order when the visible total exceeds the target, and applies distance LOD bands.
 */
public final class FxBudget {
    public static final int[] QUAD_CAP = {12, 40, 120, 400};
    public static final int[] TRAIL_SEGMENTS = {8, 16, 24, 32};
    public static final int[] PUFFS_PER_TICK = {1, 2, 4, 8};
    private static final int FRAME_TARGET_QUADS = 60000;

    private static int quadsThisFrame;
    private static int circlesThisFrame;
    private static int riftsThisFrame;
    private static int lensesThisFrame;
    private static float lodMultiplier = 1.0F;
    private static long frameId;

    private FxBudget() {}

    public static void beginFrame(long frame) {
        if (frame != frameId) {
            frameId = frame;
            quadsThisFrame = 0;
            circlesThisFrame = 0;
            riftsThisFrame = 0;
            lensesThisFrame = 0;
        }
    }

    public static void setLodMultiplier(float value) {
        lodMultiplier = Mth.clamp(value, 0.3F, 1.0F);
    }

    public static float lodMultiplier() {
        return lodMultiplier;
    }

    public static void countQuads(int quads) {
        quadsThisFrame += quads;
    }

    public static int quadsThisFrame() {
        return quadsThisFrame;
    }

    /** 1 = plenty of headroom .. 0 = over budget (demote trails and layers). */
    public static float pressure() {
        return Mth.clamp(1.0F - quadsThisFrame / (float) FRAME_TARGET_QUADS, 0.0F, 1.0F);
    }

    public static boolean claimCircle() {
        return circlesThisFrame++ < 16;
    }

    public static boolean claimRift() {
        return riftsThisFrame++ < 6;
    }

    public static boolean claimLens() {
        return lensesThisFrame++ < 4;
    }

    /** Detail level from distance bands: > 24 blocks drop importance 3, > 48 keep 1, > 96 keep 0. */
    public static int detailForDistance(int baseDetail, double distanceSqr) {
        int detail = baseDetail;
        if (distanceSqr > 96.0D * 96.0D) {
            detail = Math.min(detail, 0);
        } else if (distanceSqr > 48.0D * 48.0D) {
            detail = Math.min(detail, 1);
        } else if (distanceSqr > 24.0D * 24.0D) {
            detail = Math.min(detail, 2);
        }
        if (pressure() < 0.25F) {
            detail = Math.min(detail, 1);
        } else if (pressure() < 0.5F) {
            detail = Math.min(detail, 2);
        }
        return detail;
    }

    public static float lodForDistance(double distanceSqr) {
        float lod = distanceSqr > 48.0D * 48.0D ? 0.0F : distanceSqr > 24.0D * 24.0D ? 0.5F : 1.0F;
        return lod * lodMultiplier * (0.5F + 0.5F * pressure());
    }
}
