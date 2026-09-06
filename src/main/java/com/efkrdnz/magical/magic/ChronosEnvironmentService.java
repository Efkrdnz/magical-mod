package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.network.ChronosEnvironmentPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side director for Chronos End's boss-fight environment. The boss (or the
 * /chronosfx command) calls these to reshape the whole dimension. All effects ramp
 * smoothly over {@code rampTicks} and are safe to retrigger mid-ramp; new effect ids
 * can be added freely - clients ignore ids they do not know.
 *
 * The intended sky-cut choreography: open the cut (its interior shows the otherworld
 * theme), let it hang, then trigger the theme shift - the world takes the otherworld
 * colors while the cut interior flips to the old base theme, as if the two realities
 * traded places.
 */
public final class ChronosEnvironmentService {
    public static final int EFFECT_SKY_CUT = 0;
    public static final int EFFECT_THEME_SHIFT = 1;
    public static final int EFFECT_PULSE_STORM = 2;
    public static final int EFFECT_SKY_VORTEX = 3;
    public static final int EFFECT_TIME_FREEZE = 4;
    public static final int EFFECT_STAR_RAIN = 5;
    public static final int EFFECT_CLOCKS_ONLY = 6;
    public static final int EFFECT_COLOR_PALETTE = 7;

    // Color palette overrides (carried in the payload's strength field).
    public static final int PALETTE_DEFAULT = 0;
    /** Pitch-black scenery, stark white shapes. */
    public static final int PALETTE_BLACK_WHITE = 1;
    /** Blinding white scenery, ink-black shapes. */
    public static final int PALETTE_WHITE_BLACK = 2;
    /** Warm white scenery, radiant gold shapes. */
    public static final int PALETTE_WHITE_GOLD = 3;

    private ChronosEnvironmentService() {}

    public static void setEffect(int effect, boolean active, int rampTicks, float strength) {
        PacketDistributor.sendToAllPlayers(new ChronosEnvironmentPayload(effect, active, rampTicks, strength));
    }

    /** Tear (or seal) the great cut across the sky; its interior shows the otherworld theme. */
    public static void skyCut(boolean open, int rampTicks) {
        setEffect(EFFECT_SKY_CUT, open, rampTicks, 1.0F);
    }

    /** Swap the dimension's theme with the otherworld (gold/orange to blue/purple and back). */
    public static void themeShift(boolean active, int rampTicks) {
        setEffect(EFFECT_THEME_SHIFT, active, rampTicks, 1.0F);
    }

    /** Make every monument in the dimension throb on the beat; strength scales the throb. */
    public static void pulseStorm(boolean active, int rampTicks, float strength) {
        setEffect(EFFECT_PULSE_STORM, active, rampTicks, strength);
    }

    /** A colossal spiral maw churning at the zenith of the sky. */
    public static void skyVortex(boolean active, int rampTicks) {
        setEffect(EFFECT_SKY_VORTEX, active, rampTicks, 1.0F);
    }

    /**
     * Grind every animation in the dimension to a halt - time itself stopping - and raise a colossal
     * frozen dial beneath the world, its three hands locked at an elegant pose.
     */
    public static void timeFreeze(boolean active, int rampTicks) {
        setEffect(EFFECT_TIME_FREEZE, active, rampTicks, 1.0F);
    }

    /** A meteor storm streaking across the whole sky. */
    public static void starRain(boolean active, int rampTicks, float strength) {
        setEffect(EFFECT_STAR_RAIN, active, rampTicks, strength);
    }

    /**
     * Every monument that is not a clock dissolves from reality, leaving only the clockwork
     * rings - grown large and dominant - ticking in the empty dark.
     */
    public static void clocksOnly(boolean active, int rampTicks) {
        setEffect(EFFECT_CLOCKS_ONLY, active, rampTicks, 1.0F);
    }

    /**
     * Repaint the entire dimension - scenery and shapes separately - in one of the fixed
     * PALETTE_* schemes; PALETTE_DEFAULT eases everything back to normal theming.
     */
    public static void colorPalette(int palette, int rampTicks) {
        setEffect(EFFECT_COLOR_PALETTE, palette != PALETTE_DEFAULT, rampTicks, palette);
    }
}
