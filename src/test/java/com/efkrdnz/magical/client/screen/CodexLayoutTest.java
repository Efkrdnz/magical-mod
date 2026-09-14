package com.efkrdnz.magical.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The codex laid out on paper, so a collision fails here instead of in the game.
 *
 * <p>Written after a real one: growing the cast row from three keys to four put the fourth card
 * straight through the panel beside it and under a label, and nothing caught it because every
 * coordinate was a bare number inside a draw call. These assertions are the cheap half of what a
 * screenshot would have told me.
 *
 * <p>What this does <em>not</em> check is text: a label still has to fit the box it is drawn in,
 * and only a running font knows how wide a string is. The screen caps every drawn string to the
 * widths pinned here, which is what keeps that from turning back into a collision.
 */
class CodexLayoutTest {

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

    private static double centreX(Rect r) {
        return r.x() + r.w() / 2.0;
    }

    private static double centreY(Rect r) {
        return r.y() + r.h() / 2.0;
    }

    // ---- the frame ------------------------------------------------------------------------------

    @Test
    void theTopStripAndTheTabsAreDisjointAndSitAboveTheBody() {
        List<Rect> chrome = new ArrayList<>(ScreenChrome.headerRects());
        chrome.addAll(ScreenChrome.tabRects(CodexLayout.TAB_COUNT));
        assertDisjoint(chrome);
        for (Rect rect : ScreenChrome.headerRects()) {
            assertTrue(rect.bottom() <= ScreenChrome.DIVIDER_Y, rect.name() + " crosses the hairline");
        }
        for (Rect rect : ScreenChrome.tabRects(CodexLayout.TAB_COUNT)) {
            assertTrue(rect.y() > ScreenChrome.DIVIDER_Y && rect.bottom() <= ScreenChrome.BODY_Y,
                    rect.name() + " is not between the hairline and the body");
        }
        assertTrue(ScreenChrome.panel().contains(ScreenChrome.body()));
    }

    @Test
    void everyTabAnswersToItsCentreAndTheGapsToNothing() {
        for (int index = 0; index < CodexLayout.TAB_COUNT; index++) {
            Rect tab = ScreenChrome.tab(index);
            assertEquals(index, CodexLayout.tabAt(centreX(tab), centreY(tab)));
            assertEquals(-1, CodexLayout.tabAt(tab.right() + 0.5, centreY(tab)), "the gap after tab " + index);
        }
        assertEquals(-1, CodexLayout.tabAt(ScreenChrome.TAB_X + 1, ScreenChrome.BODY_Y + 1));
    }

    // ---- the Skills tab -------------------------------------------------------------------------

    @Test
    void theSkillsTabFitsTheBodyUnderTheTallestPyramid() {
        List<Rect> rects = CodexLayout.skillsTabRects(CodexLayout.PYRAMID_MAX_ROWS);
        assertDisjoint(rects);
        assertInside(ScreenChrome.body(), rects);
    }

    @Test
    void theSkillsTabFitsTheBodyUnderTheShortestPyramid() {
        List<Rect> rects = CodexLayout.skillsTabRects(1);
        assertDisjoint(rects);
        assertInside(ScreenChrome.body(), rects);
    }

    @Test
    void theSkillListKeepsRowsEvenUnderSixTiers() {
        assertTrue(CodexLayout.visibleSkillRows(CodexLayout.PYRAMID_MAX_ROWS) >= 4,
                "six tiers leave the skill list fewer than four rows");
        assertTrue(CodexLayout.visibleSkillRows(1) > CodexLayout.visibleSkillRows(CodexLayout.PYRAMID_MAX_ROWS),
                "a short pyramid should give the list more rows, not the same");
    }

    @Test
    void everyTierBlockNestsInsideThePyramidAreaWhicheverWayItPoints() {
        for (int rows = 1; rows <= CodexLayout.PYRAMID_MAX_ROWS; rows++) {
            for (boolean below : new boolean[] {false, true}) {
                List<Rect> blocks = new ArrayList<>();
                for (int row = 0; row < rows; row++) {
                    blocks.add(CodexLayout.tierBlock(row, rows, below));
                }
                assertDisjoint(blocks);
                assertInside(CodexLayout.pyramidArea(), blocks);
                Rect list = CodexLayout.skillList(rows);
                for (Rect block : blocks) {
                    assertTrue(block.bottom() < list.y(), block.name() + " runs into the skill list with " + rows + " rows");
                    assertEquals(blocks.indexOf(block), CodexLayout.tierRowAt(rows, below, centreX(block), centreY(block)));
                }
            }
        }
    }

    @Test
    void theSkillRowsAnswerToTheirCentresAndTheGapsToNothing() {
        int rows = CodexLayout.PYRAMID_MAX_ROWS;
        for (int row = 0; row < CodexLayout.visibleSkillRows(rows); row++) {
            Rect rect = CodexLayout.skillRow(rows, row);
            assertEquals(row, CodexLayout.skillRowAt(rows, centreX(rect), centreY(rect)));
            assertEquals(-1, CodexLayout.skillRowAt(rows, centreX(rect), rect.bottom() + 0.5), "the gap under row " + row);
            assertTrue(rect.contains(CodexLayout.skillEmblem(rows, row)), "the emblem escapes row " + row);
            assertTrue(CodexLayout.skillList(rows).contains(rect), "row " + row + " escapes the list");
        }
        assertEquals(-1, CodexLayout.skillRowAt(rows, CodexLayout.RIGHT_X + 1, centreY(CodexLayout.skillRow(rows, 0))));
    }

    @Test
    void theFourthCastCardStaysInsideTheRightColumn() {
        // The exact regression. LOADOUT_SIZE went from three to four and the new card landed on
        // top of the list that used to own that space.
        Rect last = CodexLayout.card(MagicContent.LOADOUT_SIZE - 1);
        assertTrue(last.right() <= CodexLayout.RIGHT_X + CodexLayout.RIGHT_W,
                "the last cast card runs past the column at " + last.right());
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect card = CodexLayout.card(slot);
            assertEquals(slot, CodexLayout.cardAt(centreX(card), centreY(card)));
            assertTrue(card.contains(CodexLayout.cardEmblem(slot)), "the emblem escapes card " + slot);
        }
        assertEquals(-1, CodexLayout.cardAt(CodexLayout.card(0).right() + 1, centreY(CodexLayout.card(0))));
    }

    @Test
    void theDetailHeaderAndItsScrollingContentShareTheInsetWithoutTouching() {
        Rect detail = CodexLayout.detail();
        assertTrue(detail.contains(CodexLayout.detailHeader()));
        assertTrue(detail.contains(CodexLayout.detailContent()));
        assertTrue(detail.contains(CodexLayout.detailScrollbar()));
        assertTrue(CodexLayout.detailHeader().contains(CodexLayout.detailEmblem()));
        assertTrue(!CodexLayout.detailHeader().overlaps(CodexLayout.detailContent()));
        assertTrue(!CodexLayout.detailContent().overlaps(CodexLayout.detailScrollbar()));
    }

    // ---- the Loadouts tab -----------------------------------------------------------------------

    @Test
    void theLoadoutsTabFitsTheBody() {
        List<Rect> rects = CodexLayout.loadoutsTabRects();
        assertDisjoint(rects);
        assertInside(ScreenChrome.body(), rects);
    }

    @Test
    void everyBindAndClearButtonSitsInsideItsOwnKeySlot() {
        // These are hit-tested separately from the panel they are painted on, so a stride change
        // could slide them onto the neighbouring slot and silently bind the wrong key.
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect panel = CodexLayout.keySlot(slot);
            Rect bind = CodexLayout.bindButton(slot);
            Rect clear = CodexLayout.clearButton(slot);
            Rect emblem = CodexLayout.keySlotEmblem(slot);
            assertTrue(panel.contains(bind), "bind " + slot + " escapes its slot");
            assertTrue(panel.contains(clear), "clear " + slot + " escapes its slot");
            assertTrue(panel.contains(emblem), "the emblem escapes slot " + slot);
            assertDisjoint(List.of(bind, clear, emblem));
            assertEquals(slot, CodexLayout.keySlotAt(centreX(panel), centreY(panel)));
        }
        assertEquals(-1, CodexLayout.keySlotAt(centreX(CodexLayout.keySlot(0)), CodexLayout.keySlot(0).bottom() + 0.5));
    }

    @Test
    void theLoadoutListHasRoomForTheMaximumNumberOfLoadouts() {
        // MAX_LOADOUTS drives the list height and the New/Delete row below it; raising the cap
        // without raising the body would push the rename row off the screen.
        Rect last = CodexLayout.loadoutRow(MagicContent.MAX_LOADOUTS - 1);
        assertTrue(last.bottom() <= CodexLayout.newButton().y(), "the loadout rows run into the New/Delete row");
        assertTrue(CodexLayout.nameBox().bottom() <= ScreenChrome.body().bottom(), "the rename row falls off the body");
        for (int index = 0; index < MagicContent.MAX_LOADOUTS; index++) {
            Rect row = CodexLayout.loadoutRow(index);
            assertEquals(index, CodexLayout.loadoutRowAt(centreX(row), centreY(row)));
        }
    }

    // ---- the Passives tab -----------------------------------------------------------------------

    @Test
    void thePassivesTabFitsTheBody() {
        List<Rect> rects = CodexLayout.passivesTabRects();
        assertDisjoint(rects);
        assertInside(ScreenChrome.body(), rects);
        Rect card = CodexLayout.curseCard(0);
        assertTrue(card.contains(CodexLayout.dispelButton(0)), "the dispel button escapes its curse card");
        assertTrue(CodexLayout.cursesList().contains(CodexLayout.curseCard(CodexLayout.visibleCurseRows() - 1)),
                "the last visible curse card escapes the curses list");
        assertTrue(CodexLayout.passivesList().h() >= CodexLayout.visiblePassiveRows() * CodexLayout.PASSIVE_ROW_H);
    }

    // ---- the Classes tab ------------------------------------------------------------------------

    @Test
    void theClassesTabFitsTheBodyWithEveryRootOwned() {
        List<Rect> rects = CodexLayout.classesTabRects(CodexLayout.CLASS_MAX_ROWS);
        assertDisjoint(rects);
        assertInside(ScreenChrome.body(), rects);
        List<Rect> empty = CodexLayout.emptyClassesRects();
        assertDisjoint(empty);
        assertInside(ScreenChrome.body(), empty);
        assertTrue(CodexLayout.emptyClasses().contains(CodexLayout.chooseClassButton()));
        for (int row = 0; row < CodexLayout.CLASS_MAX_ROWS; row++) {
            Rect rect = CodexLayout.classRow(row);
            assertEquals(row, CodexLayout.classRowAt(CodexLayout.CLASS_MAX_ROWS, centreX(rect), centreY(rect)));
        }
        assertEquals(-1, CodexLayout.classRowAt(CodexLayout.CLASS_MAX_ROWS, centreX(CodexLayout.classRow(0)), CodexLayout.classRow(0).bottom() + 0.5));
    }
}
