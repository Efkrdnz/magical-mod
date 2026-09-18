package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubspaceSunTest {

    @Test
    @DisplayName("a lit line is held right down under a clear noon and let go at midnight")
    void theSunSetsTheCeiling() {
        assertEquals(SubspaceSun.DAY_LIFT, SubspaceSun.lift(0), 1.0E-6F);
        assertEquals(SubspaceSun.NIGHT_LIFT, SubspaceSun.lift(SubspaceSun.DARKEST_SKY), 1.0E-6F);
        assertTrue(SubspaceSun.DAY_LIFT < SubspaceSun.NIGHT_LIFT, "daylight is not the harder case");
    }

    @Test
    @DisplayName("dusk moves through it rather than snapping at a threshold")
    void itRisesSmoothly() {
        float previous = -1.0F;
        for (int darkness = 0; darkness <= SubspaceSun.DARKEST_SKY; darkness++) {
            float lift = SubspaceSun.lift(darkness);
            assertTrue(lift > previous, "the burnish stalled at sky darkness " + darkness);
            previous = lift;
        }
    }

    @Test
    @DisplayName("a sky outside the scale is still a sky")
    void nothingEscapesTheEnds() {
        assertEquals(SubspaceSun.DAY_LIFT, SubspaceSun.lift(-4), 1.0E-6F);
        assertEquals(SubspaceSun.NIGHT_LIFT, SubspaceSun.lift(99), 1.0E-6F);
    }
}
