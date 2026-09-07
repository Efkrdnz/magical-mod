package com.efkrdnz.magical.forge.strike;

/**
 * Pure geometric hit-shape tests for forged strikes: cones, vertical planes, capsules, rings and
 * discs, all in plain doubles with no engine vector type.
 */
public final class ShapeMath {

    private static final double DEGENERATE_EPSILON = 1e-6;

    private ShapeMath() {
    }

    /**
     * An orthonormal frame built from a forward direction: {@code forward}, {@code right} and
     * {@code up}, each a unit vector.
     */
    public record Basis(double fx, double fy, double fz, double rx, double ry, double rz, double ux, double uy,
            double uz) {

        /**
         * Builds a basis from a (not necessarily unit) forward vector. Right is
         * {@code normalize(cross(forward, worldUp))}; when forward is (near) vertical that cross
         * degenerates to zero, so right falls back to the world X axis. Up is
         * {@code cross(right, forward)}.
         */
        public static Basis fromForward(double fx, double fy, double fz) {
            double len = length(fx, fy, fz);
            if (len == 0 || !Double.isFinite(len)) {
                // Degenerate (zero-length or non-finite) forward vector: dividing by len would
                // propagate NaN through the whole basis. Fall back to a fixed, sane orthonormal
                // frame (forward = world +Z, right = world +X, up = world +Y) instead.
                return new Basis(0, 0, 1, 1, 0, 0, 0, 1, 0);
            }
            double nfx = fx / len;
            double nfy = fy / len;
            double nfz = fz / len;

            // cross(forward, worldUp=(0,1,0)) = (-nfz, 0, nfx)
            double crossLen = Math.sqrt(nfz * nfz + nfx * nfx);
            double rx;
            double rz;
            if (crossLen < DEGENERATE_EPSILON) {
                rx = 1;
                rz = 0;
            } else {
                rx = -nfz / crossLen;
                rz = nfx / crossLen;
            }
            double ry = 0;

            double ux = ry * nfz - rz * nfy;
            double uy = rz * nfx - rx * nfz;
            double uz = rx * nfy - ry * nfx;

            return new Basis(nfx, nfy, nfz, rx, ry, rz, ux, uy, uz);
        }
    }

    /**
     * Whether {@code target} lies within a forward-facing cone from {@code origin}: no farther
     * than {@code reach}, and within {@code halfArcDegrees} of the basis forward, loosened by
     * {@code slack}. A target exactly at the origin counts as inside.
     */
    public static boolean cone(double ox, double oy, double oz, Basis b, double tx, double ty, double tz,
            double reach, double halfArcDegrees, double slack) {
        double dx = tx - ox;
        double dy = ty - oy;
        double dz = tz - oz;
        double dist = length(dx, dy, dz);
        if (dist > reach) {
            return false;
        }
        if (dist < DEGENERATE_EPSILON) {
            return true;
        }
        double cosAngle = dot(dx / dist, dy / dist, dz / dist, b.fx(), b.fy(), b.fz());
        double threshold = Math.cos(Math.toRadians(halfArcDegrees)) - slack;
        return cosAngle >= threshold;
    }

    /**
     * Whether {@code target} lies within a thin vertical slab in front of {@code origin}: close
     * enough to the forward axis laterally, ahead of the origin and within {@code reach}, and
     * within a world-Y band of {@code [minUp, maxUp]}.
     */
    public static boolean verticalPlane(double ox, double oy, double oz, Basis b, double tx, double ty, double tz,
            double lateralTolerance, double reach, double minUp, double maxUp) {
        double dx = tx - ox;
        double dy = ty - oy;
        double dz = tz - oz;
        double lateral = dot(dx, dy, dz, b.rx(), b.ry(), b.rz());
        double forwardDist = dot(dx, dy, dz, b.fx(), b.fy(), b.fz());
        return Math.abs(lateral) <= lateralTolerance
                && forwardDist >= 0
                && forwardDist <= reach
                && dy >= minUp
                && dy <= maxUp;
    }

    /** Distance from point {@code p} to the segment from {@code a} to {@code b}. */
    public static double capsuleDistance(double ax, double ay, double az, double bx, double by, double bz,
            double px, double py, double pz) {
        double abx = bx - ax;
        double aby = by - ay;
        double abz = bz - az;
        double apx = px - ax;
        double apy = py - ay;
        double apz = pz - az;
        double abLenSq = abx * abx + aby * aby + abz * abz;
        double t = abLenSq > DEGENERATE_EPSILON ? dot(apx, apy, apz, abx, aby, abz) / abLenSq : 0;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * abx;
        double cy = ay + t * aby;
        double cz = az + t * abz;
        return length(px - cx, py - cy, pz - cz);
    }

    /** Whether {@code target} is within {@code radius} horizontally of {@code origin} and within {@code maxDy} vertically. */
    public static boolean ring(double ox, double oy, double oz, double tx, double ty, double tz, double radius,
            double maxDy) {
        double horizontal = horizontalDistance(ox, oz, tx, tz);
        return horizontal <= radius && Math.abs(ty - oy) <= maxDy;
    }

    /** Whether {@code target} is within {@code radius} horizontally of {@code center} and within {@code maxDy} vertically. */
    public static boolean disc(double cx, double cy, double cz, double tx, double ty, double tz, double radius,
            double maxDy) {
        double horizontal = horizontalDistance(cx, cz, tx, tz);
        return horizontal <= radius && Math.abs(ty - cy) <= maxDy;
    }

    public static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double dot(double ax, double ay, double az, double bx, double by, double bz) {
        return ax * bx + ay * by + az * bz;
    }

    public static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
