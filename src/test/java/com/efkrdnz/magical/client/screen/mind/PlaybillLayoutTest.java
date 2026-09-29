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

    @Test
    void theElementListScrollsSoTheLastElementCanBeChosen() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            int rows = layout.rows();
            int count = rows + 7;
            int scroll = 0;
            for (int i = 0; i < 2 * count; i++) {
                scroll = layout.scrolled(scroll, -1.0, count);
            }
            assertEquals(count - rows, scroll, "the wheel runs past the end of the list at " + screen[0] + "x" + screen[1]);
            Rect last = layout.row(rows - 1);
            assertEquals(count - 1, layout.elementAt(cx(last), cy(last), scroll, count), "the last element is out of reach");
            assertEquals(scroll, layout.elementAt(cx(layout.row(0)), cy(layout.row(0)), scroll, count),
                    "a click on the top row does not map to the scrolled index");
            for (int i = 0; i < 2 * count; i++) {
                scroll = layout.scrolled(scroll, 1.0, count);
            }
            assertEquals(0, scroll, "the wheel runs past the top of the list");
            assertTrue(layout.overElements(cx(layout.row(0)), cy(layout.row(0))));
            assertTrue(layout.overElements(cx(last), cy(last)));
            assertFalse(layout.overElements(cx(layout.sense(0)), cy(layout.sense(0))), "the senses scroll the elements");
            assertFalse(layout.overElements(cx(layout.row(0)), cy(layout.tab(0))), "the tabs scroll the elements");
        }
    }

    @Test
    void aShortListDoesNotScrollAndItsEmptyRowsChooseNothing() {
        PlaybillLayout layout = new PlaybillLayout(427, 240);
        assertEquals(0, layout.scrolled(0, -1.0, 3));
        assertEquals(2, layout.elementAt(cx(layout.row(2)), cy(layout.row(2)), 0, 3));
        assertEquals(-1, layout.elementAt(cx(layout.row(5)), cy(layout.row(5)), 0, 3));
        assertEquals(0, layout.clampScroll(9, 3), "a list that shrank keeps a scroll it no longer has");
        assertEquals(2, layout.clampScroll(9, layout.rows() + 2));
        assertEquals(0, layout.clampScroll(-4, layout.rows() + 2));
    }

    @Test
    void everyStanceAndReactionWordFitsItsCell() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            for (int i = 0; i < 4; i++) {
                assertTrue(layout.stance(i).w() >= PlaybillLayout.WIDEST_STANCE_LABEL,
                        "a stance cell is " + layout.stance(i).w() + " wide at " + screen[0] + "x" + screen[1]);
            }
            for (int i = 0; i < 5; i++) {
                assertTrue(layout.reaction(i).w() >= PlaybillLayout.WIDEST_REACTION_LABEL,
                        "a reaction cell is " + layout.reaction(i).w() + " wide at " + screen[0] + "x" + screen[1]);
            }
        }
    }

    @Test
    void theWidestLabelConstantsCoverTheWordsInTheLanguageFile() throws Exception {
        String lang;
        try (var in = PlaybillLayoutTest.class.getResourceAsStream("/assets/magical/lang/en_us.json")) {
            assertNotNull(in);
            lang = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String[] group : new String[][] {{"stance", "" + PlaybillLayout.WIDEST_STANCE_LABEL}, {"reaction", "" + PlaybillLayout.WIDEST_REACTION_LABEL}}) {
            var matcher = java.util.regex.Pattern.compile("\"mind\\.magical\\." + group[0] + "\\.[a-z_]+\": \"([^\"]*)\"").matcher(lang);
            int found = 0;
            while (matcher.find()) {
                found++;
                assertTrue(matcher.group(1).length() * 6 <= Integer.parseInt(group[1]),
                        "'" + matcher.group(1) + "' is wider than the " + group[0] + " constant");
            }
            assertTrue(found > 0, "no " + group[0] + " words found");
        }
    }

    @Test
    void theForecastKeepsTwoTermsAndEndsAboveTheButtons() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            assertTrue(layout.forecastTerms() >= 2, "under two plausibility terms at " + screen[0] + "x" + screen[1]);
            int bottom = layout.contentTop() + PlaybillLayout.FORECAST_FIXED + layout.forecastTerms() * PlaybillLayout.LINE;
            assertTrue(bottom <= layout.save().y(), "the forecast runs to " + bottom + ", onto Save at " + layout.save().y()
                    + " at " + screen[0] + "x" + screen[1]);
        }
    }

    private static double cx(Rect rect) {
        return rect.x() + rect.w() / 2.0;
    }

    private static double cy(Rect rect) {
        return rect.y() + rect.h() / 2.0;
    }
}
