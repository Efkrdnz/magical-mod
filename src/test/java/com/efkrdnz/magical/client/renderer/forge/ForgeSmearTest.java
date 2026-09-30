package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.ForgeElementKind;
import org.junit.jupiter.api.Test;

/**
 * The contract between the generated smear atlas and the geometry that samples it. A v that
 * strays one texel reads the next row's first line as the lip of this one, and a u that is not a
 * whole number of tiles round a ring draws a seam across every spin - both silently.
 */
class ForgeSmearTest {

    private static final float EPSILON = 1.0E-4f;

    @Test
    void aVNeverLeavesItsOwnRowNorTouchesItsClearBottomLine() {
        for (ForgeSmear.Row row : ForgeSmear.Row.values()) {
            float top = row.ordinal() * ForgeSmear.ROW_HEIGHT;
            for (int i = -2; i <= 22; i++) {
                float texel = ForgeSmear.v(row, i / 20.0f) * ForgeSmear.ATLAS_HEIGHT;
                assertTrue(texel >= top + 0.5f - EPSILON, row + " sampled above its row");
                assertTrue(texel <= top + ForgeSmear.ROW_HEIGHT - 1.5f + EPSILON,
                        row + " sampled its clear bottom line or the row below");
            }
        }
    }

    @Test
    void theLipIsTheSecondLineFromTheBottomOfTheRow() {
        for (ForgeSmear.Row row : ForgeSmear.Row.values()) {
            float texel = ForgeSmear.v(row, ForgeSmear.LIP) * ForgeSmear.ATLAS_HEIGHT;
            assertEquals(row.ordinal() * ForgeSmear.ROW_HEIGHT + 14.5f, texel, EPSILON, row.name());
        }
    }

    @Test
    void theTextureIsAnchoredToTheHeadOfTheBlade() {
        assertEquals(0.3f, ForgeSmear.head(1.0f, 5.0f, 0.3f), EPSILON, "the head moved off its own streaks");
        assertEquals(0.3f - 5.0f / ForgeSmear.TILE_BLOCKS, ForgeSmear.head(0.0f, 5.0f, 0.3f), EPSILON,
                "the tail is not a blade's length behind the head at the texel density");
    }

    @Test
    void aRingIsAlwaysAWholeNumberOfTiles() {
        for (float circumference = 0.1f; circumference < 40.0f; circumference += 0.37f) {
            int repeats = ForgeSmear.ringRepeats(circumference);
            assertTrue(repeats >= 1, "a small ring drew no tile at all");
            float end = ForgeSmear.ring(1.0f, circumference);
            assertEquals(Math.round(end), end, EPSILON, "a ring of " + circumference + " does not meet itself");
        }
    }

    @Test
    void anArcIsFaintestAtItsTailAndFullFromMostOfTheWayIn() {
        float previous = -1.0f;
        for (int i = 0; i <= 50; i++) {
            float ramp = ForgeSmear.headRamp(i / 50.0f);
            assertTrue(ramp >= previous - EPSILON, "the ramp dips on the way to the head");
            assertTrue(ramp > 0.0f, "some of the arc is drawn at nothing");
            previous = ramp;
        }
        assertEquals(1.0f, ForgeSmear.headRamp(1.0f), EPSILON);
        assertEquals(1.0f, ForgeSmear.headRamp(0.8f), EPSILON);
    }

    @Test
    void aLanceIsClearAtTheHiltAndFullTowardThePoint() {
        assertEquals(0.0f, ForgeSmear.lanceRamp(0.0f), EPSILON, "the hilt of a thrust is drawn at the eye");
        assertEquals(0.0f, ForgeSmear.lanceRamp(0.2f), EPSILON, "the hilt of a thrust is drawn at the eye");
        assertEquals(1.0f, ForgeSmear.lanceRamp(0.8f), EPSILON);
        float previous = 0.0f;
        for (int i = 0; i <= 50; i++) {
            float ramp = ForgeSmear.lanceRamp(i / 50.0f);
            assertTrue(ramp >= previous - EPSILON, "the lance dips on the way to its point");
            previous = ramp;
        }
    }

    @Test
    void everyElementWearsABladeRowAndNeverAUtilityRow() {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            ForgeSmear.Row row = ForgeSmear.of(kind);
            assertNotEquals(ForgeSmear.Row.SHEATH, row, kind.name());
            assertNotEquals(ForgeSmear.Row.LIP, row, kind.name());
        }
    }

    @Test
    void aClosedSweepIsARingAndAnOpenOneIsNot() {
        assertTrue(ForgeSmear.closed(360.0f));
        assertTrue(ForgeSmear.closed(-360.0f));
        assertTrue(!ForgeSmear.closed(300.0f));
    }
}
