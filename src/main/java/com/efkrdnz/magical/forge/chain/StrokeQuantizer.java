package com.efkrdnz.magical.forge.chain;

/**
 * Snaps normalized [0, 1] canvas coordinates onto the {@link ForgeRules#CANVAS_UNITS}-wide integer
 * grid the wire protocol carries, and back. Pure integer/float arithmetic, no Minecraft types, so
 * client and server always agree bit-for-bit.
 */
public final class StrokeQuantizer {

    private static final int MAX_UNIT = ForgeRules.CANVAS_UNITS - 1;

    private StrokeQuantizer() {
    }

    /** Rounds {@code v} onto the grid, clamped to [0, {@link ForgeRules#CANVAS_UNITS} - 1]. */
    public static int toUnits(float v) {
        return Math.max(0, Math.min(MAX_UNIT, Math.round(v * MAX_UNIT)));
    }

    /** Recovers the normalized coordinate for grid step {@code k}, clamped before dividing. */
    public static float fromUnits(int k) {
        int clamped = Math.max(0, Math.min(MAX_UNIT, k));
        return clamped / (float) MAX_UNIT;
    }

    /** Whether {@code k} is a valid grid step, i.e. in {@code [0, CANVAS_UNITS)}. */
    public static boolean inRange(int k) {
        return k >= 0 && k < ForgeRules.CANVAS_UNITS;
    }
}
