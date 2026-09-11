package com.efkrdnz.magical.magic.blood.shape;

/**
 * The limits and the total arithmetic of a drawn blood shape. Every method here is pure, so the
 * editor's preview, the server's charge and the client's render all agree without talking.
 *
 * <p>This package deliberately imports nothing from Minecraft. The server hit test and the client
 * painter both run the same geometry, and the only way to guarantee that is to make it impossible
 * for either side to reach for something the other does not have.
 */
public final class BloodShapeRules {

    /** Shapes a player keeps at once, one per number key. */
    public static final int SHAPE_SLOTS = 9;

    /**
     * Strokes one shape may be drawn with.
     *
     * <p>Four is a budget, not a limit of the drawing: the whole book is written into
     * {@code PlayerMagicState.save()}, which is simultaneously the disk format and the wire format
     * and is re-serialised on every one of a hundred-odd sync calls. Nine full slots at these caps
     * come to roughly 4.9 KB, and that is the ceiling this feature adds to every sync forever.
     */
    public static final int MAX_STROKES_PER_SHAPE = 4;

    /** Points one stroke is stored with; longer strokes are resampled down on capture. */
    public static final int MAX_POINTS_PER_STROKE = 32;

    /**
     * Quantization of a stored coordinate: sixteenths of a block, which is one voxel pitch.
     *
     * <p>A power of two on purpose. {@code units / 16.0} is exact in binary floating point, so a
     * point written by the client and read by the server is the same number rather than nearly the
     * same one - the guarantee {@code StrokeQuantizer} exists to make, kept here for free.
     */
    public static final int UNITS_PER_BLOCK = 16;

    /** Smallest canvas the reach stat can produce, as a half-width in blocks. */
    public static final double MIN_HALF_EXTENT_BLOCKS = 2.0D;

    /** Largest canvas the reach stat can produce, as a half-width in blocks. */
    public static final double MAX_HALF_EXTENT_BLOCKS = 20.0D;

    /** Largest magnitude a stored coordinate may have. Well inside a signed short. */
    public static final int MAX_UNIT = (int) (MAX_HALF_EXTENT_BLOCKS * UNITS_PER_BLOCK);

    /** The canvas follows the caster's yaw instead of the compass. */
    public static final int FLAG_TRACK_YAW = 1;

    /** The formed shape pitches with the camera, so it can be aimed up and down. */
    public static final int FLAG_TRACK_PITCH = 1 << 1;

    /** While the blood is up it keeps following the caster's view instead of holding still. */
    public static final int FLAG_KEEP_ROTATING = 1 << 2;

    private static final int FLAG_MASK = FLAG_TRACK_YAW | FLAG_TRACK_PITCH | FLAG_KEEP_ROTATING;

    /**
     * Where the plane sits by default, as a percentage of the caster's height.
     *
     * <p>89% of 1.8 is 1.602 - eye level, so an untouched slider draws where the player is looking
     * from rather than at their ankles.
     */
    public static final int DEFAULT_HEIGHT_PERCENT = 89;

    /** Yaw the canvas is read at when horizontal tracking is off: due north, so canvas up is N. */
    public static final float COMPASS_YAW_DEGREES = 180.0F;

    /** Paid for pressing the button at all, before a single block of shape is drawn. */
    public static final int BASE_COST = 12;

    /** Added per block of drawn path. At {@code BloodService.COST_PER_HEALTH} this is half a heart. */
    public static final int COST_PER_BLOCK = 4;

    /**
     * Ceiling on what one shape can cost.
     *
     * <p>Without it a long enough drawing bills more than any player has, and
     * {@code BloodService.pay} would refuse every cast of a shape that looked perfectly reasonable
     * in the editor. The cap is above a full health bar on purpose - it is a guard rail, not a
     * discount.
     */
    public static final int MAX_COST = 120;

    private BloodShapeRules() {
    }

    /** Packs a quantized point into one int. The low half is y, so the high half keeps its sign. */
    public static int pack(int x, int y) {
        return ((x & 0xFFFF) << 16) | (y & 0xFFFF);
    }

    /** Unpacks the x half. The short cast is what restores the sign of a negative coordinate. */
    public static int unpackX(int packed) {
        return (short) (packed >>> 16);
    }

    /** Unpacks the y half. */
    public static int unpackY(int packed) {
        return (short) packed;
    }

    public static double fromUnits(int units) {
        return units / (double) UNITS_PER_BLOCK;
    }

    public static int toUnits(double blocks) {
        return (int) Math.round(blocks * UNITS_PER_BLOCK);
    }

    /** True when a stored coordinate is one this build could ever have produced. */
    public static boolean isStorableUnit(int units) {
        return units >= -MAX_UNIT && units <= MAX_UNIT;
    }

    /**
     * How far the canvas reaches, from the resolved size stat.
     *
     * <p>Reach rides on the existing SIZE stat under a different label. A sixth
     * {@code MagicTuningStat} would widen the tuning button band past the loadout band and change
     * the tuning save format; relabelling is what {@code beam_radius} and {@code seal_reach}
     * already do.
     */
    public static double halfExtentBlocks(double resolvedSize) {
        if (Double.isNaN(resolvedSize)) {
            return MIN_HALF_EXTENT_BLOCKS;
        }
        return Math.max(MIN_HALF_EXTENT_BLOCKS, Math.min(MAX_HALF_EXTENT_BLOCKS, resolvedSize));
    }

    /** What a shape of this drawn length costs. Monotonic in length and capped. */
    public static int bloodCost(double arcLengthBlocks) {
        if (!(arcLengthBlocks > 0.0D)) {
            return 0;
        }
        long cost = BASE_COST + Math.round(arcLengthBlocks * COST_PER_BLOCK);
        return (int) Math.min(MAX_COST, cost);
    }

    /** Voxel spacing a field would like: one block texture pixel, so the grid reads as pixels. */
    public static final double BASE_VOXEL_PITCH = 1.0D / 16.0D;

    /** Cubes one field may ask for. The shared per-frame allowance sits above this, in FxBudget. */
    public static final int MAX_FIELD_VOXELS = 1800;

    /** Spine points a field may sync. Each is four bytes of entity data on every tracking client. */
    public static final int MAX_SPINE_POINTS = 384;

    /**
     * The spacing a field of this size has to settle for.
     *
     * <p>Thinning is done by coarsening the pitch, never by dropping rows off the wall: dropping
     * rows leaves a comb, while a coarser grid still reads as a grid. Iterating rather than solving
     * in closed form because the row count is a rounding and the loop is a dozen multiplications at
     * cast time - cheap, and obviously terminating.
     */
    public static double voxelPitch(double arcLengthBlocks, double wallHeight, double thickness,
            int polylines) {
        double pitch = BASE_VOXEL_PITCH;
        double height = Math.abs(wallHeight);
        for (int step = 0; step < 24; step++) {
            int columns = (int) Math.round(arcLengthBlocks / pitch) + Math.max(1, polylines);
            int rows = Math.max(1, (int) Math.round(height / pitch));
            // The across axis is filled rather than represented by scattering one cube through it,
            // so it counts toward the budget like the other two. Left out, the expansion runs out
            // of cap partway along and the shape simply stops.
            int lanes = Math.max(1, (int) Math.round(thickness * 2.0D / pitch));
            if (columns <= MAX_SPINE_POINTS
                    && (long) columns * rows * lanes <= MAX_FIELD_VOXELS) {
                return pitch;
            }
            pitch *= 1.25D;
        }
        return pitch;
    }

    public static int clampHeightPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    /** Drops any bit this build does not define, so an unknown flag cannot switch on behaviour. */
    public static int clampFlags(int flags) {
        return flags & FLAG_MASK;
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /** Height of the plane above the caster's feet, in blocks. */
    public static double heightOffsetBlocks(int heightPercent, double casterHeight) {
        return clampHeightPercent(heightPercent) / 100.0D * casterHeight;
    }

    // ---------------------------------------------------------------- vertical spread

    /**
     * How tall the blood stands with the spread slider centred: a sheet, not a wall.
     *
     * <p>A shape is drawn on a plan view, so its interesting dimension is the one the player drew.
     * Standing it up as a tall wall by default buries the drawing inside a slab.
     */
    public static final double BASE_WALL_HEIGHT = 0.2D;

    /** Share of the caster's own height a fully pushed spread slider reaches. */
    public static final double MAX_WALL_HEIGHT_FACTOR = 1.1D;

    /** How far the spread slider travels either side of centre. */
    public static final int SPREAD_PERCENT_RANGE = 100;

    /** Centred: the thin sheet, extruded upward. */
    public static final int DEFAULT_SPREAD_PERCENT = 0;

    public static int clampSpreadPercent(int percent) {
        return Math.max(-SPREAD_PERCENT_RANGE, Math.min(SPREAD_PERCENT_RANGE, percent));
    }

    /**
     * How tall the blood stands, and which way.
     *
     * <p><b>Signed.</b> The magnitude grows from {@link #BASE_WALL_HEIGHT} at the centre to
     * {@link #MAX_WALL_HEIGHT_FACTOR} of the caster's height at either end, and the sign says which
     * way it grows from the drawn plane. One slider therefore does two jobs without a second
     * control for direction, and the centre is a real default rather than the bottom of a range.
     */
    public static double wallHeightBlocks(int spreadPercent, double casterHeight) {
        int clamped = clampSpreadPercent(spreadPercent);
        double reach = Math.max(BASE_WALL_HEIGHT, MAX_WALL_HEIGHT_FACTOR * casterHeight);
        double magnitude = BASE_WALL_HEIGHT
                + Math.abs(clamped) / (double) SPREAD_PERCENT_RANGE * (reach - BASE_WALL_HEIGHT);
        return clamped < 0 ? -magnitude : magnitude;
    }
}
