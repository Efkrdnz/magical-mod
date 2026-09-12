package com.efkrdnz.magical.boss.unwaking;

/** One earned segment per completed combination, independent of damage mitigation. */
public final class UnwakingDivineGuard {
    public static final int SEGMENTS = 3, EXPOSURE_TICKS = 200;
    private int segments = SEGMENTS;
    private long lastAward = -1, exposedUntil;

    public void reset() { segments = SEGMENTS; lastAward = -1; exposedUntil = 0; }
    public int segments(long now) { refresh(now); return segments; }
    public boolean exposed(long now) { return now < exposedUntil; }
    public long exposedUntil() { return exposedUntil; }
    public float multiplier(long now) { return exposed(now) ? 1.0F : 0.5F; }
    public boolean finish(long combination, boolean clean, long now) {
        refresh(now);
        if (!clean || combination <= lastAward || exposed(now)) return false;
        lastAward = combination;
        if (--segments > 0) return false;
        exposedUntil = now + EXPOSURE_TICKS;
        return true;
    }
    private void refresh(long now) { if (segments == 0 && now >= exposedUntil) segments = SEGMENTS; }
}
