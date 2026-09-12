package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The barrier is the only thing that scales now: health is pinned at twenty and is staying there,
 * so every number that would have been "more health" has to arrive through this pool instead.
 *
 * <p>Which makes {@code absorbDamage} the most load-bearing arithmetic in the mod. It is called on
 * every blow the player takes, and the two ways it can be wrong - letting damage through that the
 * barrier should have stopped, or eating damage the barrier could not afford - are both invisible
 * in a running game until someone dies wondering why.
 */
class BarrierEnduranceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Unlocking is what switches the passive on; the level is set separately, as the shop does. */
    private static PlayerMagicState withEndurance(int level) {
        PlayerMagicState state = new PlayerMagicState();
        if (level > 0) {
            state.unlockPassive(MagicPassiveContent.ENDURANCE.id());
            state.setPassiveLevel(MagicPassiveContent.ENDURANCE.id(), level);
        }
        return state;
    }

    @Test
    void withNoEnduranceTheBarrierIsStillExactlyOneForOne() {
        PlayerMagicState state = new PlayerMagicState();
        state.setBarrier(30);

        assertEquals(0.0f, state.barrierReduction(), 0.0001f, "endurance is on without being levelled");
        assertEquals(0.0f, state.absorbDamage(10.0f), 0.0001f, "a covered blow reached the health bar");
        assertEquals(20, state.barrier(), "ten damage should cost exactly ten barrier");
    }

    @Test
    void anEmptyBarrierIsANoOpAndSoIsAHarmlessBlow() {
        PlayerMagicState state = new PlayerMagicState();
        state.setBarrier(0);
        assertEquals(12.0f, state.absorbDamage(12.0f), 0.0001f, "an empty barrier stopped something");

        state.setBarrier(40);
        assertEquals(0.0f, state.absorbDamage(0.0f), 0.0001f);
        assertEquals(40, state.barrier(), "a blow of nothing cost barrier");
    }

    @Test
    void aLevelledBarrierStopsMoreThanItsOwnSize() {
        PlayerMagicState state = withEndurance(MagicPassiveContent.ENDURANCE.maxLevel());
        float reduction = state.barrierReduction();
        assertTrue(reduction > 0.0f, "a fully levelled endurance deflects nothing");
        assertTrue(reduction < 1.0f, "the reduction reached total immunity");

        state.setBarrier(100);
        // Everything this barrier can cover, and not a point more.
        float coverable = 100.0f / (1.0f - reduction);
        assertEquals(0.0f, state.absorbDamage(coverable), 0.01f,
                "a blow inside what the barrier could cover still reached the health bar");
        assertEquals(0, state.barrier(), "covering exactly that blow should have emptied the barrier");
    }

    @Test
    void whatTheBarrierCannotCoverArrivesUndiminished() {
        PlayerMagicState state = withEndurance(MagicPassiveContent.ENDURANCE.maxLevel());
        state.setBarrier(20);
        float coverable = 20.0f / (1.0f - state.barrierReduction());

        float leftOver = state.absorbDamage(coverable + 50.0f);

        assertEquals(50.0f, leftOver, 0.01f,
                "the overflow was reduced too, which would make an empty barrier as good as a full one");
        assertEquals(0, state.barrier());
    }

    /** Absorption may never mint barrier, or leave a negative pool, at any level or any blow. */
    @Test
    void absorptionNeverLeavesThePoolOutsideItsBounds() {
        for (int level = 0; level <= MagicPassiveContent.ENDURANCE.maxLevel(); level++) {
            PlayerMagicState state = withEndurance(level);
            for (float blow : new float[] {0.4f, 1.0f, 7.0f, 63.0f, 240.0f, 5000.0f}) {
                state.setBarrier(50);
                float leftOver = state.absorbDamage(blow);
                assertTrue(state.barrier() >= 0, "level " + level + " left a negative barrier");
                assertTrue(state.barrier() <= 50, "level " + level + " minted barrier out of a hit");
                assertTrue(leftOver >= 0.0f, "level " + level + " healed the player with a " + blow + " hit");
                assertTrue(leftOver <= blow, "level " + level + " made a " + blow + " hit worse");
            }
        }
    }

    /** Every level widens the pool, and the reduction climbs to a ceiling well short of immunity. */
    @Test
    void levellingWidensThePoolAndDeepensTheReduction() {
        int previousPool = new PlayerMagicState().maxBarrier();
        float previousReduction = -1.0f;
        for (int level = 1; level <= MagicPassiveContent.ENDURANCE.maxLevel(); level++) {
            PlayerMagicState state = withEndurance(level);
            assertTrue(state.maxBarrier() > previousPool,
                    "level " + level + " did not widen the pool");
            assertTrue(state.barrierReduction() >= previousReduction,
                    "level " + level + " deflects less than the level below it");
            previousPool = state.maxBarrier();
            previousReduction = state.barrierReduction();
        }
        assertTrue(previousReduction < 0.5f, "a maxed endurance halves every blow, which is too much");
    }
}
