package com.efkrdnz.magical.boss.unwaking;

/** Pure server-clock input state. Sequence numbers are transport ordering, never timing authority. */
public final class UnwakingGuard {
    private long sequence = -1;
    private long lastPacket = Long.MIN_VALUE / 2;
    private long releasedAt = Long.MIN_VALUE / 2;
    private long pressedAt = Long.MIN_VALUE / 2;
    private long lastAttempt = Long.MIN_VALUE / 2;
    private boolean held;

    public boolean accept(long sequence, boolean down, long now) {
        if (sequence < 0 || sequence <= this.sequence) return false;
        this.sequence = sequence;
        lastPacket = now;
        if (down && !held) {
            if (now - releasedAt >= 4 && now - lastAttempt >= 10) pressedAt = now;
            else pressedAt = Long.MIN_VALUE / 2;
            lastAttempt = now;
        } else if (!down && held) {
            releasedAt = now;
        }
        held = down;
        return true;
    }

    public boolean held(long now) {
        if (held && now - lastPacket >= 30) release(now);
        return held;
    }

    public void release(long now) {
        held = false;
        releasedAt = now;
        pressedAt = Long.MIN_VALUE / 2;
    }

    public static int graceTicks(int roundTripMillis) {
        return Math.min(4, Math.max(0, (roundTripMillis + 99) / 100));
    }

    public static boolean facing(double dot) {
        return dot >= Math.cos(Math.toRadians(50));
    }
}
