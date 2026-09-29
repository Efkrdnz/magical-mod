package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/**
 * Where everything on the Lexicon inventory sits, in vanilla creative's own numbers
 * ({@code CreativeModeInventoryScreen}): the 195x136 panel centred, a 9x5 grid of 18px cells, the
 * belt on the row where creative draws the hotbar, the scroller down the right, the tabs along the
 * top. Pure, so {@code LexiconInventoryLayoutTest} can hold it at every gui size. Hit tests are
 * half-open, so the 2px gutters between cells answer to nothing.
 */
public record LexiconInventoryLayout(int guiWidth, int guiHeight, boolean playbill) {
    public static final int PANEL_W = 195;
    public static final int PANEL_H = 136;
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    public static final int GRID_CELLS = COLUMNS * ROWS;
    public static final int BELT_CELLS = 9;
    public static final int CELL = 16;
    public static final int PITCH = 18;
    public static final int TAB_W = 26;
    public static final int TAB_H = 32;
    public static final int TAB_PITCH = 27;
    /** How far above the panel a top tab starts, as creative draws it. */
    public static final int TAB_RISE = 28;
    public static final int THUMB_W = 12;
    public static final int THUMB_H = 15;
    private static final int GRID_X = 9;
    private static final int GRID_Y = 18;
    private static final int BELT_Y = 112;
    private static final int SCROLLER_X = 175;
    private static final int SCROLLER_W = 14;
    private static final int SCROLLER_H = 112;
    /** Creative's thumb travel: the scroller less 17, not less the thumb's own 15. */
    private static final int THUMB_TRAVEL = SCROLLER_H - 17;

    public int left() {
        return (guiWidth - PANEL_W) / 2;
    }

    public int top() {
        return (guiHeight - PANEL_H) / 2;
    }

    public Rect panel() {
        return new Rect("panel", left(), top(), PANEL_W, PANEL_H);
    }

    public Rect grid(int index) {
        int row = index / COLUMNS;
        int col = index % COLUMNS;
        return new Rect("grid", left() + GRID_X + col * PITCH, top() + GRID_Y + row * PITCH, CELL, CELL);
    }

    public int gridAt(double mx, double my) {
        for (int i = 0; i < GRID_CELLS; i++) {
            if (hit(grid(i), mx, my)) {
                return i;
            }
        }
        return -1;
    }

    public Rect belt(int index) {
        return new Rect("belt", left() + GRID_X + index * PITCH, top() + BELT_Y, CELL, CELL);
    }

    public int beltAt(double mx, double my) {
        for (int i = 0; i < BELT_CELLS; i++) {
            if (hit(belt(i), mx, my)) {
                return i;
            }
        }
        return -1;
    }

    public Rect scroller() {
        return new Rect("scroller", left() + SCROLLER_X, top() + GRID_Y, SCROLLER_W, SCROLLER_H);
    }

    /** The thumb at a scroll of 0 (top) to 1 (bottom), placed as creative places it. */
    public Rect thumb(float scroll) {
        float clamped = Math.max(0.0F, Math.min(1.0F, scroll));
        return new Rect("thumb", left() + SCROLLER_X, top() + GRID_Y + (int) (THUMB_TRAVEL * clamped), THUMB_W, THUMB_H);
    }

    /** The scroll a thumb dragged to this height means: creative's own arithmetic, clamped. */
    public float scrollFor(double my) {
        double from = top() + GRID_Y;
        double scroll = (my - from - THUMB_H / 2.0) / (SCROLLER_H - THUMB_H);
        return (float) Math.max(0.0, Math.min(1.0, scroll));
    }

    public Rect search() {
        return new Rect("search", left() + 82, top() + 6, 80, 9);
    }

    /** Six tabs a page beside the Playbill's, seven without it: creative's row of seven. */
    public int tabsPerPage() {
        return playbill ? 6 : 7;
    }

    public Rect tab(int index) {
        return new Rect("tab", left() + index * TAB_PITCH, top() - TAB_RISE, TAB_W, TAB_H);
    }

    public int tabAt(double mx, double my) {
        for (int i = 0; i < tabsPerPage(); i++) {
            if (hit(tab(i), mx, my)) {
                return i;
            }
        }
        return -1;
    }

    /** At the panel's right edge, where creative hangs the survival inventory's tab. */
    public Rect playbillTab() {
        return new Rect("playbill", left() + PANEL_W - TAB_W, top() - TAB_RISE, TAB_W, TAB_H);
    }

    public boolean onPlaybill(double mx, double my) {
        return playbill && hit(playbillTab(), mx, my);
    }

    public Rect pagePrev() {
        return new Rect("prev", left() - 16, top() - 22, 12, 12);
    }

    public Rect pageNext() {
        return new Rect("next", left() + PANEL_W + 4, top() - 22, 12, 12);
    }

    public boolean onPanel(double mx, double my) {
        return hit(panel(), mx, my);
    }

    /**
     * The panel and the whole band above it the tabs and the page arrows stand in, gaps included:
     * a click here is a mis-aim at the frame, never a lie let go of.
     */
    public boolean onFrame(double mx, double my) {
        Rect panel = panel();
        int top = Math.min(tab(0).y(), pagePrev().y());
        boolean band = mx >= pagePrev().x() && mx < pageNext().right() && my >= top && my < panel.y();
        return band || onPanel(mx, my);
    }

    public static boolean hit(Rect rect, double mx, double my) {
        return mx >= rect.x() && mx < rect.right() && my >= rect.y() && my < rect.bottom();
    }
}
