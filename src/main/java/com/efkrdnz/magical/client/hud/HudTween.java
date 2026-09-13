package com.efkrdnz.magical.client.hud;

/**
 * A value the HUD shows that arrives in packets and must not jump. Ticked once per client tick,
 * sampled with the partial tick at render.
 *
 * <p>A drop (a spend) closes exponentially, punchy, about four ticks. A rise (regen) ramps
 * linearly over the interval the previous packets arrived at, so a ring filling at one packet a
 * second rises continuously instead of stepping once a second - and a burst of packets simply
 * shortens the ramp. It never overshoots: each tick moves a fraction of what is left.
 *
 * <p>No Minecraft imports, so the arithmetic is testable without a bootstrap.
 */
public final class HudTween {
    private static final int MAX_CADENCE = 60;
    private static final float SPEND_RATE = 0.35F;
    private static final float SETTLE = 0.02F;

    private float prev;
    private float cur;
    private float target;
    private long lastTargetTick = Long.MIN_VALUE;
    private int ramp = 1;

    public HudTween() {}

    public HudTween(float initial) {
        snap(initial);
    }

    /** Jump straight to a value with no motion (world join, level change). */
    public void snap(float value) {
        prev = value;
        cur = value;
        target = value;
        ramp = 1;
    }

    /** A new target from a packet that arrived at {@code tick}. */
    public void set(float value, long tick) {
        if (value == target) {
            return;
        }
        int cadence = lastTargetTick == Long.MIN_VALUE ? 1 : (int) Math.max(1L, Math.min(MAX_CADENCE, tick - lastTargetTick));
        lastTargetTick = tick;
        target = value;
        ramp = cadence;
    }

    /** A new target reached in a straight line over {@code ticks} ticks, whatever the packet cadence. */
    public void ramp(float value, int ticks) {
        target = value;
        ramp = Math.max(1, ticks);
    }

    public void tick() {
        prev = cur;
        float d = target - cur;
        if (d < 0.0F) {
            cur += d * SPEND_RATE;
        } else if (d > 0.0F) {
            cur += d / Math.max(1, ramp);
            ramp = Math.max(1, ramp - 1);
        }
        if (Math.abs(target - cur) < SETTLE) {
            cur = target;
        }
    }

    /** The value to draw this frame. */
    public float sample(float partial) {
        return prev + (cur - prev) * Math.max(0.0F, Math.min(1.0F, partial));
    }

    public float target() {
        return target;
    }

    public float current() {
        return cur;
    }

    public boolean settled() {
        return cur == target;
    }
}
