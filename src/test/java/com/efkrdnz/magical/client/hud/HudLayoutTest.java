package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.LoadoutSwitcherLayout;
import com.efkrdnz.magical.client.hud.HudLayout.Flow;
import com.efkrdnz.magical.client.hud.HudLayout.PoolsForm;
import com.efkrdnz.magical.client.hud.HudLayout.PoolsPlan;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.screen.CodexLayout;
import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The corner HUD laid out on paper, at every anchor and scale, so a collision fails here instead of
 * in the game. The same doctrine as {@code CodexLayoutTest}: a HUD that places its rows by numbers
 * inside draw calls collides with itself unnoticed.
 *
 * <p>The GUI it has to fit is 480x270, which is what vanilla's "auto" scale gives a 1080p window.
 * Vanilla's own furniture is reserved as rectangles: the hotbar band with the health and food rows
 * above it, the boss bar, and the potion icons in the top-right corner - and past those, the whole
 * of vanilla's centre column, because boss bars stack down it. There are no scale exemptions: the
 * block is small enough to clear all of it at the option's maximum. The centre column is swept at
 * every width from 480 up for the whole block, and from 384 up for the counts and the slots, which
 * give up scale rather than reach in.
 */
class HudLayoutTest {

    private static final int W = 480;
    private static final int H = 270;
    /** The option's range, and 0.8, which is what compact makes of the design scale. */
    private static final float[] SCALES = {0.5F, 0.75F, 0.8F, 1.0F, 1.25F, 1.5F};
    private static final float[] DESIGN_SCALES = {1.0F, 1.25F};

    private static final Rect HOTBAR_BAND = new Rect("vanilla hotbar band", 149, 220, 182, 50);
    private static final Rect BOSS_BAR = new Rect("vanilla boss bar", 149, 0, 182, 20);
    private static final Rect POTIONS = new Rect("vanilla potion icons", W - 130, 0, 130, 52);
    private static final Rect SCREEN = new Rect("screen", 0, 0, W, H);

    /** Vanilla's font: a digit is five pixels and one of spacing, and so is the slash. */
    private static final int DIGIT_W = 6;

    private static void assertDisjoint(List<Rect> shapes, String where) {
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                Rect a = shapes.get(i);
                Rect b = shapes.get(j);
                assertFalse(a.overlaps(b), where + ": " + a.name() + " overlaps " + b.name() + ": " + describe(a) + " vs " + describe(b));
            }
        }
    }

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    private static String where(HudAnchor anchor, float scale) {
        return anchor + " @" + scale;
    }

    private static Rect centreColumn(int guiWidth, int guiHeight) {
        return new Rect("vanilla centre column", guiWidth / 2 - HudLayout.CENTRE_COLUMN_HALF, 0, 2 * HudLayout.CENTRE_COLUMN_HALF, guiHeight);
    }

    @Test
    void theBlockNeverOverlapsItselfAndStaysOffVanillasFurniture() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                for (boolean slots : new boolean[] {true, false}) {
                    for (int lines = 0; lines <= HudLayout.READOUT_LINES_MAX; lines++) {
                        String state = where(anchor, scale) + " slots=" + slots + " lines=" + lines;
                        List<Rect> shapes = layout.blockShapes(slots, lines);
                        assertDisjoint(shapes, state);
                        for (Rect shape : shapes) {
                            assertTrue(SCREEN.contains(shape), state + ": " + shape.name() + " leaves the screen: " + describe(shape));
                            assertFalse(shape.overlaps(HOTBAR_BAND), state + ": " + shape.name() + " sits on the hotbar band: " + describe(shape));
                            if (anchor.bottom()) {
                                assertTrue(shape.bottom() <= H - HudLayout.HOTBAR_RESERVE, state + ": " + shape.name() + " dips into the hotbar rows: " + describe(shape));
                            } else {
                                assertFalse(shape.overlaps(BOSS_BAR), state + ": " + shape.name() + " sits on the boss bar: " + describe(shape));
                            }
                            if (anchor == HudAnchor.TOP_RIGHT) {
                                assertFalse(shape.overlaps(POTIONS), state + ": " + shape.name() + " sits on the potion icons: " + describe(shape));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Boss bars stack every nineteen units down the middle, and the hotbar's rows rise up it; from
     * a 480-wide GUI - 1080p on auto scale - the whole block keeps out of all of it at every scale,
     * giving up scale to do so.
     */
    @Test
    void nothingInTheCornerEntersVanillasCentreColumn() {
        for (int width = 480; width <= 640; width++) {
            Rect column = centreColumn(width, H);
            for (HudAnchor anchor : HudAnchor.values()) {
                for (float scale : SCALES) {
                    HudLayout layout = HudLayout.of(width, H, anchor, scale);
                    String at = where(anchor, scale) + " on " + width + "x" + H;
                    for (Rect shape : layout.blockShapes(true, HudLayout.READOUT_LINES_MAX)) {
                        assertFalse(shape.overlaps(column), at + ": " + shape.name() + " enters the centre column: " + describe(shape));
                    }
                    assertTrue(layout.poolsText().w() >= HudLayout.POOLS_TEXT_MIN_W, at + ": the counts have no room");
                }
            }
        }
    }

    /**
     * The scale the option asks for is kept wherever the counts fit beside the bars, and given up
     * only as far as they need: 1.5 on a 427-wide GUI would put the mana on the first boss bar.
     */
    @Test
    void theBlockGivesUpScaleRatherThanEnterTheColumn() {
        assertEquals(1.5F, HudLayout.of(640, 360, HudAnchor.TOP_LEFT, 1.5F).scale(), "a wide GUI lost scale for nothing");
        assertEquals(1.5F, HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.5F).scale(), "1080p lost scale for nothing");
        HudLayout narrow = HudLayout.of(427, 240, HudAnchor.TOP_LEFT, 1.5F);
        assertTrue(narrow.scale() < 1.5F, "1280x720 kept a scale whose counts do not fit");
        assertTrue(narrow.scale() >= 1.0F, "1280x720 gave up more scale than it needed: " + narrow.scale());
        assertEquals(HudLayout.MIN_SCALE, HudLayout.of(320, 240, HudAnchor.TOP_LEFT, 1.0F).scale(), "the narrowest GUI is not at the floor");
        // Below 480 the counts and the slots - level with the first two boss bars - still keep out,
        // at every scale, down to 384; only the readout lines, level with a third, reach in, and
        // never further than their floor takes them.
        for (int width = 384; width < 480; width++) {
            Rect column = centreColumn(width, H);
            for (HudAnchor anchor : HudAnchor.values()) {
                for (float scale : SCALES) {
                    HudLayout layout = HudLayout.of(width, H, anchor, scale);
                    String at = where(anchor, scale) + " on " + width;
                    assertEquals(HudLayout.READOUT_MIN_W, layout.readoutWidth(), at);
                    for (Rect shape : layout.blockShapes(true, 0)) {
                        assertFalse(shape.overlaps(column), at + ": " + shape.name() + " enters the centre column: " + describe(shape));
                    }
                }
            }
        }
    }

    @Test
    void neighboursKeepAPositiveGapAtTheDesignScales() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : DESIGN_SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                String at = where(anchor, scale);
                Rect mana = layout.manaBar();
                Rect barrier = layout.barrierBar();
                Rect counts = layout.poolsText();
                assertTrue(barrier.y() - mana.bottom() >= 1, at + ": the barrier touches the mana bar");
                int countsGap = anchor.right() ? mana.x() - counts.right() : counts.x() - mana.right();
                assertTrue(countsGap >= HudLayout.POOLS_GAP, at + ": the counts crowd the bars by " + countsGap);
                for (int k = 0; k < HudLayout.SLOTS; k++) {
                    Rect glyph = layout.glyph(k);
                    Rect cell = layout.cell(k);
                    assertTrue(cell.y() - glyph.bottom() >= 1, at + ": cell " + k + " touches its glyph");
                    if (k + 1 < HudLayout.SLOTS) {
                        assertTrue(layout.glyph(k + 1).x() - glyph.right() >= 2, at + ": glyphs " + k + " and " + (k + 1) + " touch");
                        assertTrue(layout.cell(k + 1).x() - cell.right() >= 2, at + ": cells " + k + " and " + (k + 1) + " touch");
                    }
                }
                // The pools line and the slots band, whichever way round the anchor stacks them.
                int slotsTop = layout.glyph(0).y();
                int slotsBottom = layout.cell(0).bottom();
                int bandGap = anchor.bottom()
                        ? Math.min(mana.y(), counts.y()) - slotsBottom
                        : slotsTop - Math.max(barrier.bottom(), counts.bottom());
                assertTrue(bandGap >= 2, at + ": the pools and the slots are only " + bandGap + " apart");
                Rect line0 = layout.readoutLine(0);
                int readoutGap = anchor.bottom() ? slotsTop - line0.bottom() : line0.y() - slotsBottom;
                assertTrue(readoutGap >= HudLayout.READOUT_GAP, at + ": the readouts crowd the slots by " + readoutGap);
                assertEquals(HudLayout.READOUT_STRIDE, Math.abs(layout.readoutLine(1).y() - line0.y()), at + ": readout stride");
            }
        }
    }

    @Test
    void theDesignGeometryIsPinned() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        assertEquals(new Rect("mana bar", 6, 6, 76, 4), layout.manaBar());
        assertEquals(new Rect("barrier bar", 6, 11, 76, 3), layout.barrierBar());
        assertEquals(new Rect("pools text", 85, 6, 64, 9), layout.poolsText());
        // Clear of the V key cell, which reaches a unit past the bars.
        assertEquals(new Rect("pools stacked line", 86, 16, 63, 9), layout.poolsStackedLine());
        assertEquals(new Rect("glyph 0", 6, 18, 16, 16), layout.glyph(0));
        assertEquals(new Rect("glyph 3", 66, 18, 16, 16), layout.glyph(3));
        assertEquals(new Rect("cell 0", 5, 35, 18, 9), layout.cell(0));
        assertEquals(new Rect("readout line 0", 6, 47, 143, 9), layout.readoutLine(0));
        assertEquals(new Rect("readout line 1", 6, 57, 143, 9), layout.readoutLine(1));
        // The third line ends on y 76, the row the B rail's first mark starts on at 240 tall.
        assertEquals(76, HudLayout.of(427, 240, HudAnchor.TOP_LEFT, 1.0F).readoutLine(2).bottom());
        assertEquals(76, LoadoutSwitcherLayout.mark(0, MagicContent.MAX_LOADOUTS, 240).y());
        // A line runs to the centre column: wider on a wider GUI, capped before it reads as a sentence.
        assertEquals(HudLayout.READOUT_MAX_W, HudLayout.of(640, 360, HudAnchor.TOP_LEFT, 1.0F).readoutWidth());
        assertEquals(HudLayout.READOUT_MIN_W, HudLayout.of(427, 240, HudAnchor.TOP_LEFT, 1.0F).readoutWidth());
        assertEquals(144, HudLayout.of(481, 270, HudAnchor.TOP_RIGHT, 1.0F).readoutWidth());
        // "82/100  49": the slash stands on 103 whatever the mana reads, and the barrier ends on 149.
        assertEquals(new PoolsPlan(PoolsForm.FULL, 103, 6, 149, 6), layout.planPools(3 * DIGIT_W, 4 * DIGIT_W, 3 * DIGIT_W));
    }

    @Test
    void theBandsHoldStillWhateverIsShownAndTheRowIsAPictureOfTheKeyboard() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                String at = where(anchor, scale);
                List<Rect> bare = layout.blockShapes(true, 0);
                List<Rect> full = layout.blockShapes(true, HudLayout.READOUT_LINES_MAX);
                assertEquals(bare, full.subList(0, bare.size()), at + ": a readout line moved the bars or the slots");
                for (int k = 0; k + 1 < HudLayout.SLOTS; k++) {
                    assertTrue(layout.glyph(k + 1).x() > layout.glyph(k).x(), at + ": Z is not the leftmost key");
                }
                assertEquals(layout.glyph(0).x(), layout.manaBar().x(), at + ": the mana bar does not start over Z");
                assertEquals(layout.glyph(HudLayout.SLOTS - 1).right(), layout.manaBar().right(), at + ": the mana bar does not end over V");
                assertEquals(layout.manaBar().x(), layout.barrierBar().x(), at);
                assertEquals(layout.manaBar().w(), layout.barrierBar().w(), at);
                for (int k = 0; k < HudLayout.SLOTS; k++) {
                    Rect glyph = layout.glyph(k);
                    assertEquals(glyph.w(), glyph.h(), at + ": glyph " + k + " is not square");
                    assertEquals(glyph.x() + glyph.w() / 2, layout.cell(k).centreX(), at + ": cell " + k + " is off its glyph");
                }
            }
        }
    }

    @Test
    void textBoxesNeverScaleAndTheirStringsFit() {
        for (float scale : SCALES) {
            HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, scale);
            int counts = layout.poolsText().w();
            assertEquals(HudLayout.TEXT_H, layout.poolsText().h());
            assertTrue(counts >= HudLayout.POOLS_TEXT_MIN_W && counts <= HudLayout.POOLS_TEXT_MAX_W, "at " + scale + " the counts box is " + counts + " wide");
            assertEquals(HudLayout.TEXT_H, layout.cell(0).h());
            assertEquals(HudLayout.CELL_W, layout.cell(0).w());
            assertEquals(HudLayout.TEXT_H, layout.readoutLine(1).h());
            assertTrue(layout.pitch() - HudLayout.CELL_W >= 2, "at " + scale + " neighbouring cells touch");
        }
        // "999" seconds and "99m" are three characters; four digits of mana is the widest count.
        assertTrue(HudLayout.CELL_W >= 3 * DIGIT_W, "a cell cannot hold three characters");
        assertTrue(HudLayout.POOLS_TEXT_MIN_W >= 4 * DIGIT_W, "the counts box cannot hold four digits");
        assertTrue(HudLayout.MORE_W >= 3 * DIGIT_W, "the marker cannot hold \"+99\"");
    }

    /**
     * The counts give way in a fixed order as the room shrinks - first the maximum, then the
     * barrier leaves the line for the one beside the slots - and every form fits where it is put,
     * at every width from 394, which is as narrow as the scale fit can promise them room: out of
     * vanilla's centre column, inside their boxes and off the slots. The barrier is never dropped:
     * it is half of what the old core showed.
     */
    @Test
    void theCountsDegradeTheMaximumFirstThenStackAndEveryFormFits() {
        int[][] maxima = {{3, 3}, {4, 4}, {3, 4}};
        for (int width : IntStream.concat(IntStream.rangeClosed(394, W), IntStream.of(640)).toArray()) {
            Rect column = centreColumn(width, H);
            for (HudAnchor anchor : HudAnchor.values()) {
                for (float scale : SCALES) {
                    HudLayout layout = HudLayout.of(width, H, anchor, scale);
                    for (int[] digits : maxima) {
                        int manaField = digits[0] * DIGIT_W;
                        int maxWidth = (digits[0] + 1) * DIGIT_W;
                        int barrierField = digits[1] * DIGIT_W;
                        PoolsPlan plan = layout.planPools(manaField, maxWidth, barrierField);
                        String at = where(anchor, scale) + " on " + width + " maxima " + Arrays.toString(digits) + " " + plan.form();
                        Rect mana = new Rect("mana count", plan.slashX() - manaField, plan.manaY(), manaField + (plan.showsMax() ? maxWidth : 0), HudLayout.TEXT_H);
                        Rect barrier = new Rect("barrier count", plan.barrierRight() - barrierField, plan.barrierY(), barrierField, HudLayout.TEXT_H);
                        Rect line = layout.poolsText();
                        Rect stacked = layout.poolsStackedLine();
                        assertTrue(line.contains(mana), at + ": the mana count leaves its box: " + describe(mana) + " in " + describe(line));
                        assertTrue(plan.form() == PoolsForm.STACKED ? stacked.contains(barrier) : line.contains(barrier),
                                at + ": the barrier count leaves its box: " + describe(barrier));
                        assertFalse(mana.overlaps(barrier), at + ": the counts overlap");
                        assertFalse(mana.overlaps(column) || barrier.overlaps(column), at + ": a count enters the centre column");
                        for (int k = 0; k < HudLayout.SLOTS; k++) {
                            assertFalse(barrier.overlaps(layout.glyph(k)) || barrier.overlaps(layout.cell(k)), at + ": the barrier count sits on slot " + k);
                            assertFalse(mana.overlaps(layout.glyph(k)) || mana.overlaps(layout.cell(k)), at + ": the mana count sits on slot " + k);
                        }
                    }
                }
            }
        }
        // A hundred of each: the whole reading at the design scale, no maximum a notch up, stacked at the top of the range.
        assertEquals(PoolsForm.FULL, HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F).planPools(18, 24, 18).form());
        assertEquals(PoolsForm.NO_MAX, HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.25F).planPools(18, 24, 18).form());
        assertEquals(PoolsForm.STACKED, HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.5F).planPools(18, 24, 18).form());
    }

    /** A stamp is a character of its line: it stands one unit above it, inside the band the sweep reserves. */
    @Test
    void aStampIsACharacterOfItsLine() {
        for (HudAnchor anchor : HudAnchor.values()) {
            HudLayout layout = HudLayout.of(W, H, anchor, 1.0F);
            Flow flow = layout.flow(new int[] {60, 60, 60, 60, 60}, 5);
            List<Rect> all = layout.blockShapes(true, HudLayout.READOUT_LINES_MAX);
            List<Rect> band = all.subList(layout.blockShapes(true, 0).size(), all.size());
            for (Rect token : flow.tokens()) {
                Rect stamp = HudLayout.stamp(token);
                assertEquals(new Rect(stamp.name(), token.x(), token.y() - 1, HudLayout.STAMP, HudLayout.STAMP), stamp);
                boolean inside = false;
                for (Rect line : band) {
                    inside |= line.contains(stamp);
                }
                assertTrue(inside, anchor + ": a stamp leaves the readout band: " + describe(stamp));
                assertTrue(HudLayout.STAMP_LEAD < token.w(), anchor + ": a token is no wider than its stamp");
            }
        }
    }

    @Test
    void readoutsFlowWithoutEverMovingAnEarlierOne() {
        int[] widths = {40, 52, 30, 66, 44, 58, 25, 70, 33, 61};
        for (HudAnchor anchor : HudAnchor.values()) {
            HudLayout layout = HudLayout.of(W, H, anchor, 1.0F);
            for (int split : new int[] {widths.length, 4, 0}) {
                String at = anchor + " passives from " + split;
                Flow all = layout.flow(widths, split);
                assertTrue(all.tokens().length > 2 && all.tokens().length <= HudLayout.READOUT_TOKENS_MAX, at + ": " + all.tokens().length + " tokens placed");
                for (int n = 1; n <= widths.length; n++) {
                    Flow prefix = layout.flow(Arrays.copyOf(widths, n), Math.min(split, n));
                    // The marker may take the last place of a flow that left something off; every other place holds.
                    int stable = prefix.dropped() == 0 ? prefix.tokens().length : prefix.tokens().length - 1;
                    for (int i = 0; i < stable && i < all.tokens().length; i++) {
                        assertEquals(all.tokens()[i], prefix.tokens()[i], at + ": token " + i + " moved when token " + (n - 1) + " arrived");
                    }
                }
                List<Rect> placed = new ArrayList<>(List.of(all.tokens()));
                assertEquals(placed.size(), all.sources().length, at + ": a placed token has no source");
                for (int i = 0; i < placed.size(); i++) {
                    Rect token = placed.get(i);
                    assertTrue(onALine(layout, token), at + ": a token is off every line: " + describe(token));
                    assertTrue(SCREEN.contains(token), at + ": a token leaves the screen: " + describe(token));
                    assertEquals(HudLayout.TEXT_H, token.h());
                    assertEquals(widths[all.sources()[i]], token.w(), at + ": token " + i + " was resized or given another's reading");
                    assertTrue(i == 0 || all.sources()[i] > all.sources()[i - 1], at + ": the sources are out of order");
                }
                if (all.more() != null) {
                    placed.add(all.more());
                    assertTrue(onALine(layout, all.more()), at + ": the marker is off every line");
                }
                assertEquals(all.dropped() > 0, all.more() != null, at + ": a reading was left off without saying so, or the marker says so for nothing");
                assertEquals(widths.length, all.tokens().length + all.dropped(), at + ": readings are missing from the count");
                assertDisjoint(placed, at + " tokens");
                if (anchor.right()) {
                    assertEquals(W - HudLayout.MARGIN, all.tokens()[0].right(), at + ": the first token does not hang from the edge");
                } else {
                    assertEquals(HudLayout.MARGIN, all.tokens()[0].x(), at + ": the first token does not start at the margin");
                }
            }
        }
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        assertEquals(0, layout.flow(new int[0], 0).tokens().length);
        assertNull(layout.flow(new int[0], 0).more());
    }

    private static boolean onALine(HudLayout layout, Rect token) {
        for (int line = 0; line < HudLayout.READOUT_LINES_MAX; line++) {
            Rect box = layout.readoutLine(line);
            if (token.y() == box.y() && box.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two resources, then three passives: widths only, as the flow sees them. It places what it is
     * given and never learns which readings those are or whether they are written out in full.
     */
    @Test
    void theResourcesAndThePassivesEachGetTheirOwnLines() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        Flow flow = layout.flow(new int[] {58, 79, 79, 21, 27}, 2);
        assertEquals(0, flow.dropped());
        assertNull(flow.more());
        assertEquals(List.of(
                new Rect("readout 0", 6, 47, 58, 9), new Rect("readout 1", 69, 47, 79, 9),
                new Rect("readout 2", 6, 57, 79, 9), new Rect("readout 3", 90, 57, 21, 9), new Rect("readout 4", 116, 57, 27, 9)),
                List.of(flow.tokens()));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, flow.sources());
        // A passive starts a line of its own even where it would fit beside a resource.
        Flow split = layout.flow(new int[] {40, 40}, 1);
        assertEquals(47, split.tokens()[0].y());
        assertEquals(57, split.tokens()[1].y());
        // With no resource at all the passives start on the first line.
        assertEquals(47, layout.flow(new int[] {40, 40}, 0).tokens()[0].y());
    }

    /** While a passive shows the resources get two lines, so the sins keep the third; the rest is counted, not lost. */
    @Test
    void theResourcesYieldTheLastLineAndWhatIsLeftOffIsCounted() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        int[] crowded = {70, 70, 70, 70, 70, 70, 30};
        Flow withSins = layout.flow(crowded, 6);
        assertEquals(3, withSins.tokens().length, "two resources and the sin");
        assertEquals(67, withSins.tokens()[2].y(), "the sin is not on the last line");
        // The third place holds the sin, which is the seventh reading and not the third: a reader
        // that paired the places with the readings by position would print a resource there.
        assertArrayEquals(new int[] {0, 1, 6}, withSins.sources());
        assertEquals(4, withSins.dropped());
        assertNotNull(withSins.more());
        assertEquals(new Rect("more", 6 + 30 + HudLayout.READOUT_TOKEN_GAP, 67, HudLayout.MORE_W, 9), withSins.more());

        Flow noSins = layout.flow(Arrays.copyOf(crowded, 6), 6);
        assertEquals(3, noSins.tokens().length, "without a passive the resources take every line");
        assertEquals(3, noSins.dropped());

        // No room for the marker anywhere: the last reading gives up its place to it.
        int line = layout.readoutWidth();
        Flow full = layout.flow(new int[] {line, line, line, line}, 4);
        assertEquals(2, full.tokens().length);
        assertArrayEquals(new int[] {0, 1}, full.sources());
        assertEquals(2, full.dropped());
        assertEquals(new Rect("more", 6, 67, HudLayout.MORE_W, 9), full.more());

        // A token wider than a line is left off, and so is everything after it in its group.
        Flow wide = layout.flow(new int[] {100, layout.readoutWidth() + 1, 10}, 3);
        assertEquals(1, wide.tokens().length);
        assertEquals(2, wide.dropped());
    }

    /**
     * Every 16:9 and 16:10 window on vanilla's auto scale among them. A top corner clears the rail
     * everywhere at the design scale and under it, 240 tall included - 1280x720, 1440p and 4K - and
     * at every scale from 256 tall; above the design scale on a 240-tall GUI it may not, and the
     * block stands down while the rail is drawn.
     */
    @Test
    void theRailRuleAgreesWithABruteForceCheckAndTheDefaultCornerClearsTheRail() {
        for (int[] gui : new int[][] {{W, H}, {640, 360}, {320, 240}, {427, 240}, {426, 240}, {455, 256}, {384, 240}, {426, 266}}) {
            for (HudAnchor anchor : HudAnchor.values()) {
                for (float scale : SCALES) {
                    HudLayout layout = HudLayout.of(gui[0], gui[1], anchor, scale);
                    boolean touches = false;
                    for (Rect rect : layout.blockShapes(true, HudLayout.READOUT_LINES_MAX)) {
                        for (CodexLayout.Rect rail : LoadoutSwitcherLayout.rects(MagicContent.MAX_LOADOUTS, gui[0], gui[1])) {
                            touches |= rect.overlaps(new Rect(rail.name(), rail.x(), rail.y(), rail.w(), rail.h()));
                        }
                    }
                    String at = where(anchor, scale) + " on " + gui[0] + "x" + gui[1];
                    assertEquals(!touches, layout.clearsLoadoutRail(), at + ": the rail rule disagrees with a brute-force check");
                    if (!anchor.bottom() && (gui[1] >= 256 || scale <= 1.0F)) {
                        assertTrue(layout.clearsLoadoutRail(), at + ": the top corner collides with the B rail");
                    }
                }
            }
        }
        assertFalse(HudLayout.of(W, H, HudAnchor.BOTTOM_LEFT, 1.0F).clearsLoadoutRail(), "the bottom-left block should meet the rail");
    }

    @Test
    void theCentreGroupIsDisjointAndInsideTheScreen() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        List<Rect> shapes = layout.centreShapes(HudLayout.STATUS_CHIPS_MAX);
        assertDisjoint(shapes, "centre group");
        for (Rect shape : shapes) {
            assertTrue(SCREEN.contains(shape), shape.name() + " leaves the screen");
            assertFalse(shape.overlaps(HOTBAR_BAND), shape.name() + " sits on the hotbar band");
        }
    }

    /** The rule flash sits over the crosshair like a totem pop: fixed size, every anchor, never on the crosshair itself. */
    @Test
    void theRuleFlashSitsAboveTheCrosshairAtEveryScaleAndAnchor() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                Rect plate = layout.ruleFlashPlate(HudLayout.RULE_FLASH_MAX_W);
                Rect caption = layout.ruleFlashCaption();
                String state = where(anchor, scale);
                assertTrue(SCREEN.contains(plate), state + ": the flash plate leaves the screen: " + describe(plate));
                assertTrue(SCREEN.contains(caption), state + ": the flash caption leaves the screen: " + describe(caption));
                assertTrue(caption.bottom() <= layout.centreY() - 8, state + ": the caption crowds the crosshair: " + describe(caption));
                assertTrue(plate.bottom() <= caption.y(), state + ": the caption sits inside the plate");
                assertFalse(plate.overlaps(BOSS_BAR), state + ": the flash sits on the boss bar: " + describe(plate));
                assertEquals(2 * HudLayout.TEXT_H + 2 * HudLayout.RULE_FLASH_PAD, plate.h(), state + ": the plate scales with the HUD");
                assertEquals(HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F).ruleFlashPlate(HudLayout.RULE_FLASH_MAX_W), plate,
                        state + ": the flash moved with the anchor or the scale");
                Rect narrow = layout.ruleFlashPlate(60);
                assertEquals(plate.centreX(), narrow.centreX(), state + ": a narrow formula is off centre");
                assertTrue(narrow.w() < plate.w(), state + ": a narrow formula gets the widest plate");
                assertTrue(plate.w() <= HudLayout.RULE_FLASH_MAX_W + 2 * HudLayout.RULE_FLASH_PAD, state + ": the plate ignores its cap");
            }
        }
    }
}
