package com.efkrdnz.magical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.MagicContent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The loadout switcher laid out on paper, the way the Manipulate Space selector and the Grimoire
 * are.
 *
 * <p>The overlap half is the cheap version of a screenshot. The other half is the reason this
 * class exists at all: the panel this replaces drew its caption with no truncation whatsoever, so
 * a thirty-four character string ran about thirty pixels out through the right wall of its own
 * box and sat unbacked on the sky. Nothing measured anything. Here every string is given a width
 * by {@link LoadoutSwitcherLayout#textLimit}, and the test walks every screen size the game can
 * hand us and asserts that nothing drawn can reach the crosshair.
 */
class LoadoutSwitcherLayoutTest {

    /** Every GUI size the game can hand the overlay, from vanilla's floor upward. */
    private static final int[][] SIZES = {
        {320, 240}, {427, 240}, {432, 243}, {480, 270}, {560, 315}, {640, 360}, {854, 480}, {960, 540},
    };

    private static final int[] COUNTS = {1, 2, 4, 8};

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    @Test
    void nothingDrawnOverlapsAnythingElse() {
        for (int[] size : SIZES) {
            for (int count : COUNTS) {
                List<Rect> rects = LoadoutSwitcherLayout.rects(count, size[0], size[1]);
                for (int i = 0; i < rects.size(); i++) {
                    for (int j = i + 1; j < rects.size(); j++) {
                        Rect a = rects.get(i);
                        Rect b = rects.get(j);
                        assertTrue(!a.overlaps(b), a.name() + " overlaps " + b.name() + " with "
                                + count + " loadouts at " + size[0] + "x" + size[1] + ": "
                                + describe(a) + " vs " + describe(b));
                    }
                }
            }
        }
    }

    @Test
    void everythingDrawnIsOnTheScreen() {
        for (int[] size : SIZES) {
            for (int count : COUNTS) {
                for (Rect r : LoadoutSwitcherLayout.rects(count, size[0], size[1])) {
                    assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= size[0] && r.bottom() <= size[1],
                            r.name() + " leaves the screen with " + count + " loadouts at "
                                    + size[0] + "x" + size[1] + ": " + describe(r));
                }
            }
        }
    }

    /**
     * The rail is read while aiming, so it may never reach the crosshair - which is the old
     * caption's failure pointed the other way, marching out of its own frame toward the middle of
     * the screen.
     */
    @Test
    void nothingReachesTheCrosshair() {
        for (int[] size : SIZES) {
            int wall = size[0] / 2 - LoadoutSwitcherLayout.CROSSHAIR_KEEP;
            for (int count : COUNTS) {
                for (Rect r : LoadoutSwitcherLayout.rects(count, size[0], size[1])) {
                    assertTrue(r.right() <= wall, r.name() + " reaches the crosshair at "
                            + size[0] + "x" + size[1] + ": " + describe(r) + " past " + wall);
                }
            }
        }
    }

    /** A string is drawn at its measured width or at the limit, never at more. */
    @Test
    void aLongNameIsCutToTheLimit() {
        int limit = LoadoutSwitcherLayout.textLimit(640);
        assertEquals(limit, LoadoutSwitcherLayout.textWidth(limit + 400, 640), "a name far over the limit");
        assertEquals(limit, LoadoutSwitcherLayout.textWidth(limit, 640), "a name exactly at the limit");
        assertEquals(12, LoadoutSwitcherLayout.textWidth(12, 640), "a short name keeps its own width");
        assertEquals(0, LoadoutSwitcherLayout.textWidth(0, 640), "an empty name is empty");
    }

    /** The limit is a function of the screen, the way both frameless precedents clamp theirs. */
    @Test
    void theLimitShrinksWithTheScreen() {
        assertTrue(LoadoutSwitcherLayout.textLimit(320) < LoadoutSwitcherLayout.textLimit(960),
                "a narrow screen must give the rail less room, or the longest name crosses the middle");
        for (int[] size : SIZES) {
            assertTrue(LoadoutSwitcherLayout.textLimit(size[0]) > 0, "no room at all at " + size[0]);
            assertTrue(LoadoutSwitcherLayout.NAME_X + LoadoutSwitcherLayout.textLimit(size[0])
                            <= size[0] / 2 - LoadoutSwitcherLayout.CROSSHAIR_KEEP,
                    "the longest name reaches the crosshair at " + size[0]);
        }
    }

    /** One pip per cast slot, in slot order, left to right, the same run on every row. */
    @Test
    void thePipRunIsOnePipPerSlot() {
        for (int count : COUNTS) {
            for (int row = 0; row < count; row++) {
                int previous = Integer.MIN_VALUE;
                for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
                    Rect pip = LoadoutSwitcherLayout.pip(row, slot, count, 640, 360);
                    assertTrue(pip.x() > previous, "slot " + slot + " is not right of slot " + (slot - 1));
                    previous = pip.x();
                    assertEquals(LoadoutSwitcherLayout.pip(0, slot, count, 640, 360).x(), pip.x(),
                            "slot " + slot + " sits at a different x on row " + row);
                }
                assertTrue(LoadoutSwitcherLayout.pip(row, MagicContent.LOADOUT_SIZE - 1, count, 640, 360).right()
                                <= LoadoutSwitcherLayout.NAME_X,
                        "the last pip runs into the name on row " + row);
            }
        }
    }

    /** The rows march down in order and never climb into their neighbours. */
    @Test
    void theRowsAreInOrder() {
        for (int[] size : SIZES) {
            Rect previous = null;
            for (int row = 0; row < 8; row++) {
                Rect name = LoadoutSwitcherLayout.name(row, 40, 8, size[0], size[1]);
                if (previous != null) {
                    assertTrue(name.y() >= previous.bottom(), "row " + row + " climbs into row " + (row - 1));
                }
                previous = name;
            }
        }
    }

    /**
     * The caption line is reserved whether or not anything is on it, because a cast can lock the
     * swap while the list is open - the cast keys still fire while the switcher is held - and a
     * list that jumps half a line at that moment is a list you lose your place in.
     */
    @Test
    void theCaptionLineIsReservedEvenWhenEmpty() {
        for (int count : COUNTS) {
            Rect caption = LoadoutSwitcherLayout.caption(count, 640, 360);
            Rect last = LoadoutSwitcherLayout.name(count - 1, 40, count, 640, 360);
            assertTrue(caption.y() >= last.bottom(), "the caption climbs into the last row");
            assertTrue(caption.bottom() <= LoadoutSwitcherLayout.top(count, 360) + LoadoutSwitcherLayout.height(count),
                    "the caption hangs below the block it is measured into");
        }
    }
}
