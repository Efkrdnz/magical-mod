package com.efkrdnz.magical.client.renderer.space;

/**
 * Where the boundary puts its structure, given that the viewer is standing at the exact centre of
 * it.
 *
 * <p>Every number here is a reaction to one geometric fact. A domain is a sphere pinned to its
 * caster body centre and the camera is at their eyes, so the eye sits {@link #EYE_OFFSET} above
 * the centre and <em>nowhere else</em> - the horizontal offset is exactly zero. Three things
 * follow, and the wall that was here before fell foul of all three.
 *
 * <ol>
 *   <li>Every sight line runs along the surface normal, to within a degree and a half. Anything
 *       shaded by the angle between them is a constant, so the wall cannot be read by shading at
 *       all and has to be read by structure.
 *   <li>Every meridian plane contains the eye, and a planar curve seen from a point in its own
 *       plane projects to a straight line. So a member drawn up a meridian is drawn as a ruler,
 *       exactly, at every radius - and a straight near-vertical bar at twenty blocks is a tree
 *       trunk, a fence post or a portal jamb. Hence {@link #twist(int)}: a rib leaves its own
 *       meridian, which makes it lean, and a leaning member is not any of those things.
 *   <li>A ring at a fixed latitude sinks as the radius grows, because the sphere is centred on a
 *       chest and not on the floor. So the springing course is not placed at a latitude at all -
 *       it is placed at an apparent elevation and the latitude is solved for.
 * </ol>
 *
 * <p>The twist is also the one thing in the domain whose shape is a function of its contents: an
 * empty domain is barely wound and a fully legislated one is wound tight, so how much law is in
 * force is legible from the silhouette before a single glyph has been parsed.
 */
public final class SubspaceVault {

    /** One rib per {@code SpaceRuleCategory}, so a slot is a direction rather than a badge. */
    public static final int RIB_COUNT = 12;
    public static final double RIB_PITCH_DEGREES = 360.0 / RIB_COUNT;

    /**
     * How high the springing course rides, as an angle above the sight line rather than as a
     * latitude.
     *
     * <p>Fourteen degrees is a hundred and twenty-eight pixels at 720p: clear of the aiming line by
     * a wide margin, low enough that the whole colonnade above it is still on screen, and - because
     * it is solved per radius - the same composition in a five block domain and a sixteen.
     */
    public static final double SPRING_ELEVATION_DEGREES = 14.0;

    /** Where the wall stops and the open sky begins. A domain is a wall and not a lid. */
    public static final double OCULUS_LATITUDE_DEGREES = 78.0;

    /**
     * Degrees of bearing a rib gains per degree of latitude it climbs, empty and per law written.
     *
     * <p>A rib leans {@code atan(twist * cos(latitude))} off vertical: twenty-three degrees with
     * nothing written, fifty-two with all twelve. That difference is the reading.
     */
    public static final double TWIST_BASE = 0.45;
    public static final double TWIST_PER_LAW = 0.075;

    /**
     * How far below the centre the wall meets the floor a caster is standing on.
     *
     * <p>Their feet are 0.90 below the sphere centre. The ring is drawn a third of a block above
     * that rather than on it: at sixteen blocks the depth buffer cannot separate a surface from
     * the floor it is six hundredths of a block above, and the ring came out patchy and half
     * swallowed. A third of a block reads as the same line and survives the comparison.
     */
    public static final double FOOTPRINT_DROP = 0.62;

    /** How far up the rib the boss carrying the law glyph sits, as a fraction of the rib. */
    public static final double BOSS_ALONG = 0.22;
    /** Half the boss, in degrees of great circle: ninety-six pixels across at boresight. */
    public static final double BOSS_HALF_DEGREES = 5.4;

    /** A tally mark crossing the springing course, so the count is legible at every bearing. */
    public static final double NOTCH_HALF_SWEEP_DEGREES = 1.15;
    public static final double NOTCH_HALF_HEIGHT_DEGREES = 1.7;

    /** Half-widths of the three rings and of a rib, in degrees of mesh - see the note below. */
    public static final double RING_HALF_DEGREES = 0.42;
    /**
     * A rib mesh is deliberately wider than the stroke drawn inside it.
     *
     * <p>The stroke is cut to a fixed number of pixels by the fragment shader, so the mesh only has
     * to contain it - but a rib leaning fifty degrees presents only {@code cos(lean)} of its
     * across-width to the screen, so at the widest lean the mesh has to be about half as wide again
     * as the stroke needs.
     */
    public static final double RIB_HALF_DEGREES = 0.9;

    /** The caster eye above the sphere centre: 1.62 of eye height less 1.80/2 of body. */
    public static final double EYE_OFFSET = 0.72;

    /** Pixels per radian at 1280x720 with Minecraft default seventy degree vertical field. */
    public static final double PIXELS_PER_RADIAN = 360.0 / Math.tan(Math.toRadians(35.0));
    /** Half the horizontal field at 16:9, which is what decides how many ribs are ever in frame. */
    public static final double HALF_FOV_DEGREES = Math.toDegrees(Math.atan(640.0 / PIXELS_PER_RADIAN));

    /** How far off the sight line everything has to stay, so nothing is ever drawn on the reticle. */
    public static final double AIM_CLEARANCE_DEGREES = 4.0;

    private SubspaceVault() {}

    /** How tightly the domain is wound, in degrees of bearing per degree of latitude. */
    public static double twist(int lawCount) {
        return TWIST_BASE + TWIST_PER_LAW * Math.max(0, lawCount);
    }

    /** How far a rib leans off vertical where it crosses this latitude. */
    public static double leanDegrees(double latitudeDegrees, int lawCount) {
        return Math.toDegrees(Math.atan(twist(lawCount) * Math.cos(Math.toRadians(latitudeDegrees))));
    }

    /**
     * The latitude that puts the springing course at {@link #SPRING_ELEVATION_DEGREES} above the
     * sight line, for this radius.
     *
     * <p>Solving {@code atan2(R sin L - d, R cos L) = t} for L is one identity: expanding gives
     * {@code sin(L - t) = d cos(t) / R}, so the latitude is the elevation plus a correction that
     * shrinks as the domain grows, which is exactly the behaviour a fixed latitude got wrong.
     */
    public static double springLatitudeDegrees(double radius) {
        double t = Math.toRadians(SPRING_ELEVATION_DEGREES);
        double correction = Math.asin(clampUnit(EYE_OFFSET * Math.cos(t) / Math.max(1.0E-6D, radius)));
        return Math.toDegrees(t + correction);
    }

    /** Where the wall cuts the floor the caster is standing on. */
    public static double footprintLatitudeDegrees(double radius) {
        return Math.toDegrees(Math.asin(clampUnit(-FOOTPRINT_DROP / Math.max(1.0E-6D, radius))));
    }

    /** The latitude of the boss, a fixed fraction of the way up the rib from the springing. */
    public static double bossLatitudeDegrees(double radius) {
        double spring = springLatitudeDegrees(radius);
        return spring + BOSS_ALONG * (OCULUS_LATITUDE_DEGREES - spring);
    }

    /** Where a rib crosses a latitude: its slot bearing, plus whatever the winding has added. */
    public static double ribBearingDegrees(int slot, double latitudeDegrees, double radius, int lawCount) {
        return slot * RIB_PITCH_DEGREES + twist(lawCount) * (latitudeDegrees - springLatitudeDegrees(radius));
    }

    /** Where the boss sits, which is wherever its own rib has got to by then. */
    public static double bossBearingDegrees(int slot, double radius, int lawCount) {
        return ribBearingDegrees(slot, bossLatitudeDegrees(radius), radius, lawCount);
    }

    /** How high on screen a latitude appears, in degrees, seen from the centre. */
    public static double apparentElevationDegrees(double latitudeDegrees, double radius) {
        double latitude = Math.toRadians(latitudeDegrees);
        return Math.toDegrees(Math.atan2(radius * Math.sin(latitude) - EYE_OFFSET, radius * Math.cos(latitude)));
    }

    /**
     * How many ribs are in frame from the worst bearing there is.
     *
     * <p>This is the whole of the structural fix. The wall this replaces put all twelve of its
     * marks on one arc round north, so from three quarters of all bearings a fully legislated
     * domain and an empty one were the same picture. A ring of twelve is a ring of twelve whichever
     * way you turn.
     */
    public static int ribsInView() {
        return (int) Math.floor(2.0 * HALF_FOV_DEGREES / RIB_PITCH_DEGREES);
    }

    private static double clampUnit(double value) {
        return value < -1.0 ? -1.0 : Math.min(value, 1.0);
    }
}
