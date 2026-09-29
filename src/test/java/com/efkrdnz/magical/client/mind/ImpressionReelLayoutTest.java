package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpressionReelLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};

    @Test
    void theReelNeverOverlapsItselfOrLeavesTheScreen() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            List<Rect> cells = new ArrayList<>();
            for (int i = -ImpressionReelLayout.SIDE; i <= ImpressionReelLayout.SIDE; i++) {
                cells.add(layout.cell(i));
            }
            for (int a = 0; a < cells.size(); a++) {
                Rect cell = cells.get(a);
                assertTrue(cell.x() >= ImpressionReelLayout.MARGIN && cell.right() <= screen[0] - ImpressionReelLayout.MARGIN,
                        cell + " leaves a " + screen[0] + "-wide screen");
                assertTrue(cell.w() >= ImpressionReelLayout.MIN_CELL, cell + " is too narrow to name anything");
                for (int b = a + 1; b < cells.size(); b++) {
                    assertFalse(cell.overlaps(cells.get(b)), cell + " overlaps " + cells.get(b));
                }
            }
        }
    }

    @Test
    void theThreeLinesStackAboveVanillasHud() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            assertTrue(layout.statusY() + ImpressionReelLayout.LINE <= layout.reelY());
            assertTrue(layout.reelY() + ImpressionReelLayout.LINE <= layout.hintY());
            assertTrue(layout.hintY() + ImpressionReelLayout.LINE <= screen[1] - ImpressionReelLayout.HUD_FLOOR,
                    "the hint runs into the hotbar at " + screen[0] + "x" + screen[1]);
        }
    }
}
