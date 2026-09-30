package com.efkrdnz.magical.magic.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiffTest {

    @Test
    void sixteenInstrumentsInFiveFamilies() {
        assertEquals(16, Instrument.values().length);
        Map<Family, Integer> counts = new EnumMap<>(Family.class);
        for (Instrument instrument : Instrument.values()) {
            counts.merge(instrument.family(), 1, Integer::sum);
        }
        assertEquals(Map.of(Family.DRUMS, 3, Family.LOW, 2, Family.KEYS, 3, Family.BELLS, 5, Family.STRINGS, 3), counts);
        assertEquals("iron_xylophone", Instrument.IRON_XYLOPHONE.id());
    }

    @Test
    void amplitudeBillsItsSquareAndPaysItsLine() {
        RiffNote quiet = RiffNote.resolve(Instrument.BELL, 12, 1);
        RiffNote loud = RiffNote.resolve(Instrument.BELL, 12, 5);
        assertEquals(25.0F, loud.mana() / quiet.mana(), 1.0E-4F);
        assertEquals(5.0F, loud.damage() / quiet.damage(), 1.0E-4F);
    }

    @Test
    void aHighNoteIsFastNarrowAndSharpAndALowOneIsWideFarAndHeavy() {
        for (Family family : Family.values()) {
            Instrument instrument = firstOf(family);
            RiffNote low = RiffNote.resolve(instrument, 0, 3);
            RiffNote high = RiffNote.resolve(instrument, 24, 3);
            assertTrue(high.damage() > low.damage(), family + " damage");
            assertTrue(high.speed() >= low.speed(), family + " speed");
            assertTrue(low.radius() > high.radius(), family + " radius");
            assertTrue(low.range() > high.range(), family + " range");
            assertTrue(low.knockback() > high.knockback(), family + " knockback");
        }
    }

    @Test
    void theDrumsAndTheKeysLandWhereTheyArePlayed() {
        assertEquals(1, RiffNote.resolve(Instrument.SNARE, 12, 3).lifeTicks());
        assertEquals(1, RiffNote.resolve(Instrument.HARP, 12, 3).lifeTicks());
        assertTrue(RiffNote.resolve(Instrument.BELL, 12, 3).lifeTicks() > 1);
    }

    @Test
    void aForgedRiffIsRepaired() {
        Riff riff = Riff.of(9, new int[] {0, 99, 5, 1, 2, 3, 4, 5, 6, 7}, new int[] {-4, 12, 40});
        assertEquals(Riff.MAX_NOTES, riff.length());
        assertEquals(Riff.MAX_AMPLITUDE, riff.amplitude());
        assertNull(riff.instrument(1), "an unknown instrument is a rest");
        assertEquals(0, riff.pitch(0));
        assertEquals(24, riff.pitch(2));
        assertEquals(Riff.CENTRE, riff.pitch(3));
        assertEquals(1, Riff.of(3, new int[0], new int[0]).length(), "never shorter than one slot");
    }

    @Test
    void theCentrePitchPlaysTheSampleAsRecorded() {
        assertEquals(1.0F, Riff.pitchRate(Riff.CENTRE), 1.0E-6F);
        assertEquals(0.5F, Riff.pitchRate(0), 1.0E-6F);
        assertEquals(2.0F, Riff.pitchRate(24), 1.0E-6F);
    }

    @Test
    void restsAreFreeAndCountInTheTime() {
        Riff one = Riff.of(3, new int[] {Instrument.BELL.ordinal()}, new int[] {12});
        Riff withRest = Riff.of(3, new int[] {Instrument.BELL.ordinal(), Riff.REST}, new int[] {12, 12});
        assertEquals(one.manaPerSecond() / 2.0F, withRest.manaPerSecond(), 1.0E-4F);
        assertEquals(3, one.withLength(3).length());
        assertTrue(one.withLength(3).rest(2));
    }

    private static Instrument firstOf(Family family) {
        for (Instrument instrument : Instrument.values()) {
            if (instrument.family() == family) {
                return instrument;
            }
        }
        throw new AssertionError(family);
    }
}
