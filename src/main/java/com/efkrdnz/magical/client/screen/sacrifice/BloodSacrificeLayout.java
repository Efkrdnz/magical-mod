package com.efkrdnz.magical.client.screen.sacrifice;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.ScreenChrome;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Geometry for the Blood Sacrifice pact screen, kept out of the screen so a test can check it.
 *
 * <p>Three columns: what you gain on the left, what it costs you in the middle, and the pact
 * itself on the right. Both lists show the same number of rows and end at the same height, because
 * the whole point of the screen is that the two halves are weighed against each other.
 *
 * <p>Every rectangle the screen paints or hit-tests is a function here, and every hit-test is a
 * function of the same numbers, so a row can never be drawn in one place and clicked in another.
 * All coordinates are screen-local: add the screen's left and top to place them.
 */
public final class BloodSacrificeLayout {

    /** The same frame the codex and the creator wear. */
    public static final int PANEL_W = ScreenChrome.PANEL_W;
    public static final int PANEL_H = ScreenChrome.PANEL_H;

    // ---- header ---------------------------------------------------------------------------------

    public static final int TITLE_X = ScreenChrome.TITLE_X;
    public static final int TITLE_Y = ScreenChrome.TITLE_Y;
    public static final int TITLE_W = ScreenChrome.TITLE_W;
    public static final int TEXT_H = ScreenChrome.TEXT_H;

    public static final int CHIP_X = ScreenChrome.CHIP_X;
    public static final int CHIP_Y = ScreenChrome.CHIP_Y;
    public static final int CHIP_W = ScreenChrome.CHIP_W;
    public static final int CHIP_H = ScreenChrome.CHIP_H;

    /**
     * The chrome's progress bar, spending its life here as the points bar.
     *
     * <p>It is the one number the player is actually budgeting against, so it belongs in the same
     * place every other screen puts the number you are working toward.
     */
    public static final int POINTS_X = ScreenChrome.XP_X;
    public static final int POINTS_Y = ScreenChrome.XP_Y;
    public static final int POINTS_W = ScreenChrome.XP_W;
    public static final int POINTS_H = ScreenChrome.XP_H;

    public static final int CLOSE_X = ScreenChrome.CORNER_X;
    public static final int CLOSE_Y = ScreenChrome.CORNER_Y;
    public static final int CLOSE_W = ScreenChrome.CORNER_W;
    public static final int CLOSE_H = ScreenChrome.CORNER_H;

    /** No tabs: the strip the codex spends on them carries the one line of instruction instead. */
    public static final int CAPTION_X = 12;
    public static final int CAPTION_Y = 30;
    public static final int CAPTION_W = 370;

    public static final int BODY_X = ScreenChrome.BODY_X;
    public static final int BODY_Y = ScreenChrome.BODY_Y;
    public static final int BODY_W = ScreenChrome.BODY_W;
    public static final int BODY_H = ScreenChrome.BODY_H;

    // ---- the three columns ------------------------------------------------------------------------

    public static final int LABEL_Y = 50;
    public static final int LIST_Y = 64;
    public static final int ROW_H = 20;
    public static final int ROW_STRIDE = 22;

    /** Rows visible in each list at once. Sixteen entries a side, so both lists scroll. */
    public static final int ROWS = 11;

    public static final int LIST_W = 140;
    public static final int SCROLLBAR_W = 5;

    public static final int BOON_X = 18;
    public static final int BOON_SCROLL_X = 160;
    public static final int PRICE_X = 170;
    public static final int PRICE_SCROLL_X = 312;

    public static final int PACT_X = 320;
    public static final int PACT_W = 106;

    /** Row furniture, measured from the row's own corner. */
    public static final int COST_DX = 3;
    public static final int COST_DY = 3;
    public static final int COST_SIZE = 14;
    public static final int ROW_TEXT_DX = 21;
    public static final int ROW_TEXT_DY = 5;
    public static final int MARK_SIZE = 12;
    public static final int MARK_RIGHT_INSET = 16;
    public static final int MARK_DY = 4;

    public static final int HINT_Y = 310;

    // ---- the pact panel ---------------------------------------------------------------------------

    public static final int PACT_INSET = 6;
    public static final int REQUIREMENT_Y = 70;
    public static final int REQUIREMENT_H = 22;
    public static final int CLOCKS_Y = 96;
    public static final int CLOCKS_H = 24;
    public static final int CHOSEN_Y = 124;
    public static final int CHOSEN_H = 130;
    public static final int SEAL_Y = 258;
    public static final int SEAL_H = 20;
    public static final int BROKER_Y = 282;
    public static final int BROKER_H = 18;

    /** Tooltip lines are wrapped to this so a description never runs off a small window. */
    public static final int TOOLTIP_W = 220;

    private BloodSacrificeLayout() {}

    // ---- rectangles -------------------------------------------------------------------------------

    public static Rect panel() {
        return new Rect("panel", 0, 0, PANEL_W, PANEL_H);
    }

    public static Rect title() {
        return new Rect("title", TITLE_X, TITLE_Y, TITLE_W, TEXT_H);
    }

    public static Rect vesselChip() {
        return new Rect("vessel chip", CHIP_X, CHIP_Y, CHIP_W, CHIP_H);
    }

    public static Rect pointsBar() {
        return new Rect("points bar", POINTS_X, POINTS_Y, POINTS_W, POINTS_H);
    }

    public static Rect close() {
        return new Rect("close", CLOSE_X, CLOSE_Y, CLOSE_W, CLOSE_H);
    }

    public static Rect caption() {
        return new Rect("caption", CAPTION_X, CAPTION_Y, CAPTION_W, TEXT_H);
    }

    public static Rect body() {
        return new Rect("body", BODY_X, BODY_Y, BODY_W, BODY_H);
    }

    public static Rect boonLabel() {
        return new Rect("boon label", BOON_X, LABEL_Y, LIST_W, TEXT_H);
    }

    public static Rect priceLabel() {
        return new Rect("price label", PRICE_X, LABEL_Y, LIST_W, TEXT_H);
    }

    public static Rect pactLabel() {
        return new Rect("pact label", PACT_X, LABEL_Y, PACT_W, TEXT_H);
    }

    public static Rect boonRow(int visibleRow) {
        return new Rect("boon " + visibleRow, BOON_X, LIST_Y + visibleRow * ROW_STRIDE, LIST_W, ROW_H);
    }

    public static Rect priceRow(int visibleRow) {
        return new Rect("price " + visibleRow, PRICE_X, LIST_Y + visibleRow * ROW_STRIDE, LIST_W, ROW_H);
    }

    public static Rect boonCost(int visibleRow) {
        return cost("boon cost " + visibleRow, boonRow(visibleRow));
    }

    public static Rect priceCost(int visibleRow) {
        return cost("price cost " + visibleRow, priceRow(visibleRow));
    }

    public static Rect boonText(int visibleRow) {
        return text("boon text " + visibleRow, boonRow(visibleRow));
    }

    public static Rect priceText(int visibleRow) {
        return text("price text " + visibleRow, priceRow(visibleRow));
    }

    public static Rect boonMark(int visibleRow) {
        return mark("boon mark " + visibleRow, boonRow(visibleRow));
    }

    public static Rect priceMark(int visibleRow) {
        return mark("price mark " + visibleRow, priceRow(visibleRow));
    }

    private static Rect cost(String name, Rect row) {
        return new Rect(name, row.x() + COST_DX, row.y() + COST_DY, COST_SIZE, COST_SIZE);
    }

    private static Rect text(String name, Rect row) {
        int width = LIST_W - ROW_TEXT_DX - MARK_RIGHT_INSET - 2;
        return new Rect(name, row.x() + ROW_TEXT_DX, row.y() + ROW_TEXT_DY, width, TEXT_H);
    }

    private static Rect mark(String name, Rect row) {
        return new Rect(name, row.right() - MARK_RIGHT_INSET, row.y() + MARK_DY, MARK_SIZE, MARK_SIZE);
    }

    public static Rect boonScrollbar() {
        return new Rect("boon scrollbar", BOON_SCROLL_X, LIST_Y, SCROLLBAR_W, listHeight());
    }

    public static Rect priceScrollbar() {
        return new Rect("price scrollbar", PRICE_SCROLL_X, LIST_Y, SCROLLBAR_W, listHeight());
    }

    /** Top of the first row to the bottom of the last, gaps included but no trailing one. */
    private static int listHeight() {
        return (ROWS - 1) * ROW_STRIDE + ROW_H;
    }

    public static Rect pactPanel() {
        return new Rect("pact panel", PACT_X, LIST_Y, PACT_W, listHeight());
    }

    public static Rect requirement() {
        return inset("requirement", REQUIREMENT_Y, REQUIREMENT_H);
    }

    public static Rect clocks() {
        return inset("clocks", CLOCKS_Y, CLOCKS_H);
    }

    public static Rect chosen() {
        return inset("chosen", CHOSEN_Y, CHOSEN_H);
    }

    public static Rect sealButton() {
        return inset("seal", SEAL_Y, SEAL_H);
    }

    public static Rect brokerNote() {
        return inset("broker note", BROKER_Y, BROKER_H);
    }

    private static Rect inset(String name, int y, int h) {
        return new Rect(name, PACT_X + PACT_INSET, y, PACT_W - PACT_INSET * 2, h);
    }

    public static Rect hint() {
        return new Rect("hint", BOON_X, HINT_Y, PRICE_SCROLL_X + SCROLLBAR_W - BOON_X, TEXT_H);
    }

    // ---- the lists the test sweeps ----------------------------------------------------------------

    public static List<Rect> headerRects() {
        return List.of(title(), vesselChip(), pointsBar(), close());
    }

    /** Everything drawn inside the body panel. The pact panel stands for its own furniture. */
    public static List<Rect> bodyRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(boonLabel());
        rects.add(priceLabel());
        rects.add(pactLabel());
        for (int row = 0; row < ROWS; row++) {
            rects.add(boonRow(row));
            rects.add(priceRow(row));
        }
        rects.add(boonScrollbar());
        rects.add(priceScrollbar());
        rects.add(pactPanel());
        rects.add(hint());
        return rects;
    }

    public static List<Rect> pactRects() {
        return List.of(requirement(), clocks(), chosen(), sealButton(), brokerNote());
    }

    // ---- hit tests ---------------------------------------------------------------------------------

    public static boolean hit(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    public static int boonRowAt(double lx, double ly) {
        return rowAt(lx, ly, BOON_X);
    }

    public static int priceRowAt(double lx, double ly) {
        return rowAt(lx, ly, PRICE_X);
    }

    private static int rowAt(double lx, double ly, int left) {
        if (lx < left || lx >= left + LIST_W) {
            return -1;
        }
        double along = ly - LIST_Y;
        if (along < 0.0D) {
            return -1;
        }
        int index = (int) (along / ROW_STRIDE);
        if (index >= ROWS || along - index * ROW_STRIDE >= ROW_H) {
            return -1;
        }
        return index;
    }

    /** How far a list of {@code total} entries may be scrolled when {@code visible} of them fit. */
    public static int clampScroll(int scroll, int total, int visible) {
        return Mth.clamp(scroll, 0, Math.max(0, total - visible));
    }
}
