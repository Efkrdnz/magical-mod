package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/** Where Daydream's reel sits: three lines along the bottom, over vanilla's HUD, sized by the gui. */
public record ImpressionReelLayout(int guiWidth, int guiHeight) {
    public static final int SIDE = 2;
    public static final int MARGIN = 16;
    public static final int GAP = 6;
    public static final int LINE = 11;
    public static final int HUD_FLOOR = 40;
    public static final int MAX_CELL = 96;
    public static final int MIN_CELL = 40;

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
        return guiHeight - HUD_FLOOR - LINE - 1;
    }

    public int reelY() {
        return hintY() - LINE - 1;
    }

    public int statusY() {
        return reelY() - LINE - 1;
    }
}
