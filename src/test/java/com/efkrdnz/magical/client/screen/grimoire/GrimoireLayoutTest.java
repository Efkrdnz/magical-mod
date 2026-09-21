package com.efkrdnz.magical.client.screen.grimoire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.incantation.Grimoire;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Grimoire laid out on paper, at every screen size the game can hand us: nothing drawn lands on
 * anything else, everything sits inside its block and the block inside the screen, every hit-test
 * answers for exactly the rectangle it is drawn as, a drop lands on the slot boundary nearest the
 * cursor and never past the end, and the row has as many slots as an incantation may hold. The
 * layout has no font, so the words here have the widths the default font gives them.
 */
class GrimoireLayoutTest {

    private static final int[] WIDTHS = {320, 427, 480, 640, 854, 1280};
    private static final int[] HEIGHTS = {240, 300, 360, 480, 720};

    /** INCANTATION I to IV in capitals. */
    private static final int[] TAB_WIDTHS = {70, 74, 78, 76};
    /** All, Projectile, Static, Modifier, Multicast, Material, Control, Utility, Passive. */
    /** All and the six types the catalogue has verses of, as the words measure. */
    /** All and the eight types in their order: All, Projectile, Static, Modifier, Multicast, Material, Control, Utility, Passive. */
    private static final int[] CATEGORY_WORDS = {12, 51, 28, 39, 43, 39, 37, 27, 38};
    private static final int COUNT = 100;

    private static void assertDisjoint(List<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                assertFalse(a.overlaps(b), a.name() + " overlaps " + b.name() + ": " + describe(a) + " vs " + describe(b));
            }
        }
    }

    private static void assertInside(Rect outer, List<Rect> rects) {
        for (Rect rect : rects) {
            assertTrue(outer.contains(rect), rect.name() + " escapes " + outer.name() + ": "
                    + describe(rect) + " outside " + describe(outer));
        }
    }

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    private static double cx(Rect r) {
        return r.x() + r.w() / 2.0D;
    }

    private static double cy(Rect r) {
        return r.y() + r.h() / 2.0D;
    }

    private static int[] categoryWidths(int blockWidth) {
        boolean words = GrimoireLayout.categoriesFitWithWords(blockWidth, CATEGORY_WORDS);
        int[] widths = new int[CATEGORY_WORDS.length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = words ? GrimoireLayout.categoryWidth(CATEGORY_WORDS[i]) : GrimoireLayout.ICON;
        }
        return widths;
    }

    private static GrimoireLayout.Controls controls(int blockWidth, int visibleRows) {
        return GrimoireLayout.controls(blockWidth, visibleRows, 36, 6, 6, 6, 155, 24, 27);
    }

    @Test
    void theRowHasASlotForEveryVerseAnIncantationMayHoldAndATabPerSlot() {
        assertEquals(ReciteCaps.MAX_VERSES, GrimoireLayout.SLOT_COUNT);
        assertEquals(Grimoire.SLOTS, GrimoireLayout.TAB_COUNT);
    }

    @Test
    void nothingDrawnLandsOnAnythingElseAtAnyScreenSize() {
        for (int guiWidth : WIDTHS) {
            for (int guiHeight : HEIGHTS) {
                int blockWidth = GrimoireLayout.blockWidth(guiWidth);
                int columns = GrimoireLayout.gridColumns(blockWidth);
                int rows = GrimoireLayout.gridRows(COUNT, columns);
                int visible = GrimoireLayout.gridRowsVisible(guiHeight, rows);
                assertTrue(visible >= 1, guiWidth + "x" + guiHeight + " shows no grid");
                Rect block = GrimoireLayout.block(guiWidth, guiHeight, visible);
                Rect screen = new Rect("screen", 0, 0, guiWidth, guiHeight);
                assertTrue(screen.contains(block), guiWidth + "x" + guiHeight + ": the block escapes the screen " + describe(block));
                List<Rect> rects = GrimoireLayout.rects(blockWidth, visible, columns, COUNT, TAB_WIDTHS,
                        categoryWidths(blockWidth), controls(blockWidth, visible));
                assertDisjoint(rects);
                assertInside(new Rect("block", 0, 0, block.w(), block.h()), rects);
            }
        }
    }

    @Test
    void wordsAreClampedSoARowAlwaysFitsItsBlock() {
        int[] wide = {200, 200, 200, 200, 200};
        List<Rect> row = GrimoireLayout.wordsRow("word", 300, 0, wide, 16);
        assertDisjoint(row);
        assertInside(new Rect("block", 0, 0, 300, GrimoireLayout.LINE_H), row);
        for (int i = 1; i < row.size(); i++) {
            assertTrue(row.get(i).x() > row.get(i - 1).right(), "the words are out of order");
        }
        List<Rect> narrow = GrimoireLayout.wordsRow("word", 300, 0, new int[] {20, 30}, 16);
        assertEquals(20, narrow.get(0).w(), "a word that fits is not clipped");
        assertEquals(30, narrow.get(1).w());
        assertEquals(narrow.get(0).right() + 16, narrow.get(1).x(), "the gap is kept when there is room");
        int total = 20 + 16 + 30;
        assertEquals((300 - total) / 2, narrow.get(0).x(), "the group is centred");
        List<Rect> mixed = GrimoireLayout.wordsRow("word", 300, 0, new int[] {100, 20, 60}, 16);
        assertEquals(100, mixed.get(0).w(), "a row that fits keeps every word at its own width");
        assertEquals(20, mixed.get(1).w());
        assertEquals(60, mixed.get(2).w());
        assertEquals((300 - (100 + 16 + 20 + 16 + 60)) / 2, mixed.get(0).x());
        List<Rect> tight = GrimoireLayout.wordsRow("word", 250, 0, new int[] {120, 120}, 16);
        assertEquals(120, tight.get(0).w(), "the gap gives way before a word does");
        assertEquals(120, tight.get(1).w());
        assertEquals(10, tight.get(1).x() - tight.get(0).right());
        assertTrue(GrimoireLayout.wordsFit(300, new int[] {100, 20, 60}, 16));
        assertFalse(GrimoireLayout.wordsFit(250, new int[] {120, 120}, 16));
    }

    @Test
    void theCaptionHasALineForTheNameAndTwoForWhatItDoes() {
        assertEquals(3, GrimoireLayout.CAPTION_LINES);
        Rect caption = GrimoireLayout.caption(GrimoireLayout.blockWidth(640), 4);
        assertEquals(3 * GrimoireLayout.LINE_H, caption.h());
        assertEquals(caption.bottom() + GrimoireLayout.SLOTS_GAP, GrimoireLayout.slotsY(4), "the row sits under the whole caption");
    }

    @Test
    void theTabsKeepTheirNamesWhereTheyFitAndFallBackToTheirNumerals() {
        assertTrue(GrimoireLayout.tabsFitWithWords(GrimoireLayout.blockWidth(640), TAB_WIDTHS));
        assertFalse(GrimoireLayout.tabsFitWithWords(GrimoireLayout.blockWidth(320), TAB_WIDTHS));
        int[] numerals = {4, 8, 12, 10};
        assertTrue(GrimoireLayout.tabsFitWithWords(GrimoireLayout.blockWidth(320), numerals));
        List<Rect> tabs = GrimoireLayout.tabs(GrimoireLayout.blockWidth(320), numerals);
        assertDisjoint(tabs);
        for (int i = 0; i < numerals.length; i++) {
            assertEquals(numerals[i], tabs.get(i).w(), "numeral " + i + " is clipped");
        }
    }

    @Test
    void theCategoriesKeepTheirWordsWhereTheyFitAndGiveThemUpWhereTheyDoNot() {
        assertTrue(GrimoireLayout.categoriesFitWithWords(GrimoireLayout.blockWidth(640), CATEGORY_WORDS));
        assertFalse(GrimoireLayout.categoriesFitWithWords(GrimoireLayout.blockWidth(320), CATEGORY_WORDS));
        int[] glyphsOnly = categoryWidths(GrimoireLayout.blockWidth(320));
        for (int w : glyphsOnly) {
            assertEquals(GrimoireLayout.ICON, w);
        }
    }

    @Test
    void everyGridCellAnswersToItsOwnCentreOnly() {
        int blockWidth = GrimoireLayout.blockWidth(640);
        int columns = GrimoireLayout.gridColumns(blockWidth);
        int rows = GrimoireLayout.gridRows(COUNT, columns);
        int visible = GrimoireLayout.gridRowsVisible(360, rows);
        assertEquals(rows, visible, "a hundred verses fit without scrolling at 640x360");
        for (int index = 0; index < COUNT; index++) {
            Rect cell = GrimoireLayout.gridCell(blockWidth, columns, index, 0);
            assertTrue(cell.contains(GrimoireLayout.gridIcon(cell)), "the icon escapes cell " + index);
            assertEquals(index, GrimoireLayout.gridIndexAt(blockWidth, columns, visible, 0, COUNT, cx(cell), cy(cell)));
        }
        Rect first = GrimoireLayout.gridCell(blockWidth, columns, 0, 0);
        assertEquals(-1, GrimoireLayout.gridIndexAt(blockWidth, columns, visible, 0, COUNT, first.x() - 1, cy(first)));
        assertEquals(-1, GrimoireLayout.gridIndexAt(blockWidth, columns, visible, 0, COUNT, cx(first), first.y() - 1));
        Rect beyond = GrimoireLayout.gridCell(blockWidth, columns, COUNT, 0);
        assertEquals(-1, GrimoireLayout.gridIndexAt(blockWidth, columns, visible, 0, COUNT, cx(beyond), cy(beyond)),
                "an empty cell past the last verse answers nothing");
    }

    @Test
    void scrollingShowsTheRowsBelowAndIsClampedToWhatIsOffScreen() {
        int blockWidth = GrimoireLayout.blockWidth(427);
        int columns = GrimoireLayout.gridColumns(blockWidth);
        int rows = GrimoireLayout.gridRows(COUNT, columns);
        int visible = GrimoireLayout.gridRowsVisible(240, rows);
        assertTrue(visible < rows, "a hundred verses need scrolling at 427x240");
        assertEquals(0, GrimoireLayout.clampGridScroll(-1, rows, visible));
        assertEquals(rows - visible, GrimoireLayout.clampGridScroll(99, rows, visible));
        assertEquals(0, GrimoireLayout.clampGridScroll(5, 3, 8), "nothing to scroll when everything fits");
        int scroll = 1;
        Rect firstShown = GrimoireLayout.gridCell(blockWidth, columns, columns, scroll);
        assertEquals(GrimoireLayout.GRID_Y, firstShown.y(), "the second row is drawn on the first line once scrolled");
        assertEquals(columns, GrimoireLayout.gridIndexAt(blockWidth, columns, visible, scroll, COUNT, cx(firstShown), cy(firstShown)));
    }

    @Test
    void everySlotHoldsItsSymbolAndItsBarAndAnswersToItsOwnCentre() {
        for (int guiWidth : WIDTHS) {
            int blockWidth = GrimoireLayout.blockWidth(guiWidth);
            Rect row = GrimoireLayout.slotsRow(blockWidth, 4);
            for (int index = 0; index < GrimoireLayout.SLOT_COUNT; index++) {
                Rect slot = GrimoireLayout.slot(blockWidth, 4, index);
                assertTrue(slot.contains(GrimoireLayout.slotSymbol(slot)), "the symbol escapes slot " + index);
                assertTrue(slot.contains(GrimoireLayout.slotBar(slot)), "the bar escapes slot " + index);
                assertTrue(row.contains(slot), "slot " + index + " escapes the row");
                assertEquals(index, GrimoireLayout.slotAt(blockWidth, 4, cx(slot), cy(slot)));
            }
            Rect first = GrimoireLayout.slot(blockWidth, 4, 0);
            assertEquals(-1, GrimoireLayout.slotAt(blockWidth, 4, first.x() - 1, cy(first)));
            assertEquals(-1, GrimoireLayout.slotAt(blockWidth, 4, cx(first), first.y() - 1));
            assertEquals(-1, GrimoireLayout.slotAt(blockWidth, 4, row.right() + 1, cy(first)));
        }
    }

    @Test
    void aDropLandsOnTheNearestSlotBoundaryAndNeverPastTheEnd() {
        int blockWidth = GrimoireLayout.blockWidth(640);
        int size = 5;
        Rect second = GrimoireLayout.slot(blockWidth, 4, 2);
        assertEquals(0, GrimoireLayout.slotInsertionAt(blockWidth, second.x() - 1000, size), "far left is the start");
        assertEquals(2, GrimoireLayout.slotInsertionAt(blockWidth, second.x() + 2, size), "the left half of slot 2 goes before it");
        assertEquals(3, GrimoireLayout.slotInsertionAt(blockWidth, second.right() - 2, size), "the right half of slot 2 goes after it");
        Rect last = GrimoireLayout.slot(blockWidth, 4, size);
        assertEquals(size, GrimoireLayout.slotInsertionAt(blockWidth, last.x() + 2, size), "just past the last written slot is the end");
        assertEquals(size, GrimoireLayout.slotInsertionAt(blockWidth, second.x() + 1000, size), "far right is the end");
        assertEquals(GrimoireLayout.SLOT_COUNT, GrimoireLayout.slotInsertionAt(blockWidth, second.x() + 1000, GrimoireLayout.SLOT_COUNT),
                "a full row ends at its cap");
    }

    @Test
    void theDropZoneIsTheRowWithALittleSlackAndNoMore() {
        int blockWidth = GrimoireLayout.blockWidth(640);
        Rect row = GrimoireLayout.slotsRow(blockWidth, 4);
        assertTrue(GrimoireLayout.overRow(blockWidth, 4, cx(row), cy(row)));
        assertTrue(GrimoireLayout.overRow(blockWidth, 4, row.x() - 1, row.y() - 1), "a little slack above and left");
        assertTrue(GrimoireLayout.overRow(blockWidth, 4, row.right() + 1, row.bottom() + 1), "a little slack below and right");
        assertFalse(GrimoireLayout.overRow(blockWidth, 4, cx(row), row.y() - GrimoireLayout.SLOT_SLACK - 1), "the caption is not the row");
        assertFalse(GrimoireLayout.overRow(blockWidth, 4, cx(row), row.bottom() + GrimoireLayout.SLOT_SLACK + 1), "the controls are not the row");
        Rect cell = GrimoireLayout.gridCell(blockWidth, GrimoireLayout.gridColumns(blockWidth), 0, 0);
        assertFalse(GrimoireLayout.overRow(blockWidth, 4, cx(cell), cy(cell)), "the grid is not the row");
    }

    @Test
    void theControlsSitOnTheRowsEndsAndAnswerToTheirCentres() {
        for (int guiWidth : WIDTHS) {
            int blockWidth = GrimoireLayout.blockWidth(guiWidth);
            Rect row = GrimoireLayout.slotsRow(blockWidth, 4);
            GrimoireLayout.Controls controls = controls(blockWidth, 4);
            assertEquals(row.x(), controls.breathLabel().x(), "the breath starts where the row does");
            assertEquals(row.right(), controls.clear().right(), "Clear ends where the row does");
            assertTrue(controls.plus().right() < controls.save().x(), "the breath runs into the actions at " + guiWidth);
            if (guiWidth >= 640) {
                assertTrue(controls.hint().w() > 0, "the wide row has room to say what the breath is");
                assertTrue(controls.hint().right() + GrimoireLayout.ACTION_GAP <= controls.save().x(), "and the word keeps clear of Save");
            } else if (guiWidth <= 320) {
                assertEquals(0, controls.hint().w(), "the narrow row drops the word rather than run it into Save");
            }
            for (Rect control : controls.all()) {
                if (control.w() == 0) {
                    continue;
                }
                assertTrue(GrimoireLayout.hit(control, cx(control), cy(control)), control.name() + " misses its own centre");
                assertFalse(GrimoireLayout.hit(control, control.right() + 0.5D, cy(control)), control.name() + " answers past its edge");
            }
            assertEquals(5, GrimoireLayout.indexAt(controls.all(), cx(controls.save()), cy(controls.save())));
        }
    }

    @Test
    void theTabsAnswerToTheirOwnCentresAndTheMarkSitsUnderThem() {
        int blockWidth = GrimoireLayout.blockWidth(640);
        List<Rect> tabs = GrimoireLayout.tabs(blockWidth, TAB_WIDTHS);
        assertEquals(GrimoireLayout.TAB_COUNT, tabs.size());
        for (int tab = 0; tab < tabs.size(); tab++) {
            Rect rect = tabs.get(tab);
            assertEquals(tab, GrimoireLayout.indexAt(tabs, cx(rect), cy(rect)));
            Rect rule = GrimoireLayout.tabRule(rect);
            assertEquals(rect.x(), rule.x());
            assertEquals(rect.w(), rule.w());
            assertTrue(rule.y() >= rect.bottom(), "the mark sits on the word");
            assertTrue(rule.bottom() <= GrimoireLayout.CATEGORY_Y, "the mark runs into the categories");
        }
        Rect first = tabs.get(0);
        assertEquals(-1, GrimoireLayout.indexAt(tabs, first.right() + GrimoireLayout.TAB_GAP / 2.0D, cy(first)),
                "the gap between tabs picks a tab");
    }

    @Test
    void theCurvesStartAtZeroEndAtOneAndOnlyOneOfThemOvershoots() {
        assertEquals(0.0F, GrimoireLayout.ease(0.0F), 1e-5F);
        assertEquals(1.0F, GrimoireLayout.ease(1.0F), 1e-5F);
        assertEquals(0.0F, GrimoireLayout.easeOut(0.0F), 1e-5F);
        assertEquals(1.0F, GrimoireLayout.easeOut(1.0F), 1e-5F);
        float peak = 0.0F;
        float last = 0.0F;
        for (int step = 0; step <= 100; step++) {
            float t = step / 100.0F;
            peak = Math.max(peak, GrimoireLayout.ease(t));
            float out = GrimoireLayout.easeOut(t);
            assertTrue(out >= last - 1e-6F, "easeOut turns back at " + t);
            assertTrue(out <= 1.0F + 1e-6F, "easeOut passes its target at " + t);
            last = out;
        }
        assertTrue(peak > 1.0F, "ease never overshoots");
        assertEquals(1.0F, GrimoireLayout.ease(2.0F), 1e-5F, "past the end it stays put");
        assertEquals(5.0F, GrimoireLayout.approach(0.0F, 10.0F, 1.0F, 1.0F), 1e-5F, "one half-life closes half the distance");
        assertEquals(10.0F, GrimoireLayout.approach(0.0F, 10.0F, 1.0F, 0.0F), 1e-5F, "no half-life is no motion at all");
    }
}
