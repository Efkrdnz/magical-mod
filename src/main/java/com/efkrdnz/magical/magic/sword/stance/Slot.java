package com.efkrdnz.magical.magic.sword.stance;

/**
 * Where one sword sits and which way it points, in the frame's own axes.
 *
 * <p>The axes are Minecraft's own and they are stated here because this is the record every other
 * class in the package hands back: <b>+Z is forward, +Y is up, +X is to the wielder's left</b>.
 * That last one is the trap - {@code Entity.calculateViewVector} turns yaw clockwise seen from
 * above, so yaw 0 faces +Z and yaw 90 faces -X, which is the wielder's <em>right</em>. A sign
 * mistaken here is a silent mirror: the formation appears on the wrong side for everybody at once,
 * consistently, with a green build and nothing in the log.
 *
 * <p>The direction is a unit vector and {@code FormationTest} holds every stance to it at every
 * count. A zero direction is not a harmless default - {@code SwordBladeRenderer} builds a rotation
 * from it, so a zero reads on screen as a blade lying flat with no orientation at all, which looks
 * like a broken model export rather than a broken formation.
 */
public record Slot(double x, double y, double z, double dx, double dy, double dz) {

    /** The offset's length from the frame origin, which is what the bounds rules are stated on. */
    public double distance() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Straight-line distance between two slots - the separation rule, and nothing else. */
    public double gapTo(Slot other) {
        double ox = x - other.x();
        double oy = y - other.y();
        double oz = z - other.z();
        return Math.sqrt(ox * ox + oy * oy + oz * oz);
    }

    /**
     * A slot with the direction normalised, or pointing forward when there was no direction at all.
     *
     * <p>Forward rather than a throw, because every caller of this is inside a formation being
     * drawn twenty times a second and the failure it guards is a degenerate arrangement - one
     * sword landing exactly on its own fan root, say - that would otherwise hand the renderer a
     * NaN rotation. The test is what refuses such an arrangement; this is what keeps the frame
     * from going black if one ever ships.
     */
    public static Slot at(double x, double y, double z, double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 1.0E-9D)) {
            return new Slot(x, y, z, 0.0D, 0.0D, 1.0D);
        }
        return new Slot(x, y, z, dx / length, dy / length, dz / length);
    }
}
