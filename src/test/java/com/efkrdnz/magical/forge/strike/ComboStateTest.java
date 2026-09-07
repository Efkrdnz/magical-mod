package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComboStateTest {

    private static final int WEAPON_HASH = 12345;

    @Test
    void idleStartsAtIndexZeroWithEveryNoneFieldAtMinusOne() {
        ComboState idle = ComboState.idle(WEAPON_HASH, 3);

        assertEquals(0, idle.index());
        assertEquals(3, idle.chainLength());
        assertEquals(WEAPON_HASH, idle.weaponHash());
        assertEquals(-1L, idle.lastStrikeTick());
        assertEquals(-1L, idle.windowEndTick());
        assertEquals(-1L, idle.readyTick());
        assertEquals(-1L, idle.pressTick());
        assertEquals(-1L, idle.guardUntilTick());
        assertEquals(-1, idle.telegraphEntityId());
        assertEquals(-1, idle.primaryTargetId());

        assertFalse(idle.windowExpired(0));
        assertFalse(idle.rateLimited(0));
        assertFalse(idle.recovering(0));
    }

    @Test
    void matchesComparesWeaponHash() {
        ComboState idle = ComboState.idle(WEAPON_HASH, 3);

        assertTrue(idle.matches(WEAPON_HASH));
        assertFalse(idle.matches(WEAPON_HASH + 1));
    }

    @Test
    void afterStrikeAdvancesTheIndexThroughTheChainAndWrapsOnTheFinisher() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3);
        assertFalse(state.finisherAt(state.index()));

        ComboState afterFirst = state.afterStrike(100L, 8, 122L);
        assertEquals(1, afterFirst.index());
        assertEquals(100L, afterFirst.lastStrikeTick());
        assertEquals(108L, afterFirst.readyTick());
        assertEquals(122L, afterFirst.windowEndTick());
        assertFalse(afterFirst.finisherAt(afterFirst.index()));

        ComboState afterSecond = afterFirst.afterStrike(130L, 8, 152L);
        assertEquals(2, afterSecond.index());
        assertEquals(152L, afterSecond.windowEndTick());
        assertTrue(afterSecond.finisherAt(afterSecond.index()));

        ComboState afterFinisher = afterSecond.afterStrike(160L, 8, 182L);
        assertEquals(0, afterFinisher.index());
        assertEquals(160L, afterFinisher.lastStrikeTick());
        assertEquals(168L, afterFinisher.readyTick());
        assertEquals(160L + 8 + ForgeStrikeMath.RESET_AFTER_FINISHER, afterFinisher.windowEndTick());
    }

    @Test
    void windowExpiredIsTrueOnlyStrictlyAfterTheWindowEndTick() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).afterStrike(100L, 8, 132L);

        assertFalse(state.windowExpired(132L));
        assertTrue(state.windowExpired(133L));
    }

    @Test
    void recoveringIsTrueBeforeTheReadyTickAndFalseAtOrAfterIt() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).afterStrike(100L, 8, 132L);

        assertTrue(state.recovering(107L));
        assertFalse(state.recovering(108L));
    }

    @Test
    void rateLimitedIsTrueWithinThreeTicksOfTheLastStrike() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).afterStrike(100L, 8, 132L);

        assertTrue(state.rateLimited(101L));
        assertTrue(state.rateLimited(102L));
        assertFalse(state.rateLimited(103L));
    }

    @Test
    void resetReturnsToIdleButKeepsTheGuardUntilTick() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3)
                .afterStrike(100L, 8, 132L)
                .withGuard(500L);

        ComboState reset = state.reset();

        assertEquals(0, reset.index());
        assertEquals(3, reset.chainLength());
        assertEquals(WEAPON_HASH, reset.weaponHash());
        assertEquals(-1L, reset.lastStrikeTick());
        assertEquals(-1L, reset.windowEndTick());
        assertEquals(-1L, reset.readyTick());
        assertEquals(-1L, reset.pressTick());
        assertEquals(500L, reset.guardUntilTick());
        assertEquals(-1, reset.telegraphEntityId());
        assertEquals(-1, reset.primaryTargetId());
    }

    @Test
    void idleCarryingPrimaryTargetResetsTheChainButKeepsTheTarget() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3)
                .afterStrike(100L, 8, 132L)
                .withGuard(500L)
                .withTelegraph(7)
                .withPrimaryTarget(77);

        ComboState fresh = state.idleCarryingPrimaryTarget(WEAPON_HASH + 1, 5);

        assertEquals(0, fresh.index());
        assertEquals(5, fresh.chainLength());
        assertEquals(WEAPON_HASH + 1, fresh.weaponHash());
        assertEquals(-1L, fresh.lastStrikeTick());
        assertEquals(-1L, fresh.windowEndTick());
        assertEquals(-1L, fresh.readyTick());
        assertEquals(-1L, fresh.pressTick());
        assertEquals(-1L, fresh.guardUntilTick());
        assertEquals(-1, fresh.telegraphEntityId());
        assertEquals(77, fresh.primaryTargetId());
    }

    @Test
    void idleCarryingPrimaryTargetCarriesNoTargetWhenNoneWasEverSet() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3);

        ComboState fresh = state.idleCarryingPrimaryTarget(WEAPON_HASH, 3);

        assertEquals(-1, fresh.primaryTargetId());
    }

    @Test
    void withPrimaryTargetSetsItAndAfterStrikeClearsIt() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).withPrimaryTarget(42);
        assertEquals(42, state.primaryTargetId());

        ComboState afterStrike = state.afterStrike(100L, 8, 132L);
        assertEquals(-1, afterStrike.primaryTargetId());
    }

    @Test
    void withTelegraphSetsItAndAfterStrikeClearsIt() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).withTelegraph(7);
        assertEquals(7, state.telegraphEntityId());

        ComboState afterStrike = state.afterStrike(100L, 8, 132L);
        assertEquals(-1, afterStrike.telegraphEntityId());
    }

    @Test
    void withoutTelegraphClearsOnlyTheTelegraph() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).withTelegraph(7).withPrimaryTarget(9);

        ComboState cleared = state.withoutTelegraph();

        assertEquals(-1, cleared.telegraphEntityId());
        assertEquals(9, cleared.primaryTargetId());
    }

    @Test
    void withPressStoresThePressTick() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).withPress(77L);
        assertEquals(77L, state.pressTick());
    }

    @Test
    void guardActiveIsTrueStrictlyBeforeTheGuardUntilTick() {
        ComboState state = ComboState.idle(WEAPON_HASH, 3).withGuard(50L);

        assertTrue(state.guardActive(49L));
        assertFalse(state.guardActive(50L));
        assertFalse(state.guardActive(51L));
        assertFalse(ComboState.idle(WEAPON_HASH, 3).guardActive(0L));
    }
}
