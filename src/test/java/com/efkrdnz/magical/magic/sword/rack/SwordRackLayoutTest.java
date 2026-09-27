package com.efkrdnz.magical.magic.sword.rack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rack screen, measured: twelve wells on a ring that touch nothing, and all of it on the
 * smallest screen the game guarantees.
 */
class SwordRackLayoutTest {

    /** {@code Window.calculateScale} never goes below a 320x240 gui. */
    private static final int SMALLEST_GUI_WIDTH = 320;
    private static final int SMALLEST_GUI_HEIGHT = 240;

    private static int[] well(int index) {
        int x = SwordRackLayout.socketX(index) - 1;
        int y = SwordRackLayout.socketY(index) - 1;
        return new int[] {x, y, x + SwordRackLayout.WELL, y + SwordRackLayout.WELL};
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }

    @Test
    void noTwoWellsTouch() {
        for (int i = 0; i < SwordRack.SIZE; i++) {
            for (int j = i + 1; j < SwordRack.SIZE; j++) {
                int[] a = well(i);
                int[] b = well(j);
                // One pixel of air round every well, so two borders never read as one.
                int[] grown = {a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1};
                assertTrue(!overlap(grown, b), "socket " + i + " and socket " + j + " share pixels");
            }
        }
    }

    @Test
    void theRingRunsClockwiseFromTheTop() {
        int top = 0;
        int right = 3;
        int bottom = 6;
        int left = 9;
        for (int i = 0; i < SwordRack.SIZE; i++) {
            assertTrue(SwordRackLayout.socketY(top) <= SwordRackLayout.socketY(i), "socket 0 is not the top one");
            assertTrue(SwordRackLayout.socketX(right) >= SwordRackLayout.socketX(i), "socket 3 is not the right one");
            assertTrue(SwordRackLayout.socketY(bottom) >= SwordRackLayout.socketY(i), "socket 6 is not the bottom one");
            assertTrue(SwordRackLayout.socketX(left) <= SwordRackLayout.socketX(i), "socket 9 is not the left one");
        }
        assertTrue(SwordRackLayout.socketX(1) > SwordRackLayout.socketX(0), "the ring turns the wrong way");
    }

    @Test
    void everythingFitsTheSmallestScreenAndTheImage() {
        assertTrue(SwordRackLayout.IMAGE_WIDTH <= SMALLEST_GUI_WIDTH);
        assertTrue(SwordRackLayout.IMAGE_HEIGHT <= SMALLEST_GUI_HEIGHT,
                "the rack is " + SwordRackLayout.IMAGE_HEIGHT + " tall on a " + SMALLEST_GUI_HEIGHT + " screen");
        for (int i = 0; i < SwordRack.SIZE; i++) {
            int[] w = well(i);
            assertTrue(w[0] >= 4 && w[2] <= SwordRackLayout.IMAGE_WIDTH - 4, "socket " + i + " leaves the panel");
            assertTrue(w[1] >= SwordRackLayout.TITLE_Y + SwordRackLayout.LINE_HEIGHT + 1,
                    "socket " + i + " sits on the title");
            assertTrue(w[3] < SwordRackLayout.INVENTORY_LABEL_Y, "socket " + i + " sits on the inventory label");
        }
        int inventoryBottom = SwordRackLayout.INVENTORY_Y + 3 * 18;
        assertTrue(SwordRackLayout.INVENTORY_LABEL_Y + SwordRackLayout.LINE_HEIGHT < SwordRackLayout.INVENTORY_Y,
                "the inventory label sits on the inventory");
        assertTrue(inventoryBottom <= SwordRackLayout.HOTBAR_Y - 2, "the hotbar sits on the inventory");
        assertTrue(SwordRackLayout.HOTBAR_Y + SwordRackLayout.WELL <= SwordRackLayout.IMAGE_HEIGHT - 4,
                "the hotbar leaves the panel");
        assertTrue(SwordRackLayout.INVENTORY_X - 1 >= 4
                        && SwordRackLayout.INVENTORY_X + 9 * 18 <= SwordRackLayout.IMAGE_WIDTH - 4,
                "the inventory leaves the panel");
    }

    @Test
    void theWordsInTheMiddleTouchNoSocket() {
        int left = SwordRackLayout.centreTextLeft();
        int[] words = {left, SwordRackLayout.STANCE_LINE_Y,
                left + SwordRackLayout.CENTRE_TEXT_WIDTH, SwordRackLayout.COUNT_LINE_Y + SwordRackLayout.LINE_HEIGHT};
        assertTrue(SwordRackLayout.STANCE_LINE_Y + SwordRackLayout.LINE_HEIGHT <= SwordRackLayout.COUNT_LINE_Y,
                "the two centre lines are drawn over each other");
        for (int i = 0; i < SwordRack.SIZE; i++) {
            assertTrue(!overlap(words, well(i)), "the centre text runs into socket " + i);
        }
    }

    @Test
    void everySocketAnswersToItsMiddleAndTheGapsToNothing() {
        for (int i = 0; i < SwordRack.SIZE; i++) {
            double x = SwordRackLayout.socketX(i) + SwordRackLayout.ITEM / 2.0D;
            double y = SwordRackLayout.socketY(i) + SwordRackLayout.ITEM / 2.0D;
            assertEquals(i, SwordRackLayout.socketAt(x, y), "the middle of socket " + i);
        }
        assertEquals(-1, SwordRackLayout.socketAt(SwordRackLayout.RING_X, SwordRackLayout.RING_Y),
                "the middle of the ring is a socket");
        assertEquals(-1, SwordRackLayout.socketAt(0.0D, 0.0D));
    }
}
