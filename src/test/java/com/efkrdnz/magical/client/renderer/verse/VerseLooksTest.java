package com.efkrdnz.magical.client.renderer.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import org.junit.jupiter.api.Test;

/** The look table is total over its three enums, and a body is never drawn smaller than it can be seen. */
class VerseLooksTest {

    @Test
    void everyLookHasARow() {
        for (VersePrototype.Look look : VersePrototype.Look.values()) {
            VerseLooks.Row row = VerseLooks.of(look);
            assertNotNull(row, look + " has a row");
            assertNotNull(row.shape(), look + " has a shape");
            switch (row.shape()) {
                case BOLT -> assertNotNull(row.filament(), look + " is a bolt and names its filament");
                case ORB -> assertNotNull(row.orb(), look + " is an orb and names its orb kind");
                case MARK -> assertNotNull(row.mark(), look + " is a mark and names its mark kind");
            }
            assertTrue(row.opacity() > 0.0F && row.opacity() <= 1.0F, look + " opacity in (0, 1]");
            assertTrue(row.count() > 0, look + " count positive");
        }
    }

    @Test
    void theStandingLooksAreMarksOnTheFloor() {
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.RING).shape());
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.BURST).shape());
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.PIT).shape());
        assertEquals(VerseLooks.Shape.ORB, VerseLooks.of(VersePrototype.Look.WORD).shape(), "a word hangs in the air, so it is not a floor mark");
    }

    @Test
    void everyBehaviourHasAGlyphAndEveryWakeAFilamentAndAColour() {
        for (Behaviour behaviour : Behaviour.values()) {
            assertNotNull(VerseLooks.glyph(behaviour), behaviour + " has a glyph");
        }
        for (Wake wake : Wake.values()) {
            assertNotNull(VerseLooks.wake(wake), wake + " has a filament");
            assertTrue(VerseLooks.wakeColor(wake) != 0, wake + " has a colour");
        }
    }

    @Test
    void aBodyIsNeverDrawnSmallerThanItCanBeSeenAndGrowsWithItsRadius() {
        VerseLooks.Row needle = VerseLooks.of(VersePrototype.Look.NEEDLE);
        assertTrue(VerseLooks.drawnSize(needle, 0.0F) >= VerseLooks.MIN_DRAWN, "a zero radius still draws");
        assertTrue(VerseLooks.drawnSize(needle, 0.5F) > VerseLooks.drawnSize(needle, 0.2F), "a wider body draws wider");
        assertEquals(0.5F * needle.scale(), VerseLooks.drawnSize(needle, 0.5F), 1.0E-6F, "the row's scale times the radius");
    }
}
