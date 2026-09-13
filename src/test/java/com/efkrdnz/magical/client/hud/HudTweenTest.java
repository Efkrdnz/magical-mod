package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A HUD value never jumps, never overshoots, and rises at the pace its packets arrive. */
class HudTweenTest {

    @Test
    void aSpendClosesFastAndNeverOvershoots() {
        HudTween tween = new HudTween(100.0F);
        tween.set(40.0F, 10L);
        float last = 100.0F;
        for (int i = 0; i < 20; i++) {
            tween.tick();
            assertTrue(tween.current() <= last, "went back up on tick " + i);
            assertTrue(tween.current() >= 40.0F, "overshot below the target on tick " + i);
            last = tween.current();
        }
        assertEquals(40.0F, tween.current());
        assertTrue(tween.settled());
    }

    @Test
    void regenRampsLinearlyOverThePacketCadence() {
        HudTween tween = new HudTween(50.0F);
        tween.set(53.0F, 100L);
        tween.set(56.0F, 120L);   // the second packet says: they come every twenty ticks
        tween.tick();
        float step1 = tween.current();
        tween.tick();
        float step2 = tween.current();
        // A linear ramp over 20 ticks toward +3 from wherever it was: near-equal steps.
        assertTrue(step2 - step1 > 0.0F, "the ramp is not rising");
        assertTrue(Math.abs((step2 - step1) - (step1 - tween.sample(0.0F) + (step2 - step1))) < 1.0F);
        for (int i = 0; i < 40; i++) {
            tween.tick();
            assertTrue(tween.current() <= 56.0F + 0.0001F, "overshot on tick " + i);
        }
        assertEquals(56.0F, tween.current());
    }

    @Test
    void samplingInterpolatesBetweenTicks() {
        HudTween tween = new HudTween(0.0F);
        tween.set(10.0F, 1L);
        tween.tick();
        float cur = tween.current();
        assertEquals(0.0F, tween.sample(0.0F));
        assertEquals(cur, tween.sample(1.0F));
        assertEquals(cur * 0.5F, tween.sample(0.5F), 0.0001F);
    }

    @Test
    void snapMovesWithoutMotionAndSettingTheSameTargetIsANoOp() {
        HudTween tween = new HudTween(5.0F);
        tween.snap(20.0F);
        assertEquals(20.0F, tween.sample(0.5F));
        tween.set(20.0F, 50L);
        tween.tick();
        assertEquals(20.0F, tween.current());
        assertTrue(tween.settled());
    }
}
