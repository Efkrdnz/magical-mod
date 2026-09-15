package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.MagicSkillTuning;
import org.junit.jupiter.api.Test;

/**
 * The whole economy of a pact is six lines of arithmetic, so all six are pinned here.
 *
 * <p>The screen and the server both read these, and a pact the screen offers that the server
 * refuses is the one bug this file exists to make impossible.
 */
class SacrificeBudgetTest {

    private static MagicSkillTuning sized(int size) {
        return new MagicSkillTuning(0, 0, size, 0, 0);
    }

    @Test
    void theBudgetStartsAtFourAndBuysOnePointPerPointOfSize() {
        assertEquals(4, SacrificeBudget.boonBudget(sized(0), false));
        assertEquals(6, SacrificeBudget.boonBudget(sized(2), false));
        assertEquals(8, SacrificeBudget.boonBudget(sized(4), false));
    }

    @Test
    void sizeStopsPayingAfterFour() {
        // The tuning budget climbs to eleven with proficiency. Uncapped, a maxed player buys every
        // boon at once and the choice the screen exists for stops being a choice.
        assertEquals(8, SacrificeBudget.boonBudget(sized(11), false), "capped at four points of size");
    }

    @Test
    void hellbrokerIsWorthOneBoonPointAndOneForgivenPrice() {
        assertEquals(9, SacrificeBudget.boonBudget(sized(4), true));
        assertEquals(5, SacrificeBudget.priceRequired(5, false), "without it you pay for what you spend");
        assertEquals(4, SacrificeBudget.priceRequired(5, true), "with it, one point is on credit");
        assertEquals(0, SacrificeBudget.priceRequired(0, true), "and credit never goes negative");
    }

    @Test
    void amplificationIsOneUntilTheSecondPriceAndStopsAtTheFourth() {
        assertEquals(1.0F, SacrificeBudget.amplification(1, true), 0.0001F);
        assertEquals(1.5F, SacrificeBudget.amplification(2, true), 0.0001F, "half again, at two prices");
        assertEquals(2.0F, SacrificeBudget.amplification(3, true), 0.0001F);
        assertEquals(2.25F, SacrificeBudget.amplification(4, true), 0.0001F);
        assertEquals(2.25F, SacrificeBudget.amplification(9, true), 0.0001F, "and it stops there");
    }

    @Test
    void theCurveIsLinearRatherThanCompound() {
        // 1.5^(n-1) reaches 5.06x at five prices, which turns a 15% spell failure into 76% and the
        // game into a disconnected mouse. Linear and capped gets to the same 1.5x at two.
        assertEquals(1.5F, SacrificeBudget.amplification(2, true), 0.0001F);
        assertEquals(2.25F, SacrificeBudget.amplification(5, true), 0.0001F, "not 5.06");
    }

    @Test
    void withoutHellbrokerNothingStacks() {
        assertEquals(1.0F, SacrificeBudget.amplification(5, false), 0.0001F);
    }

    @Test
    void amplifyNeverPassesTheEntrysOwnCeiling() {
        assertEquals(0.15F, SacrificeBudget.amplify(0.15F, 0.3375F, 1, true), 0.0001F, "one price is unchanged");
        assertEquals(0.3375F, SacrificeBudget.amplify(0.15F, 0.3375F, 4, true), 0.0001F);
        assertEquals(0.3375F, SacrificeBudget.amplify(0.15F, 0.3375F, 20, true), 0.0001F);
    }

    @Test
    void aMultiplierAmplifiesItsDistanceFromOneRatherThanItself() {
        // Glass Bones makes a hit land a fifth harder. The price is that fifth, so doubling it is
        // 1.4x, not 2.4x - scaling the whole number would double a hit only ever meant to be a
        // fifth worse, and would clear the entry's ceiling on the second price.
        assertEquals(1.2F, SacrificeBudget.amplifyMultiplier(1.2F, 1.45F, 1, true), 0.0001F);
        assertEquals(1.3F, SacrificeBudget.amplifyMultiplier(1.2F, 1.45F, 2, true), 0.0001F);
        assertEquals(1.45F, SacrificeBudget.amplifyMultiplier(1.2F, 1.45F, 4, true), 0.0001F);
    }

    @Test
    void aMultiplierBelowOneAmplifiesDownwardAndStopsAtItsFloor() {
        // Open Wound scales healing to a half. Its distance from one runs downward, so amplifying it
        // must fall toward the floor rather than rise through one, and the clamp has to be
        // two-sided rather than a plain Math.min.
        assertEquals(0.25F, SacrificeBudget.amplifyMultiplier(0.5F, 0.15F, 2, true), 0.0001F);
        assertEquals(0.15F, SacrificeBudget.amplifyMultiplier(0.5F, 0.15F, 4, true), 0.0001F,
                "and never through the floor into healing that hurts");
    }

    @Test
    void aMagnitudeScalesWholeBecauseItStartsAtZero() {
        // A chance to fail a cast has no "one" to be a distance from. Fifteen percent, doubled, is
        // thirty - the whole number moves.
        assertEquals(0.3375F, SacrificeBudget.amplify(0.15F, 0.5F, 4, true), 0.0001F);
        assertEquals(8.0F, SacrificeBudget.amplify(4.0F, 9.0F, 3, true), 0.0001F, "four blood, doubled");
        assertEquals(9.0F, SacrificeBudget.amplify(4.0F, 9.0F, 4, true), 0.0001F, "and held at nine");
    }
}
