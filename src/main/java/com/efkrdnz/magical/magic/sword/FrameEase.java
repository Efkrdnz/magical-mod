package com.efkrdnz.magical.magic.sword;

/**
 * How a formation catches up with the body carrying it.
 *
 * <p>Before this the frame was welded to the wielder: {@code SwordArrayEntity.follow} wrote the
 * player's yaw onto the entity every tick and the renderer read it raw, so turning your head
 * moved twelve swords in <b>twenty discrete steps a second</b> and the formation read as a prop
 * bolted to the camera rather than as steel flying around somebody. Two separate things fix that
 * and both live here, because both are one piece of arithmetic that has to be right about angles
 * wrapping and about frame rate.
 *
 * <p><b>{@link #approachAngle} is the lag</b> - the server eases the frame's rotation toward the
 * wielder's over a half-life, so the formation swings and settles instead of snapping. It is a
 * half-life rather than a fixed step so that it is <em>independent of the tick rate</em>: a
 * server running behind produces the same motion, and the same function is safe to call from a
 * render frame at any frame rate. <b>{@link #lerpAngle} is the interpolation</b> - the eased
 * value still only changes twenty times a second, so the client walks between the last two it was
 * sent, exactly as vanilla walks a position between {@code xOld} and {@code x}.
 *
 * <p>Only the rotation is eased. <b>The origin is not</b>, and that is deliberate: a formation
 * whose centre lagged the body would let the body walk out of the hole in the middle of it, and
 * {@code Formation.BODY_CLEARANCE} would stop meaning anything. The swords swing around you; they
 * do not drift off you.
 *
 * <p>Pure, like the rest of the placement arithmetic - no Minecraft, so {@code FrameEaseTest} can
 * pin it on exact values, including the case that motivated the wrap handling: easing 179 degrees
 * toward -179 must move two degrees forward, not 358 degrees backward.
 */
public final class FrameEase {

    private FrameEase() {
    }

    /**
     * Moves {@code current} toward {@code target}, closing half the gap every {@code halfLife}
     * ticks.
     *
     * <p>A half-life of zero or less snaps, which is what a fresh formation and a teleport both
     * want: easing in from wherever the state happened to be left would swing twelve swords
     * across the world.
     */
    public static double approach(double current, double target, double halfLife, double dt) {
        if (!(halfLife > 0.0D) || !(dt > 0.0D)) {
            return target;
        }
        return current + (target - current) * factor(halfLife, dt);
    }

    /**
     * {@link #approach} for a Minecraft yaw, which wraps.
     *
     * <p>The answer is wrapped back into [-180, 180) rather than left to accumulate, because this
     * value is stored and fed back in every tick: a wielder who spins one way for a minute would
     * otherwise carry a yaw of several thousand degrees, and the float the entity syncs it
     * through loses a bit of precision for every doubling of it.
     */
    public static float approachAngle(float current, float target, double halfLife, double dt) {
        if (!(halfLife > 0.0D) || !(dt > 0.0D)) {
            return wrap(target);
        }
        return wrap((float) (current + difference(current, target) * factor(halfLife, dt)));
    }

    /** Straight-line interpolation between two angles, the short way round. */
    public static float lerpAngle(float from, float to, float t) {
        return wrap((float) (from + difference(from, to) * Math.max(0.0F, Math.min(1.0F, t))));
    }

    /**
     * The signed short way from {@code from} to {@code to}, in [-180, 180).
     *
     * <p>Written out rather than taken from {@code Mth.degreesDifference} so this package stays
     * free of Minecraft. The {@code floor} form works for any input, including the accumulated
     * angles a caller who ignored {@link #wrap} would hand it.
     */
    public static double difference(double from, double to) {
        double delta = to - from;
        return delta - 360.0D * Math.floor((delta + 180.0D) / 360.0D);
    }

    /** An angle in [-180, 180). */
    public static float wrap(float degrees) {
        return (float) (degrees - 360.0D * Math.floor((degrees + 180.0D) / 360.0D));
    }

    /** The share of the remaining gap closed in {@code dt} ticks at this half-life. */
    private static double factor(double halfLife, double dt) {
        return 1.0D - Math.pow(0.5D, dt / halfLife);
    }
}
