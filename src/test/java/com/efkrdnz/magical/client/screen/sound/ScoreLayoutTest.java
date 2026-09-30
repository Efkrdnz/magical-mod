package com.efkrdnz.magical.client.screen.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.sound.Riff;
import com.efkrdnz.magical.magic.sound.Score;
import com.efkrdnz.magical.magic.sound.Track;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreLayoutTest {

    private static final int[] WIDTHS = {320, 427, 480, 640, 854, 1280};
    private static final int[] HEIGHTS = {240, 270, 360, 480, 720};

    @Test
    void everythingFitsTheScreenAndNothingOverlaps() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                ScoreLayout layout = new ScoreLayout(width, height);
                check(layout.songControls(), width, height, "song");
                check(layout.riffControls(), width, height, "riff");
            }
        }
    }

    @Test
    void everyCellIsFoundWhereItIsDrawnAndReadable() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                ScoreLayout layout = new ScoreLayout(width, height);
                String at = width + "x" + height;
                assertTrue(layout.cell() >= ScoreLayout.MIN_CELL, at + " cell " + layout.cell());
                assertEquals(0, Score.STEPS % layout.visibleSteps(), at);
                for (Track track : Track.values()) {
                    for (int row = 0; row < track.rows(); row++) {
                        for (int column = 0; column < layout.visibleSteps(); column++) {
                            Rect cell = layout.cell(track, row, column);
                            ScoreLayout.Cell hit = layout.cellAt(cell.x() + cell.w() / 2.0, cell.y() + cell.h() / 2.0);
                            assertEquals(new ScoreLayout.Cell(track, row, column), hit, at + " " + track + " " + row + " " + column);
                        }
                        assertTrue(layout.rowLabel(track, row).x() >= 0, at + " labels on screen");
                    }
                }
            }
        }
    }

    @Test
    void aCellIsAlwaysOneOfTheSizesTheAtlasDrawsItsBeadsAt() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                int cell = new ScoreLayout(width, height).cell();
                assertTrue(cell == ScoreLayout.LARGE_CELL || cell == ScoreLayout.SMALL_CELL, width + "x" + height + " cell " + cell);
            }
        }
    }

    @Test
    void aTrackGlyphSitsBesideItsOwnRowsAndClearOfTheNames() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                ScoreLayout layout = new ScoreLayout(width, height);
                String at = width + "x" + height;
                for (Track track : Track.values()) {
                    Rect glyph = layout.trackGlyph(track);
                    int top = layout.trackTop(track);
                    int bottom = top + track.rows() * layout.cell();
                    assertTrue(glyph.x() >= 0, at + " " + track + " glyph on screen");
                    assertTrue(glyph.y() >= top - ScoreLayout.TRACK_GAP && glyph.bottom() <= bottom + ScoreLayout.TRACK_GAP,
                            at + " " + track + " glyph " + glyph + " strays from its rows " + top + ".." + bottom);
                    for (int row = 0; row < track.rows(); row++) {
                        assertFalse(glyph.overlaps(layout.rowLabel(track, row)), at + " " + track + " glyph covers a name");
                        assertFalse(glyph.overlaps(layout.cell(track, row, 0)), at + " " + track + " glyph covers a cell");
                    }
                }
            }
        }
    }

    @Test
    void everyFamilyIconSitsOverItsOwnString() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                ScoreLayout layout = new ScoreLayout(width, height);
                for (int slot = 0; slot < Riff.MAX_NOTES; slot++) {
                    Rect icon = layout.familyIcon(slot);
                    assertEquals(slot, layout.slotAt(icon.x() + icon.w() / 2.0, icon.y() + icon.h() / 2.0), width + "x" + height);
                    assertTrue(icon.bottom() <= layout.strip(slot).y(), width + "x" + height + " icon over the string");
                }
            }
        }
    }

    @Test
    void theWholeLoopShowsOnAWideScreenAndPagesOnANarrowOne() {
        assertEquals(Score.STEPS, new ScoreLayout(640, 360).visibleSteps());
        assertTrue(new ScoreLayout(320, 240).pages() > 1);
    }

    @Test
    void theGapsBetweenTracksAreNotCells() {
        ScoreLayout layout = new ScoreLayout(640, 360);
        int gapY = layout.rowTop(Track.MELODY, 0) + layout.cell() + 1;
        assertEquals(null, layout.cellAt(layout.gridLeft() + 1, gapY));
    }

    @Test
    void everyPitchOfEveryStripIsFoundWhereItIsDrawn() {
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                ScoreLayout layout = new ScoreLayout(width, height);
                for (int slot = 0; slot < Riff.MAX_NOTES; slot++) {
                    Rect strip = layout.strip(slot);
                    assertEquals(slot, layout.slotAt(strip.x() + strip.w() / 2.0, strip.y() + 1), width + "x" + height);
                    for (int pitch = 0; pitch < Riff.PITCHES; pitch++) {
                        double y = layout.pitchTop(pitch) + layout.pitchCell() / 2.0;
                        assertEquals(pitch, layout.pitchAt(slot, y), width + "x" + height + " pitch " + pitch);
                    }
                    if (slot > 0) {
                        assertFalse(strip.overlaps(layout.strip(slot - 1)));
                    }
                }
            }
        }
    }

    private static void check(List<Rect> rects, int width, int height, String tab) {
        String at = tab + " " + width + "x" + height;
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0 && rect.right() <= width && rect.bottom() <= height,
                    at + " " + rect + " is off the screen");
        }
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(rects.get(i).overlaps(rects.get(j)), at + " " + rects.get(i) + " overlaps " + rects.get(j));
            }
        }
    }
}
