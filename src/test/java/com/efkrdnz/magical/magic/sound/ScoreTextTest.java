package com.efkrdnz.magical.magic.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoreTextTest {

    @Test
    void aSongComesBackExactlyAsItWasWritten() {
        for (SongPresets.Preset preset : SongPresets.ALL) {
            String text = ScoreText.writeSong(preset.score(), preset.name());
            ScoreText.Read read = ScoreText.read(text);
            assertTrue(read.ok(), preset.id() + " " + read.problem());
            assertEquals(preset.score(), read.song(), preset.id());
            assertEquals(preset.name(), read.name());
            assertEquals(0, read.dropped());
        }
    }

    @Test
    void aRiffComesBackExactlyAsItWasWritten() {
        Riff riff = Riff.of(4, new int[] {Instrument.BELL.ordinal(), Riff.REST, Instrument.GUITAR.ordinal()}, new int[] {19, 12, 3});
        ScoreText.Read read = ScoreText.read(ScoreText.writeRiff(riff));
        assertTrue(read.isRiff());
        assertEquals(riff, read.riff());
    }

    @Test
    void theBassAndMelodyAreWrittenAsScaleDegreesAndTheKitAsMarks() {
        Score score = Score.empty().set(Track.MELODY, 4, 0, true).score().set(Track.BASS, 0, 1, true).score()
                .set(Track.PERCUSSION, 2, 2, true).score();
        String text = ScoreText.writeSong(score, "");
        assertTrue(text.contains("\"melody\": \"5...............|"), text);
        assertTrue(text.contains("\"bass\": \".1..............|"), text);
        assertTrue(text.contains("\"hat\": \"..x.............|"), text);
        assertFalse(text.contains("\"name\""), "an unnamed song writes no name");
    }

    @Test
    void aLineOfOneBarIsTheWholeLoopAndAShortOneIsPadded() {
        ScoreText.Read read = ScoreText.read("{\"magical_song\":1,\"kick\":\"x...x...x...x...\",\"melody\":\"1 2\"}");
        assertTrue(read.ok());
        for (int bar = 0; bar < Score.BARS; bar++) {
            assertEquals(4, read.song().notesInBar(Track.PERCUSSION, bar), "bar " + bar);
        }
        assertEquals(0, read.song().voiceAt(Track.MELODY, 0));
        assertEquals(1, read.song().voiceAt(Track.MELODY, 1));
        assertEquals(-1, read.song().voiceAt(Track.MELODY, 2));
        assertEquals(Tempo.STEADY, read.song().tempo());
        assertEquals(Scale.MINOR, read.song().scale());
    }

    @Test
    void aBarOverItsCapIsTrimmedAndCounted() {
        ScoreText.Read read = ScoreText.read("{\"magical_song\":1,\"bass\":\"1111111111111111\"}");
        assertTrue(read.ok());
        assertEquals(Track.BASS.barCap(), read.song().notesInBar(Track.BASS, 0));
        assertEquals((16 - Track.BASS.barCap()) * Score.BARS, read.dropped());
    }

    @Test
    void whatIsNotMusicSaysWhy() {
        assertEquals(ScoreText.Problem.EMPTY, ScoreText.read("   ").problem());
        assertEquals(ScoreText.Problem.NOT_JSON, ScoreText.read("{nope").problem());
        assertEquals(ScoreText.Problem.NOT_MUSIC, ScoreText.read("{\"tempo\":\"slow\"}").problem());
        assertEquals(ScoreText.Problem.NOT_MUSIC, ScoreText.read("[1,2]").problem());
        assertEquals(ScoreText.Problem.NEWER_VERSION, ScoreText.read("{\"magical_song\":2}").problem());
        assertEquals(ScoreText.Problem.BAD_TEMPO, ScoreText.read("{\"magical_song\":1,\"tempo\":\"presto\"}").problem());
        assertEquals(ScoreText.Problem.BAD_SCALE, ScoreText.read("{\"magical_song\":1,\"scale\":\"lydian\"}").problem());
        assertEquals(ScoreText.Problem.BAD_ROW, ScoreText.read("{\"magical_song\":1,\"melody\":\"1..6\"}").problem());
        assertEquals(ScoreText.Problem.BAD_ROW, ScoreText.read("{\"magical_song\":1,\"kick\":\"" + "x".repeat(65) + "\"}").problem());
        assertEquals(ScoreText.Problem.TOO_LONG, ScoreText.read(" ".repeat(ScoreText.MAX_CHARS) + "{}").problem());
        assertEquals(ScoreText.Problem.BAD_AMPLITUDE, ScoreText.read("{\"magical_riff\":1,\"amplitude\":9,\"notes\":[\"harp\"]}").problem());
        assertEquals(ScoreText.Problem.BAD_NOTE, ScoreText.read("{\"magical_riff\":1,\"notes\":[\"kazoo:3\"]}").problem());
        assertEquals(ScoreText.Problem.BAD_NOTE, ScoreText.read("{\"magical_riff\":1,\"notes\":[\"harp:25\"]}").problem());
        assertEquals(ScoreText.Problem.BAD_NOTE, ScoreText.read("{\"magical_riff\":1,\"notes\":[]}").problem());
    }

    @Test
    void aRiffLongerThanEightKeepsEightAndCountsTheRest() {
        ScoreText.Read read = ScoreText.read("{\"magical_riff\":1,\"notes\":[\"harp\",\"harp\",\"harp\",\"harp\",\"harp\",\"harp\",\"harp\",\"harp\",\"bell:3\",\"bell:4\"]}");
        assertTrue(read.ok());
        assertEquals(Riff.MAX_NOTES, read.riff().length());
        assertEquals(2, read.dropped());
        assertEquals(Riff.CENTRE, read.riff().pitch(0), "a note with no pitch is the sample as recorded");
    }

    @Test
    void aNameCannotCarryFormattingOrRunOn() {
        ScoreText.Read read = ScoreText.read("{\"magical_song\":1,\"name\":\"§cRed§r " + "a".repeat(80) + "\"}");
        assertFalse(read.name().contains("§"));
        assertTrue(read.name().length() <= ScoreText.MAX_NAME);
    }
}
