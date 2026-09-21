package com.efkrdnz.magical.magic.sword;

/**
 * What a rung of the chain permits, as four caps and four booleans.
 *
 * <p>Each of the four rungs flips <b>exactly one rule of the structure</b> and nothing else is the
 * headline. That is the whole reason the chain is four rungs rather than five: every rung has to
 * be describable in one clause, or the ladder is padding.
 *
 * <ul>
 *   <li><b>Summoner</b> - the Array exists, and nothing is unlocked. Four stations at two Edge and
 *       reach three is {@code 4 * 3 * 2 = 24}, <em>exactly</em> the draw, so the tension is real in
 *       the first minute and the game never says so in words.
 *   <li><b>Rider</b> - {@link #worldOrigin}. The frame's origin comes off the wielder's body.
 *   <li><b>Saint</b> - {@link #coincidence} and {@link #freeScale}, which are one rung because they
 *       are one permission: the shape may stop being the shape you wrote.
 *   <li><b>God</b> - {@link #overdraw}. This rung <em>removes</em> a rule and grants no new active.
 * </ul>
 *
 * <p>{@link #whole} rides here rather than on {@code SwordArray} so there is one source of truth
 * for what a rung is worth; the Array reads it through {@code SwordArray.whole()}.
 */
public record SwordRules(int maxStations, int draw, int maxEdge, int whole,
                         boolean worldOrigin, boolean freeScale,
                         boolean coincidence, boolean overdraw) {

    /** Tier 0. The frame is your body centre and your look, at scale 1, and it never is not. */
    public static final SwordRules SUMMONER =
            new SwordRules(4, 24, 3, 8, false, false, false, false);

    /** Tier 1. The origin unbinds from your body: it may be frozen, ridden, or sunk under a point. */
    public static final SwordRules RIDER =
            new SwordRules(7, 40, 5, 16, true, false, false, false);

    /** Tier 2. Blades may share a bearing and the scale may move, so your opponent spends your budget. */
    public static final SwordRules SAINT =
            new SwordRules(10, 64, 12, 26, true, true, true, false);

    /** Tier 3. Exceeding the draw is legal, and at {@code strain >= draw} the whole Array lets go. */
    public static final SwordRules GOD =
            new SwordRules(12, 84, 36, 36, true, true, true, true);

    private static final SwordRules[] RUNGS = {SUMMONER, RIDER, SAINT, GOD};

    /** Clamps, because a rung is read off class progress and a missing class must still be a rung. */
    public static SwordRules forRung(int rung) {
        return RUNGS[Math.max(0, Math.min(RUNGS.length - 1, rung))];
    }

    /** How many rungs there are. Four, and {@code ClassTreeLayout} cannot lay out a fifth. */
    public static int rungs() {
        return RUNGS.length;
    }
}
