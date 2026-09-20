package com.efkrdnz.magical.client.screen.grimoire;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.incantation.Grimoire;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.VerseType;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the Grimoire: a run of slots floating over the world, the verses above it.
 *
 * <p>No panel, no plate and no frame, the way the Manipulate Space selector is drawn: the world is
 * dimmed and the words and glyphs are simply on it. Top to bottom, block-local: a line of
 * <b>tabs</b>, one per incantation slot; a line of <b>categories</b>, All and then one per verse
 * type; the <b>grid</b> of every known verse as its symbol, as many columns as the block is wide and
 * as many rows as the screen is tall, the rest reached by scrolling; a <b>caption</b> line that
 * names whatever the cursor is over; the <b>row</b> of twenty slots that is the incantation, each an
 * underline to be written on; the <b>controls</b> line, the breath at the row's left end and Save
 * and Clear at its right; and the <b>reading</b>, what a press would cast.
 *
 * <p>Words are placed from their measured widths, which the screen passes in, because the layout
 * has no font: {@link #wordsRow} clamps every word so a row always fits its block, and
 * {@link #categoriesFitWithWords} says when the category words must give way to their glyphs
 * alone. The block is centred on the screen. {@code GrimoireLayoutTest} sweeps every screen size
 * the game hands us and asserts that nothing drawn lands on anything else, that every hit-test
 * answers for exactly the rectangle it is drawn as, and where a drop lands.
 */
public final class GrimoireLayout {

    public static final int TAB_COUNT = Grimoire.SLOTS;
    /** All, then one per verse type. */
    public static final int CATEGORY_COUNT = VerseType.values().length + 1;
    public static final int SLOT_COUNT = ReciteCaps.MAX_VERSES;

    public static final int SCREEN_MARGIN = 6;
    public static final int BLOCK_W_MAX = 560;
    public static final int LINE_H = 9;
    public static final int ICON = VerseSymbols.SIZE;

    public static final int TAB_Y = 0;
    /** The mark that slides between tabs to say which incantation is open. */
    public static final int TAB_RULE_Y = 12;
    public static final int RULE_H = 1;
    public static final int TAB_GAP = 16;

    public static final int CATEGORY_Y = 24;
    public static final int CATEGORY_GAP = 12;
    /** A category is its glyph, this gap, then its word. */
    public static final int CATEGORY_GLYPH_GAP = 4;

    public static final int GRID_Y = 44;
    public static final int GRID_STRIDE = 22;
    public static final int GRID_ROWS_MAX = 8;

    public static final int CAPTION_GAP = 4;
    public static final int SLOTS_GAP = 14;
    public static final int SLOT_STRIDE_MAX = 24;
    /** Twenty slots must fit the narrowest block, so the floor is the symbol plus a pixel each side. */
    public static final int SLOT_STRIDE_MIN = ICON + 2;
    public static final int SLOT_H = 14;
    /** The underline within a slot, and how far it stops short of the slot's edges. */
    public static final int SLOT_BAR_Y = 12;
    public static final int SLOT_BAR_INSET = 3;
    /** How far outside the row a drop still counts as on it. */
    public static final int SLOT_SLACK = 10;

    public static final int CONTROLS_GAP = 10;
    public static final int CONTROL_GAP = 6;
    public static final int ACTION_GAP = 14;
    public static final int READING_GAP = 4;
    public static final int READING_LINES = 2;

    /** Everything under the grid, so the grid can be given whatever height is left. */
    public static final int BELOW_GRID = CAPTION_GAP + LINE_H + SLOTS_GAP + SLOT_H + CONTROLS_GAP + LINE_H
            + READING_GAP + READING_LINES * LINE_H;

    private static final float OVERSHOOT = 2.4F;

    private GrimoireLayout() {}

    // ---- the block --------------------------------------------------------------------------------

    public static int blockWidth(int guiWidth) {
        return Math.max(1, Math.min(BLOCK_W_MAX, guiWidth - 2 * SCREEN_MARGIN));
    }

    public static int gridColumns(int blockWidth) {
        return Math.max(1, blockWidth / GRID_STRIDE);
    }

    /** How many rows a grid of {@code count} symbols needs; one for none, so the area still exists. */
    public static int gridRows(int count, int columns) {
        return Math.max(1, (count + columns - 1) / columns);
    }

    /** How many of those rows are shown: as many as there are, up to as many as the screen has room for. */
    public static int gridRowsVisible(int guiHeight, int rows) {
        int room = (guiHeight - 2 * SCREEN_MARGIN - GRID_Y - BELOW_GRID) / GRID_STRIDE;
        return Math.max(1, Math.min(Math.min(rows, GRID_ROWS_MAX), room));
    }

    public static int blockHeight(int visibleRows) {
        return GRID_Y + visibleRows * GRID_STRIDE + BELOW_GRID;
    }

    /** The block the whole thing occupies, centred. Nothing paints it; it only places the rest. */
    public static Rect block(int guiWidth, int guiHeight, int visibleRows) {
        int w = blockWidth(guiWidth);
        int h = blockHeight(visibleRows);
        return new Rect("block", Math.max(0, (guiWidth - w) / 2), Math.max(0, (guiHeight - h) / 2), w, h);
    }

    // ---- rows of words ----------------------------------------------------------------------------

    /**
     * Words across the block on one line, centred as a group.
     *
     * <p>A row that fits keeps every word at the width it measured. One that does not gives up
     * its gap first, down to a pixel, and only then clamps each word to an equal share of the
     * block, so the row always fits whatever the words measure; the screen clips a label to the
     * width it gets back, and a caller that would rather not be clipped asks {@link #wordsFit}
     * first and hands over shorter words.
     */
    public static List<Rect> wordsRow(String name, int blockWidth, int y, int[] widths, int gap) {
        int n = widths.length;
        List<Rect> rects = new ArrayList<>(n);
        if (n == 0) {
            return rects;
        }
        int sum = 0;
        for (int w : widths) {
            sum += w;
        }
        int useGap = n == 1 ? 0 : Math.max(1, Math.min(gap, (blockWidth - sum) / (n - 1)));
        boolean fits = sum + (n - 1) * useGap <= blockWidth;
        int share = Math.max(1, (blockWidth - (n - 1) * useGap) / n);
        int total = -useGap;
        for (int w : widths) {
            total += (fits ? w : Math.min(w, share)) + useGap;
        }
        int x = (blockWidth - total) / 2;
        for (int i = 0; i < n; i++) {
            int w = fits ? widths[i] : Math.min(widths[i], share);
            rects.add(new Rect(name + " " + i, x, y, w, LINE_H));
            x += w + useGap;
        }
        return rects;
    }

    /** Whether words of these widths sit on one line of the block with their full gap between them. */
    public static boolean wordsFit(int blockWidth, int[] widths, int gap) {
        int total = (widths.length - 1) * gap;
        for (int w : widths) {
            total += w;
        }
        return total <= blockWidth;
    }

    public static List<Rect> tabs(int blockWidth, int[] widths) {
        return wordsRow("tab", blockWidth, TAB_Y, widths, TAB_GAP);
    }

    /** Where the focus mark sits under a tab once it has arrived. */
    public static Rect tabRule(Rect tab) {
        return new Rect(tab.name() + " rule", tab.x(), TAB_RULE_Y, tab.w(), RULE_H);
    }

    public static List<Rect> categories(int blockWidth, int[] widths) {
        return wordsRow("category", blockWidth, CATEGORY_Y, widths, CATEGORY_GAP);
    }

    /** The width a category takes with its word beside its glyph. */
    public static int categoryWidth(int wordWidth) {
        return ICON + CATEGORY_GLYPH_GAP + wordWidth;
    }

    /** Whether every category can show its word: if not, the row falls back to the glyphs alone. */
    public static boolean categoriesFitWithWords(int blockWidth, int[] wordWidths) {
        int[] widths = new int[wordWidths.length];
        for (int i = 0; i < wordWidths.length; i++) {
            widths[i] = categoryWidth(wordWidths[i]);
        }
        return wordsFit(blockWidth, widths, CATEGORY_GAP);
    }

    /** Whether the four incantations can be named in full: if not, the tabs fall back to their numerals. */
    public static boolean tabsFitWithWords(int blockWidth, int[] widths) {
        return wordsFit(blockWidth, widths, TAB_GAP);
    }

    // ---- the grid ---------------------------------------------------------------------------------

    public static int gridX(int blockWidth, int columns) {
        return (blockWidth - columns * GRID_STRIDE) / 2;
    }

    public static Rect gridArea(int blockWidth, int columns, int visibleRows) {
        return new Rect("grid", gridX(blockWidth, columns), GRID_Y, columns * GRID_STRIDE, visibleRows * GRID_STRIDE);
    }

    /** The cell of symbol {@code index} with the grid scrolled {@code scrollRows} down; it may lie outside the visible rows. */
    public static Rect gridCell(int blockWidth, int columns, int index, int scrollRows) {
        int col = index % columns;
        int row = index / columns - scrollRows;
        return new Rect("cell " + index, gridX(blockWidth, columns) + col * GRID_STRIDE, GRID_Y + row * GRID_STRIDE,
                GRID_STRIDE, GRID_STRIDE);
    }

    /** The symbol inside a cell, centred. */
    public static Rect gridIcon(Rect cell) {
        int inset = (GRID_STRIDE - ICON) / 2;
        return new Rect(cell.name() + " icon", cell.x() + inset, cell.y() + inset, ICON, ICON);
    }

    public static int gridIndexAt(int blockWidth, int columns, int visibleRows, int scrollRows, int count, double lx, double ly) {
        double cx = lx - gridX(blockWidth, columns);
        double cy = ly - GRID_Y;
        if (cx < 0.0D || cy < 0.0D) {
            return -1;
        }
        int col = (int) (cx / GRID_STRIDE);
        int row = (int) (cy / GRID_STRIDE);
        if (col >= columns || row >= visibleRows) {
            return -1;
        }
        int index = (row + scrollRows) * columns + col;
        return index < count ? index : -1;
    }

    public static int clampGridScroll(int scroll, int rows, int visibleRows) {
        return Math.max(0, Math.min(scroll, Math.max(0, rows - visibleRows)));
    }

    // ---- the caption ------------------------------------------------------------------------------

    public static int captionY(int visibleRows) {
        return GRID_Y + visibleRows * GRID_STRIDE + CAPTION_GAP;
    }

    public static Rect caption(int blockWidth, int visibleRows) {
        return new Rect("caption", 0, captionY(visibleRows), blockWidth, LINE_H);
    }

    // ---- the row ----------------------------------------------------------------------------------

    public static int slotStride(int blockWidth) {
        return Math.max(SLOT_STRIDE_MIN, Math.min(SLOT_STRIDE_MAX, blockWidth / SLOT_COUNT));
    }

    public static int slotsX(int blockWidth) {
        return (blockWidth - SLOT_COUNT * slotStride(blockWidth)) / 2;
    }

    public static int slotsY(int visibleRows) {
        return captionY(visibleRows) + LINE_H + SLOTS_GAP;
    }

    public static Rect slot(int blockWidth, int visibleRows, int index) {
        int stride = slotStride(blockWidth);
        return new Rect("slot " + index, slotsX(blockWidth) + index * stride, slotsY(visibleRows), stride, SLOT_H);
    }

    /** The whole row, for the scroll that sets the breath. */
    public static Rect slotsRow(int blockWidth, int visibleRows) {
        return new Rect("row", slotsX(blockWidth), slotsY(visibleRows), SLOT_COUNT * slotStride(blockWidth), SLOT_H);
    }

    public static Rect slotSymbol(Rect slot) {
        return new Rect(slot.name() + " symbol", slot.x() + (slot.w() - ICON) / 2, slot.y(), ICON, ICON);
    }

    public static Rect slotBar(Rect slot) {
        return new Rect(slot.name() + " bar", slot.x() + SLOT_BAR_INSET, slot.y() + SLOT_BAR_Y,
                slot.w() - 2 * SLOT_BAR_INSET, RULE_H);
    }

    public static int slotAt(int blockWidth, int visibleRows, double lx, double ly) {
        Rect row = slotsRow(blockWidth, visibleRows);
        if (!hit(row, lx, ly)) {
            return -1;
        }
        return (int) ((lx - row.x()) / slotStride(blockWidth));
    }

    /** Whether a drop at this point lands on the row: the row with a little slack all round. */
    public static boolean overRow(int blockWidth, int visibleRows, double lx, double ly) {
        Rect row = slotsRow(blockWidth, visibleRows);
        return lx >= row.x() - SLOT_SLACK && lx < row.right() + SLOT_SLACK
                && ly >= row.y() - SLOT_SLACK && ly < row.bottom() + SLOT_SLACK;
    }

    /**
     * Where a dragged verse lands on a row of {@code size} written slots: the slot boundary nearest
     * the cursor, so a drop on the left half of a slot goes before it and on the right half after
     * it, clamped to the end of what is written.
     */
    public static int slotInsertionAt(int blockWidth, double lx, int size) {
        int at = (int) Math.floor((lx - slotsX(blockWidth)) / (double) slotStride(blockWidth) + 0.5D);
        return Math.max(0, Math.min(at, Math.min(size, SLOT_COUNT)));
    }

    // ---- the controls and the reading -------------------------------------------------------------

    /** The breath at the row's left end, Save and Clear at its right. */
    public record Controls(Rect breathLabel, Rect minus, Rect value, Rect plus, Rect save, Rect clear) {
        public List<Rect> all() {
            return List.of(breathLabel, minus, value, plus, save, clear);
        }
    }

    public static int controlsY(int visibleRows) {
        return slotsY(visibleRows) + SLOT_H + CONTROLS_GAP;
    }

    public static Controls controls(int blockWidth, int visibleRows, int breathW, int minusW, int valueW, int plusW,
            int saveW, int clearW) {
        int y = controlsY(visibleRows);
        Rect row = slotsRow(blockWidth, visibleRows);
        int x = row.x();
        Rect breath = new Rect("breath", x, y, breathW, LINE_H);
        x += breathW + CONTROL_GAP;
        Rect minus = new Rect("minus", x, y, minusW, LINE_H);
        x += minusW + CONTROL_GAP;
        Rect value = new Rect("value", x, y, valueW, LINE_H);
        x += valueW + CONTROL_GAP;
        Rect plus = new Rect("plus", x, y, plusW, LINE_H);
        Rect clear = new Rect("clear", row.right() - clearW, y, clearW, LINE_H);
        Rect save = new Rect("save", clear.x() - ACTION_GAP - saveW, y, saveW, LINE_H);
        return new Controls(breath, minus, value, plus, save, clear);
    }

    public static Rect reading(int blockWidth, int visibleRows) {
        return new Rect("reading", 0, controlsY(visibleRows) + LINE_H + READING_GAP, blockWidth, READING_LINES * LINE_H);
    }

    // ---- the sweep --------------------------------------------------------------------------------

    /** Everything drawn at rest, for the overlap and containment sweep. */
    public static List<Rect> rects(int blockWidth, int visibleRows, int columns, int count, int[] tabWidths,
            int[] categoryWidths, Controls controls) {
        List<Rect> rects = new ArrayList<>();
        rects.addAll(tabs(blockWidth, tabWidths));
        rects.addAll(categories(blockWidth, categoryWidths));
        int shown = Math.min(count, visibleRows * columns);
        for (int index = 0; index < shown; index++) {
            rects.add(gridCell(blockWidth, columns, index, 0));
        }
        rects.add(caption(blockWidth, visibleRows));
        for (int index = 0; index < SLOT_COUNT; index++) {
            rects.add(slot(blockWidth, visibleRows, index));
        }
        rects.addAll(controls.all());
        rects.add(reading(blockWidth, visibleRows));
        return rects;
    }

    public static boolean hit(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    public static int indexAt(List<Rect> rects, double lx, double ly) {
        for (int i = 0; i < rects.size(); i++) {
            if (hit(rects.get(i), lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    // ---- the curves -------------------------------------------------------------------------------

    /**
     * Fast out of the gate, a little past the mark, then back onto it: standard ease-out-back. A
     * plain ease lands exactly and reads as a cut; the small overshoot is what makes a symbol feel
     * set down rather than placed.
     */
    public static float ease(float t) {
        float k = clamp(t, 0.0F, 1.0F) - 1.0F;
        return 1.0F + (OVERSHOOT + 1.0F) * k * k * k + OVERSHOOT * k * k;
    }

    /** The same curve without the overshoot, for anything that must not pass its target. */
    public static float easeOut(float t) {
        float k = 1.0F - clamp(t, 0.0F, 1.0F);
        return 1.0F - k * k * k;
    }

    /**
     * A value part way from where it was drawn last frame toward where it should be, closing half
     * the distance every {@code halfLife} ticks: frame-rate independent, and it never overshoots.
     */
    public static float approach(float current, float target, float dt, float halfLife) {
        if (halfLife <= 0.0F || dt <= 0.0F) {
            return target;
        }
        float keep = (float) Math.pow(0.5D, dt / halfLife);
        return target + (current - target) * keep;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
