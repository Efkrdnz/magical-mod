package com.efkrdnz.magical.magic.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The three worked boards, held to the same standard a wielder board is held to.
 *
 * <p>Which is the whole reason they are in code rather than in a wiki page: an example that has
 * quietly stopped fitting the budget, or that is wired to a pin that no longer exists, teaches the
 * wrong thing to the one person who most needs it to be right.
 */
class WeavePresetsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyPresetFitsTheBudgetAndSaysSomething() {
        for (String name : WeavePresets.NAMES) {
            Weave weave = WeavePresets.of(name);
            assertNotNull(weave, name);
            assertTrue(weave.weight() <= Weave.CAPACITY,
                    name + " weighs " + weave.weight() + " of " + Weave.CAPACITY);
            assertFalse(WeaveReview.live(weave).isEmpty(), name + " has no chain that would fire");
        }
    }

    @Test
    void everyPresetIsSoundOnceAMarkIsOutThere() {
        for (String name : WeavePresets.NAMES) {
            Weave weave = WeavePresets.of(name);
            assertTrue(WeaveReview.issues(weave, true).isEmpty(),
                    name + " has " + WeaveReview.issues(weave, true));
        }
    }

    @Test
    void theCounterPresetAsksNothingOfAWielderWhoHasNeverPlacedAMark() {
        // Both halves of it used to want one - the Store behind FROM_MARKED and the payout scoped
        // MARKED - so the doc's own worked example did nothing whatsoever until an Anchor had been
        // spent, and did it silently. The other two presets never wanted a Mark; this holds the one
        // that did to the same standard, on the review that does not assume a Mark is out there.
        assertTrue(WeaveReview.issues(WeavePresets.of(WeavePresets.COUNTER), false).isEmpty(),
                "counter still wants a Mark: " + WeaveReview.issues(WeavePresets.of(WeavePresets.COUNTER), false));
    }

    @Test
    void everyChainOfEveryPresetIsInsideTheDepthCeiling() {
        for (String name : WeavePresets.NAMES) {
            for (WeaveReview.Chain chain : WeaveReview.chains(WeavePresets.of(name))) {
                assertFalse(chain.overlong(), name + " runs " + chain.length() + " pins deep");
            }
        }
    }

    @Test
    void everyPresetSurvivesBeingSavedAndOpenedAgain() {
        for (String name : WeavePresets.NAMES) {
            Weave original = WeavePresets.of(name);
            Weave opened = new Weave();
            opened.load(original.save());
            assertEquals(original.size(), opened.size(), name);
            assertEquals(original.wires().size(), opened.wires().size(), name);
            assertEquals(original.weight(), opened.weight(), name);
        }
    }

    @Test
    void everyPinOfEveryPresetSitsOnTheBoard() {
        for (String name : WeavePresets.NAMES) {
            for (CausalNode pin : WeavePresets.of(name).nodes()) {
                assertTrue(pin.x() >= 0 && pin.x() <= Weave.BOARD_W, name + " " + pin.x());
                assertTrue(pin.y() >= 0 && pin.y() <= Weave.BOARD_H, name + " " + pin.y());
            }
        }
    }

    @Test
    void aNameNobodyHasHeardOfIsNotABoard() {
        assertNull(WeavePresets.of("nonsense"));
        assertNull(WeavePresets.of(null));
    }

    @Test
    void eachPresetHandsBackAFreshBoardRatherThanAShotAtASharedOne() {
        Weave first = WeavePresets.of(WeavePresets.THORNS);
        first.clear();
        assertFalse(WeavePresets.of(WeavePresets.THORNS).empty(),
                "a wielder who clears one must not have cleared it for everybody");
    }
}
