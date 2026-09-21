package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the Causal Anchor hold: a rail of pips and four lines of reading, under the
 * crosshair and clear of it.
 *
 * <p>Every other hold overlay in the mod is a chooser over a vocabulary the wielder owns - three
 * columns of law, five gates of fault, a ring of sub-skills - and it may sit wherever it likes,
 * because the world behind it is only scenery. This one is a chooser over <em>the world itself</em>,
 * so it owes the world two things the others do not. It keeps its hands off the crosshair
 * ({@link #CROSSHAIR_CLEAR}), because the wielder is still aiming through it while it is open. And
 * it stays small and low, because what it is really asking you to look at is the ring drawn round a
 * body out there, not the words down here: the words say what marking that body <em>does to your
 * board</em>, which is the one thing the world cannot show you.
 *
 * <p>The layout has no font. Widths come in measured and a line is clamped to
 * {@link #textLimit(int)}, the way every frameless surface in the mod clamps its own.
 */
public final class CausalAnchorLayout {

    /** No part of the overlay may come within this of the middle of the screen. */
    public static final int CROSSHAIR_CLEAR = 22;

    public static final int LINE_H = 9;
    public static final int LINE_GAP = 2;
    /** Name and range, standing, what marking it wakes, and where the focus sits in the list. */
    public static final int LINES = 4;

    public static final int PIP_W = 5;
    public static final int PIP_H = 2;
    public static final int PIP_GAP = 2;
    /** Between the rail and the first line of reading. */
    public static final int PIP_GAP_BELOW = 5;

    public static final int SIDE_MARGIN = 6;
    public static final int BLOCK_W_MAX = 220;

    /**
     * The most bodies the hold will offer at once.
     *
     * <p>A reach of 28 blocks in a busy place can hold dozens, and a rail of dozens of pips is not a
     * rail any more - nor is a wall of rings a reading. The nearest few to the crosshair are what a
     * wielder was ever going to mean.
     */
    public static final int MAX_CANDIDATES = 12;

    private CausalAnchorLayout() {}

    public static int blockWidth(int guiWidth) {
        return Math.max(1, Math.min(BLOCK_W_MAX, guiWidth - 2 * SIDE_MARGIN));
    }

    /** How wide a line of words may be before the screen clips it. */
    public static int textLimit(int guiWidth) {
        return Math.max(40, blockWidth(guiWidth) - 4);
    }

    public static int blockHeight() {
        return PIP_H + PIP_GAP_BELOW + LINES * LINE_H + (LINES - 1) * LINE_GAP;
    }

    /** The top of the rail: below the crosshair by the clearance, and never off the bottom. */
    public static int top(int guiHeight) {
        int wanted = guiHeight / 2 + CROSSHAIR_CLEAR;
        int highest = Math.max(0, guiHeight - blockHeight() - SIDE_MARGIN);
        return Math.min(wanted, highest);
    }

    /** The block the whole thing occupies, centred. Nothing paints it; it only places the rest. */
    public static Rect block(int guiWidth, int guiHeight) {
        int w = blockWidth(guiWidth);
        return new Rect("block", (guiWidth - w) / 2, top(guiHeight), w, blockHeight());
    }

    /**
     * One pip per body on offer, centred as a rail.
     *
     * <p>Position in the rail is the only channel that says <em>which</em> of them is focused, and
     * it is independent of the ring drawn in the world and of the words underneath, so the three
     * readings can never contradict each other into something unreadable.
     */
    public static List<Rect> pips(int guiWidth, int guiHeight, int count) {
        List<Rect> rects = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return rects;
        }
        int used = Math.min(count, MAX_CANDIDATES);
        int block = blockWidth(guiWidth);
        int stride = PIP_W + PIP_GAP;
        int total = used * stride - PIP_GAP;
        int x = (guiWidth - Math.min(total, block)) / 2;
        // A rail that would run wider than the block gives up its gaps before it gives up a pip,
        // because a missing pip is a missing body and the count has to stay true.
        if (total > block) {
            stride = Math.max(1, block / used);
            x = (guiWidth - (used * stride - Math.max(0, stride - PIP_W))) / 2;
        }
        int y = top(guiHeight);
        for (int i = 0; i < used; i++) {
            rects.add(new Rect("pip " + i, x + i * stride, y, Math.min(PIP_W, stride), PIP_H));
        }
        return rects;
    }

    /** One line of the reading, full block width; the screen centres its words inside it. */
    public static Rect line(int guiWidth, int guiHeight, int index) {
        int w = blockWidth(guiWidth);
        int y = top(guiHeight) + PIP_H + PIP_GAP_BELOW + index * (LINE_H + LINE_GAP);
        return new Rect("line " + index, (guiWidth - w) / 2, y, w, LINE_H);
    }
}
