package com.efkrdnz.magical.client.screen.creator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The creator screen laid out on paper: nothing drawn lands on anything else, everything sits
 * inside the panel that frames it, and every hit-test answers for exactly the rectangle it is
 * drawn as. The same check the codex and the blood editor have, for the same reason: a count
 * constant that drives a strip of rows can push the last row through the thing beside it and
 * nothing but a launched game would say so.
 */
class SpellCreatorLayoutTest {

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
        List<Rect> header = SpellCreatorLayout.headerRects();
        assertDisjoint(header);
        assertInside(SpellCreatorLayout.panel(), header);
        for (Rect rect : header) {
            assertTrue(rect.bottom() <= SpellCreatorLayout.TAB_Y, rect.name() + " runs into the tab row");
        }
    }

    @Test
    void theTabsSitBetweenTheHeaderAndTheBody() {
        List<Rect> tabs = SpellCreatorLayout.tabRects();
        assertEquals(SpellCreatorLayout.TAB_COUNT, tabs.size());
        assertDisjoint(tabs);
        assertInside(SpellCreatorLayout.panel(), tabs);
        for (Rect tab : tabs) {
            assertTrue(tab.bottom() <= SpellCreatorLayout.body().y(), tab.name() + " overlaps the body");
        }
    }

    @Test
    void nothingOnTheCreateTabOverlapsAnythingElse() {
        List<Rect> rects = SpellCreatorLayout.createTabRects();
        assertDisjoint(rects);
        assertInside(SpellCreatorLayout.body(), rects);
    }

    @Test
    void nothingOnTheFormulasTabOverlapsAnythingElse() {
        List<Rect> rects = SpellCreatorLayout.formulasTabRects();
        assertDisjoint(rects);
        assertInside(SpellCreatorLayout.body(), rects);
    }

    @Test
    void theBodyAndTheTabsSitInsideThePanel() {
        assertTrue(SpellCreatorLayout.panel().contains(SpellCreatorLayout.body()));
    }

    @Test
    void theSlotFurnitureStaysInsideItsCard() {
        for (int slot = 0; slot < 2; slot++) {
            Rect card = SpellCreatorLayout.slot(slot);
            assertTrue(card.contains(SpellCreatorLayout.slotClear(slot)), "clear " + slot + " escapes its card");
            assertTrue(card.contains(SpellCreatorLayout.slotEmblem(slot)), "emblem " + slot + " escapes its card");
            assertTrue(!SpellCreatorLayout.slotClear(slot).overlaps(SpellCreatorLayout.slotEmblem(slot)));
        }
        assertTrue(SpellCreatorLayout.result().contains(SpellCreatorLayout.resultEmblem()));
        assertTrue(SpellCreatorLayout.result().contains(SpellCreatorLayout.resultText()));
        assertTrue(!SpellCreatorLayout.resultEmblem().overlaps(SpellCreatorLayout.resultText()));
    }

    @Test
    void everyIngredientRowHoldsItsEmblemAndIsHitByItsOwnCentreOnly() {
        for (int row = 0; row < SpellCreatorLayout.INGREDIENT_ROWS; row++) {
            Rect rect = SpellCreatorLayout.ingredientRow(row);
            assertTrue(rect.contains(SpellCreatorLayout.ingredientEmblem(row)), "emblem escapes row " + row);
            assertEquals(row, SpellCreatorLayout.ingredientRowAt(cx(rect), cy(rect)));
            assertEquals(-1, SpellCreatorLayout.ingredientRowAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under row " + row + " hits a row");
        }
        Rect first = SpellCreatorLayout.ingredientRow(0);
        assertEquals(-1, SpellCreatorLayout.ingredientRowAt(first.x() - 1, cy(first)));
        assertEquals(-1, SpellCreatorLayout.ingredientRowAt(first.right() + 1, cy(first)));
        assertEquals(-1, SpellCreatorLayout.ingredientRowAt(cx(first), first.y() - 1));
    }

    @Test
    void everyFormulaRowHoldsItsFurnitureAndIsHitByItsOwnCentreOnly() {
        for (int row = 0; row < SpellCreatorLayout.FORMULA_ROWS; row++) {
            Rect rect = SpellCreatorLayout.formulaRow(row);
            assertTrue(rect.contains(SpellCreatorLayout.formulaEmblem(row)), "emblem escapes row " + row);
            assertTrue(rect.contains(SpellCreatorLayout.formulaChip(row)), "chip escapes row " + row);
            assertTrue(rect.contains(SpellCreatorLayout.formulaText(row)), "text escapes row " + row);
            assertTrue(!SpellCreatorLayout.formulaChip(row).overlaps(SpellCreatorLayout.formulaText(row)),
                    "the chip covers the text on row " + row);
            assertTrue(!SpellCreatorLayout.formulaEmblem(row).overlaps(SpellCreatorLayout.formulaText(row)));
            assertEquals(row, SpellCreatorLayout.formulaRowAt(cx(rect), cy(rect)));
            assertEquals(-1, SpellCreatorLayout.formulaRowAt(cx(rect), rect.bottom() + 0.5D),
                    "the gap under row " + row + " hits a row");
        }
        Rect last = SpellCreatorLayout.formulaRow(SpellCreatorLayout.FORMULA_ROWS - 1);
        assertEquals(-1, SpellCreatorLayout.formulaRowAt(cx(last), last.bottom() + 1));
    }

    @Test
    void theTabsAndSlotsAnswerToTheirOwnCentres() {
        for (int tab = 0; tab < SpellCreatorLayout.TAB_COUNT; tab++) {
            Rect rect = SpellCreatorLayout.tab(tab);
            assertEquals(tab, SpellCreatorLayout.tabAt(cx(rect), cy(rect)));
        }
        Rect create = SpellCreatorLayout.tab(0);
        assertEquals(-1, SpellCreatorLayout.tabAt(create.right() + SpellCreatorLayout.TAB_GAP / 2.0D, cy(create)),
                "the gap between tabs picks a tab");
        assertEquals(-1, SpellCreatorLayout.tabAt(cx(create), create.y() - 1));
        for (int slot = 0; slot < 2; slot++) {
            Rect card = SpellCreatorLayout.slot(slot);
            assertEquals(slot, SpellCreatorLayout.slotAt(cx(card), cy(card)));
            Rect clear = SpellCreatorLayout.slotClear(slot);
            assertTrue(SpellCreatorLayout.hit(clear, cx(clear), cy(clear)));
        }
        assertEquals(-1, SpellCreatorLayout.slotAt(cx(SpellCreatorLayout.plus()), cy(SpellCreatorLayout.plus())),
                "the plus between the slots is not a slot");
    }

    @Test
    void scrollingIsClampedToWhatIsOffScreen() {
        assertEquals(0, SpellCreatorLayout.clampScroll(5, 9, 11), "nothing to scroll when everything fits");
        assertEquals(0, SpellCreatorLayout.clampScroll(-1, 40, 11));
        assertEquals(29, SpellCreatorLayout.clampScroll(30, 40, 11));
        assertEquals(29, SpellCreatorLayout.clampScroll(99, 40, 11));
    }

    @Test
    void theHintRowsStayUnderTheirLists() {
        Rect lastIngredient = SpellCreatorLayout.ingredientRow(SpellCreatorLayout.INGREDIENT_ROWS - 1);
        assertTrue(lastIngredient.bottom() <= SpellCreatorLayout.ingredientHint().y());
        Rect lastFormula = SpellCreatorLayout.formulaRow(SpellCreatorLayout.FORMULA_ROWS - 1);
        assertTrue(lastFormula.bottom() <= SpellCreatorLayout.formulaHint().y());
        List<Rect> both = new ArrayList<>(SpellCreatorLayout.createTabRects());
        both.addAll(SpellCreatorLayout.formulasTabRects());
        assertInside(SpellCreatorLayout.body(), both);
    }
}
