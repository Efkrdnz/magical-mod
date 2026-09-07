package com.efkrdnz.magical.entity.ascendant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.ascendant.OpponentFlight.Situation;
import org.junit.jupiter.api.Test;

/**
 * Flight is the one change here that touches opponents at every level, and the rules that keep it
 * from being stupid are exactly the rules that are hard to check in a fight. So they are checked
 * here instead.
 */
class OpponentFlightTest {

    private static Situation situation(AscendantStance stance, float mana, boolean targetGrounded,
            boolean wantsRange, boolean melee, boolean groundPath) {
        return new Situation(stance, mana, targetGrounded, wantsRange, melee, groundPath);
    }

    /** A ground-preferring stance with no reason at all to be airborne. */
    private static Situation settled() {
        return situation(AscendantStance.DUELIST, 1.0F, true, false, false, true);
    }

    @Test
    void itLandsWhenYouAreOnTheGroundAndItHasNoReasonToBeUp() {
        assertFalse(OpponentFlight.shouldFly(settled()));
    }

    @Test
    void butStandingStillDoesNotGroundIt() {
        // The whole point of the reasons: a boss you can pin to the floor by not moving is the same
        // exploit as a boss that cannot fly.
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.DUELIST, 1.0F, true, false, true, true)), "being meleed");
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.DUELIST, 1.0F, true, true, false, true)), "wanting range");
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.DUELIST, 1.0F, true, false, false, false)), "no path to you");
    }

    @Test
    void itFollowsYouUpWhenYouLeaveTheGround() {
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.DUELIST, 1.0F, false, false, false, true)));
    }

    @Test
    void anAirHoldingStanceIsItsOwnReason() {
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.ARTILLERY, 1.0F, true, false, false, true)));
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.AUTHORITY, 1.0F, true, false, false, true)));
    }

    @Test
    void anEmptyPoolGroundsItWhateverElseIsTrue() {
        // Fuel is checked first on purpose: a boss that hovers its pool away cannot cast, and a
        // boss that cannot cast is not a fight.
        for (AscendantStance stance : AscendantStance.values()) {
            assertFalse(OpponentFlight.shouldFly(situation(stance, 0.1F, false, true, true, false)),
                    stance + " kept flying on an empty pool");
        }
    }

    @Test
    void theReserveBoundaryIsInclusive() {
        assertFalse(OpponentFlight.shouldFly(situation(
                AscendantStance.ARTILLERY, OpponentFlight.MANA_RESERVE - 0.01F, true, false, false, true)));
        assertTrue(OpponentFlight.shouldFly(situation(
                AscendantStance.ARTILLERY, OpponentFlight.MANA_RESERVE, true, false, false, true)));
    }

    @Test
    void itNeverClimbsHigherThanYouTakeIt() {
        // The whole anti-skybox rule: its ceiling is measured off you, so it cannot pillar on its
        // own initiative. Stand on the ground and it stays within nine blocks of the ground.
        assertEquals(73.0D, OpponentFlight.ceiling(64.0D), 1.0E-9D);
        // Fly to the build limit and it follows, but it is still only ever nine above you.
        assertEquals(309.0D, OpponentFlight.ceiling(300.0D), 1.0E-9D);
    }

    @Test
    void aPlayerOnHighGroundIsReachable() {
        // Standing on a tower 40 above the terrain. A ceiling measured from the ground would have
        // stranded it below; measured from you, it comes up.
        assertTrue(OpponentFlight.ceiling(104.0D) > 104.0D,
                "flight has to answer the case it exists for");
    }

    @Test
    void aMeleeStanceDropsToYourLevelRatherThanHovering() {
        Situation melee = situation(AscendantStance.EXECUTIONER, 1.0F, true, false, true, true);
        assertEquals(64.0D, OpponentFlight.desiredY(melee, 64.0D, true), 1.0E-9D,
                "it cannot poke from out of reach");
    }

    @Test
    void everyStanceSettlesUnderItsOwnCeiling() {
        for (AscendantStance stance : AscendantStance.values()) {
            Situation ranged = situation(stance, 1.0F, true, true, false, true);
            double desired = OpponentFlight.desiredY(ranged, 64.0D, false);
            assertTrue(desired <= OpponentFlight.ceiling(64.0D), stance + " overshot its ceiling");
            assertTrue(desired > 64.0D, stance + " never actually leaves the ground");
        }
    }

    @Test
    void everyTierHasAStanceAndTheHeavyBladesStayLowEnoughToSwing() {
        for (AscendantTier tier : AscendantTier.values()) {
            assertTrue(tier.stance() != null, tier + " has no stance");
        }
        // The two that carry a rider worth landing prefer the ground; the artillery tiers do not.
        assertFalse(AscendantTier.SIN_EATER.stance().holdsAir());
        assertTrue(AscendantTier.FALLEN.stance().holdsAir());
    }
}
