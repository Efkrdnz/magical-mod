package com.efkrdnz.magical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The anchor hold is drawn over a world the wielder is still aiming at, so its geometry answers to
 * the crosshair as well as to itself. These sweep every screen the game hands us.
 */
class CausalAnchorLayoutTest {

    /** Every GUI size a 16:9-ish window produces at scales 1 to 4, plus the awkward extremes. */
    private static final int[][] SCREENS = {
            {320, 240}, {360, 200}, {427, 240}, {480, 270}, {640, 360},
            {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}, {240, 180},
    };

    private static List<Rect> everything(int w, int h, int candidates) {
        List<Rect> all = new ArrayList<>(CausalAnchorLayout.pips(w, h, candidates));
        for (int i = 0; i < CausalAnchorLayout.LINES; i++) {
            all.add(CausalAnchorLayout.line(w, h, i));
        }
        return all;
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Test
    void nothingTheHoldDrawsOverlapsAnythingElse() {
        for (int[] screen : SCREENS) {
            for (int candidates = 0; candidates <= CausalAnchorLayout.MAX_CANDIDATES + 4; candidates++) {
                List<Rect> all = everything(screen[0], screen[1], candidates);
                for (int i = 0; i < all.size(); i++) {
                    for (int j = i + 1; j < all.size(); j++) {
                        assertFalse(overlaps(all.get(i), all.get(j)),
                                all.get(i).name() + " overlaps " + all.get(j).name()
                                        + " at " + screen[0] + "x" + screen[1] + " with " + candidates);
                    }
                }
            }
        }
    }

    /**
     * The one rule this overlay has that no other overlay in the mod has. It is open while the
     * wielder is aiming, so a pip or a word sitting on the crosshair would cover the very thing the
     * hold exists to help them point at.
     */
    @Test
    void nothingReachesTheCrosshair() {
        for (int[] screen : SCREENS) {
            double cx = screen[0] / 2.0D;
            double cy = screen[1] / 2.0D;
            for (Rect r : everything(screen[0], screen[1], CausalAnchorLayout.MAX_CANDIDATES)) {
                double nearestY = Math.max(r.y(), Math.min(cy, r.bottom()));
                double nearestX = Math.max(r.x(), Math.min(cx, r.right()));
                double gap = Math.hypot(nearestX - cx, nearestY - cy);
                assertTrue(gap >= CausalAnchorLayout.CROSSHAIR_CLEAR - 1,
                        r.name() + " comes within " + Math.round(gap) + " of the crosshair at "
                                + screen[0] + "x" + screen[1]);
            }
        }
    }

    @Test
    void everythingStaysOnTheScreen() {
        for (int[] screen : SCREENS) {
            for (int candidates = 1; candidates <= CausalAnchorLayout.MAX_CANDIDATES; candidates++) {
                for (Rect r : everything(screen[0], screen[1], candidates)) {
                    assertTrue(r.x() >= 0 && r.right() <= screen[0],
                            r.name() + " runs off the side at " + screen[0] + "x" + screen[1]);
                    assertTrue(r.y() >= 0 && r.bottom() <= screen[1],
                            r.name() + " runs off the bottom at " + screen[0] + "x" + screen[1]
                                    + ": " + r.bottom() + " of " + screen[1]);
                }
            }
        }
    }

    /**
     * A rail that will not fit closes its gaps rather than dropping a pip, because a pip is a body
     * and the count the last line prints has to be the count the rail shows.
     */
    @Test
    void theRailKeepsEveryBodyItWasGiven() {
        for (int[] screen : SCREENS) {
            for (int candidates = 1; candidates <= CausalAnchorLayout.MAX_CANDIDATES; candidates++) {
                assertEquals(candidates, CausalAnchorLayout.pips(screen[0], screen[1], candidates).size(),
                        "the rail lost a body at " + screen[0] + "x" + screen[1]);
            }
        }
        assertEquals(CausalAnchorLayout.MAX_CANDIDATES,
                CausalAnchorLayout.pips(480, 270, 40).size(),
                "and never offers more than it is willing to draw");
        assertTrue(CausalAnchorLayout.pips(480, 270, 0).isEmpty(),
                "with nothing in reach there is no rail at all");
    }

    /**
     * The block is capped rather than proportional, because this is a readout under a crosshair and
     * not a document - on a 1920 GUI a proportional one would fling four words across the whole
     * screen. So the limit is constant wherever the cap bites and shrinks only where the screen is
     * narrower than the cap, and the thing worth pinning is that it never outruns its own block.
     */
    @Test
    void aLineNeverOutrunsItsOwnBlock() {
        for (int[] screen : SCREENS) {
            assertTrue(CausalAnchorLayout.textLimit(screen[0]) <= CausalAnchorLayout.blockWidth(screen[0]),
                    "a line may be drawn wider than the block at " + screen[0]);
        }
        assertEquals(CausalAnchorLayout.textLimit(1280), CausalAnchorLayout.textLimit(1920),
                "past the cap a wider screen buys no more words");
        assertTrue(CausalAnchorLayout.textLimit(200) < CausalAnchorLayout.textLimit(1920),
                "but a screen too narrow for the cap does give up words");
    }
}
