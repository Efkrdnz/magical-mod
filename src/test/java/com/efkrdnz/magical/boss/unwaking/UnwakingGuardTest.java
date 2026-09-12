package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * What is left of the guard once blocking is gone.
 *
 * <p>The hold/parry resolver this class used to own answered a question the encounter no longer
 * asks - there is no block outcome to distinguish from a parry, and parries are resolved by the
 * ordinary counter service. What remains is the input record itself: sequence ordering, the
 * heartbeat that notices a release that never arrived, and the two pure helpers the combat
 * controller still calls every time it builds a pending contact.
 */
class UnwakingGuardTest {

    @Test void replayedAndOutOfOrderPacketsAreRefused() {
        // Sequence numbers are transport ordering. A replayed packet must not look like a new press.
        UnwakingGuard guard = new UnwakingGuard();
        assertTrue(guard.accept(4, false, 0));
        assertFalse(guard.accept(4, true, 30));
        assertFalse(guard.accept(3, true, 30));
        assertFalse(guard.accept(-1, true, 30));
        assertTrue(guard.accept(5, true, 30));
    }

    @Test void aLostReleaseExpiresWithoutAHeartbeat() {
        // The release packet can simply never arrive. Thirty ticks of silence is the timeout.
        UnwakingGuard guard = new UnwakingGuard();
        guard.accept(0, true, 5);
        assertTrue(guard.held(34));
        assertFalse(guard.held(35));
    }

    @Test void anExplicitReleaseDropsTheHoldImmediately() {
        UnwakingGuard guard = new UnwakingGuard();
        guard.accept(0, true, 5);
        assertTrue(guard.held(6));
        guard.accept(1, false, 7);
        assertFalse(guard.held(7));
    }

    @Test void releaseIsIdempotentAndSurvivesAPhaseChange() {
        UnwakingGuard guard = new UnwakingGuard();
        guard.accept(0, true, 5);
        guard.release(6);
        guard.release(6);
        assertFalse(guard.held(6));
    }

    @Test void facingIsAFiftyDegreeCone() {
        assertTrue(UnwakingGuard.facing(1));
        assertTrue(UnwakingGuard.facing(Math.cos(Math.toRadians(49))));
        assertFalse(UnwakingGuard.facing(Math.cos(Math.toRadians(51))));
        assertFalse(UnwakingGuard.facing(0));
        assertFalse(UnwakingGuard.facing(-1));
    }

    @Test void graceScalesWithRoundTripAndIsCappedAtFourTicks() {
        assertEquals(0, UnwakingGuard.graceTicks(0));
        assertEquals(1, UnwakingGuard.graceTicks(100));
        assertEquals(2, UnwakingGuard.graceTicks(200));
        assertEquals(4, UnwakingGuard.graceTicks(900));
        // A player on a terrible connection does not get an unbounded window.
        assertEquals(4, UnwakingGuard.graceTicks(100000));
        assertEquals(0, UnwakingGuard.graceTicks(-50));
    }
}
