package com.efkrdnz.magical.client.screen.grimoire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.incantation.Grimoire;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.VerseType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Grimoire screen laid out on paper: nothing drawn lands on anything else, everything sits
 * inside the body that frames it, and every hit-test answers for exactly the rectangle it is drawn
 * as. The page has as many lines as an incantation may hold, the rail as many buttons as there are
 * verse types and the tabs as many as the Grimoire has slots, so a change to any of those caps moves
 * this layout and this test says so before a launched game would. A drop lands on the line boundary
 * nearest the cursor, and never past the end of the page.
 */
class GrimoireLayoutTest {

    private static void assertDisjoint(List<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                assertTrue(!a.overlaps(b), a.name() + " overlaps " + b.name()
                        + ": " + describe(a) + " vs " + describe(b));
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

    @Test
    void theHeaderStaysOnTheTitleStripAndOffItself() {
        List<Rect> header = GrimoireLayout.headerRects();
        assertDisjoint(header);
        assertInside(GrimoireLayout.panel(), header);
        for (Rect rect : header) {
            assertTrue(rect.bottom() <= GrimoireLayout.TAB_Y, rect.name() + " runs into the tab row");
        }
    }

    @Test
    void oneTabPerSlotSitsBetweenTheHeaderAndTheBody() {
        List<Rect> tabs = GrimoireLayout.tabRects();
        assertEquals(Grimoire.SLOTS, tabs.size());
        assertDisjoint(tabs);
        assertInside(GrimoireLayout.panel(), tabs);
        for (Rect tab : tabs) {
            assertTrue(tab.bottom() <= GrimoireLayout.body().y(), tab.name() + " overlaps the body");
        }
    }

    @Test
    void nothingInTheBodyOverlapsAnythingElse() {
        List<Rect> rects = GrimoireLayout.bodyRects();
        assertDisjoint(rects);
        assertInside(GrimoireLayout.body(), rects);
    }

    @Test
    void thePageHoldsEveryLineAnIncantationMay() {
        assertEquals(ReciteCaps.MAX_VERSES, GrimoireLayout.LINES);
        Rect last = GrimoireLayout.line(GrimoireLayout.LINES - 1);
        assertTrue(last.bottom() <= GrimoireLayout.pageHint().y(), "the last line runs into the hint");
    }

    @Test
    void theRailHasOneButtonPerTypeBesideTheShelf() {
        assertEquals(VerseType.values().length, GrimoireLayout.RAIL_COUNT);
        for (int index = 0; index < GrimoireLayout.RAIL_COUNT; index++) {
            Rect button = GrimoireLayout.railButton(index);
            assertTrue(button.right() <= GrimoireLayout.SHELF_X, "rail " + index + " runs into the shelf");
            assertTrue(button.bottom() <= GrimoireLayout.shelfHint().y(), "rail " + index + " runs into the hint");
            assertEquals(index, GrimoireLayout.railAt(cx(button), cy(button)));
            assertEquals(-1, GrimoireLayout.railAt(cx(button), button.bottom() + 0.5D), "the gap under rail " + index + " hits a button");
        }
        Rect first = GrimoireLayout.railButton(0);
        assertEquals(-1, GrimoireLayout.railAt(first.right() + 1, cy(first)));
        assertEquals(-1, GrimoireLayout.shelfRowAt(cx(first), cy(first)), "the rail is not the shelf");
    }

    @Test
    void everyShelfRowIsHitByItsOwnCentreOnly() {
        for (int row = 0; row < GrimoireLayout.SHELF_ROWS; row++) {
            Rect rect = GrimoireLayout.shelfRow(row);
            assertEquals(row, GrimoireLayout.shelfRowAt(cx(rect), cy(rect)));
            assertEquals(-1, GrimoireLayout.shelfRowAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under shelf row " + row + " hits a row");
        }
        Rect first = GrimoireLayout.shelfRow(0);
        assertEquals(-1, GrimoireLayout.shelfRowAt(first.x() - 1, cy(first)));
        assertEquals(-1, GrimoireLayout.shelfRowAt(first.right() + 1, cy(first)));
        assertEquals(-1, GrimoireLayout.shelfRowAt(cx(first), first.y() - 1));
        Rect last = GrimoireLayout.shelfRow(GrimoireLayout.SHELF_ROWS - 1);
        assertTrue(last.bottom() <= GrimoireLayout.shelfHint().y(), "the last shelf row runs into the hint");
    }

    @Test
    void everyLineHoldsItsStrikeAndIsHitByItsOwnCentreOnly() {
        for (int index = 0; index < GrimoireLayout.LINES; index++) {
            Rect rect = GrimoireLayout.line(index);
            assertTrue(rect.contains(GrimoireLayout.lineStrike(index)), "the strike escapes line " + index);
            assertEquals(index, GrimoireLayout.lineAt(cx(rect), cy(rect)));
            assertEquals(-1, GrimoireLayout.lineAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under line " + index + " hits a line");
        }
        Rect first = GrimoireLayout.line(0);
        assertEquals(-1, GrimoireLayout.lineAt(first.x() - 1, cy(first)));
        assertEquals(-1, GrimoireLayout.lineAt(first.right() + 1, cy(first)));
        assertEquals(-1, GrimoireLayout.lineAt(cx(first), first.y() - 1));
    }

    @Test
    void aDropLandsOnTheNearestLineBoundaryAndNeverPastTheEnd() {
        int size = 5;
        assertEquals(0, GrimoireLayout.insertionIndexAt(GrimoireLayout.PAGE_Y - 100, size), "far above the page");
        assertEquals(0, GrimoireLayout.insertionIndexAt(GrimoireLayout.line(0).y() + 1, size), "the top of the first line");
        assertEquals(2, GrimoireLayout.insertionIndexAt(GrimoireLayout.line(2).y() + 2, size), "the top half of line 2 goes before it");
        assertEquals(3, GrimoireLayout.insertionIndexAt(GrimoireLayout.line(2).bottom() - 1, size), "the bottom half of line 2 goes after it");
        assertEquals(size, GrimoireLayout.insertionIndexAt(GrimoireLayout.line(size).y() + 2, size), "just under the last line is the end");
        assertEquals(size, GrimoireLayout.insertionIndexAt(GrimoireLayout.PAGE_Y + 10_000, size), "far below the page");
        assertEquals(GrimoireLayout.LINES, GrimoireLayout.insertionIndexAt(GrimoireLayout.PAGE_Y + 10_000, GrimoireLayout.LINES), "a full page ends at its cap");
    }

    @Test
    void theDropZoneIsThePageWithALittleSlackAndNoMore() {
        Rect first = GrimoireLayout.line(0);
        Rect last = GrimoireLayout.line(GrimoireLayout.LINES - 1);
        assertTrue(GrimoireLayout.overPage(cx(first), cy(first)));
        assertTrue(GrimoireLayout.overPage(first.x() - 1, first.y() - 1), "a little slack above and left");
        assertTrue(GrimoireLayout.overPage(last.right() + 1, last.bottom() + 1), "a little slack below and right");
        assertFalse(GrimoireLayout.overPage(cx(GrimoireLayout.shelfRow(0)), cy(GrimoireLayout.shelfRow(0))), "the shelf is not the page");
        assertFalse(GrimoireLayout.overPage(cx(GrimoireLayout.reading()), cy(GrimoireLayout.reading())), "the margin is not the page");
        assertFalse(GrimoireLayout.overPage(cx(first), GrimoireLayout.pageHint().bottom() + 1), "under the hint is not the page");
    }

    @Test
    void theBreathFurnitureStaysInTheMargin() {
        List<Rect> breath = List.of(GrimoireLayout.breathMinus(), GrimoireLayout.breathValue(), GrimoireLayout.breathPlus());
        assertDisjoint(breath);
        for (Rect rect : breath) {
            assertTrue(rect.x() >= GrimoireLayout.MARGIN_X && rect.right() <= GrimoireLayout.MARGIN_X + GrimoireLayout.MARGIN_W,
                    rect.name() + " leaves the margin");
            assertTrue(rect.bottom() <= GrimoireLayout.reading().y(), rect.name() + " runs into the reading");
        }
        assertTrue(GrimoireLayout.reading().bottom() <= GrimoireLayout.save().y());
        assertTrue(GrimoireLayout.save().bottom() <= GrimoireLayout.clear().y());
    }

    @Test
    void theTabsAndButtonsAnswerToTheirOwnCentres() {
        for (int tab = 0; tab < GrimoireLayout.TAB_COUNT; tab++) {
            Rect rect = GrimoireLayout.tab(tab);
            assertEquals(tab, GrimoireLayout.tabAt(cx(rect), cy(rect)));
        }
        Rect first = GrimoireLayout.tab(0);
        assertEquals(-1, GrimoireLayout.tabAt(first.right() + GrimoireLayout.TAB_GAP / 2.0D, cy(first)),
                "the gap between tabs picks a tab");
        for (Rect button : List.of(GrimoireLayout.back(), GrimoireLayout.breathMinus(), GrimoireLayout.breathPlus(),
                GrimoireLayout.save(), GrimoireLayout.clear())) {
            assertTrue(GrimoireLayout.hit(button, cx(button), cy(button)), button.name() + " misses its own centre");
            assertTrue(!GrimoireLayout.hit(button, button.right() + 0.5D, cy(button)), button.name() + " answers past its edge");
        }
    }

    @Test
    void scrollingIsClampedToWhatIsOffScreen() {
        assertEquals(0, GrimoireLayout.clampScroll(5, 9, 11), "nothing to scroll when everything fits");
        assertEquals(0, GrimoireLayout.clampScroll(-1, 40, 11));
        assertEquals(29, GrimoireLayout.clampScroll(30, 40, 11));
        assertEquals(29, GrimoireLayout.clampScroll(99, 40, 11));
    }
}
