package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.ModifierStack;
import com.efkrdnz.magical.forge.WeaponClass;

import org.junit.jupiter.api.Test;

/**
 * Drives {@link ComboState} through a whole forged-weapon chain exactly the way
 * {@code ForgeComboService.onPress} does — gate on the window/recovery, pick the form at the
 * current index, then advance with {@link ForgeStrikeMath#recovery} and
 * {@link ForgeStrikeMath#windowEnd}. This is the MC-free contract the server pipeline is built
 * on: if the numbers here move, a High sword stops chaining three presses.
 */
class ForgeComboFlowTest {

    private static final int WEAPON_HASH = 0x5EA5;

    /** Stand-in entity id for whatever vanilla's own swing hit on the press being replayed. */
    private static final int TARGET_ID = 4242;

    private static final FormStats SLASH =
            new FormStats(FormFamily.SLASH, 1.00f, 1.70f, 3.5f, 1.6f, 150f, 0f, 4, 0.35f, 8);
    private static final FormStats CLEAVE =
            new FormStats(FormFamily.CLEAVE, 1.25f, 2.10f, 3.0f, 0.9f, 120f, 0f, 4, 0.25f, 11);
    private static final FormStats THRUST =
            new FormStats(FormFamily.THRUST, 1.15f, 1.90f, 4.5f, 0.6f, 0f, 0f, 6, 0.45f, 9);

    private static final FormStats[] CHAIN = {SLASH, CLEAVE, THRUST};

    @Test
    void threePressesWalkTheWholeChainAndTheFinisherWrapsBackToTheFirstForm() {
        ComboState state = ComboState.idle(WEAPON_HASH, CHAIN.length);

        Press first = press(state, 100L, false);
        assertTrue(first.accepted());
        assertEquals(0, first.formIndex());
        assertFalse(first.finisher());
        assertEquals(8, first.recovery());
        assertEquals(1, first.state().index());
        assertEquals(108L, first.state().readyTick());
        assertEquals(122L, first.state().windowEndTick());

        Press second = press(first.state(), 108L, false);
        assertTrue(second.accepted());
        assertEquals(1, second.formIndex());
        assertFalse(second.finisher());
        assertEquals(11, second.recovery());
        assertEquals(2, second.state().index());
        assertEquals(119L, second.state().readyTick());
        assertEquals(133L, second.state().windowEndTick());

        Press third = press(second.state(), 119L, false);
        assertTrue(third.accepted());
        assertEquals(2, third.formIndex());
        assertTrue(third.finisher());
        assertEquals(9, third.recovery());
        assertEquals(0, third.state().index(), "the finisher wraps the chain back to the first form");
        assertEquals(128L, third.state().readyTick());
        assertEquals(119L + 9L + ForgeStrikeMath.RESET_AFTER_FINISHER, third.state().windowEndTick());
    }

    @Test
    void aPressInsideTheRecoveryIsRejectedAndLeavesTheChainUntouched() {
        ComboState afterFirst = press(ComboState.idle(WEAPON_HASH, CHAIN.length), 100L, false).state();

        Press tooEarly = press(afterFirst, 107L, false);

        assertFalse(tooEarly.accepted());
        assertEquals(-1, tooEarly.formIndex());
        assertEquals(1, tooEarly.state().index(), "a rejected press must not advance the chain");
        assertEquals(afterFirst.windowEndTick(), tooEarly.state().windowEndTick());

        assertTrue(press(afterFirst, 108L, false).accepted(), "the press lands the tick recovery ends");
    }

    @Test
    void thePressAfterTheFinisherRestartsAtTheFirstFormEvenWhileTheWindowIsStillOpen() {
        ComboState state = ComboState.idle(WEAPON_HASH, CHAIN.length);
        state = press(state, 100L, false).state();
        state = press(state, 108L, false).state();
        Press finisher = press(state, 119L, false);

        Press fourth = press(finisher.state(), 128L, false);

        assertTrue(fourth.accepted());
        assertEquals(0, fourth.formIndex());
        assertFalse(fourth.finisher());
        assertEquals(1, fourth.state().index());
    }

    @Test
    void lettingTheComboWindowExpireResetsTheChainToTheFirstFormAndKeepsTheCarriedTarget() {
        // notePrimaryHit writes the target onto whatever state is standing when vanilla's swing
        // lands, which is the state the expiry below replaces.
        ComboState afterFirst = press(ComboState.idle(WEAPON_HASH, CHAIN.length), 100L, false).state()
                .withPrimaryTarget(TARGET_ID);
        assertEquals(122L, afterFirst.windowEndTick());
        assertEquals(1, afterFirst.index());

        assertEquals(1, press(afterFirst, 122L, false).formIndex(), "the window is inclusive of its last tick");

        Press afterExpiry = press(afterFirst, 123L, false);

        assertTrue(afterExpiry.accepted());
        assertEquals(0, afterExpiry.formIndex(), "an expired window restarts the chain at the first form");
        assertEquals(1, afterExpiry.state().index());
        assertEquals(TARGET_ID, afterExpiry.entryState().primaryTargetId(),
                "the rebuild must carry the primary target notePrimaryHit already wrote, or the "
                        + "body that took the vanilla swing also takes an uncorrected strike");
    }

    @Test
    void aHeavyPressConsumesTheSameChainStepButPushesTheRecoveryOut() {
        ComboState idle = ComboState.idle(WEAPON_HASH, CHAIN.length);

        Press light = press(idle, 100L, false);
        Press heavy = press(idle, 100L, true);

        assertEquals(light.formIndex(), heavy.formIndex());
        assertEquals(light.state().index(), heavy.state().index(), "heavy consumes exactly one chain step");
        assertEquals(light.recovery() + ForgeStrikeMath.HEAVY_RECOVERY, heavy.recovery());
        assertEquals(100L + heavy.recovery(), heavy.state().readyTick());
    }

    @Test
    void aSingleFormChainMakesEveryPressAFinisherAndNeverLeavesIndexZero() {
        FormStats[] single = {SLASH};
        ComboState state = ComboState.idle(WEAPON_HASH, single.length);

        Press only = press(state, 100L, single, false);
        assertTrue(only.accepted());
        assertEquals(0, only.formIndex());
        assertTrue(only.finisher(), "a one-form chain is a finisher on every press");
        assertEquals(0, only.state().index());

        Press next = press(only.state(), 108L, single, false);
        assertEquals(0, next.formIndex());
        assertTrue(next.finisher());
    }

    // --- the MC-free half of ForgeComboService.onPress -------------------------------------

    /**
     * @param state      the state the press leaves behind
     * @param entryState the state the press resolved against, before {@code afterStrike} cleared its
     *                   per-press bookkeeping - the only place a carried primary target is visible
     */
    private record Press(ComboState state, ComboState entryState, int formIndex, boolean finisher, int recovery,
            boolean accepted) {}

    private static Press press(ComboState state, long now, boolean heavy) {
        return press(state, now, CHAIN, heavy);
    }

    private static Press press(ComboState state, long now, FormStats[] chain, boolean heavy) {
        // idleCarryingPrimaryTarget, not idle: this mirrors ForgeComboService.currentState, and a
        // plain idle() here would let that call regress to one without a single test noticing.
        ComboState current = state.matches(WEAPON_HASH) && !state.windowExpired(now)
                ? state
                : state.idleCarryingPrimaryTarget(WEAPON_HASH, chain.length);
        if (current.rateLimited(now) || current.recovering(now)) {
            return new Press(state, current, -1, false, 0, false);
        }
        int index = Math.min(current.index(), chain.length - 1);
        boolean finisher = current.finisherAt(index);
        int recovery = ForgeStrikeMath.recovery(chain[index], TemperStats.NONE, WeaponClass.SWORD, heavy, ModifierStack.EMPTY);
        long windowEnd = ForgeStrikeMath.windowEnd(now, recovery, TemperStats.NONE, ModifierStack.EMPTY);
        return new Press(current.afterStrike(now, recovery, windowEnd), current, index, finisher, recovery, true);
    }
}
