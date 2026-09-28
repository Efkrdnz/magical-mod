package com.efkrdnz.magical.magic.visual;

/**
 * Every number the matter layer uses, in one pure place so a test can hold it.
 *
 * <p>Two jobs. The first is how many real particles a cue throws, by tier and by the scale the
 * server sent. The second is how much lighter the shader pass gets once the matter has moved out
 * of it - and that second job is the one players see. The legacy impact bloomed a white disc
 * {@code 1.6 x flashSize} blocks across ({@value #LEGACY_FLASH_SCALE}; over three blocks at tier
 * four), stamped a second glyph circle on top, and threw up to sixty additive motes; the caster's
 * own cast circle, drawn at their feet or a block in front of their eyes, filled half or all of a
 * first-person frame. Every factor below exists because a before-and-after capture of the whole
 * roster said the old number was the mess.
 */
public final class AccentPlan {

    /** The legacy impact flash, as a multiple of the profile's {@code flashSize}. */
    public static final float LEGACY_FLASH_SCALE = 1.6F;

    /** The impact flash once the matter carries the hit: a spark at the point, not a sun. */
    public static final float FLASH_SCALE = 0.7F;

    /** How long an accented impact flash lives. The legacy one lived ten ticks. */
    public static final int FLASH_TICKS = 6;

    /** The accented flash's opacity: under one so a bright palette keeps its hue over daylight. */
    public static final float FLASH_OPACITY = 0.75F;

    /** The share of the legacy additive matter burst an accented cue still throws. */
    public static final float SHADER_MATTER_SHARE = 0.3F;

    /** The muzzle flash, as a share of the legacy size. */
    public static final float MUZZLE_SCALE = 0.55F;

    /** The hand orb inside a windup, as a share of the legacy size. */
    public static final float HAND_ORB_SCALE = 0.6F;

    /** How long an accented ground mark lingers, at most. The legacy one lived 30 + 30 x tier. */
    public static final int MARK_TICKS_CAP = 40;

    /**
     * The radius the caster's own circle is drawn at in first person, by where it is anchored.
     *
     * <p>A circle anchored 0.9 of a block in front of the eyes is a sigil at the hand, not a wall
     * across the view - and a factor on its radius cannot make it one, because the view is only
     * about 0.9 of a block tall at that distance and a tier-four circle is three blocks across. So
     * it gets a radius of its own, {@value #OWN_SIGIL_BASE} plus {@value #OWN_SIGIL_PER_TIER} a
     * tier, which keeps the tiers apart and every one of them under a third of the frame. A circle
     * at the feet is drawn at half its radius and faint ({@link #ownCircleOpacity}): 1.6 blocks
     * under the eye, anything wider reaches up into the bottom of a level view, and the first pass
     * at 0.7 of it and 0.4 opacity still stacked its additive layers into a white dome across the
     * bottom third of every ground-anchored cast. Looking down, the caster still sees it.
     */
    public static float ownCircleRadius(CircleAnchor anchor, float radius, int tier) {
        return switch (anchor) {
            case EYE_FORWARD -> Math.min(radius, OWN_SIGIL_BASE + OWN_SIGIL_PER_TIER * clampTier(tier));
            case GROUND -> radius * 0.5F;
            default -> radius;
        };
    }

    /** The caster's own sigil at the hand, at tier zero. */
    public static final float OWN_SIGIL_BASE = 0.09F;

    /** How much larger the caster's own sigil at the hand is drawn for each tier. */
    public static final float OWN_SIGIL_PER_TIER = 0.015F;

    /**
     * The caster's own circle in first person, as an opacity. Everybody else sees it whole. The
     * sigil is additive, so even at 0.6 it came out pure white over a daylight floor, the brightest
     * thing in the frame.
     */
    public static float ownCircleOpacity(CircleAnchor anchor) {
        return switch (anchor) {
            case EYE_FORWARD -> 0.35F;
            case GROUND -> 0.3F;
            default -> 1.0F;
        };
    }

    /**
     * Where the caster's own sigil sits, as a share of its distance from the eye: this far to the
     * right of the line of sight and {@link #OWN_SIGIL_DOWN} below it, which is the hand's corner of
     * the frame. Drawn on the line of sight it covered the crosshair and whatever was being aimed at.
     */
    public static final float OWN_SIGIL_RIGHT = 0.33F;

    /** See {@link #OWN_SIGIL_RIGHT}. */
    public static final float OWN_SIGIL_DOWN = 0.25F;

    /** The glow at the middle of the caster's own sigil, as an opacity. */
    public static final float OWN_HAND_ORB_OPACITY = 0.3F;

    /**
     * How much further along the aim the caster's own release matter is thrown from, in their own
     * view (SpellAccents.release). From the hand it landed on their crosshair; not thrown at all,
     * no cast read as leaving the hand.
     */
    public static final double OWN_RELEASE_PUSH = 1.5D;

    /**
     * The mark a hit lays on the ground, as a share of the legacy mark's size and opacity. At tier
     * three and scale 1.6 the legacy mark was a starburst nearly five blocks across at 0.8, which
     * over a daylight floor is a white sheet; the hit is the flash and the matter now.
     */
    public static final float MARK_SCALE = 0.6F;

    /** See {@link #MARK_SCALE}. */
    public static final float MARK_OPACITY = 0.5F;

    /**
     * A tint lifted toward white until its luminance reaches {@code floor}, keeping its hue; one
     * already that bright is returned as it is. A mote or a rune is full-bright light, and tinted
     * with a dark school's colour (the Void school's bright is a deep purple) it drew as a black
     * speck against the sky.
     */
    public static int lift(int rgb, float floor) {
        int c = rgb & 0xFFFFFF;
        float luminance = Palette.luminance(c);
        if (luminance >= floor) {
            return c;
        }
        return Palette.mix(c, 0xFFFFFF, (floor - luminance) / (1.0F - luminance));
    }

    /** The least a light sprite (a rune, a mote) may be, as a luminance: see {@link #lift}. */
    public static final float LIGHT_SPRITE_FLOOR = 0.55F;

    /** The least a shard may be: stuff can be dark, but not a hole in the daylight. */
    public static final float SHARD_FLOOR = 0.3F;

    /**
     * The least a wisp may be. A wisp is smoke and keeps the hue it is handed, but the dim colour of
     * a dark school is near black, and once a hit on a body threw its matter off the side the viewer
     * sees, the smoke of three schools hung in front of every target as dark translucent cards.
     */
    public static final float WISP_FLOOR = 0.3F;

    /** How opaque a wisp is born; it thins to nothing over its life. Smoke you can see through. */
    public static final float WISP_OPACITY = 0.5F;

    /**
     * How near a first-person camera a particle of the matter layer may be: none is born nearer, and
     * one of the mod's sprites that drifts nearer goes. A sprite a tenth of a block across fills a
     * fifth of the view from half a block away, and a cast that threw anything round its own caster -
     * a wake leaving the hand, a pulse at the feet rising past the eyes - put cards across the screen.
     */
    public static final double SPRITE_NEAR_CAMERA = 1.25D;

    /** How far outside a struck body's side its matter is thrown from, toward whoever is looking. */
    public static final double BODY_FACE_GAP = 0.15D;

    /** How near the camera a windup has to be to count as the viewer's own cast. */
    public static final double OWN_CIRCLE_REACH = 2.5D;

    /**
     * The caster's own muzzle flash, as a radius: a spark at the hand.
     *
     * <p>{@code SpellFx.release} hangs it {@value #HAND_REACH} of a block ahead of the eyes, where
     * the view is barely more than a block tall, so the {@link #MUZZLE_SCALE} share of the old
     * flash was still a white star across a third of the frame at the crosshair. Everybody else
     * sees it from where they stand and keeps that size.
     */
    public static float ownMuzzleRadius(int tier) {
        return OWN_MUZZLE_BASE + OWN_MUZZLE_PER_TIER * clampTier(tier);
    }

    /** The caster's own muzzle flash at tier zero. */
    public static final float OWN_MUZZLE_BASE = 0.08F;

    /** How much larger the caster's own muzzle flash is for each tier. */
    public static final float OWN_MUZZLE_PER_TIER = 0.015F;

    /** The caster's own muzzle flash, as an opacity. */
    public static final float OWN_MUZZLE_OPACITY = 0.5F;

    /** How far along the aim from the eyes {@code SpellFx.release} puts the hand it sends the release from. */
    public static final double HAND_REACH = 0.8D;

    /** How far under the eyes {@code SpellFx.release} puts that hand. */
    public static final double HAND_DROP = 0.2D;

    /** A standing player's eye height: what takes a slam's flash from the hand back down to the floor. */
    public static final double EYE_HEIGHT = 1.62D;

    /**
     * A slam's flash seen by the one who slammed: the rule the circle at their feet already keeps,
     * half the size and faint. The flash is a flat disc {@code 1.3} tier radii across, and it used
     * to be laid at the hand, 0.2 of a block under the eye, where it was a white sheet over the whole
     * lower half of the frame for every one of the slam skills.
     */
    public static final float OWN_SLAM_SCALE = 0.5F;

    /** The opacity of a slam's flash seen by the one who slammed. */
    public static final float OWN_SLAM_OPACITY = 0.3F;

    /** The scale the server sent, held to a range a particle count can sensibly follow. */
    public static float clampScale(float scale) {
        return Math.max(0.5F, Math.min(2.0F, scale));
    }

    /** Particles in the body of an impact: the element's own matter. */
    public static int impactCount(int tier, float scale) {
        return Math.round((5 + 3 * clampTier(tier)) * clampScale(scale));
    }

    /** Crumbs of the struck block, when an impact lands on terrain. */
    public static int crumbCount(int tier, float scale) {
        return Math.round((4 + 2 * clampTier(tier)) * clampScale(scale));
    }

    /** Whether an impact is heavy enough for vanilla's explosion sprite and a ring of puffs. */
    public static boolean heavy(int tier, float scale) {
        return clampTier(tier) >= 3 || scale >= 1.5F;
    }

    /**
     * True when a heavy hit lands where another one already blew up a moment ago, and should ring
     * as an ordinary hit. Every tier-three hit is heavy, so a skill that bites one body every ten
     * ticks, or a chain of detonations at one spot, stacked an explosion sprite and a ring of puffs
     * on every beat; a volley into one body blasts once, the way a sword volley rings once.
     */
    public static boolean blastEchoes(long ticksSince, double distanceSqr) {
        return ticksSince >= 0 && ticksSince < BLAST_ECHO_TICKS && distanceSqr < BLAST_ECHO_RADIUS * BLAST_ECHO_RADIUS;
    }

    /** How long after a blast another heavy hit near it is only an ordinary one. */
    public static final int BLAST_ECHO_TICKS = 20;

    /** How near a blast another heavy hit has to be to count as its echo. */
    public static final double BLAST_ECHO_RADIUS = 3.0D;

    /**
     * No blast within this reach of a first-person camera: vanilla's explosion sprite is a block
     * and a half across, and one at the caster's own feet or hand fills the view.
     */
    public static final double OWN_BLAST_REACH = 3.0D;

    /** Puffs in the ring round a heavy impact. */
    public static int puffCount(int tier, float scale) {
        // a poof is as big as a block face: a ring of eight reads as a ring, fourteen as a wall
        return heavy(tier, scale) ? 4 + clampTier(tier) : 0;
    }

    /** Particles thrown out of the hand as a spell leaves it. */
    public static int releaseCount(int tier) {
        return 3 + clampTier(tier);
    }

    /** Runes lifting off a windup circle into the hand. The legacy shader motes were 3 + tier. */
    public static int windupRunes(int tier) {
        return 2 + (clampTier(tier) + 1) / 2;
    }

    /** Particles rising out of a zone each time it pulses, by its radius in blocks. */
    public static int zoneCount(float radius) {
        return Math.max(2, Math.min(8, Math.round(radius * 1.2F)));
    }

    /** Fragments off a barrier that is struck. */
    public static final int BARRIER_SHARDS = 4;

    /**
     * The slowest a profile entity may move, in blocks a tick, and still trail: a thrown or flying
     * spell, not a summon walking about or a field drifting with its caster.
     */
    public static final double TRAIL_MIN_SPEED = 0.25D;

    /** Trail particles a tick, by speed: one, then one more for each further block a tick. */
    public static int trailCount(double speed) {
        if (speed < TRAIL_MIN_SPEED) {
            return 0;
        }
        return Math.min(3, 1 + (int) speed);
    }

    /** A negative tier is the forbidden layers and the Authorities: read as the top of the table. */
    public static int clampTier(int tier) {
        return tier < 0 ? 4 : Math.min(4, tier);
    }

    private AccentPlan() {
    }
}
