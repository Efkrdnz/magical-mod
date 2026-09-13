package com.efkrdnz.magical.client.hud;

/**
 * The player's HUD settings as one immutable value, read once per snapshot rebuild and never per
 * frame. The client config fills it in; until it is loaded these are the defaults.
 */
public record HudOptions(
        boolean enabled,
        HudAnchor anchor,
        float scale,
        float opacity,
        boolean showSins,
        boolean showStatuses,
        boolean compact,
        boolean reducedMotion,
        boolean debug) {

    public static final HudOptions DEFAULTS = new HudOptions(true, HudAnchor.TOP_LEFT, 1.0F, 1.0F, true, true, false, false, false);

    /** The scale actually laid out: compact mode is a smaller HUD with less on it. */
    public float layoutScale() {
        return compact ? scale * 0.8F : scale;
    }
}
