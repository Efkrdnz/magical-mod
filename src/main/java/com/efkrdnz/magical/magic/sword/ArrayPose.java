package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.magic.sword.stance.Slot;

/**
 * A frame-local {@link Slot} turned into the world, and <b>the one rotation both sides run</b>.
 *
 * <p>The server behaviour and the client painter call these methods with identical arguments and
 * get identical doubles, which is why twelve sword positions cost <b>zero bytes</b> on the wire:
 * the client is told the stance, the present mask and the frame - none of which change often - and
 * works out the rest itself. A sword cannot desync because there is nothing to desync.
 *
 * <p>Which means a sign error in here is a <em>silent mirror</em> - the formation appears on the
 * wrong side for everybody at once, consistently, with a green build and no log line. So the
 * handedness is Minecraft's own and is written down on {@link Slot}: yaw 0 faces +Z and turns
 * clockwise seen from above, so +X is the wielder's left.
 *
 * <p>It used to take a {@code Station} - a bearing on a lattice, with a reach multiplied onto a
 * unit vector. A {@link Slot} already carries its whole offset, so the reach is gone and the only
 * scalar left is the frame's own scale, which One Blade drives to zero to gather everything home.
 */
public final class ArrayPose {

    private ArrayPose() {
    }

    /** Where this slot's sword sits, as an offset from the frame origin, in world axes. */
    public static double[] worldOffset(Slot slot, Frame frame) {
        double scale = frame.scale();
        return rotate(slot.x() * scale, slot.y() * scale, slot.z() * scale, frame);
    }

    /** Which way this slot's sword points, in world axes, unit length in and unit length out. */
    public static double[] worldDirection(Slot slot, Frame frame) {
        return rotate(slot.dx(), slot.dy(), slot.dz(), frame);
    }

    /**
     * Pitch about the frame's own lateral axis first, then yaw about world up.
     *
     * <p>The order {@code Entity.calculateViewVector} uses, so a frame at the wielder's look and a
     * slot at {@code (0, 0, 1)} gives back the wielder's look vector exactly. Reversing the two is
     * the other silent mirror available here: it is correct at pitch zero, which is where every
     * casual test stands.
     */
    public static double[] rotate(double x, double y, double z, Frame frame) {
        double pitch = Math.toRadians(frame.pitch());
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double py = y * cp - z * sp;
        double pz = y * sp + z * cp;

        // Minecraft's yaw runs clockwise seen from above, which is a rotation about +Y by -yaw.
        double yaw = Math.toRadians(-frame.yaw());
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        return new double[] {x * cy + pz * sy, py, -x * sy + pz * cy};
    }

    /** The yaw a sword pointing this way wears, in Minecraft degrees: 0 is +Z, clockwise. */
    public static float yawOf(double[] direction) {
        return (float) -Math.toDegrees(Math.atan2(direction[0], direction[2]));
    }

    /**
     * The pitch a sword pointing this way wears, in Minecraft degrees - <b>positive is down</b>.
     *
     * <p>The opposite sign to the elevation {@code Formation.place} takes, and this method is one
     * of only two places in the kit those two conventions are allowed to meet.
     */
    public static float pitchOf(double[] direction) {
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]
                + direction[2] * direction[2]);
        if (!(length > 1.0E-9D)) {
            return 0.0F;
        }
        double up = Math.max(-1.0D, Math.min(1.0D, direction[1] / length));
        return (float) -Math.toDegrees(Math.asin(up));
    }
}
