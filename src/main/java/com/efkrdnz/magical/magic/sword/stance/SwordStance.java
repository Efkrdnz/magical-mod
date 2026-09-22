package com.efkrdnz.magical.magic.sword.stance;

import java.util.Locale;

/**
 * The six formations, and the whole of what a Sword Summoner chooses.
 *
 * <p>The class used to be authored: a wielder wrote one bearing per press from their crosshair
 * onto a 24 x 9 x 6 lattice, and every interesting thing the kit did was a projection of that
 * shape. It was coherent and it was unreadable, because half the bearings sat behind the wielder's
 * head and none of them could be seen while they were being placed. So the formations are
 * <b>designed</b> now, six of them, and picking one is the whole interaction.
 *
 * <p>Each constant carries four decisions and nothing else:
 *
 * <ul>
 *   <li>{@link Anchor} - whether the formation's <em>position</em> follows the wielder's look or
 *       only their body yaw. This is not cosmetic: a Crown anchored to the look swings under the
 *       wielder's feet the moment they glance down, which is the single most obvious way to get a
 *       stance wrong and the reason the field exists at all.
 *   <li>{@link Facing} - which way the blades point, which is a separate question from where they
 *       are. Guard sits behind the shoulders and points <em>forward</em>; collapsing the two
 *       fields into one would make that impossible to say.
 *   <li>{@link Watch} - what the swords do with nobody pressing anything.
 *   <li>{@link Pattern} - the shape Loose and Below take while this stance is held.
 *   <li>{@link #swords} - how many blades it fields. See below.
 *   <li>{@link #followHalfLife} - how tightly it tracks the body. See below.
 * </ul>
 *
 * <p><b>A stance caps its own complement, and that cap is a real cost.</b> Every stance used to
 * field the rung's whole count and it made two of them worse: Guard's arc is 250 degrees of a
 * 1.25-block radius, so twelve blades sit 0.50 apart on it - barely over
 * {@code Formation.MIN_SEPARATION} - and it reads as a thicket rather than a guard, which is
 * exactly the complaint it drew. So Guard fields six and Vanguard five, and the swords that are
 * not fielded are <em>not</em> held in reserve: they do not exist while that stance is held, so
 * they do not widen the pool through Sword Heart and they are not there for Loose or One Blade
 * to spend. A tight silhouette buys legibility and pays for it in steel, which is a trade a
 * wielder can make on purpose. Rain fields all twelve - a scattered disc is the one shape that
 * reads better full - so the apex complement is still reachable and {@code SwordMath}'s cap is
 * still a number somebody can hit.
 *
 * <p><b>The half-life is how long the formation takes to catch up with a turn</b>, in ticks, and
 * it is per stance because the stances want different things from it. Vanguard is a cluster on
 * the aim line and has to stay on the crosshair, so it is nearly rigid; Rain hangs eight feet up
 * and should swing like something heavy. See {@code FrameEase}.
 *
 * <p>Declaration order is the unlock order and {@code SwordRules.stances()} is a count taken off
 * the front of it, so <b>a stance may not be reordered</b> without moving which rung owns it. The
 * ordinal is also what goes on the wire and into the save, which is normally the thing this mod
 * refuses to write - {@code Weave} stores enum names for exactly this reason. It is an ordinal
 * here because the set is six, closed, and the failure is recoverable in one keypress: a
 * renumbering would put a wielder in the wrong posture, not in a corrupted one, and
 * {@code SwordArray.load} clamps anything out of range back to {@link #GUARD}.
 */
public enum SwordStance {

    /**
     * Over the shoulders and behind the head, points forward. The defensive posture, and the one
     * the class starts in because it is the one whose behaviour needs no explaining: things that
     * come at you get hit by a sword.
     */
    GUARD(Anchor.BODY, Facing.LOOK, Watch.INTERCEPT, Pattern.LINE, 0, 6, 3.0D),

    /** A cluster in front at eye level, all pointing exactly where you look. Pressure at range. */
    VANGUARD(Anchor.LOOK, Facing.LOOK, Watch.STAB, Pattern.COLUMN, 0, 5, 1.2D),

    /** A turning ring above the head, points outward. Close ground denial. */
    CROWN(Anchor.BODY, Facing.OUTWARD, Watch.SHEAR, Pattern.RING, 1, 10, 4.0D),

    /** Two swept-back fans at the shoulders, angled up. Mobility. */
    WINGS(Anchor.BODY, Facing.ALONG, Watch.GLIDE, Pattern.FAN, 1, 8, 2.5D),

    /** A fast level orbit at the waist, points outward. A bodyguard you cannot walk through. */
    COIL(Anchor.BODY, Facing.OUTWARD, Watch.SHRED, Pattern.SPRAY, 2, 10, 2.0D),

    /** High overhead on a scattered disc, points straight down. Waiting to fall on something. */
    RAIN(Anchor.BODY, Facing.DOWN, Watch.DROP, Pattern.FALL, 2, 12, 5.0D);

    /** Whether the formation's position turns with the wielder's look or only with their body. */
    public enum Anchor {

        /** Yaw only. The formation stays level however the wielder tilts their head. */
        BODY,

        /** Yaw and pitch. The formation is rigid to the aim line. */
        LOOK
    }

    /** Which way a blade points, which is a different question from where the blade is. */
    public enum Facing {

        /** Wherever the wielder is looking, pitch included. */
        LOOK,

        /** Radially out from the formation's own vertical axis. */
        OUTWARD,

        /** Straight down. */
        DOWN,

        /** Along the slot's own sweep, out from the root it grew from. */
        ALONG
    }

    private static final SwordStance[] ORDER = values();

    private final Anchor anchor;
    private final Facing facing;
    private final Watch watch;
    private final Pattern pattern;
    private final int rung;
    private final int swords;
    private final double followHalfLife;

    SwordStance(Anchor anchor, Facing facing, Watch watch, Pattern pattern, int rung,
            int swords, double followHalfLife) {
        this.anchor = anchor;
        this.facing = facing;
        this.watch = watch;
        this.pattern = pattern;
        this.rung = rung;
        this.swords = swords;
        this.followHalfLife = followHalfLife;
    }

    public Anchor anchor() {
        return anchor;
    }

    public Facing facing() {
        return facing;
    }

    public Watch watch() {
        return watch;
    }

    public Pattern pattern() {
        return pattern;
    }

    /** The rung that opens it: 0 Summoner, 1 Rider, 2 Saint. Nothing waits for Sword God. */
    public int rung() {
        return rung;
    }

    /** The most blades this shape carries, whatever the rung fields. See the class note. */
    public int swordCap() {
        return swords;
    }

    /**
     * How many blades this stance fields for a wielder whose rung allows {@code complement}.
     *
     * <p>The one place the cap is applied, so nothing anywhere else has to remember it: this is
     * what {@code SwordArray.swords} answers with and therefore what the mask, the pool, the
     * volley and the picker's diagram are all counted in.
     */
    public int swords(int complement) {
        return Math.max(1, Math.min(complement, swords));
    }

    /** Ticks for the formation to close half the angle between itself and the body. */
    public double followHalfLife() {
        return followHalfLife;
    }

    /** {@code stance.magical.<key>} and {@code .desc}. Lower case, no namespace. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Its name, for the picker, the refusal and the codex. */
    public String nameKey() {
        return "stance.magical." + key();
    }

    /** One line on what it does, drawn under the name in the picker. */
    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    /** The default and the fallback, named once so nothing else has to know it is the first. */
    public static SwordStance first() {
        return GUARD;
    }

    /** Total: an ordinal off the wire or out of a hand-edited save clamps to {@link #first()}. */
    public static SwordStance byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : first();
    }

    /** By name, case-insensitively, for the command. Null when nothing answers to it. */
    public static SwordStance byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        for (SwordStance stance : ORDER) {
            if (stance.key().equals(wanted)) {
                return stance;
            }
        }
        return null;
    }

    /** How many there are. Six, and {@code SwordStanceLayout} cannot lay out a seventh. */
    public static int count() {
        return ORDER.length;
    }
}
