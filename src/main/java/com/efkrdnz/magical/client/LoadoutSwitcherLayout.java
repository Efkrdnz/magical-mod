package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the loadout switcher: a rail of names down the left edge, floating over the world.
 *
 * <p>No panel, no plate and no frame, the way the Manipulate Space selector and the Grimoire are
 * drawn. What that costs is the thing a box gives you for free: an edge. The old switcher was a
 * 148px panel and every number in it was measured from that width - the pips hung off the right
 * wall, the names were cut to the gap in between - so the moment the wall went, the arithmetic had
 * to be rebuilt from the left. Hence the pips lead the row now rather than trail it, and hence
 * this class: a rail has no width of its own, it has a limit.
 *
 * <p>The limit is the whole point. The panel drew its caption with no truncation at all, so
 * "Scroll to choose, release to switch" ran about thirty pixels out through its own right wall
 * and sat unbacked on the sky. Nothing here is drawn without a measured width, and
 * {@link #textLimit} is a function of the screen rather than a constant, because on a 320-wide
 * GUI a fixed 120px name would be drawn through the crosshair.
 *
 * <p>It lives outside the overlay so it can be checked without a render context, the way
 * {@link SpaceManipulationLayout} is. The layout has no font, so the overlay measures its strings
 * and passes the widths in. {@code LoadoutSwitcherLayoutTest} sweeps every screen size the game
 * can hand us and asserts that nothing drawn lands on anything else, that nothing leaves the
 * screen, and that nothing reaches the crosshair.
 *
 * <p>All coordinates are screen coordinates: the rail is pinned to the left edge, so there is no
 * block origin to add.
 */
public final class LoadoutSwitcherLayout {

    /** The rail sits this far in from the left edge, where the old panel did. */
    public static final int LEFT = 10;

    /** One line of text with a pixel of air above and below it. */
    public static final int LINE_H = 9;
    public static final int ROW_H = 13;

    /** The gutter the highlight mark stands in, left of everything else. */
    public static final int MARK_W = 6;
    public static final int MARK_GAP = 3;

    public static final int PIP = 4;
    public static final int PIP_GAP = 2;
    public static final int PIP_PITCH = PIP + PIP_GAP;
    public static final int PIP_RUN = MagicContent.LOADOUT_SIZE * PIP_PITCH - PIP_GAP;
    public static final int PIP_NAME_GAP = 7;

    /** Where every name starts. One text column, so the rail reads as a column and not a table. */
    public static final int NAME_X = LEFT + MARK_W + MARK_GAP + PIP_RUN + PIP_NAME_GAP;

    /** The gap between the last row and the caption line under it. */
    public static final int CAPTION_GAP = 5;

    /** As wide as a name is ever drawn, before the screen has its say. */
    public static final int NAME_MAX = 120;
    /** And never narrower than this, or a name stops being a name. */
    public static final int NAME_MIN = 36;

    /**
     * Clear water either side of the crosshair.
     *
     * <p>The rail is read while aiming, so it may not grow into the middle of the screen. Vanilla
     * draws a 15px crosshair centred on {@code guiWidth / 2}; this is that half-width rounded up
     * with room to spare, so even the longest name on the narrowest GUI stops short of it.
     */
    public static final int CROSSHAIR_KEEP = 24;

    /** The rail never runs closer than this to the top or bottom of the screen. */
    public static final int EDGE_KEEP = 4;

    private LoadoutSwitcherLayout() {}

    /** The rail is the rows plus one reserved caption line. */
    public static int height(int count) {
        return count * ROW_H + CAPTION_GAP + LINE_H;
    }

    /**
     * The top of the rail: vertically centred, then held off both edges.
     *
     * <p>Eight loadouts on a 240-tall GUI is 135px of rail, which centres fine; the clamp is there
     * for the day the cap moves, so a long list runs off the bottom instead of off both ends.
     */
    public static int top(int count, int guiHeight) {
        int centred = (guiHeight - height(count)) / 2;
        int lowest = Math.max(EDGE_KEEP, guiHeight - EDGE_KEEP - height(count));
        return Math.max(EDGE_KEEP, Math.min(centred, lowest));
    }

    /**
     * As wide as a string may be drawn on this screen.
     *
     * <p>Both frameless precedents clamp their block against the screen rather than trusting a
     * constant - {@code SpaceManipulationLayout.columnWidth} and {@code GrimoireLayout.blockWidth}
     * - and this is the same move for the same reason: a fixed 120 puts the end of a long name at
     * x 168, and on a 320-wide GUI the crosshair starts at 136.
     */
    public static int textLimit(int guiWidth) {
        int toCrosshair = guiWidth / 2 - CROSSHAIR_KEEP - NAME_X;
        return Math.max(NAME_MIN, Math.min(NAME_MAX, toCrosshair));
    }

    /** What a string of {@code measured} pixels is actually drawn at here. */
    public static int textWidth(int measured, int guiWidth) {
        return Math.max(0, Math.min(measured, textLimit(guiWidth)));
    }

    /** The top of a row, counted from the top of the rail. */
    public static int rowTop(int row, int count, int guiHeight) {
        return top(count, guiHeight) + row * ROW_H;
    }

    /** The gutter the highlight mark stands in on this row. */
    public static Rect mark(int row, int count, int guiHeight) {
        return new Rect("mark " + row, LEFT, rowTop(row, count, guiHeight) + 2, MARK_W, LINE_H);
    }

    /**
     * One slot dot.
     *
     * <p>Centred on the text line rather than sitting on its baseline, so the colour run reads as
     * one horizontal band across the rail whatever the names do.
     */
    public static Rect pip(int row, int slot, int count, int guiWidth, int guiHeight) {
        int x = LEFT + MARK_W + MARK_GAP + slot * PIP_PITCH;
        int y = rowTop(row, count, guiHeight) + 2 + (LINE_H - PIP) / 2;
        return new Rect("pip " + row + "." + slot, x, y, PIP, PIP);
    }

    /** The whole colour run on a row, which is what the name may not walk into. */
    public static Rect pipRun(int row, int count, int guiHeight) {
        return new Rect("pips " + row, LEFT + MARK_W + MARK_GAP, rowTop(row, count, guiHeight) + 2,
                PIP_RUN, LINE_H);
    }

    /** A name, at its measured width or at the limit. */
    public static Rect name(int row, int measured, int count, int guiWidth, int guiHeight) {
        return new Rect("name " + row, NAME_X, rowTop(row, count, guiHeight) + 2,
                textWidth(measured, guiWidth), LINE_H);
    }

    /**
     * The 1px rule under the active name, as wide as the name above it.
     *
     * <p>It is the one rectangle here that is not a colour sample, and it earns its place: the
     * rail has to say two different things at once - which loadout you are wearing and which one
     * the wheel is pointing at - and a baseline says "here" without competing with the leading
     * mark that says "there".
     */
    public static Rect activeRule(int row, int measured, int count, int guiWidth, int guiHeight) {
        Rect name = name(row, measured, count, guiWidth, guiHeight);
        return new Rect("rule " + row, name.x(), name.bottom(), Math.max(1, name.w()), 1);
    }

    /** The reserved line under the rail, where the lock says so. */
    public static Rect caption(int count, int guiWidth, int guiHeight) {
        return new Rect("caption", NAME_X, top(count, guiHeight) + count * ROW_H + CAPTION_GAP,
                textLimit(guiWidth), LINE_H);
    }

    /**
     * Everything the rail draws, at its widest, for the overlap and containment sweep.
     *
     * <p>Names are measured at the limit rather than at any real width, because the test is asking
     * whether the worst case fits - a rail that only holds together for short names is a rail that
     * breaks the first time somebody names a loadout properly.
     */
    public static List<Rect> rects(int count, int guiWidth, int guiHeight) {
        List<Rect> rects = new ArrayList<>();
        int widest = textLimit(guiWidth);
        for (int row = 0; row < count; row++) {
            rects.add(mark(row, count, guiHeight));
            rects.add(pipRun(row, count, guiHeight));
            rects.add(name(row, widest, count, guiWidth, guiHeight));
        }
        rects.add(caption(count, guiWidth, guiHeight));
        return rects;
    }
}
