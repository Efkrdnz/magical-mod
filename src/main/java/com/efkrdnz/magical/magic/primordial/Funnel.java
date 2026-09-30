package com.efkrdnz.magical.magic.primordial;

/**
 * A Cyclone's shape and the draw it has on a body, as arithmetic.
 *
 * <p>The funnel is narrow at the foot and wide at the crown: {@code radiusAt(dy)} grows from
 * {@link #FOOT} of the radius at the ground to the whole of it at {@code height}. Offsets are from
 * the eye on the ground, in blocks, with Minecraft's axes. The storm turns counter-clockwise seen
 * from above, which in Minecraft's axes (x east, z south) is the tangent {@code (uz, -ux)} of the
 * outward unit {@code (ux, uz)}: east turns toward north. {@link #SPIN} is the sign of the change of
 * the angle {@code atan2(dz, dx)} along that turn, for the client drawing the same way.
 */
public record Funnel(double radius, double height) {
    public static final double FOOT = 0.35D;
    private static final double CURVE = 1.3D;
    /** How far past the wall a body is still drawn in, as a multiple of the radius. */
    public static final double REACH = 1.6D;
    /** From this fraction of the height a body is at the crown and is flung out. */
    public static final double CROWN = 0.85D;
    public static final double IN = 0.22D;
    public static final double AROUND = 0.32D;
    public static final double LIFT = 0.16D;
    public static final double FLING = 0.95D;
    public static final double FLING_UP = 0.45D;
    public static final int SPIN = -1;

    public double radiusAt(double dy) {
        double t = Math.max(0.0D, Math.min(1.0D, dy / height));
        return radius * (FOOT + (1.0D - FOOT) * Math.pow(t, CURVE));
    }

    public double reach() {
        return radius * REACH;
    }

    public boolean inside(double dx, double dy, double dz) {
        if (dy < -0.5D || dy > height) {
            return false;
        }
        double r = radiusAt(dy);
        return dx * dx + dz * dz <= r * r;
    }

    public boolean inReach(double dx, double dy, double dz) {
        if (dy < -1.0D || dy > height) {
            return false;
        }
        double r = reach();
        return dx * dx + dz * dz <= r * r;
    }

    public boolean atCrown(double dy) {
        return dy >= height * CROWN;
    }

    /**
     * The velocity the storm wants a body at this offset to have: in toward the axis (in full
     * outside the wall, easing to nothing at the axis, so a body inside settles into an orbit),
     * round it, and up while it is inside.
     */
    public double[] pull(double dx, double dy, double dz, double strength) {
        double h = Math.sqrt(dx * dx + dz * dz);
        double ux = h > 1.0E-4D ? dx / h : 1.0D;
        double uz = h > 1.0E-4D ? dz / h : 0.0D;
        double wall = Math.max(0.3D, radiusAt(Math.max(0.0D, Math.min(height, dy))));
        double inward = IN * strength * Math.min(1.0D, h / wall);
        double around = AROUND * strength;
        double up = inside(dx, dy, dz) ? LIFT * strength : 0.0D;
        return new double[] {-ux * inward + uz * around, up, -uz * inward - ux * around};
    }

    /** Thrown out from the crown: away from the axis and a little up. */
    public double[] fling(double dx, double dz, double strength) {
        double h = Math.sqrt(dx * dx + dz * dz);
        double ux = h > 1.0E-4D ? dx / h : 1.0D;
        double uz = h > 1.0E-4D ? dz / h : 0.0D;
        return new double[] {ux * FLING * strength, FLING_UP * strength, uz * FLING * strength};
    }
}
