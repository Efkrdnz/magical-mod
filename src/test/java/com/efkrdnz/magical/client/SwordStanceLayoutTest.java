package com.efkrdnz.magical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The stance picker laid out on paper, the way the loadout switcher and the Grimoire are.
 *
 * <p>This is the third frameless surface in the mod and the reason it gets measured rather than
 * read is that the first draft of it was wrong twice over, in two ways nobody reading the file
 * would have caught. Six cells of 72 pixels want <b>452</b>, and the narrowest gui Minecraft can
 * hand an overlay is <b>320</b> - so at maximum gui scale the last two stances were off the right
 * of the screen. And at 480x270, the size every capture in this project is taken at, the focused
 * stance's description landed on y 239 and the release hint on y 240: two lines of text drawn one
 * pixel apart, on top of each other, in the middle of the picker.
 *
 * <p>So every rectangle here is built out of {@link SwordStanceLayout}'s own accessors and swept
 * over every gui size the game offers. The three things it holds are the three that were broken:
 * nothing overlaps, everything is on the screen, and nothing comes near the crosshair.
 */
class SwordStanceLayoutTest {

    /**
     * Every gui size the game can hand the overlay, from vanilla's floor upward.
     *
     * <p>320x240 is not a curiosity: {@code Window.calculateScale} clamps a forced gui scale to
     * the largest one that still leaves 320x240, so it is exactly what a wielder who has dragged
     * the slider to the end is playing at.
     */
    private static final int[][] SIZES = {
        {320, 240}, {427, 240}, {432, 243}, {480, 270}, {560, 315},
        {640, 360}, {854, 480}, {960, 540}, {1280, 720},
    };

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    /**
     * Everything the overlay paints, as rectangles.
     *
     * <p>A centred string is given its whole {@code textLimit} rather than its measured width,
     * because the limit is what the overlay truncates to and a test that measured the actual
     * English would pass on a translation that does not fit.
     */
    private static List<Rect> rects(int w, int h) {
        List<Rect> all = new ArrayList<>();
        int cellWidth = SwordStanceLayout.cellWidth(w);
        int diagram = SwordStanceLayout.diagram(w);
        int limit = SwordStanceLayout.textLimit(w);
        for (int i = 0; i < SwordStanceLayout.CELLS; i++) {
            int centre = SwordStanceLayout.cellCentre(w, i);
            String name = SwordStance.byOrdinal(i).key();
            all.add(new Rect(name + " cell", SwordStanceLayout.cellLeft(w, i),
                    SwordStanceLayout.rowTop(w, h), cellWidth, SwordStanceLayout.cellHeight(w)));
            all.add(new Rect(name + " diagram", centre - diagram / 2,
                    SwordStanceLayout.rowTop(w, h), diagram, diagram));
            all.add(new Rect(name + " name", centre - limit / 2,
                    SwordStanceLayout.nameTop(w, h), limit, SwordStanceLayout.LINE_HEIGHT));
            all.add(new Rect(name + " sub", centre - limit / 2,
                    SwordStanceLayout.subTop(w, h), limit, SwordStanceLayout.LINE_HEIGHT));
        }
        int desc = SwordStanceLayout.descLimit(w);
        all.add(new Rect("description", w / 2 - desc / 2, SwordStanceLayout.descTop(w, h),
                desc, SwordStanceLayout.LINE_HEIGHT));
        all.add(new Rect("hint", w / 2 - desc / 2, SwordStanceLayout.hintTop(w, h),
                desc, SwordStanceLayout.LINE_HEIGHT));
        return all;
    }

    /** The cells and the two lines under the row: what may not run into each other. */
    private static List<Rect> blocks(int w, int h) {
        List<Rect> all = new ArrayList<>();
        for (Rect r : rects(w, h)) {
            if (r.name().endsWith(" cell") || r.name().equals("description") || r.name().equals("hint")) {
                all.add(r);
            }
        }
        return all;
    }

    private static Rect cellOf(int w, int h, String stance) {
        for (Rect candidate : rects(w, h)) {
            if (candidate.name().equals(stance + " cell")) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    void nothingDrawnOverlapsAnythingElse() {
        for (int[] size : SIZES) {
            List<Rect> blocks = blocks(size[0], size[1]);
            for (int i = 0; i < blocks.size(); i++) {
                for (int j = i + 1; j < blocks.size(); j++) {
                    Rect a = blocks.get(i);
                    Rect b = blocks.get(j);
                    assertTrue(!a.overlaps(b), a.name() + " overlaps " + b.name() + " at "
                            + size[0] + "x" + size[1] + ": " + describe(a) + " vs " + describe(b));
                }
            }
        }
    }

    @Test
    void everyCellHoldsItsOwnDiagramAndItsOwnTwoLines() {
        // The inner pieces are measured against their cell rather than against each other,
        // because a name wider than its cell reads as a name overlapping its neighbour's - and
        // the fix for those two is not the same one.
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            for (Rect r : rects(w, h)) {
                if (r.name().endsWith(" cell")
                        || r.name().equals("description") || r.name().equals("hint")) {
                    continue;
                }
                String stance = r.name().substring(0, r.name().lastIndexOf(' '));
                Rect cell = cellOf(w, h, stance);
                assertTrue(cell != null && cell.contains(r), r.name() + " leaves its cell at "
                        + w + "x" + h + ": " + describe(r) + " in " + describe(cell));
            }
        }
    }

    @Test
    void everythingDrawnIsOnTheScreen() {
        for (int[] size : SIZES) {
            for (Rect r : rects(size[0], size[1])) {
                assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= size[0] && r.bottom() <= size[1],
                        r.name() + " leaves the screen at " + size[0] + "x" + size[1] + ": "
                                + describe(r));
            }
        }
    }

    @Test
    void nothingReachesTheCrosshair() {
        // A picker you cannot aim through is a picker you cannot use: the stance is chosen while
        // the wielder is holding the key with something in front of them. The scrim covers the
        // whole screen and is deliberately not measured here - it is a wash, not a mark.
        for (int[] size : SIZES) {
            int floor = size[1] / 2 + SwordStanceLayout.CROSSHAIR_CLEARANCE;
            for (Rect r : rects(size[0], size[1])) {
                assertTrue(r.y() >= floor, r.name() + " is drawn at y " + r.y()
                        + ", above the crosshair's clearance of " + floor + " at "
                        + size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void theBladesOfADiagramStayInsideIt() {
        // diagramHalf is what the formation is scaled into, and a sword is drawn as a bar
        // reaching BLADE_HALF past wherever its slot landed - so the two have to be reserved
        // together or the topmost blade of every stance is painted outside its own square.
        for (int[] size : SIZES) {
            int w = size[0];
            assertTrue(SwordStanceLayout.diagramHalf(w) + SwordStanceLayout.BLADE_HALF
                            <= SwordStanceLayout.diagram(w) / 2,
                    "a blade on the rim of a diagram overruns it at width " + w);
            assertTrue(SwordStanceLayout.diagramHalf(w) >= 1,
                    "the diagram at width " + w + " has no room to scale a formation into");
        }
    }

    @Test
    void everyStringIsClampedToTheSurfaceItSitsOn() {
        // The bug this whole file descends from: the loadout switcher drew a thirty-four
        // character caption with no truncation at all and it ran out through its own wall.
        for (int[] size : SIZES) {
            int w = size[0];
            assertTrue(SwordStanceLayout.textLimit(w) <= SwordStanceLayout.cellWidth(w),
                    "a cell's text may be wider than the cell at width " + w);
            assertTrue(SwordStanceLayout.descLimit(w) <= w - 2 * SwordStanceLayout.SIDE_MARGIN,
                    "the description may be wider than the screen at width " + w);
            assertTrue(SwordStanceLayout.descLimit(w) <= SwordStanceLayout.rowWidth(w),
                    "the description may be wider than the row it describes at width " + w);
            assertTrue(SwordStanceLayout.textLimit(w) > 0 && SwordStanceLayout.descLimit(w) > 0,
                    "a limit of nothing at width " + w + " truncates every string to empty");
        }
    }

    @Test
    void theRowIsTheSixStancesInOrderAndCannotBeAskedForASeventh() {
        assertEquals(SwordStance.count(), SwordStanceLayout.CELLS);
        for (int[] size : SIZES) {
            int w = size[0];
            for (int i = 1; i < SwordStanceLayout.CELLS; i++) {
                assertTrue(SwordStanceLayout.cellLeft(w, i) > SwordStanceLayout.cellLeft(w, i - 1),
                        "cell " + i + " does not sit right of cell " + (i - 1) + " at width " + w);
            }
            // The overlay walks the focus with floorMod, so an out-of-range index is a bug
            // elsewhere - but clamping rather than throwing keeps it a misdrawn frame instead of
            // a crash in the middle of a held key.
            assertEquals(SwordStanceLayout.cellLeft(w, 0), SwordStanceLayout.cellLeft(w, -3));
            assertEquals(SwordStanceLayout.cellLeft(w, SwordStanceLayout.CELLS - 1),
                    SwordStanceLayout.cellLeft(w, SwordStanceLayout.CELLS + 4));
        }
    }

    @Test
    void theRowGivesUpItsDropBeforeTheScreenGivesUpItsEdge() {
        // Two rules that pull against each other, and the whole of rowTop is which one yields.
        // On a tall gui the row sits its full ROW_DROP below the middle; on a short one it
        // climbs to keep the hint on screen, and stops dead at the crosshair's clearance.
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            int top = SwordStanceLayout.rowTop(w, h);
            assertTrue(top >= h / 2 + SwordStanceLayout.CROSSHAIR_CLEARANCE,
                    "the row climbed over the crosshair at " + w + "x" + h);
            assertTrue(top <= h / 2 + SwordStanceLayout.ROW_DROP,
                    "the row sank below its own drop at " + w + "x" + h);
            assertTrue(SwordStanceLayout.hintTop(w, h) + SwordStanceLayout.LINE_HEIGHT
                            <= h - SwordStanceLayout.BOTTOM_MARGIN,
                    "the hint is under the bottom margin at " + w + "x" + h);
        }
    }
}
