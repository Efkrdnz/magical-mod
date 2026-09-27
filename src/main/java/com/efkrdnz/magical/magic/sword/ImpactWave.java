package com.efkrdnz.magical.magic.sword;

/**
 * A sword hitting something, as arithmetic: a small wave of vanilla puffs running out across the
 * struck surface, the sparks or grit the hit throws back, and the rule that stops a volley landing
 * in one body from being twelve waves stacked on each other.
 *
 * <p>The school draws its steel and nothing else - no aura, no glint, no thread, no FX silhouette -
 * so the moment a blade meets something is the one moment it may add anything to the frame, and
 * what it adds is vanilla particles the player already reads: {@code POOF} for the wave,
 * {@code CRIT} for the sparks, the struck block's own crumbs for grit. The client spawner owns the
 * particle types; this class owns every number they are launched with, so {@code ImpactWaveTest}
 * can hold the ring in its plane, the reach at the radius it was asked for and a volley under what
 * vanilla spends on one critical hit, without a level or a renderer anywhere near it.
 *
 * <p><b>The normal points out of the struck surface</b>, back toward whatever struck it: against a
 * blade's travel for a body, the face normal for a wall, up for the ground. The ring lies square to
 * it, so a blade seen from behind throws a ring that faces its thrower, and the sparks and grit
 * leave inside a cone round it, so nothing flies on into the thing that was hit.
 *
 * <p>Pure: no Minecraft here, same split as {@code Formation} and {@code FrameEase}.
 */
public final class ImpactWave {

    /** Where a ring's puffs are born, in blocks from the point of the hit. */
    public static final double RING_START = 0.15D;

    /** Ticks a ring's puff lives. The spawner sets it on every puff, so the reach below is exact. */
    public static final int RING_LIFE = 9;

    /**
     * {@code ExplodeParticle}'s friction - vanilla's {@code POOF} keeps nine tenths of its velocity
     * each tick. Not ours to tune, so it is stated here rather than chosen.
     */
    public static final double PUFF_FRICTION = 0.9D;

    /** The widest a hit's wave may run, in blocks: about a body and a half across. */
    public static final double MAX_HIT_RADIUS = 1.0D;

    /** Degrees either side of the normal a spark or a crumb may leave at. */
    public static final double SPARK_CONE_DEGREES = 55.0D;

    /**
     * Blocks per tick a spark leaves at. {@code CritParticle} keeps seven tenths a tick, so this is
     * two thirds of a block of flight before its own gravity has it.
     */
    public static final double SPARK_SPEED = 0.2D;

    /** Blocks per tick a crumb of the struck block is thrown at. {@code TerrainParticle} falls fast. */
    public static final double CRUMB_SPEED = 0.22D;

    /** Blocks per tick a trembling crumb hops up off the rim. */
    public static final double TREMOR_HOP = 0.16D;

    /** Upward speed handed to a {@code DUST_PILLAR} crumb, which adds its own scatter to it. */
    public static final double PILLAR_LIFT = 0.35D;

    /**
     * What one hit throws: puffs in the ring, how far the ring runs (zero means the caller names
     * it), sparks, crumbs of the struck block, and how large a puff is drawn.
     */
    public enum Kind {
        /** A blade driven into a body: the commonest hit in the school. */
        CUT(10, 0.70D, 5, 0, 0.8F),
        /** A blade meeting stone and turning for home. */
        CLANG(8, 0.50D, 3, 6, 0.6F),
        /** A shot turned aside by Guard. */
        PARRY(8, 0.55D, 8, 0, 0.6F),
        /** Crown's or Coil's orbit passing through a body. Also throws vanilla's sweep. */
        SHEAR(8, 0.60D, 3, 0, 0.7F),
        /** The air at a greatsword's point as One Blade drives it home. */
        THRUST(12, 0.90D, 0, 0, 0.9F),
        /** Below coming up out of the ground, on the ring that catches. */
        ERUPTION(16, 0.0D, 0, 14, 1.1F),
        /** The ground over Below trembling during the warning, on the ring that catches. */
        TREMOR(3, 0.0D, 0, 8, 0.6F);

        private final int puffs;
        private final double radius;
        private final int sparks;
        private final int crumbs;
        private final float puffScale;

        Kind(int puffs, double radius, int sparks, int crumbs, float puffScale) {
            this.puffs = puffs;
            this.radius = radius;
            this.sparks = sparks;
            this.crumbs = crumbs;
            this.puffScale = puffScale;
        }

        public int puffs() {
            return puffs;
        }

        public double radius() {
            return radius;
        }

        public int sparks() {
            return sparks;
        }

        public int crumbs() {
            return crumbs;
        }

        /**
         * What a puff's own size is multiplied by. {@code ExplodeParticle} rolls anything from 0.1 to
         * 0.7 of a block, which is a fine death poof and a heavy ring round a sword wound.
         */
        public float puffScale() {
            return puffScale;
        }

        /** Born on the rim and staying there, which is what a warning of an edge is. */
        public boolean rim() {
            return this == TREMOR;
        }

        /** Throws vanilla's sweep crescent as well, because an orbit through a body is a sweep. */
        public boolean sweep() {
            return this == SHEAR;
        }

        /** Grit thrown up from beneath, the way a mace smash breaks the ground. */
        public boolean pillar() {
            return this == ERUPTION;
        }

        /**
         * Whether a second hit on the same spot folds into the first. Below's kinds and the thrust
         * are one event each and never land on top of one another.
         */
        public boolean merges() {
            return this == CUT || this == CLANG || this == PARRY || this == SHEAR;
        }

        /** Clamped rather than thrown: the ordinal arrives off the wire. */
        public static Kind byOrdinal(int ordinal) {
            Kind[] kinds = values();
            return kinds[Math.max(0, Math.min(kinds.length - 1, ordinal))];
        }
    }

    private ImpactWave() {
    }

    // ---- the ring -----------------------------------------------------------------------------------

    /**
     * {@code count} unit directions, evenly round the plane square to {@code (nx, ny, nz)},
     * starting {@code phase} radians round.
     */
    public static double[][] ring(int count, double nx, double ny, double nz, double phase) {
        double[][] basis = basis(nx, ny, nz);
        double[] u = basis[1];
        double[] v = basis[2];
        int n = Math.max(1, count);
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double a = phase + 2.0D * Math.PI * i / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            out[i] = new double[] {c * u[0] + s * v[0], c * u[1] + s * v[1], c * u[2] + s * v[2]};
        }
        return out;
    }

    /** The launch speed that carries a puff born at {@link #RING_START} out to {@code radius}. */
    public static double ringSpeed(double radius) {
        return Math.max(0.0D, radius - RING_START) / travelPerUnitSpeed();
    }

    /** How far out a puff launched at {@code speed} ends its life. */
    public static double reach(double speed) {
        return RING_START + speed * travelPerUnitSpeed();
    }

    /** The geometric series a dragged particle covers in its life, per unit of launch speed. */
    private static double travelPerUnitSpeed() {
        return (1.0D - Math.pow(PUFF_FRICTION, RING_LIFE)) / (1.0D - PUFF_FRICTION);
    }

    // ---- sparks and grit ----------------------------------------------------------------------------

    /**
     * A unit direction inside {@link #SPARK_CONE_DEGREES} of the normal, from two uniform numbers in
     * {@code [0, 1)}. Uniform over the cap rather than over the angle, so the sparks do not bunch
     * on the axis.
     */
    public static double[] spark(double nx, double ny, double nz, double a, double b) {
        double[][] basis = basis(nx, ny, nz);
        double[] n = basis[0];
        double[] u = basis[1];
        double[] v = basis[2];
        double cosCone = Math.cos(Math.toRadians(SPARK_CONE_DEGREES));
        double cos = 1.0D - Math.max(0.0D, Math.min(1.0D, a)) * (1.0D - cosCone);
        double sin = Math.sqrt(Math.max(0.0D, 1.0D - cos * cos));
        double phi = 2.0D * Math.PI * b;
        double c = Math.cos(phi) * sin;
        double s = Math.sin(phi) * sin;
        return new double[] {
                cos * n[0] + c * u[0] + s * v[0],
                cos * n[1] + c * u[1] + s * v[1],
                cos * n[2] + c * u[2] + s * v[2]};
    }

    /** How many puffs a hit rings with. An echo rings with none: the first hit's ring is running. */
    public static int puffs(Kind kind, boolean echo) {
        return echo ? 0 : kind.puffs();
    }

    /** How many sparks a hit throws. An echo still throws some, so every blade shows. */
    public static int sparks(Kind kind, boolean echo) {
        if (!echo || kind.sparks() == 0) {
            return kind.sparks();
        }
        return Math.max(1, kind.sparks() / 2);
    }

    /**
     * Motes in the weapon's colour a hit throws, when its sword carries a weapon with one: a
     * forged element, or fire. An echo throws one, and gives up a spark for it
     * ({@link #sparks(Kind, boolean, boolean)}), so a volley of racked blades costs exactly what a
     * volley of plain ones did. The tremor is a warning rather than a hit and carries no weapon.
     */
    public static int accents(Kind kind, boolean echo) {
        if (kind == Kind.TREMOR) {
            return 0;
        }
        return echo ? 1 : Math.max(2, kind.puffs() / 3);
    }

    /** Sparks for a hit that may carry a colour: an accented echo trades one for its mote. */
    public static int sparks(Kind kind, boolean echo, boolean accented) {
        int sparks = sparks(kind, echo);
        return accented && echo ? Math.max(0, sparks - accents(kind, true)) : sparks;
    }

    /** How many crumbs of the struck block a hit throws. An echo throws half. */
    public static int crumbs(Kind kind, boolean echo) {
        if (!echo || kind.crumbs() == 0) {
            return kind.crumbs();
        }
        return Math.max(1, kind.crumbs() / 2);
    }

    /**
     * The normal and two unit vectors spanning the plane square to it, as {@code {n, u, v}}.
     *
     * <p>The helper axis is world up unless the normal is nearly vertical itself, where the cross
     * product would vanish; world X then. A degenerate normal is read as up.
     */
    private static double[][] basis(double nx, double ny, double nz) {
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double[] n = length < 1.0E-9D ? new double[] {0.0D, 1.0D, 0.0D}
                : new double[] {nx / length, ny / length, nz / length};
        double[] helper = Math.abs(n[1]) < 0.9D ? new double[] {0.0D, 1.0D, 0.0D} : new double[] {1.0D, 0.0D, 0.0D};
        double[] u = normalize(cross(helper, n));
        double[] v = cross(n, u);
        return new double[][] {n, u, v};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    // ---- a volley -----------------------------------------------------------------------------------

    /**
     * The client's memory of where it has just rung, so a volley into one body rings once.
     *
     * <p>Up to twelve blades land inside a handful of ticks, and twelve rings of ten puffs on one
     * body is a cloud with a golem somewhere inside it. So a hit within {@link #MERGE_RADIUS} of one
     * that rang in the last {@link #MERGE_TICKS} is an echo: it throws its sparks and no ring. An
     * echo does not refresh the memory, so a body under a steady stream of blades still rings again
     * every few ticks rather than once for good.
     */
    public static final class Ledger {

        /** Blocks within which a second hit is the same hit, visually. About a body's width. */
        public static final double MERGE_RADIUS = 0.9D;

        /** Ticks a ring is remembered for. A volley arrives inside this; the next volley does not. */
        public static final int MERGE_TICKS = 4;

        private static final int MEMORY = 16;

        private final double[] xs = new double[MEMORY];
        private final double[] ys = new double[MEMORY];
        private final double[] zs = new double[MEMORY];
        private final long[] ticks = new long[MEMORY];
        private int size;
        private int next;

        /** True if this hit is an echo of one that just rang; otherwise it is remembered as a ring. */
        public boolean echo(double x, double y, double z, long tick) {
            double reach = MERGE_RADIUS * MERGE_RADIUS;
            for (int i = 0; i < size; i++) {
                long age = tick - ticks[i];
                if (age < 0L || age > MERGE_TICKS) {
                    continue;
                }
                double dx = x - xs[i];
                double dy = y - ys[i];
                double dz = z - zs[i];
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    return true;
                }
            }
            xs[next] = x;
            ys[next] = y;
            zs[next] = z;
            ticks[next] = tick;
            next = (next + 1) % MEMORY;
            size = Math.min(MEMORY, size + 1);
            return false;
        }
    }
}
