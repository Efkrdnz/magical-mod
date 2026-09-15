package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.MagicSkillTuning;

/**
 * What a Blood Sacrifice may buy, what it must pay, and what a Hellbroker's interest comes to.
 *
 * <p>Pure arithmetic with no state behind it, so the pick screen, the sealing service and the tests
 * all read the same numbers. A pact the screen offers and the server refuses would be the worst
 * failure this feature has, and one file with no branches is the cheapest way to make it impossible.
 */
public final class SacrificeBudget {

    /** Points a pact starts with before the skill is tuned at all. */
    public static final int BASE_BOON_POINTS = 4;

    /**
     * Points of {@code size} that still buy a boon point.
     *
     * <p>Capped because the tuning budget climbs to eleven with proficiency, and an uncapped budget
     * would let a maxed player take every boon at once - which is not a pact, it is a menu.
     */
    public static final int SIZE_POINTS_CAP = 4;

    /** What each price past the first adds to the multiplier, and where the multiplier stops. */
    private static final float AMPLIFICATION_STEP = 0.5F;
    private static final float AMPLIFICATION_CAP = 2.25F;

    private SacrificeBudget() {
    }

    /** How many points of boons this pact may spend. */
    public static int boonBudget(MagicSkillTuning tuning, boolean hellbroker) {
        return BASE_BOON_POINTS + Math.min(SIZE_POINTS_CAP, tuning.size()) + (hellbroker ? 1 : 0);
    }

    /**
     * How many points of prices the pact owes for what it spent.
     *
     * <p>A Hellbroker's point is genuinely free: it raises the budget and lowers the bill, so the
     * extra boon does not quietly need a matching price of its own.
     */
    public static int priceRequired(int boonSpent, boolean hellbroker) {
        return Math.max(0, boonSpent - (hellbroker ? 1 : 0));
    }

    /**
     * The multiplier every amplified price wears while a Hellbroker holds the pact.
     *
     * <p>Linear and capped rather than the compound curve the idea started as. {@code 1.5^(n-1)}
     * turns a 15% chance to fail a cast into 50.6% at four prices and 75.9% at five, which is not a
     * build but a disconnected mouse; this reaches the same 1.5x at two prices and stops at 2.25x.
     */
    public static float amplification(int activePrices, boolean hellbroker) {
        if (!hellbroker || activePrices <= 1) {
            return 1.0F;
        }
        return Math.min(AMPLIFICATION_CAP, 1.0F + AMPLIFICATION_STEP * (activePrices - 1));
    }

    /**
     * One amplified magnitude, held to the entry's own ceiling: a chance, a count of blood, a number
     * of health. These start at zero and grow, so the multiplier applies to the whole number.
     */
    public static float amplify(float magnitude, float clamp, int activePrices, boolean hellbroker) {
        return Math.min(clamp, magnitude * amplification(activePrices, hellbroker));
    }

    /**
     * One amplified multiplier, held to the entry's own ceiling.
     *
     * <p>What amplifies is the distance from one, not the multiplier itself. Glass Bones makes a hit
     * land at 1.2x; the price is the 0.2, and doubling the price means 1.4x, not 2.4x. Scaling the
     * whole number would double a hit that was only ever meant to be a fifth worse.
     *
     * <p>The clamp is two-sided for the same reason. Open Wound scales healing to a half, so its
     * distance from one runs downward and its ceiling is really a floor.
     */
    public static float amplifyMultiplier(float multiplier, float clamp, int activePrices, boolean hellbroker) {
        float scaled = 1.0F + (multiplier - 1.0F) * amplification(activePrices, hellbroker);
        return multiplier <= clamp ? Math.min(clamp, scaled) : Math.max(clamp, scaled);
    }
}
