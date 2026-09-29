package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexiconInventoryLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}};
    private static final boolean[] PLAYBILL = {false, true};

    private static List<Rect> cells(LexiconInventoryLayout layout) {
        List<Rect> rects = new ArrayList<>();
        for (int i = 0; i < LexiconInventoryLayout.GRID_CELLS; i++) rects.add(layout.grid(i));
        for (int i = 0; i < LexiconInventoryLayout.BELT_CELLS; i++) rects.add(layout.belt(i));
        for (int i = 0; i < layout.tabsPerPage(); i++) rects.add(layout.tab(i));
        if (layout.playbill()) rects.add(layout.playbillTab());
        return rects;
    }

    private static List<Rect> everything(LexiconInventoryLayout layout) {
        List<Rect> rects = cells(layout);
        rects.add(layout.panel());
        rects.add(layout.scroller());
        rects.add(layout.search());
        rects.add(layout.pagePrev());
        rects.add(layout.pageNext());
        rects.add(layout.thumb(0.0F));
        rects.add(layout.thumb(1.0F));
        return rects;
    }

    @Test
    void theNumbersAreVanillaCreatives() {
        LexiconInventoryLayout layout = new LexiconInventoryLayout(640, 360, true);
        int left = (640 - 195) / 2;
        int top = (360 - 136) / 2;
        assertEquals(left, layout.left());
        assertEquals(top, layout.top());
        assertEquals(new Rect("grid", left + 9, top + 18, 16, 16), layout.grid(0));
        assertEquals(new Rect("grid", left + 9 + 8 * 18, top + 18 + 4 * 18, 16, 16), layout.grid(44));
        assertEquals(new Rect("belt", left + 9 + 3 * 18, top + 112, 16, 16), layout.belt(3));
        assertEquals(new Rect("scroller", left + 175, top + 18, 14, 112), layout.scroller());
        assertEquals(new Rect("search", left + 82, top + 6, 80, 9), layout.search());
        assertEquals(new Rect("tab", left + 2 * 27, top - 28, 26, 32), layout.tab(2));
        assertEquals(new Rect("playbill", left + 195 - 26, top - 28, 26, 32), layout.playbillTab());
        assertEquals(new Rect("prev", left - 16, top - 22, 12, 12), layout.pagePrev());
        assertEquals(new Rect("next", left + 195 + 4, top - 22, 12, 12), layout.pageNext());
        assertEquals(6, layout.tabsPerPage());
        assertEquals(7, new LexiconInventoryLayout(640, 360, false).tabsPerPage());
    }

    @Test
    void everythingIsOnScreenAndNoCellOrTabOverlapsAnother() {
        for (int[] screen : SCREENS) {
            for (boolean playbill : PLAYBILL) {
                LexiconInventoryLayout layout = new LexiconInventoryLayout(screen[0], screen[1], playbill);
                String where = " at " + screen[0] + "x" + screen[1] + (playbill ? " with" : " without") + " the Playbill";
                for (Rect rect : everything(layout)) {
                    assertTrue(rect.x() >= 0 && rect.right() <= screen[0] && rect.y() >= 0 && rect.bottom() <= screen[1],
                            rect + " leaves the gui" + where);
                }
                List<Rect> cells = cells(layout);
                cells.add(layout.scroller());
                cells.add(layout.search());
                cells.add(layout.pagePrev());
                cells.add(layout.pageNext());
                for (int a = 0; a < cells.size(); a++) {
                    for (int b = a + 1; b < cells.size(); b++) {
                        assertFalse(cells.get(a).overlaps(cells.get(b)), cells.get(a) + " overlaps " + cells.get(b) + where);
                    }
                }
                for (Rect rect : cells(layout)) {
                    if (!rect.name().equals("tab") && !rect.name().equals("playbill")) {
                        assertTrue(layout.panel().contains(rect), rect + " is off the panel" + where);
                    }
                }
            }
        }
    }

    @Test
    void everyCellAnswersAtItsCentreAndNothingAnswersInTheGaps() {
        for (int[] screen : SCREENS) {
            for (boolean playbill : PLAYBILL) {
                LexiconInventoryLayout layout = new LexiconInventoryLayout(screen[0], screen[1], playbill);
                for (int i = 0; i < LexiconInventoryLayout.GRID_CELLS; i++) {
                    Rect cell = layout.grid(i);
                    assertEquals(i, layout.gridAt(cx(cell), cy(cell)));
                    assertEquals(-1, layout.beltAt(cx(cell), cy(cell)));
                    assertEquals(-1, layout.gridAt(cell.right() + 1, cy(cell)), "the gap right of grid " + i);
                    assertEquals(-1, layout.gridAt(cx(cell), cell.bottom() + 1), "the gap under grid " + i);
                }
                for (int i = 0; i < LexiconInventoryLayout.BELT_CELLS; i++) {
                    Rect cell = layout.belt(i);
                    assertEquals(i, layout.beltAt(cx(cell), cy(cell)));
                    assertEquals(-1, layout.gridAt(cx(cell), cy(cell)));
                    assertEquals(-1, layout.beltAt(cell.right() + 1, cy(cell)), "the gap right of belt " + i);
                }
                for (int i = 0; i < layout.tabsPerPage(); i++) {
                    assertEquals(i, layout.tabAt(cx(layout.tab(i)), cy(layout.tab(i))));
                    assertEquals(-1, layout.tabAt(layout.tab(i).right(), cy(layout.tab(i))), "the gap right of tab " + i);
                }
                assertEquals(playbill, layout.onPlaybill(cx(layout.playbillTab()), cy(layout.playbillTab())));
                if (playbill) {
                    assertEquals(-1, layout.tabAt(cx(layout.playbillTab()), cy(layout.playbillTab())));
                }
                assertEquals(-1, layout.gridAt(layout.left() - 1, layout.top() + 20));
            }
        }
    }

    @Test
    void theThumbRunsTheLengthOfTheScrollerAndTheMouseDrivesItBack() {
        LexiconInventoryLayout layout = new LexiconInventoryLayout(480, 270, false);
        Rect scroller = layout.scroller();
        assertEquals(scroller.y(), layout.thumb(0.0F).y());
        assertEquals(12, layout.thumb(0.5F).w());
        assertEquals(15, layout.thumb(0.5F).h());
        assertTrue(scroller.contains(layout.thumb(1.0F)));
        assertEquals(0.0F, layout.scrollFor(scroller.y()), 1.0e-6F);
        assertEquals(1.0F, layout.scrollFor(scroller.bottom()), 1.0e-6F);
        assertEquals(0.5F, layout.scrollFor(scroller.y() + 7.5 + (112 - 15) / 2.0), 1.0e-6F);
    }

    private static double cx(Rect rect) {
        return rect.x() + rect.w() / 2.0;
    }

    private static double cy(Rect rect) {
        return rect.y() + rect.h() / 2.0;
    }
}
