package com.efkrdnz.magical.magic.sword.stance;

/**
 * Where every sword of a stance sits, and <b>the one arithmetic both sides run</b>.
 *
 * <p>The server behaviour and the client painter call {@link #place} with identical arguments and
 * get identical doubles, which is the discipline the old lattice already had and is why twelve
 * blade positions cost <b>zero bytes</b> on the wire: the client is told the stance, the present
 * mask and the frame - none of which change often - and works the rest out itself. A blade cannot
 * desync because there is nothing to desync.
 *
 * <p>Which is also why a sign error in here is a <em>silent mirror</em>: the formation appears on
 * the wrong side for everybody at once, consistently, with a green build and no log line. So the
 * handedness is stated on {@link Slot} and {@code FormationTest} measures the result rather than
 * trusting the reasoning - and it measures <b>the view the player actually has</b>, because the
 * last time this school shipped a green suite it had certified a cast circle 73 degrees wide on a
 * 70 degree screen.
 *
 * <p>Pure. No level, no entity, no clock: {@code phase} is handed in, so the same tick gives the
 * same picture twice and a test can sweep it.
 */
public final class Formation {

    /**
     * The closest two blades may sit, in blocks.
     *
     * <p>It is a blade's <em>width</em> plus margin and not its length, because two parallel
     * swords a third of a block apart read as a pair and two crossing ones read as one object.
     * Duskfall is drawn about 1.17 blocks long and a tenth of that wide, so 0.45 is roughly four
     * blade-widths - enough to tell them apart at the range a formation is seen from, which is
     * arm's length. {@code FormationTest} holds every stance to it at every count.
     */
    public static final double MIN_SEPARATION = 0.45D;

    /**
     * No slot may sit further than this from the frame origin.
     *
     * <p>The renderer's cull box and the entity's bounds are both sized off it, so a formation
     * that quietly reached further would be one that vanishes whenever the wielder's own bounding
     * box leaves the frustum.
     */
    public static final double MAX_EXTENT = 4.0D;

    /**
     * Nothing may sit inside the wielder: this is the radius of the hole in every formation.
     *
     * <p>It is 0.80 because <b>a sword is drawn about its own middle</b>. The blade model is
     * about 1.26 blocks pommel to point as drawn, so half of it hangs back toward the frame
     * origin, and a slot nearer than that half-length puts the pommel of its own sword in the
     * wielder's chest. {@code SwordSilhouetteTest} measures the drawn half-length against this
     * constant and fails when they cross, which is the only thing holding the two in step -
     * this package is pure and cannot see the renderer, and the renderer has no business
     * knowing about stances.
     *
     * <p>It was 0.55, which was under that half-length and under the real floor as well: the
     * tightest slot any of the six actually asks for is Wings at 0.8806. A clearance nothing
     * comes within a third of a block of is not a hole radius, it is a number nobody measured.
     */
    public static final double BODY_CLEARANCE = 0.80D;

    /**
     * The eye, in frame-local coordinates, <b>for a {@link SwordStance.Anchor#BODY} stance
     * only</b>. Such a frame is pinned to the body centre at {@code SwordService.BODY_CENTRE} =
     * 0.9 above the feet, and a standing player's eye is at 1.62, so the eye is 0.72 above the
     * origin with the horizontal offset exactly zero. Every first-person rule below is measured
     * from there and not from the origin, because the origin is in the wielder's chest and the
     * camera is not.
     *
     * <p>A {@link SwordStance.Anchor#LOOK} frame is pinned to <b>the eye itself</b>, so its eye
     * offset is zero and Vanguard's rings are centred on the aim line by construction. That is a
     * requirement on {@code SwordService.frame} rather than a choice this class can make, and it
     * is the only arrangement that survives the wielder looking up: a LOOK ring hung 0.72 above a
     * chest-pinned origin is centred at pitch zero and slides off the crosshair at every other
     * pitch, which is a formation that wanders across the aim as the head moves.
     */
    public static final double EYE_HEIGHT = 0.72D;

    /** Nothing may sit within this of the eye. A blade nearer than this is inside the camera. */
    public static final double EYE_CLEARANCE = 0.45D;

    /** Degrees of the forward axis that stay clear of steel, inside {@link #CROSSHAIR_RANGE}. */
    public static final double CROSSHAIR_CONE = 8.0D;

    /** Blades further out than this may cross the aim: they are scenery, not an obstruction. */
    public static final double CROSSHAIR_RANGE = 2.5D;

    // ---- Guard: an arc across the shoulders and behind the head --------------------------------

    private static final double GUARD_RADIUS = 1.25D;

    /**
     * How far round the wielder the guard wraps.
     *
     * <p>250 and not the 200 it was first written at, and the reason is arithmetic rather than
     * taste. Twelve blades on a 200 degree arc at this radius are {@code 2 * r * sin(9.09 deg)}
     * = 0.40 apart, and the alternating {@link #GUARD_STAGGER} cannot make that up once
     * {@link #GUARD_BOB} is allowed to move two neighbours in opposite directions - the worst
     * case lands at 0.41 against a {@link #MIN_SEPARATION} of 0.45, so a Sword God's guard had
     * two pairs of blades inside each other and nothing but the test would ever have said so.
     * 250 degrees puts the chord at 0.49 and the worst case at 0.53, and it reads better: the
     * guard wraps round to 55 degrees off the wielder's own forward, which is where a shoulder is.
     */
    private static final double GUARD_ARC_DEGREES = 250.0D;

    /**
     * Jaw height, and <b>this constant is against a wall</b>.
     *
     * <p>0.60 above the body centre is 1.50 above the feet, so the arc spans 1.50 to 1.80 once
     * {@link #GUARD_STAGGER} is added - jaw to the crown of the head, and it reads as held over
     * the wielder rather than resting on them. It was 0.45, a shoulder, and before that 0.80.
     *
     * <p>0.80 failed, and it failed for a reason that is still live: it put Guard's arc and
     * Crown's ring within {@code 0.62} of each other blade for blade, under the 0.9 that
     * {@code FormationTest.everyStanceLooksDifferentFromEveryOtherStance} demands. Two stances
     * that close are one stance with two names, which is the exact failure this whole redesign
     * exists to prevent, and no other rule in the file would have mentioned it. So the ceiling
     * here is set by {@link #CROWN_Y} and by nothing else, and it was measured rather than
     * guessed: the mean per-index gap to Crown runs 0.9521 at 0.60, 0.9154 at 0.65 and 0.8799 at
     * 0.70. <b>0.65 is the last value that passes and 0.60 is the last one with any margin</b> -
     * 0.052, against 0.015. Raising Guard further means raising Crown first.
     *
     * <p>Everything else this moves, it moves the right way: the nearest slot to the wielder goes
     * 1.3125 to 1.3657, clear of {@link #BODY_CLEARANCE}, and the arc's own worst separation is
     * untouched at 0.5554 because y shifts every blade together.
     */
    private static final double GUARD_BASE_Y = 0.60D;

    /** Alternate blades ride higher, which is what buys the separation the arc alone cannot. */
    private static final double GUARD_STAGGER = 0.30D;

    /** Small, because two neighbours out of phase spend twice this out of the stagger. */
    private static final double GUARD_BOB = 0.05D;

    // ---- Vanguard: two rings about the aim line ------------------------------------------------

    private static final double VANGUARD_NEAR = 1.50D;
    private static final double VANGUARD_FAR = 2.25D;
    private static final double VANGUARD_NEAR_RADIUS = 0.62D;
    private static final double VANGUARD_FAR_RADIUS = 0.86D;
    /**
     * Small, because it moves the two rings toward each other.
     *
     * <p>The rings are 0.75 apart in depth and two blades drifting in opposite phase spend twice
     * this out of that gap, so 0.15 left the worst case at 0.45 - inside {@link #MIN_SEPARATION}
     * once the rings' own radii are accounted for.
     */
    private static final double VANGUARD_DRIFT = 0.10D;

    // ---- Crown: a turning ring above the head --------------------------------------------------

    /** Clear above the head: Guard has the shoulders, and a crown that shared them is a collar. */
    private static final double CROWN_Y = 1.45D;

    private static final double CROWN_RADIUS = 0.90D;
    private static final double CROWN_SPIN = 0.055D;

    // ---- Wings: two swept-back fans -------------------------------------------------------------

    private static final double WING_ROOT_X = 0.42D;
    private static final double WING_ROOT_Y = 0.55D;
    private static final double WING_ROOT_Z = -0.15D;
    private static final double WING_OUT = 0.55D;
    private static final double WING_OUT_STEP = 0.30D;
    private static final double WING_UP = 0.62D;
    private static final double WING_UP_STEP = 0.26D;
    private static final double WING_BACK = 0.45D;
    private static final double WING_BACK_STEP = 0.36D;
    private static final double WING_FLAP = 0.10D;

    // ---- Coil: a fast level orbit at the waist ---------------------------------------------------

    private static final double COIL_Y = -0.40D;
    private static final double COIL_RADIUS = 1.30D;
    private static final double COIL_SPIN = 0.13D;

    // ---- Rain: a scattered disc overhead ----------------------------------------------------------

    private static final double RAIN_Y = 2.30D;
    private static final double RAIN_RADIUS = 1.25D;
    private static final double RAIN_DRIFT = 0.16D;

    private Formation() {
    }

    /** {@link #place(SwordStance, int, int, double, double)} with the look level. */
    public static Slot place(SwordStance stance, int index, int count, double phase) {
        return place(stance, index, count, phase, 0.0D);
    }

    /**
     * Sword {@code index} of {@code count}, at tick {@code phase}.
     *
     * <p>{@code lookElevationDegrees} is how far above the horizon the wielder is looking,
     * <b>positive up</b>, expressed in the stance's own anchor frame - so it is the wielder's
     * elevation for a {@link SwordStance.Anchor#BODY} stance and <b>zero</b> for a
     * {@link SwordStance.Anchor#LOOK} one, whose frame already carries the pitch. Positive-up
     * rather than Minecraft's positive-down because this is the second pitch convention in the
     * kit and the two have already been allowed to meet unmarked once; the conversion happens at
     * the one call site that knows a player, and nowhere in here.
     *
     * <p>Exactly one thing reads it - {@link SwordStance.Facing#LOOK} - so Crown, Coil and Rain
     * ignore it entirely, which is correct: an outward-pointing ring does not care where its
     * wielder is looking.
     */
    public static Slot place(SwordStance stance, int index, int count, double phase,
            double lookElevationDegrees) {
        int n = Math.max(1, count);
        int i = Math.max(0, Math.min(n - 1, index));
        double[] pos = switch (stance) {
            case GUARD -> guard(i, n, phase);
            case VANGUARD -> vanguard(i, n, phase);
            case CROWN -> crown(i, n, phase);
            case WINGS -> wings(i, phase);
            case COIL -> coil(i, n, phase);
            case RAIN -> rain(i, n, phase);
        };
        double[] dir = facing(stance, pos, i, lookElevationDegrees);
        return Slot.at(pos[0], pos[1], pos[2], dir[0], dir[1], dir[2]);
    }

    // ---- the six -----------------------------------------------------------------------------

    /**
     * An arc from one shoulder round behind the head to the other, alternating high and low.
     *
     * <p>The arc is centred on the rear, so a single sword hangs directly behind the wielder and a
     * full set reaches round to just forward of both shoulders. Nothing is in front: the whole
     * point of a guard is that it covers what you are not looking at.
     */
    private static double[] guard(int i, int n, double phase) {
        double t = n == 1 ? 0.5D : (double) i / (n - 1);
        double bearing = 180.0D - GUARD_ARC_DEGREES * 0.5D + GUARD_ARC_DEGREES * t;
        double a = Math.toRadians(bearing);
        double y = GUARD_BASE_Y + (i % 2 == 0 ? 0.0D : GUARD_STAGGER)
                + GUARD_BOB * Math.sin(phase * 0.05D + i * 0.9D);
        // -sin for x: yaw turns clockwise seen from above, so bearing 90 is the wielder's right,
        // which is -X. Slot's class note is where that is written down once.
        return new double[] {-Math.sin(a) * GUARD_RADIUS, y, Math.cos(a) * GUARD_RADIUS};
    }

    /**
     * Two rings about the aim line, alternating near and far.
     *
     * <p><b>Nothing is on the aim line itself</b>, which is the rule this stance exists to break
     * and must not: a sword parked on the crosshair is the most obstructive thing a first-person
     * formation can do, and Vanguard is the only stance with blades in front at all. The near
     * ring stands off 22 degrees and the far one 21, both well outside {@link #CROSSHAIR_CONE}.
     */
    private static double[] vanguard(int i, int n, double phase) {
        // Split into two rings rather than alternating by parity. Parity was the obvious way and
        // it is wrong on an odd count: at nine, blades 0 and 8 are both on the near ring and 40
        // degrees apart, which is 0.43 - inside MIN_SEPARATION, and invisible at every even count.
        int near = (n + 1) / 2;
        boolean far = i >= near;
        int ring = far ? n - near : near;
        int j = far ? i - near : i;
        double radius = far ? VANGUARD_FAR_RADIUS : VANGUARD_NEAR_RADIUS;
        double depth = (far ? VANGUARD_FAR : VANGUARD_NEAR)
                + VANGUARD_DRIFT * Math.sin(phase * 0.06D + i * 0.7D);
        // The far ring is turned half a step, so no near blade is directly behind a far one - a
        // pair on one bearing reads in first person as a single sword with a doubled outline.
        double a = 2.0D * Math.PI * j / Math.max(1, ring)
                + (far ? Math.PI / Math.max(1, ring) : 0.0D)
                + phase * 0.02D;
        return new double[] {-Math.sin(a) * radius, Math.cos(a) * radius, depth};
    }

    /** A level ring above the head, turning slowly. */
    private static double[] crown(int i, int n, double phase) {
        double a = 2.0D * Math.PI * i / n + phase * CROWN_SPIN;
        return new double[] {-Math.sin(a) * CROWN_RADIUS, CROWN_Y, Math.cos(a) * CROWN_RADIUS};
    }

    /**
     * Two fans, sides alternating by parity, each blade further out, higher and further back.
     *
     * <p>Parity rather than splitting the run, so five swords is three on the left and two on the
     * right rather than a fan with everything on one wing.
     */
    private static double[] wings(int i, double phase) {
        double side = i % 2 == 0 ? 1.0D : -1.0D;
        int j = i / 2;
        double y = WING_UP + WING_UP_STEP * j + WING_FLAP * Math.sin(phase * 0.055D - j * 0.6D);
        return new double[] {
            side * (WING_OUT + WING_OUT_STEP * j),
            y,
            -(WING_BACK + WING_BACK_STEP * j)
        };
    }

    /** A fast level orbit at the waist, below the eye so it sweeps under the crosshair. */
    private static double[] coil(int i, int n, double phase) {
        double a = 2.0D * Math.PI * i / n + phase * COIL_SPIN;
        return new double[] {-Math.sin(a) * COIL_RADIUS, COIL_Y, Math.cos(a) * COIL_RADIUS};
    }

    /**
     * A scattered disc high overhead, drifting.
     *
     * <p>The scatter is a golden-angle spiral rather than a random number, for the reason the
     * whole package is pure: both sides compute it, so it has to be the same scatter on both.
     */
    private static double[] rain(int i, int n, double phase) {
        double a = i * Pattern.GOLDEN;
        double r = RAIN_RADIUS * Math.sqrt((i + 0.5D) / n);
        double y = RAIN_Y + RAIN_DRIFT * Math.sin(phase * 0.035D + i * 1.7D);
        return new double[] {Math.cos(a) * r, y, Math.sin(a) * r};
    }

    // ---- which way it points -------------------------------------------------------------------

    private static double[] facing(SwordStance stance, double[] pos, int i, double elevation) {
        return switch (stance.facing()) {
            case LOOK -> {
                double e = Math.toRadians(elevation);
                yield new double[] {0.0D, Math.sin(e), Math.cos(e)};
            }
            case OUTWARD -> new double[] {pos[0], 0.0D, pos[2]};
            case DOWN -> new double[] {0.0D, -1.0D, 0.0D};
            case ALONG -> {
                double side = i % 2 == 0 ? 1.0D : -1.0D;
                yield new double[] {
                    pos[0] - side * WING_ROOT_X, pos[1] - WING_ROOT_Y, pos[2] - WING_ROOT_Z
                };
            }
        };
    }
}
