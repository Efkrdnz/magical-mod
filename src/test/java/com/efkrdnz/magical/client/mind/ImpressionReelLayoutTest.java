package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpressionReelLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};

    @Test
    void theTwoLinesStackAboveVanillasHud() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            assertEquals(layout.hintY() - ImpressionReelLayout.LINE - 1, layout.statusY(),
                    "the status sits one line above the hint, now the reel row is gone");
            assertTrue(layout.statusY() + ImpressionReelLayout.LINE <= layout.hintY());
            assertTrue(layout.hintY() + ImpressionReelLayout.LINE <= screen[1] - ImpressionReelLayout.HUD_FLOOR,
                    "the hint runs into the hotbar at " + screen[0] + "x" + screen[1]);
        }
    }

    @Test
    void nothingInTheReelReachesVanillasActionBar() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            Rect actionBar = new Rect("action bar", 0, screen[1] - ImpressionReelLayout.ACTION_BAR_TOP,
                    screen[0], ImpressionReelLayout.ACTION_BAR_TOP - ImpressionReelLayout.ACTION_BAR_BOTTOM);
            List<Rect> reel = new ArrayList<>();
            for (int y : new int[]{layout.statusY(), layout.hintY()}) {
                reel.add(new Rect("line", 0, y, screen[0], ImpressionReelLayout.LINE));
            }
            reel.add(layout.scrim(screen[0] - 2 * ImpressionReelLayout.MARGIN));
            // The belt's lie name is drawn where vanilla draws the held item's: h-59 at the lowest,
            // one font line tall; creative drops it 14 further, which only moves it away.
            Rect lieName = new Rect("lie name", 0, screen[1] - 59, screen[0], 9);
            for (Rect part : reel) {
                assertFalse(part.overlaps(actionBar), part + " is drawn over the action bar at " + screen[0] + "x" + screen[1]);
                assertFalse(part.overlaps(lieName), part + " is drawn over the lie's name at " + screen[0] + "x" + screen[1]);
            }
        }
    }

    @Test
    void theScrimCoversTheLinesAndNothingMoreThanTheScreen() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            for (int textWidth : new int[]{0, 120, screen[0] / 2, screen[0] - 2 * ImpressionReelLayout.MARGIN}) {
                Rect scrim = layout.scrim(textWidth);
                int feather = layout.scrimFeather(textWidth);
                int width = layout.contentWidth(textWidth);
                Rect lines = new Rect("lines", (screen[0] - width) / 2, layout.statusY(), width,
                        layout.hintY() + ImpressionReelLayout.LINE - layout.statusY());
                assertTrue(scrim.x() >= 0 && scrim.right() <= screen[0], scrim + " leaves a " + screen[0] + "-wide screen");
                assertTrue(scrim.contains(lines), scrim + " does not cover " + lines);
                assertTrue(scrim.x() + feather <= lines.x() && lines.right() <= scrim.right() - feather,
                        "the fade runs in under the text at " + screen[0] + "x" + screen[1]);
                assertTrue(lines.y() - scrim.y() >= ImpressionReelLayout.FEATHER_Y
                        && scrim.bottom() - lines.bottom() >= ImpressionReelLayout.FEATHER_Y, "the fade runs in under a line");
            }
        }
    }
}
