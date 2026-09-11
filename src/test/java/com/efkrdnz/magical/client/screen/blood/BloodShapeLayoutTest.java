package com.efkrdnz.magical.client.screen.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shape editor laid out on paper, the way the codex already is.
 *
 * <p>The overlap half of this is the cheap version of a screenshot. The arithmetic half matters
 * more: the canvas conversion flips a sign, and a flipped sign draws a shape that is mirrored in
 * the world while looking entirely correct in the editor. Nobody finds that by reading a diff.
 */
class BloodShapeLayoutTest {

    private static String describe(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    @Test
    void nothingInTheEditorOverlapsAnythingElse() {
        List<Rect> rects = BloodShapeLayout.editorRects();
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                assertTrue(!a.overlaps(b), a.name() + " overlaps " + b.name()
                        + ": " + describe(a) + " vs " + describe(b));
            }
        }
    }

    @Test
    void thePanelHoldsEverythingDrawnInIt() {
        Rect panel = BloodShapeLayout.panel();
        for (Rect rect : BloodShapeLayout.editorRects()) {
            assertTrue(panel.contains(rect), rect.name() + " escapes the panel: "
                    + describe(rect) + " outside " + describe(panel));
        }
    }

    @Test
    void allNineSlotCellsFitBesideTheActionRow() {
        // The failure the codex once had: a count constant drives a strip's width, the strip grows
        // past the column beside it, and nothing says so until the game is running.
        Rect last = BloodShapeLayout.stripCell(BloodShapeRules.SHAPE_SLOTS - 1);
        assertTrue(last.right() <= BloodShapeLayout.SIDE_X,
                "the slot strip reaches " + last.right() + ", into the side column at "
                        + BloodShapeLayout.SIDE_X);
    }

    @Test
    void everySlotCellIsHitByItsOwnCentreAndNoOther() {
        for (int slot = 0; slot < BloodShapeRules.SHAPE_SLOTS; slot++) {
            double x = BloodShapeLayout.stripCellX(slot) + BloodShapeLayout.STRIP_CELL / 2.0D;
            double y = BloodShapeLayout.STRIP_Y + BloodShapeLayout.STRIP_CELL / 2.0D;
            assertEquals(slot, BloodShapeLayout.slotAt(x, y));
        }
    }

    @Test
    void theGapsBetweenSlotCellsHitNothing() {
        double y = BloodShapeLayout.STRIP_Y + BloodShapeLayout.STRIP_CELL / 2.0D;
        for (int slot = 0; slot < BloodShapeRules.SHAPE_SLOTS - 1; slot++) {
            double x = BloodShapeLayout.stripCellX(slot) + BloodShapeLayout.STRIP_CELL + 0.5D;
            assertEquals(-1, BloodShapeLayout.slotAt(x, y),
                    "the gap after slot " + slot + " picked a slot anyway");
        }
    }

    @Test
    void nothingOutsideTheStripPicksASlot() {
        double insideX = BloodShapeLayout.stripCellX(0) + 2.0D;
        assertEquals(-1, BloodShapeLayout.slotAt(insideX, BloodShapeLayout.STRIP_Y - 1));
        assertEquals(-1, BloodShapeLayout.slotAt(insideX,
                BloodShapeLayout.STRIP_Y + BloodShapeLayout.STRIP_CELL));
        assertEquals(-1, BloodShapeLayout.slotAt(BloodShapeLayout.STRIP_X - 1,
                BloodShapeLayout.STRIP_Y + 2));
        assertEquals(-1, BloodShapeLayout.slotAt(
                BloodShapeLayout.stripCellX(BloodShapeRules.SHAPE_SLOTS) + 2.0D,
                BloodShapeLayout.STRIP_Y + 2));
    }

    @Test
    void theSliderRoundTripsFromFractionToPixelAndBack() {
        // Quantized to whole pixels on the way out, so the tolerance is half a pixel of travel.
        double tolerance = 0.5D / BloodShapeLayout.sliderTravel() + 1.0E-6D;
        for (int percent = 0; percent <= 100; percent++) {
            float fraction = percent / 100.0F;
            float back = BloodShapeLayout.sliderFraction(BloodShapeLayout.sliderKnobX(fraction));
            assertEquals(fraction, back, tolerance, "round trip failed at " + percent + "%");
        }
    }

    @Test
    void theSliderClampsAtBothEndStops() {
        assertEquals(0.0F, BloodShapeLayout.sliderFraction(BloodShapeLayout.SLIDER_X - 40));
        assertEquals(1.0F, BloodShapeLayout.sliderFraction(
                BloodShapeLayout.SLIDER_X + BloodShapeLayout.SLIDER_W + 40));
        assertEquals(0.0F, BloodShapeLayout.sliderFraction(BloodShapeLayout.SLIDER_X));
        assertEquals(1.0F, BloodShapeLayout.sliderFraction(
                BloodShapeLayout.SLIDER_X + BloodShapeLayout.sliderTravel()));
    }

    @Test
    void theSpreadSliderIsCentreZeroAndSignedAtItsEnds() {
        int centre = BloodShapeLayout.SLIDER_X + BloodShapeLayout.sliderTravel() / 2;
        assertEquals(0, BloodShapeLayout.spreadPercent(centre),
                "the middle of the spread track is the default sheet");
        assertEquals(-BloodShapeRules.SPREAD_PERCENT_RANGE,
                BloodShapeLayout.spreadPercent(BloodShapeLayout.SLIDER_X - 40),
                "left is all the way down");
        assertEquals(BloodShapeRules.SPREAD_PERCENT_RANGE,
                BloodShapeLayout.spreadPercent(
                        BloodShapeLayout.SLIDER_X + BloodShapeLayout.SLIDER_W + 40),
                "right is all the way up");
    }

    @Test
    void theSpreadSliderSnapsToZeroNearTheMiddleAndNowhereElse() {
        // Zero is a default rather than an end stop, so it has to be reachable by hand; on a track
        // this long an exact centre is a coin flip without a detent.
        int centre = BloodShapeLayout.SLIDER_X + BloodShapeLayout.sliderTravel() / 2;
        for (int dx = -3; dx <= 3; dx++) {
            assertEquals(0, BloodShapeLayout.spreadPercent(centre + dx),
                    "the detent should hold " + dx + " pixels off centre");
        }
        int outside = BloodShapeLayout.sliderKnobX(BloodShapeLayout.spreadFraction(40));
        assertEquals(40, BloodShapeLayout.spreadPercent(outside), 1,
                "and it must not reach anywhere near the rest of the track");
    }

    @Test
    void theSpreadSliderRoundTripsThroughItsOwnFraction() {
        for (int percent = -BloodShapeRules.SPREAD_PERCENT_RANGE;
                percent <= BloodShapeRules.SPREAD_PERCENT_RANGE; percent++) {
            if (Math.abs(percent) <= BloodShapeLayout.SPREAD_DETENT) {
                continue;
            }
            int pixel = BloodShapeLayout.sliderKnobX(BloodShapeLayout.spreadFraction(percent));
            assertEquals(percent, BloodShapeLayout.spreadPercent(pixel), 1,
                    "round trip failed at " + percent);
        }
    }

    @Test
    void theCanvasReadsLeftToRightAndBottomToTop() {
        int left = BloodShapeLayout.CANVAS_X;
        int right = BloodShapeLayout.CANVAS_X + BloodShapeLayout.CANVAS_SIZE;
        int top = BloodShapeLayout.CANVAS_Y;
        int bottom = BloodShapeLayout.CANVAS_Y + BloodShapeLayout.CANVAS_SIZE;

        assertEquals(-1.0D, BloodShapeLayout.canvasU(left), 1.0E-9D);
        assertEquals(1.0D, BloodShapeLayout.canvasU(right), 1.0E-9D);
        // The sign flip. Screen y grows downward and canvas v grows forward, so the top of the
        // square is +1 and the bottom is -1 - not the other way round.
        assertEquals(1.0D, BloodShapeLayout.canvasV(top), 1.0E-9D);
        assertEquals(-1.0D, BloodShapeLayout.canvasV(bottom), 1.0E-9D);
    }

    @Test
    void theCanvasCentreIsThePlayer() {
        double centreX = BloodShapeLayout.CANVAS_X + BloodShapeLayout.CANVAS_SIZE / 2.0D;
        double centreY = BloodShapeLayout.CANVAS_Y + BloodShapeLayout.CANVAS_SIZE / 2.0D;
        assertEquals(0.0D, BloodShapeLayout.canvasU(centreX), 1.0E-9D);
        assertEquals(0.0D, BloodShapeLayout.canvasV(centreY), 1.0E-9D);
    }

    @Test
    void pixelsAndCanvasUnitsAreExactInverses() {
        for (int step = 0; step <= 16; step++) {
            double unit = -1.0D + step / 8.0D;
            assertEquals(unit, BloodShapeLayout.canvasU(BloodShapeLayout.pixelX(unit)), 1.0E-9D);
            assertEquals(unit, BloodShapeLayout.canvasV(BloodShapeLayout.pixelY(unit)), 1.0E-9D);
        }
    }

    @Test
    void theCursorIsClampedToThePlateRatherThanRunningOffIt() {
        // A drag that leaves the square rides the boundary, so a stroke pulled off the edge still
        // ends somewhere the caster can actually reach.
        assertEquals(1.0D, BloodShapeLayout.canvasU(BloodShapeLayout.CANVAS_X + 400), 1.0E-9D);
        assertEquals(-1.0D, BloodShapeLayout.canvasU(BloodShapeLayout.CANVAS_X - 400), 1.0E-9D);
        assertEquals(-1.0D, BloodShapeLayout.canvasV(BloodShapeLayout.CANVAS_Y + 400), 1.0E-9D);
        assertEquals(1.0D, BloodShapeLayout.canvasV(BloodShapeLayout.CANVAS_Y - 400), 1.0E-9D);
    }
}
