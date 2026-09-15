package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A ritual passive is an ordinary passive with a clock on it. What is pinned here is the clock:
 * that granting one unlocks it, that ticking it down removes it at zero, that it survives a save,
 * and that re-granting extends rather than shortens.
 */
class RitualPassiveTimerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation boon() {
        return MagicPassiveContent.SANGUINE_MIGHT.id();
    }

    private static ResourceLocation price() {
        return MagicPassiveContent.GLASS_BONES.id();
    }

    @Test
    void grantingOneUnlocksItAndStartsItsClock() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 40);

        assertTrue(state.hasPassive(boon()), "a granted boon is an owned passive");
        assertTrue(state.isPassiveEnabled(boon()), "and it is on: a boon you can switch off is free");
        assertEquals(40, state.ritualRemaining(boon()));
    }

    @Test
    void itIsGoneTheTickItsClockRunsOut() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 3);

        for (int i = 0; i < 3; i++) {
            state.tickRitualPassives();
        }

        assertEquals(0, state.ritualRemaining(boon()));
        assertFalse(state.hasPassive(boon()), "an expired boon is not merely idle, it is removed");
    }

    @Test
    void theLastTickAsksForASyncSoTheCodexStopsCountingToo() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 2);

        assertFalse(state.tickRitualPassives(), "an ordinary tick is not worth a whole state blob");
        assertTrue(state.tickRitualPassives(), "but an expiry is");
    }

    @Test
    void anIdleStateNeverAsksForAnything() {
        assertFalse(new PlayerMagicState().tickRitualPassives(), "no ritual, no traffic");
    }

    @Test
    void regrantingTakesTheLongerClockRatherThanTheNewer() {
        // The failure this guards: a second ritual with a shorter duration cutting the first one
        // short, which reads as the game taking away a boon that was paid for.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.grantRitualPassive(boon(), 20);

        assertEquals(100, state.ritualRemaining(boon()));
    }

    @Test
    void onlyPricesAreCounted() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.grantRitualPassive(price(), 100);

        assertEquals(1, state.activeRitualPriceCount(), "Hellbroker counts what hurts, not what helps");
    }

    @Test
    void theClockSurvivesARelog() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(price(), 77);

        CompoundTag tag = state.save();
        PlayerMagicState loaded = PlayerMagicState.load(tag);

        assertEquals(77, loaded.ritualRemaining(price()));
        assertTrue(loaded.hasPassive(price()), "and the passive it belongs to comes back with it");
    }

    @Test
    void theClockReachesTheClientThroughCopyAsWellAsThroughSave() {
        // ClientMagicState rebuilds through load() then copy(). A field missing from either draws a
        // codex row with no countdown on it, or no row at all.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 55);

        assertEquals(55, state.copy().ritualRemaining(boon()), "lost in copy()");
        assertEquals(55, PlayerMagicState.load(state.save()).copy().ritualRemaining(boon()));
    }

    @Test
    void pullingThePassiveAnyOtherWayTakesItsClockWithIt() {
        // removePassive is reachable from the command tree and from Ironblood spending itself. A
        // clock left behind would keep counting for a passive that is no longer there.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.removePassive(boon());

        assertEquals(0, state.ritualRemaining(boon()));
        assertEquals(0, state.activeRitualPriceCount());
    }

    @Test
    void clearingWipesBothThePassivesAndTheirClocks() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.grantRitualPassive(price(), 100);

        state.clearRitualPassives();

        assertFalse(state.hasPassive(boon()));
        assertFalse(state.hasPassive(price()));
        assertEquals(0, state.ritualRemaining(price()));
    }

    @Test
    void aPactPassiveRefusesTheCheckbox() {
        // The codex draws a checkbox beside every normal passive, and a ritual price is a normal
        // passive. Without this a player could seal a pact and then switch the curse half off,
        // which is the whole ability for free. Refused in the state rather than in the screen, so
        // the menu button that carries the toggle is refused with it.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 200);
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 200);

        state.togglePassive(MagicPassiveContent.GLASS_BONES.id());
        state.togglePassive(MagicPassiveContent.CRIMSON_EDGE.id());

        assertTrue(state.isPassiveEnabled(MagicPassiveContent.GLASS_BONES.id()), "a price switched itself off");
        assertTrue(state.isPassiveEnabled(MagicPassiveContent.CRIMSON_EDGE.id()), "a boon switched itself off");
    }

    @Test
    void anOrdinaryPassiveStillTogglesFreely() {
        // The guard must be about the pact, not about passives in general.
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());

        state.togglePassive(MagicPassiveContent.MANA_SKIN.id());

        assertFalse(state.isPassiveEnabled(MagicPassiveContent.MANA_SKIN.id()), "an ordinary passive lost its checkbox");
    }
}
