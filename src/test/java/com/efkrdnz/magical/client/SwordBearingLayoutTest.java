package com.efkrdnz.magical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.skill.sword.TheBearingSkill;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Bearing plot laid out on paper, at every screen size the game can hand us.
 *
 * <p>The seven assertions of the family the Manipulate Space selector, the loadout switcher and
 * the Grimoire all answer to: nothing drawn lands on anything else, everything sits inside the
 * block and the block inside the screen, every hit-test answers for exactly the rectangle it is
 * drawn as and for nothing in the gaps between them, the text budgets are what they are said to
 * be, and the curves start at zero and land on one without leaving the range on the way.
 *
 * <p>Plus the one this overlay owes on top: <b>nothing reaches the crosshair</b>. A hold is open
 * while the wielder is still aiming, so a plot of where their blades point may not be drawn over
 * the place they are pointing - which is what forces the disc off centre in the first place, and
 * is the loadout switcher's own caption bug pointed the other way.
 *
 * <p>And the one a disc owes that a list does not: every one of the 1296 places on the lattice is
 * drawn inside the disc at the largest mark the rung allows, with its focus ring round it, and
 * none of them lands on the dead centre - because the centre is straight up, the lattice stops
 * eighteen degrees short of it, and a projection that collapsed pitch +4 onto one pixel would
 * draw twenty-four distinct bearings on top of each other.
 *
 * <p>The layout has no font, so the widths here are the ones the overlay would measure.
 */
class SwordBearingLayoutTest {

    /** Every GUI size the game can hand the overlay, from vanilla's floor upward. */
    private static final int[][] SIZES = {
        {320, 240}, {427, 240}, {432, 243}, {480, 270}, {560, 315}, {640, 360}, {854, 480}, {960, 540},
    };

    /**
     * The pinned budgets: GUI width, GUI height, block width, cardinal limit, disc diameter.
     *
     * <p>Exact ints on purpose. Every one of them is a function of the screen rather than a
     * constant, and the only way to know that a change to one of the five ratios did not quietly
     * take the disc to a pixel or the bearing words to nothing is to write the numbers down.
     */
    private static final int[][] BUDGETS = {
        {320, 240, 130, 26, 70},
        {427, 240, 183, 36, 103},
        {432, 243, 186, 37, 104},
        {480, 270, 200, 38, 116},
        {560, 315, 200, 38, 116},
        {640, 360, 200, 38, 116},
        {854, 480, 200, 38, 116},
        {960, 540, 200, 38, 116},
    };

    /** Nothing measured, the words at their limit, and a plausible set of real widths. */
    private static int[][] cardinalCases(int guiWidth) {
        int limit = SwordBearingLayout.cardinalLimit(guiWidth);
        return new int[][] {
            {0, 0, 0, 0},
            {limit, limit, limit, limit},
            {limit + 400, limit + 400, limit + 400, limit + 400},
            {25, 22, 30, 17},
        };
    }

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    private static double cx(Rect r) {
        return r.x() + r.w() / 2.0D;
    }

    private static double cy(Rect r) {
        return r.y() + r.h() / 2.0D;
    }

    private static void assertDisjoint(List<Rect> rects, String where) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                assertFalse(a.overlaps(b), a.name() + " overlaps " + b.name() + " at " + where
                        + ": " + describe(a) + " vs " + describe(b));
            }
        }
    }

    // ---- the seven ------------------------------------------------------------------------------

    @Test
    void nothingDrawnLandsOnAnythingElseAtAnyScreenSize() {
        for (int[] size : SIZES) {
            for (int[] widths : cardinalCases(size[0])) {
                assertDisjoint(SwordBearingLayout.rects(size[0], size[1], widths),
                        size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void everythingSitsInsideTheBlockAndTheBlockInsideTheScreen() {
        for (int[] size : SIZES) {
            Rect block = SwordBearingLayout.block(size[0], size[1]);
            Rect screen = new Rect("screen", 0, 0, size[0], size[1]);
            assertTrue(screen.contains(block), size[0] + "x" + size[1]
                    + ": the block escapes the screen " + describe(block));
            Rect local = new Rect("block", 0, 0, block.w(), block.h());
            for (int[] widths : cardinalCases(size[0])) {
                for (Rect rect : SwordBearingLayout.rects(size[0], size[1], widths)) {
                    assertTrue(local.contains(rect), rect.name() + " escapes the block at "
                            + size[0] + "x" + size[1] + ": " + describe(rect) + " outside " + describe(local));
                }
            }
        }
    }

    /**
     * Every rectangle answers to its own centre, and the gaps between them answer to nothing.
     *
     * <p>The gaps matter as much as the hits. The block has four of them by construction - the
     * air round the disc where the bearing words stand off it, and the caption gap above the
     * reading - and a rectangle that had quietly grown into one would still pass a hit test on
     * its own centre.
     */
    @Test
    void everyRectangleAnswersToItsOwnCentreAndNothingInTheGaps() {
        for (int[] size : SIZES) {
            int guiWidth = size[0];
            int guiHeight = size[1];
            List<Rect> rects = SwordBearingLayout.rects(guiWidth, guiHeight, cardinalCases(guiWidth)[1]);
            for (int i = 0; i < rects.size(); i++) {
                Rect rect = rects.get(i);
                assertEquals(i, SwordBearingLayout.indexAt(rects, cx(rect), cy(rect)),
                        rect.name() + " does not answer to its own centre at " + guiWidth + "x" + guiHeight);
            }
            Rect disc = SwordBearingLayout.disc(guiWidth, guiHeight);
            assertEquals(-1, SwordBearingLayout.indexAt(rects, disc.x() - 2.0D, cy(disc)),
                    "the gap between the disc and the left bearing word picks something at "
                            + guiWidth + "x" + guiHeight);
            assertEquals(-1, SwordBearingLayout.indexAt(rects, disc.right() + 2.0D, cy(disc)),
                    "the gap between the disc and the right bearing word picks something at "
                            + guiWidth + "x" + guiHeight);
            assertEquals(-1, SwordBearingLayout.indexAt(rects, cx(disc), disc.bottom() + 2.0D),
                    "the gap under the disc picks something at " + guiWidth + "x" + guiHeight);
            assertEquals(-1, SwordBearingLayout.indexAt(rects, cx(disc),
                            SwordBearingLayout.reading(guiWidth, guiHeight, 0).y() - 2.0D),
                    "the caption gap picks something at " + guiWidth + "x" + guiHeight);
        }
    }

    @Test
    void theTextBudgetsAreWhatTheyAreSaidToBe() {
        for (int[] row : BUDGETS) {
            int guiWidth = row[0];
            int guiHeight = row[1];
            String at = " at " + guiWidth + "x" + guiHeight;
            assertEquals(row[2], SwordBearingLayout.blockWidth(guiWidth), "block width" + at);
            assertEquals(row[3], SwordBearingLayout.cardinalLimit(guiWidth), "cardinal limit" + at);
            assertEquals(row[4], SwordBearingLayout.discSize(guiWidth, guiHeight), "disc" + at);
            assertEquals(row[2], SwordBearingLayout.readingLimit(guiWidth), "reading limit" + at);
        }
    }

    /** A budget is a function of the screen, the way all three frameless precedents clamp theirs. */
    @Test
    void theBudgetsShrinkWithTheScreen() {
        assertTrue(SwordBearingLayout.cardinalLimit(320) < SwordBearingLayout.cardinalLimit(960),
                "a narrow screen must give the bearing words less room");
        assertTrue(SwordBearingLayout.readingLimit(320) < SwordBearingLayout.readingLimit(960),
                "a narrow screen must give the reading less room");
        for (int[] size : SIZES) {
            assertTrue(SwordBearingLayout.cardinalLimit(size[0]) > 0, "no room for a word at " + size[0]);
            assertTrue(SwordBearingLayout.discSize(size[0], size[1]) > 0, "no disc at " + size[0]);
        }
    }

    /** A string is drawn at its measured width or at the limit, never at more. */
    @Test
    void aLongWordIsCutToItsBudget() {
        int limit = SwordBearingLayout.cardinalLimit(640);
        assertEquals(limit, SwordBearingLayout.textWidth(limit + 400, limit), "a word far over the limit");
        assertEquals(limit, SwordBearingLayout.textWidth(limit, limit), "a word exactly at the limit");
        assertEquals(11, SwordBearingLayout.textWidth(11, limit), "a short word keeps its own width");
        assertEquals(0, SwordBearingLayout.textWidth(-4, limit), "nothing measured is nothing drawn");
    }

    @Test
    void theCurvesStartAtZeroLandOnOneAndNeverLeaveTheRange() {
        assertEquals(0.0F, SwordBearingLayout.ease(0.0F), 1e-5F);
        assertEquals(1.0F, SwordBearingLayout.ease(1.0F), 1e-5F);
        assertEquals(0.0F, SwordBearingLayout.easeOut(0.0F), 1e-5F);
        assertEquals(1.0F, SwordBearingLayout.easeOut(1.0F), 1e-5F);
        assertEquals(1.0F, SwordBearingLayout.ease(2.0F), 1e-5F, "past the end it stays put");
        assertEquals(0.0F, SwordBearingLayout.ease(-1.0F), 1e-5F, "before the start it stays put");

        float lastEase = 0.0F;
        float lastOut = 0.0F;
        for (int step = 0; step <= 100; step++) {
            float t = step / 100.0F;
            float eased = SwordBearingLayout.ease(t);
            float out = SwordBearingLayout.easeOut(t);
            assertTrue(eased >= -1e-6F && eased <= 1.0F + 1e-6F, "ease leaves 0..1 at " + t);
            assertTrue(out >= -1e-6F && out <= 1.0F + 1e-6F, "easeOut leaves 0..1 at " + t);
            assertTrue(eased >= lastEase - 1e-6F, "ease turns back at " + t);
            assertTrue(out >= lastOut - 1e-6F, "easeOut turns back at " + t);
            lastEase = eased;
            lastOut = out;
        }
    }

    /**
     * The plot is read while aiming, so it may never reach the crosshair.
     *
     * <p>This is the assertion that decides the whole arrangement: a disc centred on the screen
     * is a disc centred on the crosshair, so the block is pinned to the left margin and given
     * whatever is left before {@link SwordBearingLayout#CROSSHAIR_KEEP}.
     */
    @Test
    void nothingReachesTheCrosshair() {
        for (int[] size : SIZES) {
            int wall = SwordBearingLayout.wall(size[0]);
            Rect block = SwordBearingLayout.block(size[0], size[1]);
            for (int[] widths : cardinalCases(size[0])) {
                for (Rect rect : SwordBearingLayout.rects(size[0], size[1], widths)) {
                    assertTrue(block.x() + rect.right() <= wall, rect.name()
                            + " reaches the crosshair at " + size[0] + "x" + size[1] + ": "
                            + describe(rect) + " past " + (wall - block.x()));
                }
            }
            // And the marks, which live inside the disc and so are covered, said out loud anyway:
            // the disc is the widest thing in the block and it is what the marks are bounded by.
            assertTrue(block.x() + SwordBearingLayout.disc(size[0], size[1]).right() <= wall,
                    "the disc reaches the crosshair at " + size[0] + "x" + size[1]);
        }
    }

    // ---- the projection -------------------------------------------------------------------------

    /**
     * Every place on the lattice is drawn inside the disc, at the largest mark any rung allows,
     * with its focus ring round it.
     *
     * <p>All 1296 of them at every screen size, because the one thing a plot can do that a list
     * cannot is put something half off its own edge, and the worst case is not at the rim of the
     * disc but at whichever bearing the rounding happens to push outward.
     */
    @Test
    void everyPlaceOnTheLatticeIsDrawnInsideTheDisc() {
        for (int[] size : SIZES) {
            Rect disc = SwordBearingLayout.disc(size[0], size[1]);
            for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
                for (int pitch = Station.PITCH_MIN; pitch <= Station.PITCH_MAX; pitch++) {
                    // Reach is brightness and never geometry, so the Edge at its cap is the only
                    // axis of the lattice that can make a mark bigger.
                    Station station = new Station(yaw, pitch, Station.REACH_MAX, Station.EDGE_MAX);
                    Rect mark = SwordBearingLayout.mark(disc, 0, station, Station.EDGE_MAX);
                    assertEquals(SwordBearingLayout.MARK_MAX, mark.w(),
                            "a station at the cap is not drawn at the largest mark");
                    Rect ring = SwordBearingLayout.focusRing(mark);
                    assertTrue(disc.contains(ring), "the mark for " + yaw + "/" + pitch
                            + " hangs off the disc at " + size[0] + "x" + size[1] + ": "
                            + describe(ring) + " outside " + describe(disc));
                }
            }
        }
    }

    /** No mark lands on the zenith, which is the one direction the lattice cannot name. */
    @Test
    void theDeadCentreIsNeverAMark() {
        for (int[] size : SIZES) {
            Rect disc = SwordBearingLayout.disc(size[0], size[1]);
            for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
                Station station = new Station(yaw, Station.PITCH_MAX, 1, 1);
                Rect mark = SwordBearingLayout.mark(disc, 0, station, 1);
                double dx = cx(mark) - cx(disc);
                double dy = cy(mark) - cy(disc);
                assertTrue(Math.hypot(dx, dy) >= SwordBearingLayout.MARK_MAX,
                        "a mark at the top of the lattice sits on the zenith at "
                                + size[0] + "x" + size[1]);
            }
        }
    }

    /**
     * The projection is monotone and its whole range is used: radius climbs as pitch falls,
     * bearing climbs with yaw, and the innermost ring is held off the centre.
     */
    @Test
    void radiusFollowsPitchAndAngleFollowsYaw() {
        double previous = -1.0D;
        for (int pitch = Station.PITCH_MAX; pitch >= Station.PITCH_MIN; pitch--) {
            double fraction = SwordBearingLayout.radiusFraction(pitch);
            assertTrue(fraction > previous, "pitch " + pitch + " does not sit further out than the one above");
            previous = fraction;
        }
        assertEquals(SwordBearingLayout.RING_INNER, SwordBearingLayout.radiusFraction(Station.PITCH_MAX), 1e-6D,
                "the top of the lattice is not on the inner ring");
        assertEquals(1.0D, SwordBearingLayout.radiusFraction(Station.PITCH_MIN), 1e-6D,
                "the bottom of the lattice is not on the rim");

        double lastBearing = -1.0D;
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
            double bearing = SwordBearingLayout.bearingDegrees(yaw);
            assertTrue(bearing > lastBearing, "yaw " + yaw + " does not turn past the one before it");
            lastBearing = bearing;
        }
        assertTrue(lastBearing < 360.0D, "the last yaw step laps the first");
    }

    /** A mark grows with its Edge, and an emptied bearing keeps the smallest one rather than none. */
    @Test
    void aMarkGrowsWithItsEdgeAndAnEmptiedBearingStillHasOne() {
        assertEquals(SwordBearingLayout.MARK_MIN, SwordBearingLayout.markSize(0, 12));
        assertEquals(SwordBearingLayout.MARK_MAX, SwordBearingLayout.markSize(12, 12));
        assertEquals(SwordBearingLayout.MARK_MAX, SwordBearingLayout.markSize(36, 12), "over the cap is at the cap");
        int previous = 0;
        for (int edge = 0; edge <= 36; edge++) {
            int size = SwordBearingLayout.markSize(edge, 36);
            assertTrue(size >= previous, "a mark shrinks as its Edge grows at " + edge);
            assertTrue(size >= SwordBearingLayout.MARK_MIN && size <= SwordBearingLayout.MARK_MAX,
                    "a mark leaves its range at " + edge);
            previous = size;
        }
    }

    // ---- the walk -------------------------------------------------------------------------------

    /**
     * The scroll order visits every slot exactly once, clockwise from straight ahead and inside
     * out - not slot order, which is the Array's insertion order and would send the ring skipping
     * about the disc at random.
     */
    @Test
    void theWalkGoesRoundTheDiscAndVisitsEverySlotOnce() {
        List<Station> stations = new ArrayList<>(List.of(
                new Station(18, 2, 5, 4),
                new Station(0, 1, 3, 3),
                new Station(8, -2, 6, 5),
                new Station(0, -1, 4, 2),
                new Station(12, 0, 4, 3)));
        int[] order = SwordBearingLayout.bearingOrder(stations);
        assertEquals(stations.size(), order.length);

        boolean[] seen = new boolean[stations.size()];
        for (int slot : order) {
            assertFalse(seen[slot], "slot " + slot + " is walked twice");
            seen[slot] = true;
        }
        for (int slot = 0; slot < seen.length; slot++) {
            assertTrue(seen[slot], "slot " + slot + " is never walked");
        }

        int lastYaw = -1;
        int lastPitch = Integer.MAX_VALUE;
        for (int slot : order) {
            Station station = stations.get(slot);
            assertTrue(station.yaw() >= lastYaw, "the walk turns back on itself");
            if (station.yaw() == lastYaw) {
                assertTrue(station.pitch() <= lastPitch, "two stations on one bearing are not read inside out");
            }
            lastYaw = station.yaw();
            lastPitch = station.pitch();
        }
        assertEquals(0, SwordBearingLayout.bearingOrder(List.of()).length, "an empty Array walks nowhere");
    }

    /** A full ring is twelve marks, and a pull mask has a bit for each of them. */
    @Test
    void aFullRingFitsThePlotAndTheMask() {
        assertEquals(SwordArray.MAX_STATIONS, TheBearingSkill.MASK_BITS,
                "the mask is not one bit per station");
        List<Station> full = new ArrayList<>();
        for (int i = 0; i < SwordArray.MAX_STATIONS; i++) {
            full.add(new Station(i * 2, 0, 4, 3));
        }
        assertEquals(SwordArray.MAX_STATIONS, SwordBearingLayout.bearingOrder(full).length);
        Rect disc = SwordBearingLayout.disc(320, 240);
        for (int slot = 0; slot < full.size(); slot++) {
            assertTrue(disc.contains(SwordBearingLayout.focusRing(
                            SwordBearingLayout.mark(disc, slot, full.get(slot), 3))),
                    "slot " + slot + " of a full ring hangs off the smallest disc");
        }
    }
}
