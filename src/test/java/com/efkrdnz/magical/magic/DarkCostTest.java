package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Corruption: what it does to the pools, what it refuses to do to them, and that it is genuinely
 * permanent.
 *
 * <p>Everything here runs without a live ServerPlayer, which is most of the school - the cost is a
 * number on the state rather than an interaction with the world, so it can be pinned exactly.
 */
class DarkCostTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void corruptionSurvivesTheRoundTripTheClientActuallyMakes() {
        // Same shape as the race pool bonus bug: save() is simultaneously the disk format and the
        // wire format, and ClientMagicState rebuilds through copy(). A field missing from any one
        // of the three is invisible on the client, and the HUD would then draw an uncorrupted
        // ceiling over a corrupted one.
        PlayerMagicState state = new PlayerMagicState();
        state.addCorruption(47);

        assertEquals(47, PlayerMagicState.load(state.save()).corruption(), "lost over the wire");
        assertEquals(47, PlayerMagicState.load(state.save()).copy().corruption(),
                "the client applies load() then copy(), so Corruption must survive both");
    }

    @Test
    void aSaveFromBeforeTheDarkSchoolLoadsClean() {
        PlayerMagicState state = new PlayerMagicState();
        CompoundTag legacy = state.save();
        legacy.remove("corruption");

        assertEquals(0, PlayerMagicState.load(legacy).corruption(),
                "an old world must open with no debt rather than whatever an absent tag reads as");
    }

    @Test
    void corruptionIsClampedOnTheWayInAndOnTheWayBack() {
        PlayerMagicState state = new PlayerMagicState();
        state.addCorruption(10_000);
        assertEquals(PlayerMagicState.MAX_CORRUPTION, state.corruption(), "the ladder has a top");

        state.addCorruption(-10_000);
        assertEquals(0, state.corruption(), "and a bottom");

        CompoundTag tag = state.save();
        tag.putInt("corruption", 9999);
        assertEquals(PlayerMagicState.MAX_CORRUPTION, PlayerMagicState.load(tag).corruption(),
                "an edited save must not hand back a value the rest of the code cannot survive");
    }

    @Test
    void aCleanMageHasExactlyTheCeilingsTheyAlwaysHad() {
        // The regression that matters most: Corruption must be invisible to every player who has
        // never cast a dark skill, and the penalty term lives inside methods the whole mod calls.
        PlayerMagicState clean = new PlayerMagicState();
        int mana = clean.maxMana();
        int barrier = clean.maxBarrier();

        PlayerMagicState corrupted = new PlayerMagicState();
        corrupted.addCorruption(PlayerMagicState.MAX_CORRUPTION);

        assertTrue(corrupted.maxMana() < mana, "full Corruption has to actually bite");
        assertTrue(corrupted.maxBarrier() < barrier, "on both pools");
        assertEquals(0, new PlayerMagicState().corruption(), "and a fresh state owes nothing");
    }

    @Test
    void noAmountOfCorruptionCanZeroAPool() {
        // The reason this is a penalty term with a cap rather than a plain subtraction: a pool that
        // could reach zero would make the last cast before Purification impossible, which is a trap
        // rather than a cost.
        PlayerMagicState clean = new PlayerMagicState();
        int baseMana = clean.maxMana();
        int baseBarrier = clean.maxBarrier();

        PlayerMagicState state = new PlayerMagicState();
        state.addCorruption(PlayerMagicState.MAX_CORRUPTION);

        int floorMana = baseMana - Math.round(baseMana * PlayerMagicState.MAX_POOL_PENALTY);
        int floorBarrier = baseBarrier - Math.round(baseBarrier * PlayerMagicState.MAX_POOL_PENALTY);
        assertEquals(floorMana, state.maxMana(), "the mana floor is a fixed share of the ceiling");
        assertEquals(floorBarrier, state.maxBarrier(), "and so is the barrier floor");
        assertTrue(state.maxMana() > 0 && state.maxBarrier() > 0, "neither pool may ever be zeroed");
    }

    @Test
    void thePenaltyRisesWithTheDebtRatherThanArrivingAllAtOnce() {
        int last = new PlayerMagicState().maxMana() + 1;
        for (int corruption = 0; corruption <= PlayerMagicState.MAX_CORRUPTION; corruption += 10) {
            PlayerMagicState state = new PlayerMagicState();
            state.setCorruption(corruption);
            assertTrue(state.maxMana() < last, "the ceiling must fall every step, not only at thresholds");
            last = state.maxMana();
        }
    }

    @Test
    void raisingCorruptionPullsThePoolsDownWithIt() {
        // setCorruption re-clamps, or mana sits above its own maximum until something else happens
        // to touch it - and everything reading the pair would disagree in the meantime.
        PlayerMagicState state = new PlayerMagicState();
        state.setMana(state.maxMana());
        state.setBarrier(state.maxBarrier());

        state.setCorruption(PlayerMagicState.MAX_CORRUPTION);

        assertTrue(state.mana() <= state.maxMana(), "mana left above its own ceiling");
        assertTrue(state.barrier() <= state.maxBarrier(), "barrier left above its own ceiling");
    }

    @Test
    void theLadderIsFourEvenStepsAndTheTopOneIsTheCursedOne() {
        assertEquals(0, DarkService.thresholdOf(0));
        assertEquals(0, DarkService.thresholdOf(DarkService.THRESHOLD_STEP - 1));
        assertEquals(1, DarkService.thresholdOf(DarkService.THRESHOLD_STEP));
        assertEquals(3, DarkService.thresholdOf(DarkService.THRESHOLD_STEP * 3));
        assertEquals(4, DarkService.thresholdOf(PlayerMagicState.MAX_CORRUPTION));
        assertEquals(PlayerMagicState.MAX_CORRUPTION, DarkService.CURSE_AT,
                "the curse belongs at the ceiling, not somewhere near it");
    }

    @Test
    void aSingleGainSmallerThanAStepCannotCrossTwoThresholds() {
        // The whole of what Ledger buys, stated as arithmetic: the cap is one below the step, and
        // that is exactly the largest gain which can never skip a rung.
        assertEquals(DarkService.THRESHOLD_STEP - 1, DarkService.LEDGER_MAX_SINGLE_GAIN);
        for (int from = 0; from < PlayerMagicState.MAX_CORRUPTION; from++) {
            int to = Math.min(PlayerMagicState.MAX_CORRUPTION, from + DarkService.LEDGER_MAX_SINGLE_GAIN);
            assertTrue(DarkService.thresholdOf(to) - DarkService.thresholdOf(from) <= 1,
                    "a capped gain from " + from + " skipped a rung");
        }
    }

    @Test
    void theCurseAttachesAtTheCeilingAndOnlyLetsGoBelowIt() {
        PlayerMagicState state = new PlayerMagicState();
        DarkService.corrupt(null, state, PlayerMagicState.MAX_CORRUPTION);

        assertTrue(state.hasCurse(MagicPassiveContent.CORRUPTION_CURSE.id()), "the debt was called");
        assertFalse(state.canDispelCurse(MagicPassiveContent.CORRUPTION_CURSE.id()),
                "the codex button must not argue with a debt that is still at the top");

        DarkService.cleanse(null, state, DarkService.THRESHOLD_STEP);
        assertFalse(state.hasCurse(MagicPassiveContent.CORRUPTION_CURSE.id()),
                "paying the debt down is what lifts it, wherever the payment came from");
    }

    @Test
    void corruptionBelowTheCeilingNeverAttachesTheCurse() {
        PlayerMagicState state = new PlayerMagicState();
        DarkService.corrupt(null, state, PlayerMagicState.MAX_CORRUPTION - 1);

        assertEquals(3, DarkService.threshold(state), "one short of the top is still the third rung");
        assertFalse(state.hasCurse(MagicPassiveContent.CORRUPTION_CURSE.id()),
                "three quarters of the way down is meant to be survivable");
    }

    @Test
    void aDarkCastAlwaysWritesSomethingDownRatherThanRoundingToNothing() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1, DarkService.corrupt(null, state, 1), "the smallest possible cast still costs");
        assertEquals(0, DarkService.corrupt(null, state, 0), "and a zero cost is not a cost");
    }

    @Test
    void cleansingReportsWhatItActuallyCleared() {
        PlayerMagicState state = new PlayerMagicState();
        state.setCorruption(10);

        assertEquals(10, DarkService.cleanse(null, state, 40),
                "it cannot clear more than is owed, and must not claim to");
        assertEquals(0, state.corruption());
        assertEquals(0, DarkService.cleanse(null, state, 40), "nothing owed, nothing cleared");
    }
}
