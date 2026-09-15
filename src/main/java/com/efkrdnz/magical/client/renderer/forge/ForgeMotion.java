package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;

import net.minecraft.util.Mth;

/**
 * How a strike draws itself over the few ticks it is alive.
 *
 * <p>Every form used to appear whole on its first frame and then fade out, which reads as a decal
 * being switched on rather than a blade going through something. A swing travels: the arc opens
 * along its own span from where the blade started, and a thrown crescent grows out to its reach
 * instead of arriving at full size.
 *
 * <p>Both of the ceilings here are load-bearing. The drawing may never claim more span or more
 * radius than the hit shape the player is actually judged against, or the picture teaches a reach
 * the game will not honour.
 */
public final class ForgeMotion {

    /**
     * How far past the middle the easing pushes. A blade is quickest through the middle of its arc
     * and settles at the end, and the settling frames are the ones the eye has time to catch - most
     * of these forms live three to six ticks.
     */
    private static final float LEAD = 1.7f;

    private ForgeMotion() {}

    /** The fraction of its span a strike has drawn by {@code progress}, eased and clamped. */
    public static float swept(float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - t, LEAD);
    }

    /**
     * The arc opened out to {@link #swept} of its span, anchored where the swing began.
     *
     * <p>Anchored rather than centred: an arc that grew out of its own middle reads as an expanding
     * ring, and a swing is a thing that travels from one side to the other.
     */
    public static Sweep opening(Sweep full, float progress) {
        float to = Mth.lerp(swept(progress), full.fromDegrees(), full.toDegrees());
        // The bow comes along. A blade that straightened out while it opened would be flat for the
        // frames the eye actually catches, which are the early ones.
        return new Sweep(full.plane(), full.radius(), full.thickness(), full.fromDegrees(), to, full.bow());
    }

    /**
     * The same arc at a radius grown from {@code fromFraction} of its reach out to all of it.
     *
     * <p>Scaled rather than resized, so the blade keeps its proportions the whole way out: a young
     * crescent at full thickness would be a paddle, and an old one at the starting thickness a
     * razor.
     */
    public static Sweep reaching(Sweep full, float fromFraction, float progress) {
        float from = Mth.clamp(fromFraction, 0.0f, 1.0f);
        return full.scaled(Mth.lerp(swept(progress), from, 1.0f));
    }

    /** The progress at which {@link #reaching} from nothing stands at {@code fraction} of its reach. */
    public static float progressForRadius(float fraction) {
        float f = Mth.clamp(fraction, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - f, 1.0f / LEAD);
    }
}
