package com.efkrdnz.magical.magic.cast;

/**
 * Which tuning stats matter for a skill, and what the codex calls them (drives the tuning panel).
 *
 * <p>A null label keeps the generic name. Relabelling never changes which stats are shown: the
 * flags say what a skill exposes, the labels say what it calls them.
 */
public record TuningView(boolean damage, boolean speed, boolean size, boolean duration, boolean efficiency,
        String damageLabelKey, String speedLabelKey, String sizeLabelKey, String durationLabelKey,
        String efficiencyLabelKey) {
    public static final TuningView DEFAULT = new TuningView(true, true, true, true, true, null, null, null, null, null);
    public static final TuningView UTILITY = new TuningView(false, false, true, true, true, null, null, null, null, null);
    public static final TuningView NO_SPEED = new TuningView(true, false, true, true, true, null, null, null, null, null);

    public TuningView labels(String damage, String speed, String size, String duration) {
        return labels(damage, speed, size, duration, null);
    }

    public TuningView labels(String damage, String speed, String size, String duration, String efficiency) {
        return new TuningView(this.damage, this.speed, this.size, this.duration, this.efficiency,
                damage, speed, size, duration, efficiency);
    }
}
