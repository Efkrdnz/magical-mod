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
    public static final float OWN_SIGIL_BASE = 0.16F;

    /** How much larger the caster's own sigil at the hand is drawn for each tier. */
    public static final float OWN_SIGIL_PER_TIER = 0.025F;

    /** The caster's own circle in first person, as an opacity. Everybody else sees it whole. */
    public static float ownCircleOpacity(CircleAnchor anchor) {
        return switch (anchor) {
            case EYE_FORWARD -> 0.6F;
            case GROUND -> 0.3F;
            default -> 1.0F;
        };
    }

    /** How near the camera a windup has to be to count as the viewer's own cast. */
    public static final double OWN_CIRCLE_REACH = 2.5D;

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

    /** Puffs in the ring round a heavy impact. */
    public static int puffCount(int tier, float scale) {
        return heavy(tier, scale) ? 6 + 2 * clampTier(tier) : 0;
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
