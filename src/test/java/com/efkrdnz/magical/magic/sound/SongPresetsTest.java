package com.efkrdnz.magical.magic.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SongPresetsTest {

    @Test
    void thereAreThreeAndEachHasItsOwnTempo() {
        assertEquals(3, SongPresets.ALL.size());
        Set<Tempo> tempos = new HashSet<>();
        for (SongPresets.Preset preset : SongPresets.ALL) {
            tempos.add(preset.score().tempo());
        }
        assertEquals(3, tempos.size());
    }

    @Test
    void everyPresetUsesEveryTrackAndHasAName() {
        for (SongPresets.Preset preset : SongPresets.ALL) {
            assertFalse(preset.name().isBlank(), preset.id());
            for (Track track : Track.values()) {
                int notes = 0;
                for (int bar = 0; bar < Score.BARS; bar++) {
                    notes += preset.score().notesInBar(track, bar);
                }
                assertTrue(notes > 0, preset.id() + " is silent on " + track);
            }
        }
    }

    @Test
    void theLastBarTurnsRoundRatherThanRepeatingTheFirst() {
        for (SongPresets.Preset preset : SongPresets.ALL) {
            Score score = preset.score();
            boolean differs = false;
            for (int step = 0; step < Score.BAR && !differs; step++) {
                for (Track track : Track.values()) {
                    for (int row = 0; row < track.rows(); row++) {
                        if (score.has(track, row, step) != score.has(track, row, 3 * Score.BAR + step)) {
                            differs = true;
                        }
                    }
                }
            }
            assertTrue(differs, preset.id() + " is one bar four times");
        }
    }

    @Test
    void theButtonWalksThemInOrderAndWraps() {
        assertEquals(1, SongPresets.next(0, 1));
        assertEquals(0, SongPresets.next(2, 1));
        assertEquals(2, SongPresets.next(0, -1));
        assertNotEquals(SongPresets.ALL.get(0).score(), SongPresets.ALL.get(1).score());
        assertEquals(SongPresets.pulse(), SongPresets.named("nonsense"));
    }
}
