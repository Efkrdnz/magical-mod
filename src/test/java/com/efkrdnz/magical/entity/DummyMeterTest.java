package com.efkrdnz.magical.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DummyMeterTest {
    private static DummyMeter.Hit hit(int tick, float raw, float taken, float armor, String source) {
        return new DummyMeter.Hit(tick, raw, taken, armor, 0F, 0F, 0F, source);
    }

    @Test
    void anEmptyMeterSaysNothing() {
        assertTrue(new DummyMeter().lines(0).isEmpty());
    }

    @Test
    void theLastHitShowsWhatWasDealtAndWhatWasTaken() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 12F, 8.4F, 3.6F, "Iron Sword"));
        assertEquals("Last  12.0 → 8.4  (-30%)  Iron Sword", meter.lines(0).get(0));
    }

    @Test
    void anUnreducedHitSaysSoWithoutAPercentage() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 6F, 6F, 0F, "Fist"));
        assertEquals("Last  6.0  Fist", meter.lines(0).get(0));
    }

    @Test
    void oneHitIsANumberNotARate() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 500F, 500F, 0F, "Big"));
        assertEquals(0F, meter.runDps());
        assertTrue(meter.lines(0).get(1).startsWith("DPS  -"), meter.lines(0).get(1));
    }

    @Test
    void theRunIsTakenAndRawDamageOverTheTimeBetweenFirstAndLastHit() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 10F, 5F, 5F, "A"));
        meter.record(hit(20, 10F, 5F, 5F, "A"));
        meter.record(hit(40, 10F, 5F, 5F, "A"));
        // 15 taken over 2 seconds, 30 dealt over the same 2.
        assertEquals(7.5F, meter.runDps(), 1e-4F);
        assertEquals(15F, meter.rawRunDps(), 1e-4F);
        List<String> lines = meter.lines(40);
        assertEquals("DPS 7.5   raw 15.0   last 5s 7.5", lines.get(1));
        assertEquals("Dealt 30.0 → taken 15.0  (-50%)  over 2.0s", lines.get(2));
        assertEquals("Hits 3   avg 5.0   peak 5.0", lines.get(3));
        assertEquals("Cut  armor 15.0", lines.get(4));
    }

    @Test
    void everyKindOfReductionIsNamedAndWhatIsLeftOverIsOther() {
        DummyMeter meter = new DummyMeter();
        // 20 offered: armor 4, enchantments 2, effects 1, absorption 3, and 2 more the magic took.
        meter.record(new DummyMeter.Hit(0, 20F, 8F, 4F, 2F, 1F, 3F, "Spell"));
        assertEquals("Cut  armor 4.0  ench 2.0  effects 1.0  absorb 3.0  other 2.0", meter.lines(0).get(4));
    }

    @Test
    void anAmplifiedHitReadsAsAGainNotACut() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 10F, 12F, 0F, "Boosted"));
        assertEquals("Last  10.0 → 12.0  (+20%)  Boosted", meter.lines(0).get(0));
        assertEquals("Cut  other -2.0", meter.lines(0).get(4));
    }

    @Test
    void theSourcesAreRankedByWhatTheyLanded() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 2F, 2F, 0F, "Small"));
        meter.record(hit(1, 6F, 6F, 0F, "Large"));
        List<String> lines = meter.lines(1);
        // No reduction, so no Cut line: the sources follow the hits line.
        assertEquals("  Large  6.0 (75%)", lines.get(4));
        assertEquals("  Small  2.0 (25%)", lines.get(5));
    }

    @Test
    void theWindowForgetsWhatIsOlderThanFiveSeconds() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 50F, 50F, 0F, "Old"));
        meter.record(hit(90, 10F, 10F, 0F, "New"));
        // At tick 150 the first hit is 150 ticks old and the second 60: only the 10 remains, over 5s.
        assertEquals(2F, meter.windowDps(150), 1e-4F);
    }

    @Test
    void aLongSilenceStartsANewRunWhenTheNextHitLands() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 50F, 50F, 0F, "Old"));
        meter.record(hit(DummyMeter.IDLE_RESET_TICKS + 1, 7F, 7F, 0F, "New"));
        assertEquals("Hits 1   avg 7.0   peak 7.0", meter.lines(DummyMeter.IDLE_RESET_TICKS + 1).get(3));
    }

    @Test
    void largeNumbersAreShortened() {
        DummyMeter meter = new DummyMeter();
        meter.record(hit(0, 23000F, 23000F, 0F, "Huge"));
        assertEquals("Last  23.0k  Huge", meter.lines(0).get(0));
    }
}
