package com.efkrdnz.magical.client.renderer.space;

import net.minecraft.util.Mth;

/**
 * How much the sun is allowed to burnish the boundary's lines.
 *
 * <p>A shader effect always looks good at midnight, because at midnight anything glowing looks
 * good: there is nothing to compete with. Daylight is the real test, and the failure there is not
 * that the glow is too weak but that it is the wrong instrument - an additive line over a bright
 * sky is a line that is not there, and the same line at the same strength over a night sky is a
 * flare.
 *
 * <p>So the burnish is a function of the light it has to sit on. In daylight the lines are cut
 * dark into the pane and only lightly lit, so they read as engraving and hold their contrast
 * against sand and snow; at night the same lines carry their full colour, because nothing is
 * going to blow out. The wall's own body never changes - it darkens, and darkening works at every
 * hour.
 *
 * <p>Driven off the game's own measure of the sky rather than off the clock, so a thunderstorm at
 * noon lifts the burnish exactly as far as the sky it stole, and dusk moves through it instead of
 * snapping at a threshold.
 */
public final class SubspaceSun {

    /** The burnish under a clear noon: present, but well under the sky it has to sit on. */
    public static final float DAY_LIFT = 0.28F;
    /** The burnish at midnight, where a lit line has the whole frame to itself. */
    public static final float NIGHT_LIFT = 1.0F;
    /** Vanilla's darkest sky, and the top of its own 0..11 scale. */
    public static final int DARKEST_SKY = 11;

    private SubspaceSun() {}

    /** @param skyDarken the level's own sky darkness, 0 in clear daylight and 11 at midnight */
    public static float lift(int skyDarken) {
        return Mth.lerp(Mth.clamp(skyDarken / (float) DARKEST_SKY, 0.0F, 1.0F), DAY_LIFT, NIGHT_LIFT);
    }
}
