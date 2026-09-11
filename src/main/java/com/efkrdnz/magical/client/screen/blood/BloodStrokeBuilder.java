package com.efkrdnz.magical.client.screen.blood;

import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.ArrayList;
import java.util.List;

/**
 * The drawing half of the editor: one drag becomes one stroke.
 *
 * <p>A stroke in progress is kept in canvas units, -1 to 1, and only multiplied out to blocks when
 * the drag ends. Capturing in units keeps the minimum gap between points a fixed number of pixels
 * whatever the caster's reach is, so drawing at reach 2 feels exactly like drawing at reach 20.
 * Committed strokes are packed absolute sixteenths of a block - the same form they are stored and
 * sent in, so the editor never holds a second representation that could fall out of step.
 *
 * <p>Capture is dense and the resample at the end is what fits a stroke inside the point cap,
 * rather than throttling the cursor. Throttling loses corners: a fast diagonal flick would land two
 * points and draw a straight line through the curve the player actually made.
 */
public final class BloodStrokeBuilder {

    /** Minimum gap between captured points, in canvas units. Roughly two pixels of the canvas. */
    private static final double MIN_CAPTURE_SPACING = 2.0D / (BloodShapeLayout.CANVAS_SIZE / 2.0D);

    /** Ceiling on raw captured points, well above what the resample will keep. */
    private static final int MAX_RAW_POINTS = 256;

    /** Closest two resampled points may sit: one voxel pitch, below which they draw the same cube. */
    private static final double MIN_RESAMPLE_SPACING = 1.0D / BloodShapeRules.UNITS_PER_BLOCK;

    private final List<int[]> strokes = new ArrayList<>(BloodShapeRules.MAX_STROKES_PER_SHAPE);
    private final List<double[]> live = new ArrayList<>();
    private boolean drawing;

    /** Starts a builder holding what a slot already contains, so a stored shape can be edited. */
    public static BloodStrokeBuilder of(BloodShape shape) {
        BloodStrokeBuilder builder = new BloodStrokeBuilder();
        if (shape != null) {
            builder.strokes.addAll(shape.strokes());
        }
        return builder;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public boolean isEmpty() {
        return strokes.isEmpty() && live.isEmpty();
    }

    public int strokeCount() {
        return strokes.size();
    }

    /** @return true when another stroke may be started */
    public boolean hasRoom() {
        return strokes.size() < BloodShapeRules.MAX_STROKES_PER_SHAPE;
    }

    /** The stroke being dragged right now, as canvas-unit pairs, for the canvas to draw. */
    public List<double[]> livePoints() {
        return live;
    }

    /** The committed strokes, packed. Shared for reading only; the canvas never writes to them. */
    public List<int[]> strokes() {
        return strokes;
    }

    public void begin(double u, double v) {
        if (!hasRoom()) {
            return;
        }
        live.clear();
        live.add(new double[] {u, v});
        drawing = true;
    }

    public void extend(double u, double v) {
        if (!drawing || live.size() >= MAX_RAW_POINTS) {
            return;
        }
        double[] last = live.get(live.size() - 1);
        double du = u - last[0];
        double dv = v - last[1];
        if (du * du + dv * dv < MIN_CAPTURE_SPACING * MIN_CAPTURE_SPACING) {
            return;
        }
        live.add(new double[] {u, v});
    }

    /**
     * Ends the drag and commits the stroke, resampled and quantized.
     *
     * @param halfExtent the reach the canvas currently represents, which is what turns units into
     *                   blocks - the one place the two coordinate systems meet
     */
    public void finish(double halfExtent) {
        if (!drawing) {
            return;
        }
        drawing = false;
        if (live.size() < 2 || !hasRoom()) {
            // A click without a drag traces no path. Dropped rather than kept as a single point:
            // the geometry would produce an empty spine from it anyway.
            live.clear();
            return;
        }

        double[] line = new double[live.size() * 2];
        for (int i = 0; i < live.size(); i++) {
            double[] point = live.get(i);
            line[i * 2] = point[0] * halfExtent;
            line[i * 2 + 1] = point[1] * halfExtent;
        }
        live.clear();

        double spacing = Math.max(MIN_RESAMPLE_SPACING,
                length(line) / (BloodShapeRules.MAX_POINTS_PER_STROKE - 1));
        double[] resampled = BloodShapeGeometry.resample(line, spacing);
        int count = Math.min(resampled.length / 2, BloodShapeRules.MAX_POINTS_PER_STROKE);
        if (count < 2) {
            return;
        }
        int[] packed = new int[count];
        for (int i = 0; i < count; i++) {
            packed[i] = BloodShapeRules.pack(
                    BloodShapeRules.toUnits(resampled[i * 2]),
                    BloodShapeRules.toUnits(resampled[i * 2 + 1]));
        }
        strokes.add(packed);
    }

    /** Drops the stroke being drawn if there is one, otherwise the last committed stroke. */
    public void undo() {
        if (drawing || !live.isEmpty()) {
            drawing = false;
            live.clear();
            return;
        }
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
        }
    }

    public void clear() {
        drawing = false;
        live.clear();
        strokes.clear();
    }

    /**
     * The finished shape. {@link BloodShape#of} re-applies every cap, so this stays honest even
     * though the builder already respects them.
     */
    public BloodShape toShape(int heightPercent, int flags) {
        return BloodShape.of(strokes, heightPercent, flags);
    }

    private static double length(double[] line) {
        double total = 0.0D;
        for (int i = 0; i + 3 < line.length; i += 2) {
            double dx = line[i + 2] - line[i];
            double dy = line[i + 3] - line[i + 1];
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }
}
