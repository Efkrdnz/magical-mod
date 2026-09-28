package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.sigil.SigilMotion;
import org.junit.jupiter.api.Test;

class SigilMotionTest {

    @Test
    void everyMotionStartsWholeAndEndsInvisible() {
        for (SigilMotion motion : SigilMotion.values()) {
            assertTrue(motion.life() > 0, motion.name());
            assertTrue(motion.lifeJitter() >= 0, motion.name());
            assertEquals(1.0F, motion.alpha(0.0F), 1e-6F, motion.name());
            assertEquals(0.0F, motion.alpha(1.0F), 1e-6F, motion.name());
            for (float t = 0.0F; t < 1.0F; t += 0.05F) {
                assertTrue(motion.alpha(t) >= 0.0F && motion.alpha(t) <= 1.0F, motion + " alpha at " + t);
                assertTrue(motion.size(t) > 0.5F && motion.size(t) <= 1.0F, motion + " size at " + t);
            }
        }
    }

    @Test
    void eachMotionMovesTheWayItsNameSays() {
        assertTrue(SigilMotion.RISE.gravity() < 0.0F);
        assertEquals(0.0F, SigilMotion.HOVER.gravity());
        assertTrue(SigilMotion.HOVER.friction() < 0.7F, "a hover stops where it is written");
        assertEquals(0.0F, SigilMotion.DRIFT.gravity());
        assertTrue(SigilMotion.DRIFT.friction() >= 0.95F, "a drift carries its speed");
        assertTrue(SigilMotion.FALL.gravity() > 0.0F);
        assertTrue(SigilMotion.FALL.physics(), "a falling sigil settles on what it lands on");
        assertTrue(SigilMotion.FALL.sway() > 0.0F);
    }

    @Test
    void namesReadBack() {
        for (SigilMotion motion : SigilMotion.values()) {
            assertEquals(motion, SigilMotion.byName(motion.serializedName()).orElseThrow());
        }
        assertTrue(SigilMotion.byName("teleport").isEmpty());
    }
}
