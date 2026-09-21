package com.efkrdnz.magical.magic.sword;

/**
 * One authored bearing, and the whole of what a sword's place in the world is made of.
 *
 * <p>A station is a <em>direction</em> with a distance and a quantity of Edge on it, and it is
 * deliberately nothing else. There is no mode, no state machine and no per-blade clock here,
 * because the moment a station could carry one the kit stops being an Array and becomes five
 * projectile skills with a dropdown. A blade is a bearing and a bit.
 *
 * <p>The lattice: yaw 0..23 in steps of {@link #YAW_STEP_DEGREES}, pitch -4..+4 in steps of
 * {@link #PITCH_STEP_DEGREES}, reach 1..6 blocks. That is 24 x 9 x 6 = 1296 distinct places, and
 * with the Edge the whole record fits in 18 bits - which is why the save format is an array of
 * ints rather than a list of compounds, and why no enum ordinal is written anywhere in this kit.
 *
 * <p><b>Edge 0 is legal on a stored station and never on a planted one.</b> Ward spends a
 * station's metal without unwriting its bearing, and a shed empties one the same way: the shape
 * survives, the metal does not. So {@link #onLattice()} admits it and {@code SwordArray.plant}
 * refuses it.
 */
public record Station(int yaw, int pitch, int reach, int edge) {

    /** Compass steps around the frame's up axis. 24 of them is 15 degrees each. */
    public static final int YAW_STEPS = 24;

    /** Degrees of bearing per yaw step. {@code YAW_STEPS * YAW_STEP_DEGREES == 360}. */
    public static final double YAW_STEP_DEGREES = 360.0D / YAW_STEPS;

    /** Elevation steps. -4 is 72 degrees below the frame plane and +4 is 72 above it. */
    public static final int PITCH_MIN = -4;

    public static final int PITCH_MAX = 4;

    /** Degrees of elevation per pitch step. */
    public static final double PITCH_STEP_DEGREES = 18.0D;

    /** Blocks out from the frame origin. The reach is half the bill; the Edge is the other half. */
    public static final int REACH_MIN = 1;

    public static final int REACH_MAX = 6;

    /** A station may hold no metal at all - see the class note - and never more than the whole. */
    public static final int EDGE_MIN = 0;

    public static final int EDGE_MAX = 36;

    // The packing, and the four shifts are the save format. Widths: edge 6 bits (0..63 holds
    // 0..36), reach 3 (0..5), pitch 4 (0..8), yaw 5 (0..23). Eighteen bits, one int, no ordinals.
    private static final int EDGE_BITS = 6;
    private static final int REACH_SHIFT = EDGE_BITS;
    private static final int REACH_BITS = 3;
    private static final int PITCH_SHIFT = REACH_SHIFT + REACH_BITS;
    private static final int PITCH_BITS = 4;
    private static final int YAW_SHIFT = PITCH_SHIFT + PITCH_BITS;
    private static final int YAW_BITS = 5;

    private static final int EDGE_MASK = (1 << EDGE_BITS) - 1;
    private static final int REACH_MASK = (1 << REACH_BITS) - 1;
    private static final int PITCH_MASK = (1 << PITCH_BITS) - 1;
    private static final int YAW_MASK = (1 << YAW_BITS) - 1;

    /** One int, and the only thing this kit ever writes to disk. */
    public int packed() {
        return (yaw & YAW_MASK) << YAW_SHIFT
                | ((pitch - PITCH_MIN) & PITCH_MASK) << PITCH_SHIFT
                | ((reach - REACH_MIN) & REACH_MASK) << REACH_SHIFT
                | (edge & EDGE_MASK);
    }

    /**
     * Total: every input is a station, because this reads a save a text editor may have touched.
     *
     * <p>It <em>clamps</em> rather than wrapping, yaw included. A wrap would quietly rotate a
     * corrupted bearing onto a legal one somewhere else on the ring and the shape would load
     * looking deliberate; a clamp piles the damage against one edge of the lattice, where
     * {@code SwordArray.load} then puts it through the same separation and bill rules a hand
     * placement goes through and mostly throws it away.
     */
    public static Station unpack(int packed) {
        int yaw = Math.min(YAW_STEPS - 1, (packed >>> YAW_SHIFT) & YAW_MASK);
        int pitch = Math.min(PITCH_MAX, ((packed >>> PITCH_SHIFT) & PITCH_MASK) + PITCH_MIN);
        int reach = Math.min(REACH_MAX, ((packed >>> REACH_SHIFT) & REACH_MASK) + REACH_MIN);
        int edge = Math.min(EDGE_MAX, packed & EDGE_MASK);
        return new Station(yaw, pitch, reach, edge);
    }

    /** Whether this is a place on the lattice at all. Off it, a plant answers OUT_OF_REACH. */
    public boolean onLattice() {
        return yaw >= 0 && yaw < YAW_STEPS
                && pitch >= PITCH_MIN && pitch <= PITCH_MAX
                && reach >= REACH_MIN && reach <= REACH_MAX
                && edge >= EDGE_MIN && edge <= EDGE_MAX;
    }

    /** The short way round the ring, so yaw 23 and yaw 0 are one step apart and not twenty-three. */
    public static int circularYawSteps(int a, int b) {
        int direct = Math.abs(a - b);
        return Math.min(direct, YAW_STEPS - direct);
    }

    /**
     * How far apart two bearings are, in steps, on whichever axis separates them most.
     *
     * <p>The max rather than the sum, because the rule it feeds is "two blades may not share a
     * bearing" and a pair a long way apart in pitch is unambiguous however close their yaw is.
     */
    public int separationFrom(Station other) {
        return Math.max(circularYawSteps(yaw, other.yaw()), Math.abs(pitch - other.pitch()));
    }

    /**
     * The frame-local direction this station points, as {x, y, z} of length one.
     *
     * <p><b>The handedness is Minecraft's own and nothing else will do</b>, because the server
     * behaviour and the client painter both run this and a sign here is a silent mirror that no
     * log line and no green test would ever mention. Yaw 0 faces <b>+Z</b>, yaw increases
     * <b>clockwise seen from above</b> (so yaw step 6, 90 degrees, faces -X), and <b>+Y is up</b>,
     * exactly as {@code Entity.calculateViewVector} lays it out. This record's {@code pitch} is an
     * <em>elevation</em> and not Minecraft's screen pitch: +4 is 72 degrees <em>above</em> the
     * frame plane, so it carries +Y. {@code ArrayPoseTest} pins all four cardinals and both poles.
     */
    public double[] unitBearing() {
        double bearing = Math.toRadians(yaw * YAW_STEP_DEGREES);
        double elevation = Math.toRadians(pitch * PITCH_STEP_DEGREES);
        double flat = Math.cos(elevation);
        return new double[] {-Math.sin(bearing) * flat, Math.sin(elevation), Math.cos(bearing) * flat};
    }

    public Station withEdge(int edge) {
        return new Station(yaw, pitch, reach, edge);
    }

    /** True while this station has metal on it. An unmanned bearing draws no bill and fires nothing. */
    public boolean manned() {
        return edge > 0;
    }

    /** This station's share of the bill: reach times Edge, and the whole of the budget rule. */
    public int weight() {
        return reach * edge;
    }
}
