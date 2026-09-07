package com.efkrdnz.magical.forge.glyph;

/** Maps a recognition score onto the 0..100 quality scale shown to the player. */
public final class GlyphQuality {

    /** Score at or below which quality is 0. */
    public static final float FLOOR_SCORE = 0.45f;

    /** Score at or above which quality is 100. */
    public static final float CEILING_SCORE = 0.92f;

    private GlyphQuality() {
    }

    /** Linear remap of {@code score} from [0.45, 0.92] onto [0, 100], clamped at both ends. */
    public static int toQuality(float score) {
        float normalized = (score - FLOOR_SCORE) / (CEILING_SCORE - FLOOR_SCORE);
        int quality = Math.round(100f * normalized);
        return Math.max(0, Math.min(100, quality));
    }
}
