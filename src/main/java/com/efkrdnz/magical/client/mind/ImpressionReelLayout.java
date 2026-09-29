package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/**
 * Where Daydream's lines sit: the status and the hint along the bottom, over vanilla's HUD. The
 * lie in hand is the belt's, drawn where the hotbar was, so there is no reel row any more.
 *
 * <p>The lines end above the action bar, which prints a stroke's refusal at {@code h-68} and would
 * otherwise draw it over the budget. Behind them is a soft scrim, only as wide and tall as the
 * lines, because the wielder needs the rest of the world to draw in.
 */
public record ImpressionReelLayout(int guiWidth, int guiHeight) {
    public static final int MARGIN = 16;
    public static final int LINE = 11;
    public static final int HUD_FLOOR = 40;
    /** The top of the band vanilla's action bar is drawn in, measured up from the bottom of the gui. */
    public static final int ACTION_BAR_TOP = 72;
    /** Its bottom edge, measured the same way; nothing of the reel is drawn between the two. */
    public static final int ACTION_BAR_BOTTOM = 59;
    /** How far the scrim fades out past the lines, above and below. */
    public static final int FEATHER_Y = 6;
    /** How far it fades out past the widest line, to either side. */
    public static final int FEATHER_X = 24;
    /** The scrim where it is solid: the number the loadout switcher settled. */
    public static final int SCRIM_ALPHA = 0x8C;

    public int hintY() {
        return guiHeight - ACTION_BAR_TOP - FEATHER_Y - LINE;
    }

    public int statusY() {
        return hintY() - LINE - 1;
    }

    /** The width the two lines take together: the wider of them, never more than the screen. */
    public int contentWidth(int textWidth) {
        return Math.min(guiWidth, textWidth);
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
