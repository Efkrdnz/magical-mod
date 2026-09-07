package com.efkrdnz.magical.forge.strike;

/**
 * MC-free mirror of {@code TemperDefinition}, without the id or the stability delta (which only
 * matters at forge time, never during a strike).
 */
public record TemperStats(float reachDelta, float widthScale, float speedScale, float knockbackScale,
        float critChance, int comboWindowDelta, int recoveryDelta, int chargeThresholdDelta) {

    /** Neutral temper: no deltas, scales of 1, no crit chance. */
    public static final TemperStats NONE = new TemperStats(0, 1, 1, 1, 0, 0, 0, 0);
}
