package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Notice gauge: a heat that rises with every call and cools in silence, and what it buys. */
class EldritchServiceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noticeIsHeldToItsRange() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(0, state.notice());
        state.addNotice(130);
        assertEquals(PlayerMagicState.MAX_NOTICE, state.notice(), "the ceiling holds");
        state.addNotice(-500);
        assertEquals(0, state.notice(), "and so does the floor");
    }

    @Test
    void noticeSurvivesSaveLoadAndCopy() {
        PlayerMagicState state = new PlayerMagicState();
        state.setNotice(37);
        assertEquals(37, PlayerMagicState.load(state.save()).notice());
        assertEquals(37, state.copy().notice());
    }

    @Test
    void theRungsSitAtFiftyAndTheTop() {
        assertEquals(0, EldritchService.rung(0));
        assertEquals(0, EldritchService.rung(49));
        assertEquals(1, EldritchService.rung(EldritchService.WATCHED_AT));
        assertEquals(1, EldritchService.rung(99));
        assertEquals(2, EldritchService.rung(EldritchService.NOTICED_AT));
    }

    @Test
    void theDeepGivesMoreToTheOnesItWatches() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1.0F, EldritchService.potency(state), 1.0E-6F, "unwatched, a call is ordinary");
        state.setNotice(PlayerMagicState.MAX_NOTICE);
        assertEquals(EldritchService.MAX_POTENCY, EldritchService.potency(state), 1.0E-6F);
        state.setNotice(50);
        assertEquals(1.25F, EldritchService.potency(state), 1.0E-6F, "and it is linear between");
    }

    // Land with Task 2 (they name the passives):
    @Test
    void noticeCoolsOnePointASlowTickAndHalfAsFastForTheLidless() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1, EldritchService.decayStep(state));
        assertEquals(1, EldritchService.decayStep(state));
        state.unlockPassive(MagicPassiveContent.LIDLESS.id());
        int over = 0;
        for (int i = 0; i < 10; i++) {
            over += EldritchService.decayStep(state);
        }
        assertEquals(5, over, "Lidless cools every other slow tick");
    }

    @Test
    void theBargainWaivesManaOnlyAtTheTop() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.DEEP_BARGAIN.id());
        state.setNotice(99);
        assertFalse(EldritchService.manaWaived(state), "Watched is not Noticed");
        state.setNotice(PlayerMagicState.MAX_NOTICE);
        assertTrue(EldritchService.manaWaived(state));
        PlayerMagicState without = new PlayerMagicState();
        without.setNotice(PlayerMagicState.MAX_NOTICE);
        assertFalse(EldritchService.manaWaived(without), "the top rung alone waives nothing");
    }

    @Test
    void aPlayerWhoHasNeverCalledTheDeepIsNotAnEldritchMage() {
        assertFalse(EldritchService.isEldritchMage(new PlayerMagicState()));
        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.TENDRIL_LASH.id());
        assertTrue(EldritchService.isEldritchMage(state));
    }
}
