package com.efkrdnz.magical.client.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.MagicContent;
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

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    @Test
    void nothingInTheLoadoutPanelOverlapsAnythingElse() {
        assertDisjoint(CodexLayout.loadoutPanelRects());
    }

    @Test
    void theLoadoutPanelHoldsEverythingDrawnInIt() {
        Rect panel = CodexLayout.loadoutPanel();
        for (Rect rect : CodexLayout.loadoutPanelRects()) {
            assertTrue(panel.contains(rect), rect.name() + " escapes the panel: "
                    + describe(rect) + " outside " + describe(panel));
        }
    }

    @Test
    void theFourthCastCardStaysInsideThePanel() {
        // The exact regression. LOADOUT_SIZE went from three to four and the new card landed on
        // top of the list that used to own that space.
        Rect last = CodexLayout.loadoutPanelRects().stream()
                .filter(r -> r.name().equals("card " + (MagicContent.LOADOUT_SIZE - 1)))
                .findFirst()
                .orElseThrow();
        assertTrue(last.right() <= CodexLayout.PANEL_X + CodexLayout.PANEL_W,
                "the last cast card runs past the panel's right edge at " + last.right());
    }

    @Test
    void theHeaderButtonsStayOffThePanelAndOffEachOther() {
        // They sit on the title strip above the panel; drifting down would put them over the cards.
        Rect panel = CodexLayout.loadoutPanel();
        for (Rect header : CodexLayout.headerRects()) {
            assertTrue(!header.overlaps(panel), header.name() + " overlaps the loadout panel");
        }
        assertDisjoint(CodexLayout.headerRects());
    }

    @Test
    void nothingInTheLoadoutsTabOverlapsAnythingElse() {
        assertDisjoint(CodexLayout.loadoutEditorRects());
    }

    @Test
    void theLoadoutsTabHoldsEverythingDrawnInIt() {
        Rect editor = CodexLayout.editorPanel();
        for (Rect rect : CodexLayout.loadoutEditorRects()) {
            assertTrue(editor.contains(rect), rect.name() + " escapes the editor: "
                    + describe(rect) + " outside " + describe(editor));
        }
    }

    @Test
    void everyBindAndClearButtonSitsInsideItsOwnSlotPanel() {
        // These are hit-tested separately from the panel they are painted on, so a stride change
        // could slide them onto the neighbouring slot and silently bind the wrong key.
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect panel = new Rect("slot " + slot, CodexLayout.EDITOR_X + CodexLayout.SLOT_DX,
                    CodexLayout.EDITOR_Y + CodexLayout.slotY(slot), CodexLayout.SLOT_W, CodexLayout.SLOT_H);
            Rect bind = new Rect("bind " + slot, CodexLayout.EDITOR_X + CodexLayout.SLOT_BIND_DX,
                    CodexLayout.EDITOR_Y + CodexLayout.slotY(slot) + CodexLayout.SLOT_BUTTON_DY,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H);
            Rect clear = new Rect("clear " + slot, CodexLayout.EDITOR_X + CodexLayout.SLOT_CLEAR_DX,
                    CodexLayout.EDITOR_Y + CodexLayout.slotY(slot) + CodexLayout.SLOT_BUTTON_DY,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H);

            assertTrue(panel.contains(bind), "bind " + slot + " escapes its slot panel");
            assertTrue(panel.contains(clear), "clear " + slot + " escapes its slot panel");
            assertTrue(!bind.overlaps(clear), "bind and clear overlap on slot " + slot);
        }
    }

    @Test
    void theLoadoutListHasRoomForTheMaximumNumberOfLoadouts() {
        // MAX_LOADOUTS drives the list height and the New/Delete row below it; raising the cap
        // without raising the editor would push the buttons off the panel.
        Rect editor = CodexLayout.editorPanel();
        int listBottom = CodexLayout.EDITOR_Y + CodexLayout.LIST_DY
                + MagicContent.MAX_LOADOUTS * CodexLayout.LIST_ROW_STRIDE;
        assertTrue(listBottom <= CodexLayout.EDITOR_Y + CodexLayout.LIST_ACTION_DY,
                "the loadout rows run into the New/Delete row");
        assertTrue(CodexLayout.EDITOR_Y + CodexLayout.LIST_ACTION_DY + CodexLayout.LIST_ACTION_H
                <= editor.bottom(), "the New/Delete row falls off the editor");
    }
}
