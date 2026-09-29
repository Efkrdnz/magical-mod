package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybillLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};

    private static List<Rect> everything(PlaybillLayout layout) {
        List<Rect> rects = new ArrayList<>();
        for (int i = 0; i < 3; i++) rects.add(layout.tab(i));
        for (int i = 0; i < layout.rows(); i++) rects.add(layout.row(i));
        for (int i = 0; i < 3; i++) rects.add(layout.sense(i));
        for (int i = 0; i < 4; i++) rects.add(layout.stance(i));
        for (int i = 0; i < 5; i++) rects.add(layout.reaction(i));
        rects.add(layout.save());
        rects.add(layout.done());
        return rects;
    }

    @Test
    void nothingOverlapsAndEverythingIsOnScreen() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            List<Rect> rects = everything(layout);
            for (int a = 0; a < rects.size(); a++) {
                Rect rect = rects.get(a);
                assertTrue(rect.x() >= 0 && rect.right() <= screen[0] && rect.y() >= 0 && rect.bottom() <= screen[1],
                        rect + " leaves " + screen[0] + "x" + screen[1]);
                for (int b = a + 1; b < rects.size(); b++) {
                    assertFalse(rect.overlaps(rects.get(b)), rect + " overlaps " + rects.get(b) + " at " + screen[0] + "x" + screen[1]);
                }
            }
            assertTrue(layout.rows() >= 6, "fewer than six element rows at " + screen[0] + "x" + screen[1]);
            assertTrue(layout.forecastX() >= layout.stance(0).right(), "the forecast runs under the script");
            assertTrue(layout.forecastX() + layout.forecastWidth() <= screen[0]);
        }
    }

    @Test
    void everyHitTestAnswersAtItsCentreAndNothingInTheGaps() {
        PlaybillLayout layout = new PlaybillLayout(427, 240);
        for (int i = 0; i < 3; i++) {
            assertEquals(i, layout.tabAt(cx(layout.tab(i)), cy(layout.tab(i))));
            assertEquals(i, layout.senseAt(cx(layout.sense(i)), cy(layout.sense(i))));
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(i, layout.stanceAt(cx(layout.stance(i)), cy(layout.stance(i))));
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(i, layout.reactionAt(cx(layout.reaction(i)), cy(layout.reaction(i))));
        }
        assertEquals(2, layout.rowAt(cx(layout.row(2)), cy(layout.row(2))));
        assertEquals(-1, layout.tabAt(layout.tab(0).right() + PlaybillLayout.GAP / 2.0, cy(layout.tab(0))));
        assertEquals(-1, layout.rowAt(layout.row(0).right() + PlaybillLayout.GAP / 2.0, cy(layout.row(0))));
    }

    private static double cx(Rect rect) {
        return rect.x() + rect.w() / 2.0;
    }

    private static double cy(Rect rect) {
        return rect.y() + rect.h() / 2.0;
    }
}
