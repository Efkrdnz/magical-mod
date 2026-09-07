package com.efkrdnz.magical.forge.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlyphQualityTest {

    @Test
    void scoresMapOntoTheZeroToHundredQualityScale() {
        assertEquals(0, GlyphQuality.toQuality(0.45f));
        assertEquals(21, GlyphQuality.toQuality(0.55f));
        assertEquals(53, GlyphQuality.toQuality(0.70f));
        assertEquals(100, GlyphQuality.toQuality(0.92f));
    }

    @Test
    void scoresOutsideTheScaleAreClamped() {
        assertEquals(100, GlyphQuality.toQuality(1.0f));
        assertEquals(0, GlyphQuality.toQuality(0.0f));
        assertEquals(0, GlyphQuality.toQuality(-1.0f));
        assertEquals(100, GlyphQuality.toQuality(5.0f));
    }
}
