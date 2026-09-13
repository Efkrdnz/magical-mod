package com.efkrdnz.magical.client.screen.creator;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Geometry for the Spell Creator screen, kept out of the screen so a test can check it.
 *
 * <p>Every rectangle the screen paints or hit-tests is a function here, and every hit-test is a
 * function of the same numbers, so a row can never be drawn in one place and clicked in another.
 * {@code SpellCreatorLayoutTest} asserts that nothing overlaps, that everything sits inside the
 * body, and that each row answers to its own centre and to nothing else.
 *
 * <p>All coordinates are screen-local: add the screen's left and top to place them.
 */
public final class SpellCreatorLayout {

    /** The codex footprint, so Back and forth does not move the frame. */
    public static final int PANEL_W = 444;
    public static final int PANEL_H = 340;

    // ---- header ---------------------------------------------------------------------------------

    public static final int TITLE_X = 12;
    public static final int TITLE_Y = 8;
    public static final int TITLE_W = 108;
    public static final int TEXT_H = 10;

    public static final int CHIP_X = 126;
    public static final int CHIP_Y = 6;
    public static final int CHIP_W = 110;
    public static final int CHIP_H = 14;

    public static final int XP_X = 242;
    public static final int XP_Y = 6;
    public static final int XP_W = 140;
    public static final int XP_H = 14;

    public static final int BACK_X = 390;
    public static final int BACK_Y = 5;
    public static final int BACK_W = 44;
    public static final int BACK_H = 16;

    // ---- tabs and body --------------------------------------------------------------------------

    public static final int TAB_COUNT = 2;
    public static final int TAB_X = 12;
    public static final int TAB_Y = 28;
    public static final int TAB_W = 90;
    public static final int TAB_H = 14;
    public static final int TAB_GAP = 2;

    public static final int BODY_X = 10;
    public static final int BODY_Y = 44;
    public static final int BODY_W = 424;
    public static final int BODY_H = 284;

    // ---- the Create tab -------------------------------------------------------------------------

    public static final int INGREDIENTS_LABEL_Y = 50;

    public static final int LIST_X = 18;
    public static final int LIST_Y = 64;
    public static final int LIST_W = 160;
    public static final int ROW_H = 20;
    public static final int ROW_STRIDE = 22;
    public static final int INGREDIENT_ROWS = 11;
    public static final int LIST_SCROLL_X = 181;
    public static final int SCROLLBAR_W = 5;
    public static final int LIST_HINT_Y = 310;

    public static final int SLOT_Y = 50;
    public static final int SLOT_W = 104;
    public static final int SLOT_H = 46;
    public static final int SLOT_ONE_X = 192;
    public static final int SLOT_TWO_X = 322;
    public static final int SLOT_CLEAR_DX = 90;
    public static final int SLOT_CLEAR_DY = 4;
    public static final int SLOT_CLEAR_SIZE = 10;
    public static final int SLOT_EMBLEM_DX = 16;
    public static final int SLOT_EMBLEM_DY = 30;
    public static final int SLOT_EMBLEM_HALF = 9;
    public static final int SLOT_TEXT_DX = 32;

    public static final int PLUS_X = 300;
    public static final int PLUS_Y = 64;
    public static final int PLUS_SIZE = 18;

    public static final int RESULT_X = 192;
    public static final int RESULT_Y = 102;
    public static final int RESULT_W = 234;
    public static final int RESULT_H = 118;
    public static final int RESULT_EMBLEM_DX = 26;
    public static final int RESULT_EMBLEM_DY = 28;
    public static final int RESULT_EMBLEM_HALF = 16;
    public static final int RESULT_TEXT_DX = 50;
    public static final int RESULT_TEXT_DY = 10;
    public static final int RESULT_TEXT_W = 174;
    public static final int RESULT_TEXT_H = 100;

    public static final int CREATE_X = 328;
    public static final int CREATE_Y = 226;
    public static final int CREATE_W = 98;
    public static final int CREATE_H = 20;

    public static final int NOTE_X = 192;
    public static final int NOTE_Y = 252;
    public static final int NOTE_W = 234;
    public static final int NOTE_H = 68;

    // ---- the Formulas tab -----------------------------------------------------------------------

    public static final int FORMULA_X = 18;
    public static final int FORMULA_Y = 50;
    public static final int FORMULA_W = 404;
    public static final int FORMULA_ROW_H = 26;
    public static final int FORMULA_ROW_STRIDE = 28;
    public static final int FORMULA_ROWS = 9;
    public static final int FORMULA_SCROLL_X = 425;
    public static final int FORMULA_EMBLEM_DX = 14;
    public static final int FORMULA_EMBLEM_DY = 13;
    public static final int FORMULA_EMBLEM_HALF = 9;
    public static final int FORMULA_TEXT_DX = 28;
    public static final int FORMULA_TEXT_DY = 3;
    public static final int FORMULA_TEXT_W = 230;
    public static final int FORMULA_TEXT_H = 20;
    public static final int FORMULA_CHIP_W = 130;
    public static final int FORMULA_CHIP_H = 14;
    public static final int FORMULA_CHIP_RIGHT_INSET = 6;
    public static final int FORMULA_CHIP_DY = 6;
    public static final int FORMULA_HINT_Y = 306;

    /** Tooltip lines are wrapped to this so a description never runs off a small window. */
    public static final int TOOLTIP_W = 220;
    /** How long the result card celebrates a creation. */
    public static final int REVEAL_TICKS = 20;

    private SpellCreatorLayout() {}

    // ---- rectangles ------------------------------------------------------------------------------

    public static Rect panel() {
        return new Rect("panel", 0, 0, PANEL_W, PANEL_H);
    }

    public static Rect title() {
        return new Rect("title", TITLE_X, TITLE_Y, TITLE_W, TEXT_H);
    }

    public static Rect classChip() {
        return new Rect("class chip", CHIP_X, CHIP_Y, CHIP_W, CHIP_H);
    }

    public static Rect xpBar() {
        return new Rect("xp bar", XP_X, XP_Y, XP_W, XP_H);
    }

    public static Rect back() {
        return new Rect("back", BACK_X, BACK_Y, BACK_W, BACK_H);
    }

    public static Rect tab(int index) {
        return new Rect("tab " + index, TAB_X + index * (TAB_W + TAB_GAP), TAB_Y, TAB_W, TAB_H);
    }

    public static Rect body() {
        return new Rect("body", BODY_X, BODY_Y, BODY_W, BODY_H);
    }

    public static Rect ingredientsLabel() {
        return new Rect("ingredients label", LIST_X, INGREDIENTS_LABEL_Y, 150, TEXT_H);
    }

    public static Rect ingredientRow(int visibleRow) {
        return new Rect("ingredient " + visibleRow, LIST_X, LIST_Y + visibleRow * ROW_STRIDE, LIST_W, ROW_H);
    }

    public static Rect ingredientEmblem(int visibleRow) {
        Rect row = ingredientRow(visibleRow);
        return square("ingredient emblem " + visibleRow, row.x() + 11, row.y() + ROW_H / 2, SLOT_EMBLEM_HALF);
    }

    public static Rect ingredientScrollbar() {
        return new Rect("ingredient scrollbar", LIST_SCROLL_X, LIST_Y, SCROLLBAR_W, INGREDIENT_ROWS * ROW_STRIDE);
    }

    public static Rect ingredientHint() {
        return new Rect("ingredient hint", LIST_X, LIST_HINT_Y, LIST_SCROLL_X + SCROLLBAR_W - LIST_X, TEXT_H);
    }

    public static Rect slot(int index) {
        return new Rect("slot " + index, index == 0 ? SLOT_ONE_X : SLOT_TWO_X, SLOT_Y, SLOT_W, SLOT_H);
    }

    public static Rect slotClear(int index) {
        Rect card = slot(index);
        return new Rect("slot clear " + index, card.x() + SLOT_CLEAR_DX, card.y() + SLOT_CLEAR_DY, SLOT_CLEAR_SIZE, SLOT_CLEAR_SIZE);
    }

    public static Rect slotEmblem(int index) {
        Rect card = slot(index);
        return square("slot emblem " + index, card.x() + SLOT_EMBLEM_DX, card.y() + SLOT_EMBLEM_DY, SLOT_EMBLEM_HALF);
    }

    public static Rect plus() {
        return new Rect("plus", PLUS_X, PLUS_Y, PLUS_SIZE, PLUS_SIZE);
    }

    public static Rect result() {
        return new Rect("result", RESULT_X, RESULT_Y, RESULT_W, RESULT_H);
    }

    public static Rect resultEmblem() {
        return square("result emblem", RESULT_X + RESULT_EMBLEM_DX, RESULT_Y + RESULT_EMBLEM_DY, RESULT_EMBLEM_HALF);
    }

    public static Rect resultText() {
        return new Rect("result text", RESULT_X + RESULT_TEXT_DX, RESULT_Y + RESULT_TEXT_DY, RESULT_TEXT_W, RESULT_TEXT_H);
    }

    public static Rect createButton() {
        return new Rect("create", CREATE_X, CREATE_Y, CREATE_W, CREATE_H);
    }

    public static Rect note() {
        return new Rect("note", NOTE_X, NOTE_Y, NOTE_W, NOTE_H);
    }

    public static Rect formulaRow(int visibleRow) {
        return new Rect("formula " + visibleRow, FORMULA_X, FORMULA_Y + visibleRow * FORMULA_ROW_STRIDE, FORMULA_W, FORMULA_ROW_H);
    }

    public static Rect formulaEmblem(int visibleRow) {
        Rect row = formulaRow(visibleRow);
        return square("formula emblem " + visibleRow, row.x() + FORMULA_EMBLEM_DX, row.y() + FORMULA_EMBLEM_DY, FORMULA_EMBLEM_HALF);
    }

    public static Rect formulaText(int visibleRow) {
        Rect row = formulaRow(visibleRow);
        return new Rect("formula text " + visibleRow, row.x() + FORMULA_TEXT_DX, row.y() + FORMULA_TEXT_DY, FORMULA_TEXT_W, FORMULA_TEXT_H);
    }

    public static Rect formulaChip(int visibleRow) {
        Rect row = formulaRow(visibleRow);
        return new Rect("formula chip " + visibleRow, row.right() - FORMULA_CHIP_RIGHT_INSET - FORMULA_CHIP_W,
                row.y() + FORMULA_CHIP_DY, FORMULA_CHIP_W, FORMULA_CHIP_H);
    }

    public static Rect formulaScrollbar() {
        return new Rect("formula scrollbar", FORMULA_SCROLL_X, FORMULA_Y, SCROLLBAR_W, FORMULA_ROWS * FORMULA_ROW_STRIDE);
    }

    public static Rect formulaHint() {
        return new Rect("formula hint", FORMULA_X, FORMULA_HINT_Y, FORMULA_W, TEXT_H);
    }

    private static Rect square(String name, int cx, int cy, int half) {
        return new Rect(name, cx - half, cy - half, half * 2, half * 2);
    }

    // ---- the lists the test sweeps ---------------------------------------------------------------

    public static List<Rect> headerRects() {
        return List.of(title(), classChip(), xpBar(), back());
    }

    public static List<Rect> tabRects() {
        List<Rect> rects = new ArrayList<>(TAB_COUNT);
        for (int index = 0; index < TAB_COUNT; index++) {
            rects.add(tab(index));
        }
        return rects;
    }

    public static List<Rect> createTabRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(ingredientsLabel());
        for (int row = 0; row < INGREDIENT_ROWS; row++) {
            rects.add(ingredientRow(row));
        }
        rects.add(ingredientScrollbar());
        rects.add(ingredientHint());
        rects.add(slot(0));
        rects.add(slot(1));
        rects.add(plus());
        rects.add(result());
        rects.add(createButton());
        rects.add(note());
        return rects;
    }

    public static List<Rect> formulasTabRects() {
        List<Rect> rects = new ArrayList<>();
        for (int row = 0; row < FORMULA_ROWS; row++) {
            rects.add(formulaRow(row));
        }
        rects.add(formulaScrollbar());
        rects.add(formulaHint());
        return rects;
    }

    // ---- hit tests --------------------------------------------------------------------------------

    public static boolean hit(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    public static int tabAt(double lx, double ly) {
        return stripIndex(lx - TAB_X, TAB_W, TAB_W + TAB_GAP, TAB_COUNT, ly >= TAB_Y && ly < TAB_Y + TAB_H);
    }

    public static int ingredientRowAt(double lx, double ly) {
        return stripIndex(ly - LIST_Y, ROW_H, ROW_STRIDE, INGREDIENT_ROWS, lx >= LIST_X && lx < LIST_X + LIST_W);
    }

    public static int formulaRowAt(double lx, double ly) {
        return stripIndex(ly - FORMULA_Y, FORMULA_ROW_H, FORMULA_ROW_STRIDE, FORMULA_ROWS, lx >= FORMULA_X && lx < FORMULA_X + FORMULA_W);
    }

    public static int slotAt(double lx, double ly) {
        for (int index = 0; index < 2; index++) {
            if (hit(slot(index), lx, ly)) {
                return index;
            }
        }
        return -1;
    }

    /** Which cell of a strip of {@code count} cells, {@code size} long every {@code stride}, a coordinate lands in; -1 in a gap or outside. */
    private static int stripIndex(double along, int size, int stride, int count, boolean across) {
        if (!across || along < 0.0D) {
            return -1;
        }
        int index = (int) (along / stride);
        if (index >= count || along - index * stride >= size) {
            return -1;
        }
        return index;
    }

    public static int clampScroll(int scroll, int total, int visible) {
        return Mth.clamp(scroll, 0, Math.max(0, total - visible));
    }
}
