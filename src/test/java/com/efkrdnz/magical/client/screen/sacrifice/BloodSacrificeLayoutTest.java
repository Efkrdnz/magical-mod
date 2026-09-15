package com.efkrdnz.magical.client.screen.sacrifice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pact screen on paper.
 *
 * <p>Three columns side by side is the arrangement most likely to go wrong by arithmetic: a row
 * count or a column width nudged by one puts the prices through the boons, and only a launched
 * game would ever say so. So every rectangle is a function here and every hit-test is a function
 * of the same numbers, and this sweeps the lot for overlap, for fit, and for rows that answer to
 * their own centre and to nothing in the gaps.
 */
class BloodSacrificeLayoutTest {

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
    void theHeaderStaysOnItsStripAndOffItself() {
        List<Rect> header = BloodSacrificeLayout.headerRects();
        assertDisjoint(header);
        assertInside(BloodSacrificeLayout.panel(), header);
        for (Rect rect : header) {
            assertTrue(rect.bottom() <= BloodSacrificeLayout.CAPTION_Y, rect.name() + " runs into the caption");
        }
    }

    @Test
    void theCaptionSitsBetweenTheHeaderAndTheBody() {
        Rect caption = BloodSacrificeLayout.caption();
        assertTrue(BloodSacrificeLayout.panel().contains(caption));
        assertTrue(caption.bottom() <= BloodSacrificeLayout.body().y(), "the caption runs into the body");
    }

    @Test
    void nothingInTheBodyOverlapsAnythingElse() {
        List<Rect> rects = BloodSacrificeLayout.bodyRects();
        assertDisjoint(rects);
        assertInside(BloodSacrificeLayout.body(), rects);
    }

    @Test
    void theThreeColumnsRunLeftToRightWithoutTouching() {
        Rect boons = BloodSacrificeLayout.boonRow(0);
        Rect prices = BloodSacrificeLayout.priceRow(0);
        Rect pact = BloodSacrificeLayout.pactPanel();
        assertTrue(boons.right() <= prices.x(), "the boon column runs into the price column");
        assertTrue(prices.right() <= pact.x(), "the price column runs into the pact panel");
        assertTrue(BloodSacrificeLayout.boonScrollbar().x() >= boons.right(), "the boon scrollbar sits on its rows");
        assertTrue(BloodSacrificeLayout.priceScrollbar().x() >= prices.right(), "the price scrollbar sits on its rows");
    }

    @Test
    void everyBoonRowHoldsItsFurnitureAndIsHitByItsOwnCentreOnly() {
        for (int row = 0; row < BloodSacrificeLayout.ROWS; row++) {
            Rect rect = BloodSacrificeLayout.boonRow(row);
            assertTrue(rect.contains(BloodSacrificeLayout.boonCost(row)), "cost escapes boon row " + row);
            assertTrue(rect.contains(BloodSacrificeLayout.boonText(row)), "text escapes boon row " + row);
            assertTrue(rect.contains(BloodSacrificeLayout.boonMark(row)), "mark escapes boon row " + row);
            assertTrue(!BloodSacrificeLayout.boonCost(row).overlaps(BloodSacrificeLayout.boonText(row)));
            assertTrue(!BloodSacrificeLayout.boonMark(row).overlaps(BloodSacrificeLayout.boonText(row)));
            assertEquals(row, BloodSacrificeLayout.boonRowAt(cx(rect), cy(rect)));
            assertEquals(-1, BloodSacrificeLayout.boonRowAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under boon row " + row + " hits a row");
            assertEquals(-1, BloodSacrificeLayout.priceRowAt(cx(rect), cy(rect)),
                    "boon row " + row + " also answers as a price");
        }
        Rect first = BloodSacrificeLayout.boonRow(0);
        assertEquals(-1, BloodSacrificeLayout.boonRowAt(first.x() - 1, cy(first)));
        assertEquals(-1, BloodSacrificeLayout.boonRowAt(first.right() + 1, cy(first)));
        assertEquals(-1, BloodSacrificeLayout.boonRowAt(cx(first), first.y() - 1));
    }

    @Test
    void everyPriceRowHoldsItsFurnitureAndIsHitByItsOwnCentreOnly() {
        for (int row = 0; row < BloodSacrificeLayout.ROWS; row++) {
            Rect rect = BloodSacrificeLayout.priceRow(row);
            assertTrue(rect.contains(BloodSacrificeLayout.priceCost(row)), "cost escapes price row " + row);
            assertTrue(rect.contains(BloodSacrificeLayout.priceText(row)), "text escapes price row " + row);
            assertTrue(rect.contains(BloodSacrificeLayout.priceMark(row)), "mark escapes price row " + row);
            assertEquals(row, BloodSacrificeLayout.priceRowAt(cx(rect), cy(rect)));
            assertEquals(-1, BloodSacrificeLayout.priceRowAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under price row " + row + " hits a row");
            assertEquals(-1, BloodSacrificeLayout.boonRowAt(cx(rect), cy(rect)),
                    "price row " + row + " also answers as a boon");
        }
        Rect last = BloodSacrificeLayout.priceRow(BloodSacrificeLayout.ROWS - 1);
        assertEquals(-1, BloodSacrificeLayout.priceRowAt(cx(last), last.bottom() + 1));
    }

    @Test
    void thePactPanelHoldsItsFurnitureAndTheSealIsHitByItsOwnCentre() {
        Rect panel = BloodSacrificeLayout.pactPanel();
        List<Rect> inside = BloodSacrificeLayout.pactRects();
        assertDisjoint(inside);
        assertInside(panel, inside);
        Rect seal = BloodSacrificeLayout.sealButton();
        assertTrue(BloodSacrificeLayout.hit(seal, cx(seal), cy(seal)));
        assertTrue(!BloodSacrificeLayout.hit(seal, cx(seal), seal.y() - 1));
        assertTrue(!BloodSacrificeLayout.hit(seal, seal.right(), cy(seal)));
    }

    @Test
    void bothListsShowTheSameNumberOfRowsAndTheHintSitsUnderThem() {
        Rect lastBoon = BloodSacrificeLayout.boonRow(BloodSacrificeLayout.ROWS - 1);
        Rect lastPrice = BloodSacrificeLayout.priceRow(BloodSacrificeLayout.ROWS - 1);
        assertEquals(lastBoon.bottom(), lastPrice.bottom(), "the two columns end at different heights");
        assertTrue(lastBoon.bottom() <= BloodSacrificeLayout.hint().y(), "the hint runs into the rows");
        assertTrue(BloodSacrificeLayout.body().contains(BloodSacrificeLayout.hint()));
    }

    @Test
    void scrollingIsClampedToWhatIsOffScreen() {
        assertEquals(0, BloodSacrificeLayout.clampScroll(5, 9, BloodSacrificeLayout.ROWS),
                "nothing to scroll when everything fits");
        assertEquals(0, BloodSacrificeLayout.clampScroll(-1, 16, BloodSacrificeLayout.ROWS));
        assertEquals(16 - BloodSacrificeLayout.ROWS,
                BloodSacrificeLayout.clampScroll(99, 16, BloodSacrificeLayout.ROWS));
    }

    @Test
    void theWholeScreenSweptAtOnceHasNothingOnTopOfAnythingElse() {
        List<Rect> everything = new ArrayList<>(BloodSacrificeLayout.headerRects());
        everything.add(BloodSacrificeLayout.caption());
        everything.addAll(BloodSacrificeLayout.bodyRects());
        assertDisjoint(everything);
        assertInside(BloodSacrificeLayout.panel(), everything);
    }
}
