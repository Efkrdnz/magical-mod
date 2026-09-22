package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;

/**
 * What a rung of the chain permits: how many swords, and how many stances.
 *
 * <p>Each rung still flips <b>exactly one rule</b> and nothing else is the headline, which is the
 * whole reason the chain is four rungs rather than five - every rung has to be describable in one
 * clause or the ladder is padding. What changed is that the clauses are now about the swords
 * rather than about a budget, because the budget is gone: there is no draw, no bill, no strain and
 * no divisible measure of Edge, only a count of steel the wielder can see orbiting their own head.
 *
 * <ul>
 *   <li><b>Summoner</b> - four swords, a stance, and the swords act on their own. Guard and
 *       Vanguard.
 *   <li><b>Rider</b> - {@link #worldOrigin}. The frame's origin comes off the wielder's body,
 *       which is the Keel. Crown and Wings.
 *   <li><b>Saint</b> - Coil and Rain, and every stance is open.
 *   <li><b>God</b> - {@link #relentless}. This rung <em>removes</em> a limit rather than adding a
 *       verb: every {@code Watch} interval is halved and so is every return clock.
 * </ul>
 *
 * <p>{@link #stances} is a count taken off the front of {@link SwordStance}'s declaration order,
 * so <b>reordering that enum silently moves which rung owns which stance</b>. That is stated on
 * the enum as well, in both directions, because a count-off-the-front is the cheapest way to
 * express this and the only way it can go wrong.
 */
public record SwordRules(int swords, int stances, boolean worldOrigin, boolean relentless) {

    /** Tier 0. Four swords, two stances, and the frame is your body and your look. */
    public static final SwordRules SUMMONER = new SwordRules(4, 2, false, false);

    /** Tier 1. Seven, and the origin unbinds from your body: it may be frozen, ridden or sunk. */
    public static final SwordRules RIDER = new SwordRules(7, 4, true, false);

    /** Tier 2. Ten, and every stance is open. */
    public static final SwordRules SAINT = new SwordRules(10, 6, true, false);

    /** Tier 3. Twelve, and nothing waits: every clock in the kit runs at double speed. */
    public static final SwordRules GOD = new SwordRules(12, 6, true, true);

    private static final SwordRules[] RUNGS = {SUMMONER, RIDER, SAINT, GOD};

    /** Clamps, because a rung is read off class progress and a missing class must still be one. */
    public static SwordRules forRung(int rung) {
        return RUNGS[Math.max(0, Math.min(RUNGS.length - 1, rung))];
    }

    /** How many rungs there are. Four, and {@code ClassTreeLayout} cannot lay out a fifth. */
    public static int rungs() {
        return RUNGS.length;
    }

    /** Whether this rung has opened that stance. Null is never allowed, so null is never open. */
    public boolean allows(SwordStance stance) {
        return stance != null && stance.ordinal() < stances;
    }

    /**
     * The stance this rung would put a wielder in who is holding one it has not opened.
     *
     * <p>Which happens on a reset and on any read of a save written at a higher rung, so it is a
     * fall back to {@link SwordStance#first()} rather than a refusal: a wielder must always be in
     * some posture, and the first one is the one every rung has.
     */
    public SwordStance clamp(SwordStance stance) {
        return allows(stance) ? stance : SwordStance.first();
    }
}
