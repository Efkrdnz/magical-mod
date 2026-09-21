package com.efkrdnz.magical.magic.sword;

/**
 * Where a station actually is, and <b>the one arithmetic both sides run</b>.
 *
 * <p>The server behaviour and the client painter call these methods with identical arguments and
 * get identical doubles, which is the Gravemoons discipline and is why twelve blade positions cost
 * <b>zero bytes</b> on the wire: the client is told the frame and the shape, both of which change
 * rarely, and works out the rest itself. A blade cannot desync because there is nothing to desync.
 *
 * <p>Which means a sign error in here is a <em>silent mirror</em> - the blades appear on the wrong
 * side for everybody at once, consistently, with a green build and no log line. So the handedness
 * is Minecraft's own, stated out loud in {@link Station#unitBearing()}, and
 * {@code ArrayPoseTest.aStationIsWhereBothSidesSayItIs} pins it at all four cardinals and both
 * poles rather than trusting the reasoning.
 */
public final class ArrayPose {

    /**
     * The distance at which a bound frame is still at scale 1.
     *
     * <p>Past it the scale grows with the distance and the bill inflates with the scale, which is
     * how an opponent's footwork comes to spend the wielder's budget. Inside it, nothing happens
     * at all - a bind is not a penalty, it is a leash.
     */
    public static final double BIND_REST = 8.0D;

    private ArrayPose() {
    }

    /**
     * The offset from the frame origin to where this station's blade sits, in world axes.
     *
     * <p>{@code reach * scale} along the frame-rotated bearing. At scale 0 every station is on the
     * origin, which is the fusion and is exactly one line of arithmetic rather than a mode.
     */
    public static double[] worldOffset(Station station, Frame frame) {
        double[] bearing = worldBearing(station, frame);
        double out = station.reach() * frame.scale();
        return new double[] {bearing[0] * out, bearing[1] * out, bearing[2] * out};
    }

    /**
     * The station's bearing turned into the world, unit length.
     *
     * <p>Pitch about the frame's own lateral axis first, then yaw about world up - the order
     * {@code Entity.calculateViewVector} uses, so a frame at the wielder's look and a station at
     * yaw 0, pitch 0 gives back the wielder's look vector exactly.
     */
    public static double[] worldBearing(Station station, Frame frame) {
        double[] local = station.unitBearing();

        double pitch = Math.toRadians(frame.pitch());
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double x = local[0];
        double y = local[1] * cp - local[2] * sp;
        double z = local[1] * sp + local[2] * cp;

        // Minecraft's yaw runs clockwise seen from above, which is a rotation about +Y by -yaw.
        double yaw = Math.toRadians(-frame.yaw());
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        return new double[] {x * cy + z * sy, y, -x * sy + z * cy};
    }

    /** The yaw a blade at this station wears, in Minecraft degrees: 0 is +Z and it turns clockwise. */
    public static float bladeYaw(Station station, Frame frame) {
        double[] bearing = worldBearing(station, frame);
        return (float) -Math.toDegrees(Math.atan2(bearing[0], bearing[2]));
    }

    /**
     * The pitch a blade at this station wears, in Minecraft degrees - <b>positive is down</b>, the
     * opposite sign to {@link Station#pitch()}, which is an elevation. This method is the only
     * place in the kit those two conventions are allowed to meet.
     */
    public static float bladePitch(Station station, Frame frame) {
        double[] bearing = worldBearing(station, frame);
        double up = Math.max(-1.0D, Math.min(1.0D, bearing[1]));
        return (float) -Math.toDegrees(Math.asin(up));
    }

    /** {@code max(1, distance / 8)}. Inside the rest distance a bind costs the wielder nothing. */
    public static float boundScale(double distance) {
        return (float) Math.max(1.0D, distance / BIND_REST);
    }
}
