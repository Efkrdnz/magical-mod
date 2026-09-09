package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the codex panels whose contents are sized by {@link MagicContent} constants.
 *
 * <p>It lives outside the screen so it can be checked without a render context. The screen both
 * draws and hit-tests from these numbers, and {@code CodexLayoutTest} asserts that nothing drawn
 * lands on anything else.
 *
 * <p>That test exists because the check was missing: when the cast row grew from three keys to
 * four, the fourth card was placed straight through the panel that sat beside it, and nothing
 * failed - the collision was only visible by launching the game. Anything positioned by arithmetic
 * over {@code LOADOUT_SIZE} or {@code MAX_LOADOUTS} belongs here rather than inline in a draw call,
 * so the next change to either constant breaks a test instead of the screen.
 *
 * <p>All coordinates are screen-local: add {@code leftPos} / {@code topPos} to place them.
 */
public final class CodexLayout {

    /** A rectangle with a name, so a failed overlap assertion says which two things collided. */
    public record Rect(String name, int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean contains(Rect inner) {
            return inner.x >= x && inner.y >= y && inner.right() <= right() && inner.bottom() <= bottom();
        }
    }

    // ---- the loadout panel on the codex main page -------------------------------------------

    public static final int PANEL_X = 160;
    public static final int PANEL_Y = 30;
    public static final int PANEL_W = 274;
    public static final int PANEL_H = 108;

    /** Origin the panel's contents are laid out from; everything below is an offset off this. */
    public static final int ORIGIN_X = 170;
    public static final int ORIGIN_Y = 40;

    /** The active loadout's name, on its own line so a long name has the full panel width. */
    public static final int NAME_DY = 0;
    public static final int NAME_H = 10;
    public static final int NAME_MAX_W = 250;

    public static final int CARD_DY = 14;
    public static final int CARD_W = 54;
    public static final int CARD_H = 26;
    public static final int CARD_STRIDE = 62;

    public static final int ACTION_DY = 46;
    public static final int ACTION_W = 88;
    public static final int ACTION_H = 20;
    public static final int CLEAR_DX = 96;

    public static final int TAB_DY = 70;
    public static final int TAB_W = 184;
    public static final int TAB_H = 20;

    /** The two header buttons sit above the panel, on the title strip. */
    public static final int HEADER_DY = -32;
    public static final int PASSIVES_DX = 128;
    public static final int PASSIVES_W = 64;
    public static final int CLASSES_DX = 198;
    public static final int CLASSES_W = 62;

    public static int cardX(int slot) {
        return ORIGIN_X + slot * CARD_STRIDE;
    }

    // ---- the Loadouts tab ---------------------------------------------------------------------

    public static final int EDITOR_X = 22;
    public static final int EDITOR_Y = 22;
    public static final int EDITOR_W = 400;
    public static final int EDITOR_H = 300;

    public static final int BACK_DX = EDITOR_W - 54;
    public static final int BACK_DY = 6;
    public static final int BACK_W = 44;
    public static final int BACK_H = 16;

    public static final int LIST_DX = 14;
    public static final int LIST_DY = 34;
    public static final int LIST_W = 166;
    public static final int LIST_ROW_H = 17;
    public static final int LIST_ROW_STRIDE = 20;

    public static final int LIST_ACTION_DY = LIST_DY + MagicContent.MAX_LOADOUTS * LIST_ROW_STRIDE + 10;
    public static final int LIST_ACTION_W = 80;
    public static final int LIST_ACTION_H = 18;
    public static final int DELETE_DX = LIST_DX + 86;

    public static final int SLOT_DX = 200;
    public static final int SLOT_W = 158;
    public static final int SLOT_H = 38;
    public static final int SLOT_STRIDE = 44;
    /** 12 high, not 10: the font is 9, and a 10px button clips its own label top and bottom. */
    public static final int SLOT_BUTTON_DY = 25;
    public static final int SLOT_BUTTON_W = 70;
    public static final int SLOT_BUTTON_H = 12;
    public static final int SLOT_BIND_DX = SLOT_DX + 6;
    public static final int SLOT_CLEAR_DX = SLOT_DX + 82;

    /**
     * Which skill a Bind press would use. Sits on the title strip, above the slot column, and stops
     * short of the Back button in the corner.
     */
    public static final int BIND_TARGET_DX = SLOT_DX + 22;
    public static final int BIND_TARGET_DY = 10;
    public static final int BIND_TARGET_MAX_W = BACK_DX - 4 - BIND_TARGET_DX;

    public static int slotY(int slot) {
        return LIST_DY + slot * SLOT_STRIDE;
    }

    private CodexLayout() {}

    /** Every rectangle the loadout panel draws or hit-tests, for the overlap check. */
    public static List<Rect> loadoutPanelRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect("name", ORIGIN_X, ORIGIN_Y + NAME_DY, NAME_MAX_W, NAME_H));
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            rects.add(new Rect("card " + slot, cardX(slot), ORIGIN_Y + CARD_DY, CARD_W, CARD_H));
        }
        rects.add(new Rect("equip", ORIGIN_X, ORIGIN_Y + ACTION_DY, ACTION_W, ACTION_H));
        rects.add(new Rect("clear", ORIGIN_X + CLEAR_DX, ORIGIN_Y + ACTION_DY, ACTION_W, ACTION_H));
        rects.add(new Rect("loadouts tab", ORIGIN_X, ORIGIN_Y + TAB_DY, TAB_W, TAB_H));
        return rects;
    }

    /** The two header buttons, which sit outside the panel and must stay outside it. */
    public static List<Rect> headerRects() {
        return List.of(
                new Rect("passives", ORIGIN_X + PASSIVES_DX, ORIGIN_Y + HEADER_DY, PASSIVES_W, ACTION_H),
                new Rect("classes", ORIGIN_X + CLASSES_DX, ORIGIN_Y + HEADER_DY, CLASSES_W, ACTION_H));
    }

    public static Rect loadoutPanel() {
        return new Rect("panel", PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
    }

    public static Rect editorPanel() {
        return new Rect("editor", EDITOR_X, EDITOR_Y, EDITOR_W, EDITOR_H);
    }

    /** Every rectangle the Loadouts tab draws or hit-tests. */
    public static List<Rect> loadoutEditorRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect("back", EDITOR_X + BACK_DX, EDITOR_Y + BACK_DY, BACK_W, BACK_H));
        rects.add(new Rect("bind target", EDITOR_X + BIND_TARGET_DX, EDITOR_Y + BIND_TARGET_DY,
                BIND_TARGET_MAX_W, NAME_H));
        for (int index = 0; index < MagicContent.MAX_LOADOUTS; index++) {
            rects.add(new Rect("row " + index, EDITOR_X + LIST_DX,
                    EDITOR_Y + LIST_DY + index * LIST_ROW_STRIDE, LIST_W, LIST_ROW_H));
        }
        rects.add(new Rect("new", EDITOR_X + LIST_DX, EDITOR_Y + LIST_ACTION_DY, LIST_ACTION_W, LIST_ACTION_H));
        rects.add(new Rect("delete", EDITOR_X + DELETE_DX, EDITOR_Y + LIST_ACTION_DY, LIST_ACTION_W, LIST_ACTION_H));
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            rects.add(new Rect("slot " + slot, EDITOR_X + SLOT_DX, EDITOR_Y + slotY(slot), SLOT_W, SLOT_H));
        }
        return rects;
    }
}
