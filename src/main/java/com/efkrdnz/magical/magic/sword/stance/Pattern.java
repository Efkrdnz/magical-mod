package com.efkrdnz.magical.magic.sword.stance;

/**
 * The shape a volley takes, and the one thing a stance lends to the four actives.
 *
 * <p>Six geometries, read by <b>Loose</b> going forward from the wielder and by <b>Below</b>
 * coming up from under the target, so six patterns and two verbs give twelve results out of one
 * implementation each. That is the whole of "different stances do different things with
 * abilities": the wielder picks a posture and their volley changes shape with it, and there is no
 * per-skill dropdown anywhere.
 *
 * <p>Pure, and the frame is <b>target-local</b>: +Z runs from the caster toward the target, +Y is
 * up, +X is the caster's left. {@link #spread} answers where one blade of the volley aims relative
 * to the target's centre and which way it travels getting there; the caller turns that into the
 * world with the same rotation {@code ArrayPose} uses for everything else.
 *
 * <p><b>No pattern takes a cap.</b> How many swords are present is the cap - the one good idea the
 * old {@code Projection} had, kept for its reason: a wielder who has just spent six swords Looses
 * with six, and the number is on screen the whole time.
 */
public enum Pattern {

    /** Guard. Abreast across the aim - a wall, which is what a guard's volley should be. */
    LINE,

    /** Vanguard. One behind another down the aim line: deep, narrow and all on one body. */
    COLUMN,

    /** Crown. Outward in every direction at once, or all round the target. */
    RING,

    /** Wings. Two arcs converging on the aim point, or two lines flanking it. */
    FAN,

    /** Coil. Wide, short and fast - a shred rather than a volley. */
    SPRAY,

    /** Rain. From above, whichever verb asked. */
    FALL;

    /** Half the width {@link #LINE} lays its blades over, in blocks. */
    public static final double LINE_HALF_SPAN = 2.2D;

    /** Blocks between two blades of a {@link #COLUMN}, measured back along the aim. */
    public static final double COLUMN_STEP = 1.1D;

    /** The radius {@link #RING} and {@link #FAN} stand off at. */
    public static final double RING_RADIUS = 2.0D;

    /** Degrees either side of the aim that a {@link #FAN}'s two arcs open to. */
    public static final double FAN_SWEEP = 55.0D;

    /** Half the width and height {@link #SPRAY} scatters over. */
    public static final double SPRAY_HALF = 1.5D;

    /** How high above the target {@link #FALL} starts. */
    public static final double FALL_HEIGHT = 5.0D;

    /** The golden angle: how a scatter is made to look random without a random number in it. */
    static final double GOLDEN = 2.39996322972865332D;

    /**
     * Where blade {@code index} of a volley of {@code count} aims, and which way it goes.
     *
     * <p>Target-local, as the class note says. The position is the offset from the target's centre
     * that this blade converges on; the direction is its travel, unit length. A caller wanting the
     * launch point walks back along the direction by however far the skill launches from - none of
     * that is this method's business, which is why it is pure and checkable with nothing under it.
     *
     * <p><b>A volley of one is not a special case anywhere.</b> Every parameterisation is written
     * so the single blade lands on the pattern's own axis rather than at one end of a spread it is
     * the only member of, and {@code PatternTest} checks exactly that - an off-centre single is
     * the kind of thing that only ever surfaces as "my last sword misses".
     */
    public static Slot spread(Pattern pattern, int index, int count, double reach) {
        int n = Math.max(1, count);
        int i = Math.max(0, Math.min(n - 1, index));
        // -0.5 .. +0.5, with a single blade dead on the axis. The branch is the whole reason this
        // is not i / (n - 1): that is 0/0 at one and hard against one end at two.
        double t = n == 1 ? 0.0D : (double) i / (n - 1) - 0.5D;

        return switch (pattern) {
            case LINE -> Slot.at(t * 2.0D * LINE_HALF_SPAN, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D);
            case COLUMN -> Slot.at(
                    // A hair of stagger, alternating, so a column reads as several blades rather
                    // than as one blade drawn six times down a line the camera is looking along.
                    i % 2 == 0 ? 0.18D : -0.18D,
                    i % 2 == 0 ? -0.14D : 0.14D,
                    -i * COLUMN_STEP,
                    0.0D, 0.0D, 1.0D);
            case RING -> ring(i, n);
            case FAN -> fan(i, n);
            case SPRAY -> spray(i, n);
            case FALL -> fall(i, n, reach);
        };
    }

    /** Evenly round the target, every blade pointing inward at it. */
    private static Slot ring(int i, int n) {
        double a = 2.0D * Math.PI * i / n;
        double x = Math.cos(a) * RING_RADIUS;
        double z = Math.sin(a) * RING_RADIUS;
        return Slot.at(x, 0.0D, z, -x, 0.0D, -z);
    }

    /**
     * Two arcs, one per side, sweeping in from {@link #FAN_SWEEP} to meet at the target.
     *
     * <p>Sides alternate by parity rather than splitting the run in half, so an odd count is one
     * blade heavier on the left and never a fan with four on one wing and one on the other.
     */
    private static Slot fan(int i, int n) {
        double side = i % 2 == 0 ? 1.0D : -1.0D;
        int j = i / 2;
        int perSide = Math.max(1, (n + (i % 2 == 0 ? 1 : 0)) / 2);
        double t = perSide == 1 ? 1.0D : 0.4D + 0.6D * j / (perSide - 1);
        double a = Math.toRadians(FAN_SWEEP * t) * side;
        double x = Math.sin(a) * RING_RADIUS;
        double z = -Math.cos(a) * RING_RADIUS;
        return Slot.at(x, 0.0D, z, -x, 0.0D, -z);
    }

    /** A scattered disc square-on to the aim, on the golden angle so it is not a random number. */
    private static Slot spray(int i, int n) {
        double a = i * GOLDEN;
        double r = scatter(i, n, SPRAY_HALF);
        return Slot.at(Math.cos(a) * r, Math.sin(a) * r, 0.0D, 0.0D, 0.0D, 1.0D);
    }

    /** Overhead on the same golden scatter, coming straight down. */
    private static Slot fall(int i, int n, double reach) {
        double a = i * GOLDEN;
        double spread = Math.min(SPRAY_HALF, Math.max(0.6D, reach * 0.12D));
        double r = scatter(i, n, spread);
        return Slot.at(Math.cos(a) * r, FALL_HEIGHT, Math.sin(a) * r, 0.0D, -1.0D, 0.0D);
    }

    /**
     * The golden-spiral radius, and <b>zero when there is only one blade</b>.
     *
     * <p>{@code sqrt((i + 0.5) / n)} is the right density for a scatter and it is 0.707 at
     * {@code n == 1}, which put a wielder's last sword a full 1.06 off the axis - a miss, on the
     * one shot where a miss costs everything. The half-step exists to keep the innermost blade of
     * a real scatter off the exact centre, and with one blade there is no scatter to keep it out
     * of the middle of.
     */
    private static double scatter(int i, int n, double half) {
        return n <= 1 ? 0.0D : half * Math.sqrt((i + 0.5D) / n);
    }
}
