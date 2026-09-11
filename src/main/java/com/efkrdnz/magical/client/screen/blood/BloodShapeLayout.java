package com.efkrdnz.magical.client.screen.blood;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the shape editor, kept out of the screen so a test can check it.
 *
 * <p>Every number the editor draws or hit-tests against lives here, including the two conversions
 * that are easy to get wrong and impossible to see in a diff: pixels to a slider fraction, and
 * pixels to canvas coordinates. Left inline in the draw calls - which is where the forge screen
 * keeps its equivalents - neither can be asserted without launching the game, and one of them flips
 * a sign.
 *
 * <p>The canvas is a map seen from above. Screen y grows downward while canvas v grows
 * <em>forward</em>, so {@link #canvasV} negates and {@link #canvasU} does not. The symptom of
 * getting that wrong is a shape mirrored in the world, which reads as a drawing bug rather than an
 * arithmetic one - the whole reason this is a named function with a test instead of a minus sign
 * buried in a loop.
 *
 * <p>All coordinates are screen-local: add the screen's left and top to place them.
 */
public final class BloodShapeLayout {

    public static final int PANEL_W = 420;
    public static final int PANEL_H = 244;

    /** The square the player draws in. The same 176 the forge canvas uses. */
    public static final int CANVAS_X = 12;
    public static final int CANVAS_Y = 28;
    public static final int CANVAS_SIZE = 176;

    /** The controls, to the right of the canvas. */
    public static final int SIDE_X = 200;
    public static final int SIDE_W = 208;

    public static final int TITLE_Y = 30;
    public static final int TITLE_H = 10;

    public static final int SLIDER_LABEL_Y = 46;
    public static final int SLIDER_Y = 58;
    public static final int SLIDER_H = 10;
    /** Value readout width, reserved on the right so the track never runs under the number. */
    public static final int SLIDER_VALUE_W = 22;
    public static final int SLIDER_X = SIDE_X;
    public static final int SLIDER_W = SIDE_W - SLIDER_VALUE_W - 4;

    public static final int CHECK_Y = 84;
    public static final int CHECK_STRIDE = 20;
    public static final int CHECK_SIZE = 10;
    public static final int CHECK_COUNT = 3;

    public static final int READOUT_Y = 152;
    public static final int READOUT_H = 44;

    public static final int ACTION_Y = 204;
    public static final int ACTION_W = 64;
    public static final int ACTION_H = 20;
    public static final int ACTION_STRIDE = 68;
    public static final int ACTION_COUNT = 3;

    /** The nine slots, along the bottom under the canvas. */
    public static final int STRIP_X = 12;
    public static final int STRIP_Y = 212;
    public static final int STRIP_CELL = 18;
    public static final int STRIP_STRIDE = 20;

    private static final double CANVAS_CENTER_X = CANVAS_X + CANVAS_SIZE / 2.0D;
    private static final double CANVAS_CENTER_Y = CANVAS_Y + CANVAS_SIZE / 2.0D;
    private static final double CANVAS_HALF = CANVAS_SIZE / 2.0D;

    private BloodShapeLayout() {
    }

    public static Rect panel() {
        return new Rect("panel", 0, 0, PANEL_W, PANEL_H);
    }

    public static Rect canvas() {
        return new Rect("canvas", CANVAS_X, CANVAS_Y, CANVAS_SIZE, CANVAS_SIZE);
    }

    public static Rect slider() {
        return new Rect("slider", SLIDER_X, SLIDER_Y, SLIDER_W, SLIDER_H);
    }

    public static Rect sliderValue() {
        return new Rect("sliderValue", SIDE_X + SIDE_W - SLIDER_VALUE_W, SLIDER_Y,
                SLIDER_VALUE_W, SLIDER_H);
    }

    /** One checkbox row: the box plus the label beside it, because the whole row is clickable. */
    public static Rect checkRow(int index) {
        return new Rect("check" + index, SIDE_X, CHECK_Y + index * CHECK_STRIDE, SIDE_W, CHECK_STRIDE);
    }

    public static Rect actionButton(int index) {
        return new Rect("action" + index, SIDE_X + index * ACTION_STRIDE, ACTION_Y, ACTION_W, ACTION_H);
    }

    public static int stripCellX(int slot) {
        return STRIP_X + slot * STRIP_STRIDE;
    }

    public static Rect stripCell(int slot) {
        return new Rect("slot" + slot, stripCellX(slot), STRIP_Y, STRIP_CELL, STRIP_CELL);
    }

    /** Every box the editor draws or hit-tests, named, for the overlap test to walk. */
    public static List<Rect> editorRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(canvas());
        rects.add(new Rect("title", SIDE_X, TITLE_Y, SIDE_W, TITLE_H));
        rects.add(new Rect("sliderLabel", SIDE_X, SLIDER_LABEL_Y, SIDE_W, 10));
        rects.add(slider());
        rects.add(sliderValue());
        for (int i = 0; i < CHECK_COUNT; i++) {
            rects.add(checkRow(i));
        }
        rects.add(new Rect("readout", SIDE_X, READOUT_Y, SIDE_W, READOUT_H));
        for (int i = 0; i < ACTION_COUNT; i++) {
            rects.add(actionButton(i));
        }
        for (int i = 0; i < BloodShapeRules.SHAPE_SLOTS; i++) {
            rects.add(stripCell(i));
        }
        return rects;
    }

    /**
     * @return the slot under the cursor, or -1 outside the strip and in the gaps between cells
     */
    public static int slotAt(double lx, double ly) {
        if (ly < STRIP_Y || ly >= STRIP_Y + STRIP_CELL) {
            return -1;
        }
        double offset = lx - STRIP_X;
        if (offset < 0) {
            return -1;
        }
        int slot = (int) (offset / STRIP_STRIDE);
        if (slot >= BloodShapeRules.SHAPE_SLOTS) {
            return -1;
        }
        // The gap between cells is dead rather than rounded to the nearer neighbour. Rounding picks
        // one of two slots arbitrarily, and arbitrary is the wrong answer when the two hold
        // different shapes and one of them is about to be overwritten.
        return offset - slot * STRIP_STRIDE < STRIP_CELL ? slot : -1;
    }

    /** @return the index of the checkbox row under the cursor, or -1 */
    public static int checkAt(double lx, double ly) {
        for (int i = 0; i < CHECK_COUNT; i++) {
            if (contains(checkRow(i), lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    /** @return the index of the action button under the cursor, or -1 */
    public static int actionAt(double lx, double ly) {
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (contains(actionButton(i), lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean contains(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    /** How far the knob's left edge can travel; the knob itself never leaves the track. */
    public static int sliderTravel() {
        return SLIDER_W - MagicalGuiStyle.SLIDER_KNOB_WIDTH;
    }

    /**
     * Left edge of the knob at this fraction - the exact number {@link MagicalGuiStyle#slider}
     * computes, so what is drawn and what is hit-tested cannot drift apart.
     */
    public static int sliderKnobX(float fraction) {
        return SLIDER_X + Math.round(sliderTravel() * clamp01(fraction));
    }

    /**
     * Cursor to fraction, the exact inverse of {@link #sliderKnobX} so the round trip can be
     * asserted. The knob is four pixels wide, so following its left edge rather than its centre is
     * invisible in the hand, and the clamp keeps both end stops reachable from anywhere past them.
     */
    public static float sliderFraction(double lx) {
        return clamp01((float) ((lx - SLIDER_X) / sliderTravel()));
    }

    /** Canvas x under the cursor, -1 at the left edge to 1 at the right. */
    public static double canvasU(double lx) {
        return clampUnit((lx - CANVAS_CENTER_X) / CANVAS_HALF);
    }

    /** Canvas y under the cursor, -1 at the bottom to 1 at the top. Negated: screen y grows down. */
    public static double canvasV(double ly) {
        return clampUnit((CANVAS_CENTER_Y - ly) / CANVAS_HALF);
    }

    /** The inverse of {@link #canvasU}, for drawing a stored point back onto the canvas. */
    public static double pixelX(double u) {
        return CANVAS_CENTER_X + u * CANVAS_HALF;
    }

    /** The inverse of {@link #canvasV}. */
    public static double pixelY(double v) {
        return CANVAS_CENTER_Y - v * CANVAS_HALF;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : value > 1.0F ? 1.0F : value;
    }

    private static double clampUnit(double value) {
        return value < -1.0D ? -1.0D : value > 1.0D ? 1.0D : value;
    }
}
