package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrokeQuantizerTest {

    @Test
    void toUnitsMapsTheEndsOfTheUnitInterval() {
        assertEquals(0, StrokeQuantizer.toUnits(0f));
        assertEquals(1023, StrokeQuantizer.toUnits(1f));
    }

    @Test
    void toUnitsClampsOutOfRangeInput() {
        assertEquals(1023, StrokeQuantizer.toUnits(1.5f));
        assertEquals(0, StrokeQuantizer.toUnits(-1f));
    }

    @Test
    void fromUnitsRoundTripsEveryGridStepExactly() {
        for (int k = 0; k <= 1023; k++) {
            float v = k / 1023f;
            assertEquals(k, StrokeQuantizer.toUnits(v), "toUnits round-trip failed for k=" + k);
            assertEquals(v, StrokeQuantizer.fromUnits(StrokeQuantizer.toUnits(v)), 0.0f,
                    "fromUnits(toUnits(v)) round-trip failed for k=" + k);
        }
    }

    @Test
    void fromUnitsClampsOutOfRangeInput() {
        assertEquals(0f, StrokeQuantizer.fromUnits(-5));
        assertEquals(1f, StrokeQuantizer.fromUnits(5000));
    }

    @Test
    void inRangeAcceptsTheGridAndRejectsOneBeyondIt() {
        assertTrue(StrokeQuantizer.inRange(0));
        assertTrue(StrokeQuantizer.inRange(1023));
        assertFalse(StrokeQuantizer.inRange(1024));
        assertFalse(StrokeQuantizer.inRange(-1));
    }
}
