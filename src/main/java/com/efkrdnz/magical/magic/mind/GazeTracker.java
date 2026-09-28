package com.efkrdnz.magical.magic.mind;

/** Counts unbroken, still looking at one thing; a full count is one gaze. */
public final class GazeTracker {
    public static final int BLOCK_TICKS = 40;
    public static final int CREATURE_TICKS = 60;

    private String key;
    private int ticks;

    /** The key whose gaze completed on this tick, or null. */
    public String tick(String lookingAt, boolean still) {
        if (lookingAt == null) {
            key = null;
            ticks = 0;
            return null;
        }
        if (!lookingAt.equals(key)) {
            key = lookingAt;
            ticks = 0;
        }
        if (!still) {
            ticks = 0;
            return null;
        }
        ticks++;
        if (ticks >= need(key)) {
            ticks = 0;
            return key;
        }
        return null;
    }

    public String key() {
        return key;
    }

    public float progress() {
        return key == null ? 0.0F : (float) ticks / need(key);
    }

    public static int need(String key) {
        return key.startsWith("creature:") ? CREATURE_TICKS : BLOCK_TICKS;
    }
}
