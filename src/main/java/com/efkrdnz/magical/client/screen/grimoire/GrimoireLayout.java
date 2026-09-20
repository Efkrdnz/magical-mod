package com.efkrdnz.magical.client.screen.grimoire;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.ScreenChrome;
import com.efkrdnz.magical.magic.incantation.Grimoire;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Geometry for the Grimoire screen, kept out of the screen so a test can check it.
 *
 * <p>Three columns under the shared chrome: the <b>shelf</b> on the left lists every verse the
 * wielder knows; the <b>page</b> in the middle is the incantation on the selected tab, one line per
 * verse written, as many lines as an incantation may hold; the <b>margin</b> on the right carries
 * the breath, the reading (what a press would cast, from the pure Reciter), and Save and Clear.
 * Every rectangle the screen paints or hit-tests is a function here and every hit-test is a function
 * of the same numbers, so a line can never be drawn in one place and struck in another;
 * {@code GrimoireLayoutTest} asserts that nothing overlaps and everything sits inside the body.
 *
 * <p>All coordinates are screen-local: add the screen's left and top to place them.
 */
public final class GrimoireLayout {

    public static final int PANEL_W = ScreenChrome.PANEL_W;
    public static final int PANEL_H = ScreenChrome.PANEL_H;
    public static final int TEXT_H = ScreenChrome.TEXT_H;

    /** One tab per incantation slot. */
    public static final int TAB_COUNT = Grimoire.SLOTS;
    public static final int TAB_Y = ScreenChrome.TAB_Y;
    public static final int TAB_GAP = ScreenChrome.TAB_GAP;

    // ---- the shelf ------------------------------------------------------------------------------

    public static final int SHELF_LABEL_Y = 50;
    public static final int SHELF_X = 18;
    public static final int SHELF_Y = 64;
    public static final int SHELF_W = 160;
    public static final int SHELF_ROW_H = 20;
    public static final int SHELF_STRIDE = 22;
    public static final int SHELF_ROWS = 11;
    public static final int SHELF_SCROLL_X = 181;
    public static final int SCROLLBAR_W = 5;
    /** Both hints sit on this row, under their columns. */
    public static final int HINT_Y = 310;

    // ---- the page -------------------------------------------------------------------------------

    public static final int PAGE_LABEL_Y = 50;
    public static final int PAGE_X = 192;
    public static final int PAGE_Y = 64;
    public static final int PAGE_W = 134;
    public static final int LINE_H = 11;
    public static final int LINE_STRIDE = 12;
    /** As many lines as an incantation may hold, so the page never needs to scroll. */
    public static final int LINES = ReciteCaps.MAX_VERSES;
    /** The strike mark at the right end of a written line. */
    public static final int LINE_STRIKE_W = 10;

    // ---- the margin -----------------------------------------------------------------------------

    public static final int MARGIN_X = 332;
    public static final int MARGIN_W = 94;
    public static final int BREATH_LABEL_Y = 50;
    public static final int BREATH_Y = 64;
    public static final int BREATH_BUTTON = 14;
    public static final int READING_Y = 86;
    public static final int READING_H = 156;
    public static final int SAVE_Y = 250;
    public static final int BUTTON_H = 20;
    public static final int CLEAR_Y = 276;

    /** Tooltip lines are wrapped to this so a description never runs off a small window. */
    public static final int TOOLTIP_W = 220;

    private GrimoireLayout() {}

    // ---- the chrome, by delegation ---------------------------------------------------------------

    public static Rect panel() {
        return ScreenChrome.panel();
    }

    public static Rect body() {
        return ScreenChrome.body();
    }

    public static Rect back() {
        return ScreenChrome.cornerButton();
    }

    public static Rect tab(int index) {
        return ScreenChrome.tab(index);
    }

    // ---- rectangles ------------------------------------------------------------------------------

    public static Rect shelfLabel() {
        return new Rect("shelf label", SHELF_X, SHELF_LABEL_Y, 150, TEXT_H);
    }

    public static Rect shelfRow(int visibleRow) {
        return new Rect("shelf row " + visibleRow, SHELF_X, SHELF_Y + visibleRow * SHELF_STRIDE, SHELF_W, SHELF_ROW_H);
    }

    public static Rect shelfScrollbar() {
        return new Rect("shelf scrollbar", SHELF_SCROLL_X, SHELF_Y, SCROLLBAR_W, SHELF_ROWS * SHELF_STRIDE);
    }

    public static Rect shelfHint() {
        return new Rect("shelf hint", SHELF_X, HINT_Y, SHELF_SCROLL_X + SCROLLBAR_W - SHELF_X, TEXT_H);
    }

    public static Rect pageLabel() {
        return new Rect("page label", PAGE_X, PAGE_LABEL_Y, PAGE_W, TEXT_H);
    }

    public static Rect line(int index) {
        return new Rect("line " + index, PAGE_X, PAGE_Y + index * LINE_STRIDE, PAGE_W, LINE_H);
    }

    public static Rect lineStrike(int index) {
        Rect row = line(index);
        return new Rect("line strike " + index, row.right() - LINE_STRIKE_W, row.y(), LINE_STRIKE_W, LINE_H);
    }

    public static Rect pageHint() {
        return new Rect("page hint", PAGE_X, HINT_Y, MARGIN_X + MARGIN_W - PAGE_X, TEXT_H);
    }

    public static Rect breathLabel() {
        return new Rect("breath label", MARGIN_X, BREATH_LABEL_Y, MARGIN_W, TEXT_H);
    }

    public static Rect breathMinus() {
        return new Rect("breath minus", MARGIN_X, BREATH_Y, BREATH_BUTTON, BREATH_BUTTON);
    }

    public static Rect breathValue() {
        return new Rect("breath value", MARGIN_X + BREATH_BUTTON + 2, BREATH_Y, MARGIN_W - 2 * (BREATH_BUTTON + 2), BREATH_BUTTON);
    }

    public static Rect breathPlus() {
        return new Rect("breath plus", MARGIN_X + MARGIN_W - BREATH_BUTTON, BREATH_Y, BREATH_BUTTON, BREATH_BUTTON);
    }

    public static Rect reading() {
        return new Rect("reading", MARGIN_X, READING_Y, MARGIN_W, READING_H);
    }

    public static Rect save() {
        return new Rect("save", MARGIN_X, SAVE_Y, MARGIN_W, BUTTON_H);
    }

    public static Rect clear() {
        return new Rect("clear", MARGIN_X, CLEAR_Y, MARGIN_W, BUTTON_H);
    }

    // ---- the lists the test sweeps ---------------------------------------------------------------

    public static List<Rect> headerRects() {
        return ScreenChrome.headerRects();
    }

    public static List<Rect> tabRects() {
        return ScreenChrome.tabRects(TAB_COUNT);
    }

    public static List<Rect> bodyRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(shelfLabel());
        for (int row = 0; row < SHELF_ROWS; row++) {
            rects.add(shelfRow(row));
        }
        rects.add(shelfScrollbar());
        rects.add(shelfHint());
        rects.add(pageLabel());
        for (int index = 0; index < LINES; index++) {
            rects.add(line(index));
        }
        rects.add(pageHint());
        rects.add(breathLabel());
        rects.add(breathMinus());
        rects.add(breathValue());
        rects.add(breathPlus());
        rects.add(reading());
        rects.add(save());
        rects.add(clear());
        return rects;
    }

    // ---- hit tests --------------------------------------------------------------------------------

    public static boolean hit(Rect rect, double lx, double ly) {
        return ScreenChrome.hit(rect, lx, ly);
    }

    public static int tabAt(double lx, double ly) {
        return ScreenChrome.tabAt(TAB_COUNT, lx, ly);
    }

    public static int shelfRowAt(double lx, double ly) {
        return stripIndex(ly - SHELF_Y, SHELF_ROW_H, SHELF_STRIDE, SHELF_ROWS, lx >= SHELF_X && lx < SHELF_X + SHELF_W);
    }

    public static int lineAt(double lx, double ly) {
        return stripIndex(ly - PAGE_Y, LINE_H, LINE_STRIDE, LINES, lx >= PAGE_X && lx < PAGE_X + PAGE_W);
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
