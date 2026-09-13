package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.network.CooldownSyncPayload;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A cooldown heard once counts itself down, with the partial tick, and ends exactly once. */
class CooldownClockTest {

    private static final ResourceLocation SNAP = ResourceLocation.fromNamespaceAndPath("magical", "arcane_snap");
    private static final ResourceLocation FLAME = ResourceLocation.fromNamespaceAndPath("magical", "black_flames");

    private static CooldownSyncPayload one(ResourceLocation id, int remaining, int total) {
        return new CooldownSyncPayload(false, List.of(new CooldownSyncPayload.Entry(id, remaining, total)));
    }

    @Test
    void remainingCountsDownFromTheTickItWasHeard() {
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 60, 80), 100L);
        assertEquals(60, clock.remaining(SNAP, 100L));
        assertEquals(40, clock.remaining(SNAP, 120L));
        assertEquals(0, clock.remaining(SNAP, 160L));
        assertEquals(0, clock.remaining(SNAP, 999L));
        assertEquals(0, clock.remaining(FLAME, 100L), "an unknown skill is ready");
    }

    @Test
    void theFractionIsContinuousAndRunsFromTheTotalNotTheRemaining() {
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 60, 80), 100L);
        assertEquals(0.75F, clock.fraction(SNAP, 100.0F), 0.0001F);
        assertEquals(0.49375F, clock.fraction(SNAP, 120.5F), 0.0001F);
        assertEquals(0.0F, clock.fraction(SNAP, 200.0F), 0.0001F);
        assertTrue(clock.isOnCooldown(SNAP, 159L));
        assertFalse(clock.isOnCooldown(SNAP, 160L));
    }

    @Test
    void aFinishedCooldownIsFlaggedForExactlyOneTick() {
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 10, 10), 0L);
        clock.tick(5L);
        assertFalse(clock.justFinished(SNAP));
        clock.tick(10L);
        assertTrue(clock.justFinished(SNAP), "expiry sets the flag");
        assertTrue(clock.isEmpty(), "and drops the entry");
        clock.tick(11L);
        assertFalse(clock.justFinished(SNAP), "the flag lasts one tick");
    }

    @Test
    void aZeroFromTheServerClearsAndFlagsOnlyWhatWasThere() {
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 40, 40), 0L);
        clock.accept(one(FLAME, 0, 0), 1L);
        assertFalse(clock.justFinished(FLAME), "clearing a skill that was not on cooldown flags nothing");
        clock.accept(one(SNAP, 0, 0), 2L);
        assertTrue(clock.justFinished(SNAP), "a cleared cooldown flashes ready");
        assertEquals(0, clock.remaining(SNAP, 2L));
    }

    @Test
    void replaceAllWipesAndResetForgets() {
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 40, 40), 0L);
        clock.accept(new CooldownSyncPayload(true, List.of(new CooldownSyncPayload.Entry(FLAME, 20, 20))), 5L);
        assertEquals(0, clock.remaining(SNAP, 5L), "replaceAll dropped the old entry");
        assertEquals(20, clock.remaining(FLAME, 5L));
        int version = clock.version();
        clock.reset();
        assertTrue(clock.isEmpty());
        assertTrue(clock.version() > version, "reset is a change the snapshot must see");
    }

    @Test
    void aTotalSmallerThanTheRemainingIsRaisedToIt() {
        // The server never sends this, but a fraction above one would draw a sweep past twelve.
        CooldownClock clock = new CooldownClock();
        clock.accept(one(SNAP, 50, 10), 0L);
        assertEquals(1.0F, clock.fraction(SNAP, 0.0F), 0.0001F);
    }
}
