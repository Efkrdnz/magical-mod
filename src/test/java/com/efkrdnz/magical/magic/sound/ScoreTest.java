package com.efkrdnz.magical.magic.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoreTest {

    @Test
    void theGridIsThirteenRowsOfSixtyFourSteps() {
        assertEquals(64, Score.STEPS);
        assertEquals(4, Score.BARS);
        int rows = 0;
        for (Track track : Track.values()) {
            assertEquals(rows, track.firstRow(), track + " starts where the one before it ends");
            rows += track.rows();
        }
        assertEquals(Track.TOTAL_ROWS, rows);
    }

    @Test
    void aBarRefusesTheNoteOverItsCap() {
        Score score = Score.empty();
        for (int step = 0; step < Track.PERCUSSION.barCap(); step++) {
            score = score.set(Track.PERCUSSION, 0, step, true).score();
        }
        Score.Edit over = score.set(Track.PERCUSSION, 1, 12, true);
        assertEquals(Score.Refusal.BAR_FULL, over.refusal());
        assertSame(score, over.score());
        assertTrue(score.set(Track.PERCUSSION, 0, Score.BAR, true).accepted(), "the next bar has its own cap");
    }

    @Test
    void theKitMaySoundTwoRowsOnAStepAndTheBassMayNot() {
        Score kit = Score.empty().set(Track.PERCUSSION, 0, 0, true).score().set(Track.PERCUSSION, 2, 0, true).score();
        assertTrue(kit.has(Track.PERCUSSION, 0, 0) && kit.has(Track.PERCUSSION, 2, 0));
        Score bass = Score.empty().set(Track.BASS, 0, 0, true).score().set(Track.BASS, 3, 0, true).score();
        assertFalse(bass.has(Track.BASS, 0, 0), "a second row on the step moves the voice");
        assertEquals(3, bass.voiceAt(Track.BASS, 0));
        assertEquals(1, bass.notesInBar(Track.BASS, 0));
    }

    @Test
    void movingAVoiceInAFullBarIsNotRefused() {
        Score score = Score.empty();
        for (int step = 0; step < Track.BASS.barCap(); step++) {
            score = score.set(Track.BASS, 0, step * 2, true).score();
        }
        Score.Edit moved = score.set(Track.BASS, 4, 0, true);
        assertTrue(moved.accepted());
        assertEquals(4, moved.score().voiceAt(Track.BASS, 0));
    }

    @Test
    void aForgedGridIsRepairedToTheEditorRules() {
        long[] raw = new long[Track.TOTAL_ROWS];
        raw[Track.PERCUSSION.firstRow()] = 0xFFFFL;
        raw[Track.MELODY.firstRow()] = 1L;
        raw[Track.MELODY.firstRow() + 1] = 1L;
        Score score = Score.of(Tempo.BRISK, Scale.MAJOR, raw);
        assertEquals(Track.PERCUSSION.barCap(), score.notesInBar(Track.PERCUSSION, 0));
        assertEquals(1, score.notesInBar(Track.MELODY, 0), "a chord in the melody keeps one note");
        assertEquals(score, Score.of(Tempo.BRISK, Scale.MAJOR, score.rows()), "a repaired grid is stable");
    }

    @Test
    void thePresetsAreWithinTheRulesAndNotEmpty() {
        for (String name : SongPresets.NAMES) {
            Score score = SongPresets.named(name);
            assertFalse(score.isEmpty(), name);
            assertEquals(score, Score.of(score.tempo(), score.scale(), score.rows()), name + " needs no repair");
        }
    }

    @Test
    void temposAreWholeTicksAStep() {
        assertEquals(150, Tempo.BRISK.bpm());
        assertEquals(100, Tempo.STEADY.bpm());
        assertEquals(75, Tempo.SLOW.bpm());
        assertEquals(Score.STEPS * 3, Tempo.STEADY.loopTicks());
    }
}
