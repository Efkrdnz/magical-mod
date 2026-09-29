package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/** The Playbill's geometry: three tabs over three columns, all of it a function of the gui. */
public record PlaybillLayout(int guiWidth, int guiHeight) {
    public static final int MARGIN = 16;
    public static final int GAP = 10;
    public static final int LINE = 12;
    public static final int TOP = 14;
    public static final int MAX_TAB = 72;
    public static final int BUTTON = 44;
    /** Gap between the Stance and Reaction columns, which share the middle column. */
    public static final int INNER_GAP = 4;
    /**
     * The widest Stance and Reaction words in font pixels, six per character: "Wander"/"Follow"
     * and "Approach". That is the width Font.plainSubstrByWidth counts, trailing advance included,
     * so a cell narrower than this cuts the last letter off.
     */
    public static final int WIDEST_STANCE_LABEL = 6 * 6;
    public static final int WIDEST_REACTION_LABEL = 8 * 6;

    public int columnWidth() {
        return (guiWidth - 2 * MARGIN - 2 * GAP) / 3;
    }

    private int column(int i) {
        return MARGIN + i * (columnWidth() + GAP);
    }

    public Rect tab(int i) {
        int width = Math.min(MAX_TAB, columnWidth());
        return new Rect("tab" + i, MARGIN + i * (width + GAP), TOP, width, LINE);
    }

    public int contentTop() {
        return TOP + 2 * LINE;
    }

    private int floor() {
        return guiHeight - MARGIN - LINE - 4;
    }

    public int rows() {
        return (floor() - contentTop()) / LINE;
    }

    public Rect row(int i) {
        return new Rect("row" + i, column(0), contentTop() + i * LINE, columnWidth(), LINE);
    }

    /** Senses start one line down: the line above is their heading. */
    public Rect sense(int i) {
        return new Rect("sense" + i, column(1), contentTop() + (i + 1) * LINE, columnWidth(), LINE);
    }

    /** Reaction has the longer words, so it is given its share first and Stance takes what is left. */
    private int reactionWidth() {
        return Math.max(WIDEST_REACTION_LABEL, (columnWidth() - INNER_GAP) / 2);
    }

    public int stanceWidth() {
        return columnWidth() - INNER_GAP - reactionWidth();
    }

    public Rect stance(int i) {
        return new Rect("stance" + i, column(1), contentTop() + (i + 6) * LINE, stanceWidth(), LINE);
    }

    public Rect reaction(int i) {
        return new Rect("reaction" + i, column(1) + stanceWidth() + INNER_GAP, contentTop() + (i + 6) * LINE, reactionWidth(), LINE);
    }

    /**
     * Everything in the forecast but its plausibility terms: eleven lines and three 4 px gaps. The
     * terms get what is left above the buttons, largest movers first. The bound is the top of Save
     * rather than {@link #floor()}: the forecast column runs under the buttons, and at the smallest
     * gui the floor's four spare pixels are the difference between two terms and one.
     */
    public static final int FORECAST_FIXED = 11 * LINE + 3 * 4;

    public int forecastTerms() {
        return Math.max(0, (save().y() - contentTop() - FORECAST_FIXED) / LINE);
    }

    public int forecastX() {
        return column(2);
    }

    public int forecastWidth() {
        return columnWidth();
    }

    public Rect done() {
        return new Rect("done", guiWidth - MARGIN - BUTTON, guiHeight - MARGIN - LINE, BUTTON, LINE);
    }

    public Rect save() {
        return new Rect("save", guiWidth - MARGIN - 2 * BUTTON - GAP, guiHeight - MARGIN - LINE, BUTTON, LINE);
    }

    public int tabAt(double x, double y) {
        return hit(x, y, 3, this::tab);
    }

    public int rowAt(double x, double y) {
        return hit(x, y, rows(), this::row);
    }

    /** How far the element list can scroll: none while every element has a row of its own. */
    public int maxScroll(int count) {
        return Math.max(0, count - rows());
    }

    /** A scroll held inside the list, for a list that may have shrunk since. */
    public int clampScroll(int scroll, int count) {
        return Math.max(0, Math.min(scroll, maxScroll(count)));
    }

    /** One row per notch of the wheel: turned up (positive) toward the top of the list, down toward its end. */
    public int scrolled(int scroll, double wheel, int count) {
        return clampScroll(scroll - (int) Math.signum(wheel), count);
    }

    /** Whether the pointer is over the element rows, the one place the wheel scrolls them. */
    public boolean overElements(double x, double y) {
        Rect first = row(0);
        return x >= first.x() && x < first.right() && y >= first.y() && y < first.y() + rows() * LINE;
    }

    /** The element a click on the rows chooses, with the list scrolled by {@code scroll}; -1 for none. */
    public int elementAt(double x, double y, int scroll, int count) {
        int row = rowAt(x, y);
        return row < 0 || scroll + row >= count ? -1 : scroll + row;
    }

    public int senseAt(double x, double y) {
        return hit(x, y, 3, this::sense);
    }

    public int stanceAt(double x, double y) {
        return hit(x, y, 4, this::stance);
    }

    public int reactionAt(double x, double y) {
        return hit(x, y, 5, this::reaction);
    }

    public static boolean inside(Rect rect, double x, double y) {
        return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
    }

    private static int hit(double x, double y, int count, java.util.function.IntFunction<Rect> rect) {
        for (int i = 0; i < count; i++) {
            if (inside(rect.apply(i), x, y)) {
                return i;
            }
        }
        return -1;
    }
}
