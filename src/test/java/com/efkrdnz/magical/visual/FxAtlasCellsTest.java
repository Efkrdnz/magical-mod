package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.StampId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Two enums share one procedural SDF atlas and neither knows about the other.
 *
 * <p>{@code StampId.atlasCell()} is the constant's own ordinal and {@code EmblemId.atlasCell()} is
 * {@code FIRST_CELL + ordinal()}, so the two ranges meet at exactly one number and that number is
 * hard-coded in one of them. Adding a stamp walks the stamp range into the emblem range, and the
 * only symptom is that something draws the wrong mark: the atlas is generated at runtime from
 * these very ids, so both cells exist, both are painted, and the second painter silently wins.
 * There is no exception, no warning and no failing assertion anywhere else in the build.
 *
 * <p>It has already happened once. {@code StampId} held exactly thirty-two constants filling cells
 * 0..31 while {@code EmblemId.FIRST_CELL} was 32; the Sword school added {@code StampId.EDGE},
 * which took cell 32, and every draw of {@code EmblemId.BLANK} and every HUD card falling back to
 * the Sword school's default stamp began sampling the same square. This test is what should have
 * said so.
 */
class FxAtlasCellsTest {

    /** {@code FxTextures} paints a 16x16 grid of 64px cells into a 1024x1024 image. */
    private static final int ATLAS_CELLS = 16 * 16;

    /** {@code STAMP_BAND} packs a stamp's cell into a five-bit paramB field. */
    private static final int BANDABLE_CELLS = 32;

    @Test
    void noStampAndEmblemShareACell() {
        Map<Integer, String> owners = new HashMap<>();
        for (StampId stamp : StampId.values()) {
            owners.put(stamp.atlasCell(), "StampId." + stamp.name());
        }
        for (EmblemId emblem : EmblemId.values()) {
            String clash = owners.put(emblem.atlasCell(), "EmblemId." + emblem.name());
            assertEquals(null, clash, "EmblemId." + emblem.name() + " and " + clash
                    + " both draw into atlas cell " + emblem.atlasCell()
                    + "; move EmblemId.FIRST_CELL past the last stamp");
        }
    }

    /**
     * The emblems begin immediately after the stamps. A gap would waste one cell and nothing would
     * notice, but an overlap is the bug above, so the two are pinned to the same number here and
     * {@code EmblemId.FIRST_CELL} is the one place it is written down.
     */
    @Test
    void theEmblemsBeginWhereTheStampsEnd() {
        assertEquals(StampId.values().length, EmblemId.FIRST_CELL,
                "EmblemId.FIRST_CELL must be the stamp count: " + StampId.values().length
                        + " stamps occupy cells 0.." + (StampId.values().length - 1));
    }

    @Test
    void everyCellFitsInTheAtlas() {
        int last = EmblemId.values()[EmblemId.values().length - 1].atlasCell();
        assertTrue(last < ATLAS_CELLS,
                "the last emblem sits in cell " + last + " of an atlas holding " + ATLAS_CELLS
                        + "; FxTextures must grow its grid before another emblem is added");
    }

    /**
     * A stamp's cell is its ordinal, so a stamp past 31 cannot ride the five-bit field
     * {@code STAMP_BAND} packs it into. {@code EDGE} is the thirty-third and the Sword school bands
     * with {@code TICK_BAND} instead, which is why it is legal; the next one added has to make the
     * same choice deliberately rather than discover it in a screenshot.
     */
    @Test
    void aStampPastTheFiveBitBandIsADeliberateChoice() {
        int past = 0;
        for (StampId stamp : StampId.values()) {
            if (stamp.atlasCell() >= BANDABLE_CELLS) {
                past++;
            }
        }
        assertEquals(1, past, "only StampId.EDGE may sit past cell " + (BANDABLE_CELLS - 1)
                + " without a band that can carry it; found " + past + " such stamps");
    }
}
