package com.efkrdnz.magical.entity.ascendant;

/**
 * Whether an opponent should be in the air right now, and how high it may go.
 *
 * <p>Being able to fly is not the interesting part - the player has had it for a long time, and an
 * opponent that could not simply lost to it. What matters is that flight stays a tool rather than a
 * state. A boss that pillars into the sky is not harder, and a boss that only leaves the ground when
 * you do can be grounded by standing still, which is the same exploit wearing a different coat.
 *
 * <p>So the decision is four questions asked in order, and the first "no" lands it. Pure functions
 * of a described situation, with no Minecraft types, so the rules can be pinned by a plain test
 * rather than discovered in a fight.
 */
public final class OpponentFlight {

    /**
     * Highest an opponent may climb above its target, and the whole of the anti-skybox rule.
     *
     * <p>An earlier draft also capped altitude relative to the ground below, on the theory that
     * this was what stopped an opponent pillaring into the sky. It did not: a cap measured from the
     * ground has to be overridden whenever the target is somewhere high, or a player on a tower
     * becomes unreachable, and that override wins in every case where the ground cap would have
     * bound. It was dead arithmetic dressed as a safeguard.
     *
     * <p>What actually holds is this one bound. An opponent can never be more than nine blocks
     * above you, so it can only get as high as you take it - it never climbs on its own, and it
     * always follows when you leave the ground.
     */
    public static final double MAX_ABOVE_TARGET = 9.0D;

    /** Below this share of its pool an opponent lands and saves the rest for spells. */
    public static final float MANA_RESERVE = 0.25F;

    private OpponentFlight() {}

    /**
     * Everything the decision reads.
     *
     * @param stance             how this tier prefers to fight
     * @param manaFraction       current mana over max, 0..1
     * @param targetGrounded     whether the target is standing on something
     * @param wantsRange         it has a ranged option and the target is inside its preferred minimum
     * @param underMeleePressure it was struck in melee recently and wants out
     * @param hasGroundPath      a walkable route to the target exists
     */
    public record Situation(AscendantStance stance, float manaFraction, boolean targetGrounded,
            boolean wantsRange, boolean underMeleePressure, boolean hasGroundPath) {}

    /**
     * The four gates.
     *
     * <p>Fuel first, because a boss that spends its pool hovering cannot cast and stops being a
     * fight. Then a concrete reason. Then, only if there is none, the target's own stance - which is
     * a preference and not a leash: every reason in {@link #hasReason} still overrides it.
     */
    public static boolean shouldFly(Situation situation) {
        if (situation.manaFraction() < MANA_RESERVE) {
            return false;
        }
        if (hasReason(situation)) {
            return true;
        }
        if (situation.targetGrounded()) {
            // Nothing to gain by being up there, and everything to gain by being reachable.
            return false;
        }
        return true;
    }

    /**
     * A reason it can name for leaving the ground.
     *
     * <p>A stance that holds the air counts as its own reason, which is why tiers 8 and 10 kite and
     * the rest do not.
     */
    public static boolean hasReason(Situation situation) {
        return situation.underMeleePressure()
                || situation.wantsRange()
                || !situation.hasGroundPath()
                || situation.stance().holdsAir();
    }

    /** The highest Y this opponent may occupy: yours, plus {@link #MAX_ABOVE_TARGET}. */
    public static double ceiling(double targetY) {
        return targetY + MAX_ABOVE_TARGET;
    }

    /**
     * The altitude it settles at while airborne, already clamped to {@link #ceiling}.
     *
     * <p>An opponent whose chosen skill is a close-range one drops to the target's own level instead,
     * so it cannot hover untouchably while poking.
     */
    public static double desiredY(Situation situation, double targetY, boolean wantsMelee) {
        if (wantsMelee) {
            return targetY;
        }
        return Math.min(targetY + situation.stance().preferredAltitude(), ceiling(targetY));
    }
}
