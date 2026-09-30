package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The land answers between half and half again, and the word the caster hears follows the number. */
class WellspringTest {

    @Test
    void theReadingSpansHalfToHalfAgain() {
        assertEquals(Wellspring.MIN, Wellspring.of(0.0D), 1.0E-6F);
        assertEquals(Wellspring.MAX, Wellspring.of(1.0D), 1.0E-6F);
        assertEquals(1.0F, Wellspring.of(0.5D), 1.0E-6F);
        assertEquals(Wellspring.MIN, Wellspring.of(-3.0D), 1.0E-6F, "a negative fraction is clamped");
        assertEquals(Wellspring.MAX, Wellspring.of(7.0D), 1.0E-6F, "an over-full fraction is clamped");
    }

    @Test
    void nothingSampledIsTheFaintestReading() {
        assertEquals(Wellspring.MIN, Wellspring.sky(0, 0), 1.0E-6F);
        assertEquals(Wellspring.MIN, Wellspring.mass(0, 0), 1.0E-6F);
    }

    @Test
    void theNetherIsHeatBeforeABlockIsCounted() {
        float overworld = Wellspring.heat(0, false, 64);
        float nether = Wellspring.heat(0, true, 64);
        assertEquals(Wellspring.MIN, overworld, 1.0E-6F);
        assertTrue(nether > overworld + 0.3F, "the Nether answers a caldera with no lava in sight");
        assertTrue(Wellspring.heat(0, false, -20) > overworld, "the deep rock is warm");
        assertEquals(Wellspring.MAX, Wellspring.heat(Wellspring.HEAT_FULL, false, 64), 1.0E-6F);
    }

    @Test
    void aFullSeaIsAFullWall() {
        assertEquals(Wellspring.MAX, Wellspring.water(Wellspring.WATER_FULL), 1.0E-6F);
        assertEquals(Wellspring.MAX, Wellspring.water(Wellspring.WATER_FULL * 4), 1.0E-6F);
        assertTrue(Wellspring.water(10) < 1.0F, "a trickle is under par");
    }

    @Test
    void theWordFollowsTheNumber() {
        assertEquals(Wellspring.Word.FAINT, Wellspring.word(Wellspring.MIN));
        assertEquals(Wellspring.Word.STEADY, Wellspring.word(1.0F));
        assertEquals(Wellspring.Word.STRONG, Wellspring.word(1.2F));
        assertEquals(Wellspring.Word.OVERWHELMING, Wellspring.word(Wellspring.MAX));
        Wellspring.Word last = Wellspring.Word.FAINT;
        for (float r = Wellspring.MIN; r <= Wellspring.MAX; r += 0.01F) {
            Wellspring.Word now = Wellspring.word(r);
            assertTrue(now.ordinal() >= last.ordinal(), "the word never falls as the reading rises");
            last = now;
        }
    }
}
