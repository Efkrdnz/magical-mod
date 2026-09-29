package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/**
 * Where Daydream's reel sits: three lines along the bottom, over vanilla's HUD, sized by the gui.
 *
 * <p>The lines end above the action bar, which prints a stroke's refusal at {@code h-68} and would
 * otherwise draw it over the budget. Behind them is a soft scrim, only as wide and tall as the
 * lines, because the wielder needs the rest of the world to draw in.
 */
public record ImpressionReelLayout(int guiWidth, int guiHeight) {
    public static final int SIDE = 2;
    public static final int MARGIN = 16;
    public static final int GAP = 6;
    public static final int LINE = 11;
    public static final int HUD_FLOOR = 40;
    /** The top of the band vanilla's action bar is drawn in, measured up from the bottom of the gui. */
    public static final int ACTION_BAR_TOP = 72;
    /** Its bottom edge, measured the same way; nothing of the reel is drawn between the two. */
    public static final int ACTION_BAR_BOTTOM = 59;
    public static final int MAX_CELL = 96;
    public static final int MIN_CELL = 40;
    /** How far the scrim fades out past the lines, above and below. */
    public static final int FEATHER_Y = 6;
    /** How far it fades out past the widest line, to either side. */
    public static final int FEATHER_X = 24;
    /** The scrim where it is solid: the number the loadout switcher settled. */
    public static final int SCRIM_ALPHA = 0x8C;

    public int cellWidth() {
        int slots = 2 * SIDE + 1;
        int room = guiWidth - 2 * MARGIN - GAP * (slots - 1);
        return Math.max(MIN_CELL, Math.min(MAX_CELL, room / slots));
    }

    public Rect cell(int index) {
        int width = cellWidth();
        int x = guiWidth / 2 + index * (width + GAP) - width / 2;
        return new Rect("reel" + index, x, reelY(), width, LINE);
    }

    public int hintY() {
        return guiHeight - ACTION_BAR_TOP - FEATHER_Y - LINE;
    }

    public int reelY() {
        return hintY() - LINE - 1;
    }

    public int statusY() {
        return reelY() - LINE - 1;
    }

    /** The width the lines take together: the reel, or the widest of the other two if that is wider. */
    public int contentWidth(int textWidth) {
        int reel = (2 * SIDE + 1) * cellWidth() + 2 * SIDE * GAP;
        return Math.min(guiWidth, Math.max(reel, textWidth));
    }

    /** The fade at each end: {@link #FEATHER_X}, or what the screen has left beside the lines. */
    public int scrimFeather(int textWidth) {
        return Math.max(0, Math.min(FEATHER_X, (guiWidth - contentWidth(textWidth)) / 2));
    }

    /** The scrim, feather included; solid inside it by {@link #scrimFeather} on the sides and {@link #FEATHER_Y} above and below. */
    public Rect scrim(int textWidth) {
        int feather = scrimFeather(textWidth);
        int width = contentWidth(textWidth) + 2 * feather;
        int top = statusY() - FEATHER_Y;
        return new Rect("reel scrim", (guiWidth - width) / 2, top, width, hintY() + LINE + FEATHER_Y - top);
    }
}
