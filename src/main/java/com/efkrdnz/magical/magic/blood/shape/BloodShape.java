package com.efkrdnz.magical.magic.blood.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * One drawn shape: the path the blood forms along, plus the three per-slot switches that decide
 * where it is read from.
 *
 * <p>Points are stored in <b>absolute sixteenths of a block</b> rather than normalized to the
 * canvas, and that one decision is the whole anti-cheese rule. A normalized coordinate means
 * nothing without the reach it was normalized against; keeping that reach alongside it leaves two
 * choices and both are wrong - rescale the drawing when reach changes, which is exactly the
 * loophole this closes, or divide the reach back out on every read, which is absolute units with a
 * second number to get wrong. Stored absolutely, "outside the canvas" is a property of the point,
 * answerable against whatever the reach happens to be at the moment of the cast.
 *
 * <p>Immutable. The editor rebuilds rather than mutates, which is what lets the owning book hand
 * the same tag instance to every sync until something actually changes.
 *
 * <p>The canvas is read as a map seen from above: u is the caster's right, v is forward. Vertical
 * position comes from the height slider and the wall extrusion, never from the drawing.
 */
public final class BloodShape {

    /** An unset slot. Shared, because there is nothing in one to tell it apart from another. */
    public static final BloodShape EMPTY = new BloodShape(new int[0], new int[0],
            BloodShapeRules.DEFAULT_HEIGHT_PERCENT, BloodShapeRules.DEFAULT_SPREAD_PERCENT, 0);

    private final int[] points;
    private final int[] strokeEnds;
    private final int heightPercent;
    private final int spreadPercent;
    private final int flags;

    private BloodShape(int[] points, int[] strokeEnds, int heightPercent, int spreadPercent,
            int flags) {
        this.points = points;
        this.strokeEnds = strokeEnds;
        this.heightPercent = heightPercent;
        this.spreadPercent = spreadPercent;
        this.flags = flags;
    }

    /**
     * Builds a shape from strokes of packed points, enforcing every cap on the way in.
     *
     * <p>Strokes arrive from a client packet, so nothing here is trusted: extra strokes and extra
     * points are dropped, coordinates are clamped into the storable range, and a stroke of fewer
     * than two points is discarded because it draws no path at all. Clamping rather than dropping
     * an out-of-range point is deliberate - this is the format check, and the reach check happens
     * at cast time against the reach the caster has then.
     */
    public static BloodShape of(List<int[]> strokes, int heightPercent, int spreadPercent,
            int flags) {
        if (strokes == null || strokes.isEmpty()) {
            return withSettings(EMPTY, heightPercent, spreadPercent, flags);
        }
        List<int[]> kept = new ArrayList<>(BloodShapeRules.MAX_STROKES_PER_SHAPE);
        int total = 0;
        for (int[] stroke : strokes) {
            if (kept.size() >= BloodShapeRules.MAX_STROKES_PER_SHAPE) {
                break;
            }
            if (stroke == null || stroke.length < 2) {
                continue;
            }
            int length = Math.min(stroke.length, BloodShapeRules.MAX_POINTS_PER_STROKE);
            int[] clean = new int[length];
            for (int i = 0; i < length; i++) {
                clean[i] = clampPoint(stroke[i]);
            }
            kept.add(clean);
            total += length;
        }
        if (kept.isEmpty()) {
            return withSettings(EMPTY, heightPercent, spreadPercent, flags);
        }
        int[] flat = new int[total];
        int[] ends = new int[kept.size()];
        int cursor = 0;
        for (int s = 0; s < kept.size(); s++) {
            int[] stroke = kept.get(s);
            System.arraycopy(stroke, 0, flat, cursor, stroke.length);
            cursor += stroke.length;
            ends[s] = cursor;
        }
        return new BloodShape(flat, ends, BloodShapeRules.clampHeightPercent(heightPercent),
                BloodShapeRules.clampSpreadPercent(spreadPercent),
                BloodShapeRules.clampFlags(flags));
    }

    /**
     * Rebuilds a shape from the flat form it is stored in, for the NBT and wire readers.
     *
     * <p>A stroke table that does not ascend, or that runs past the points it indexes, means the
     * tag was edited by hand or written by an older build. Reading stops there and keeps what
     * parsed, rather than throwing and taking the whole player state down with it.
     */
    public static BloodShape ofFlat(int[] points, int[] strokeEnds, int heightPercent,
            int spreadPercent, int flags) {
        if (points == null || strokeEnds == null || points.length == 0 || strokeEnds.length == 0) {
            return withSettings(EMPTY, heightPercent, spreadPercent, flags);
        }
        List<int[]> strokes = new ArrayList<>(strokeEnds.length);
        int start = 0;
        for (int end : strokeEnds) {
            if (end <= start || end > points.length) {
                break;
            }
            int[] stroke = new int[end - start];
            System.arraycopy(points, start, stroke, 0, stroke.length);
            strokes.add(stroke);
            start = end;
        }
        return of(strokes, heightPercent, spreadPercent, flags);
    }

    private static BloodShape withSettings(BloodShape base, int heightPercent, int spreadPercent,
            int flags) {
        return new BloodShape(base.points, base.strokeEnds,
                BloodShapeRules.clampHeightPercent(heightPercent),
                BloodShapeRules.clampSpreadPercent(spreadPercent),
                BloodShapeRules.clampFlags(flags));
    }

    private static int clampPoint(int packed) {
        return BloodShapeRules.pack(clampUnit(BloodShapeRules.unpackX(packed)),
                clampUnit(BloodShapeRules.unpackY(packed)));
    }

    private static int clampUnit(int units) {
        return Math.max(-BloodShapeRules.MAX_UNIT, Math.min(BloodShapeRules.MAX_UNIT, units));
    }

    public boolean isEmpty() {
        return strokeEnds.length == 0;
    }

    public int strokeCount() {
        return strokeEnds.length;
    }

    public int strokeStart(int stroke) {
        return stroke <= 0 ? 0 : strokeEnds[stroke - 1];
    }

    public int strokeEnd(int stroke) {
        return strokeEnds[stroke];
    }

    public int pointCount() {
        return points.length;
    }

    public int packed(int index) {
        return points[index];
    }

    /** Canvas x of a point, in blocks. Positive is the caster's right. */
    public double u(int index) {
        return BloodShapeRules.fromUnits(BloodShapeRules.unpackX(points[index]));
    }

    /** Canvas y of a point, in blocks. Positive is forward - up on the canvas, not up in the world. */
    public double v(int index) {
        return BloodShapeRules.fromUnits(BloodShapeRules.unpackY(points[index]));
    }

    public int heightPercent() {
        return heightPercent;
    }

    /** How far the blood stands off the drawn plane, and which way. See the rules for the curve. */
    public int spreadPercent() {
        return spreadPercent;
    }

    public int flags() {
        return flags;
    }

    public boolean trackYaw() {
        return BloodShapeRules.has(flags, BloodShapeRules.FLAG_TRACK_YAW);
    }

    public boolean trackPitch() {
        return BloodShapeRules.has(flags, BloodShapeRules.FLAG_TRACK_PITCH);
    }

    public boolean keepRotating() {
        return BloodShapeRules.has(flags, BloodShapeRules.FLAG_KEEP_ROTATING);
    }

    public BloodShape withHeightPercent(int percent) {
        return new BloodShape(points, strokeEnds, BloodShapeRules.clampHeightPercent(percent),
                spreadPercent, flags);
    }

    public BloodShape withSpreadPercent(int percent) {
        return new BloodShape(points, strokeEnds, heightPercent,
                BloodShapeRules.clampSpreadPercent(percent), flags);
    }

    public BloodShape withFlags(int newFlags) {
        return new BloodShape(points, strokeEnds, heightPercent, spreadPercent,
                BloodShapeRules.clampFlags(newFlags));
    }

    public BloodShape toggling(int flag) {
        return withFlags(flags ^ BloodShapeRules.clampFlags(flag));
    }

    /** The raw points, copied. Every caller is a writer, and none of them may alias the original. */
    public int[] pointsCopy() {
        return points.clone();
    }

    public int[] strokeEndsCopy() {
        return strokeEnds.clone();
    }

    /** The strokes split back out, copied. What the editor draws and what the wire writes. */
    public List<int[]> strokes() {
        List<int[]> out = new ArrayList<>(strokeEnds.length);
        int start = 0;
        for (int end : strokeEnds) {
            int[] stroke = new int[end - start];
            System.arraycopy(points, start, stroke, 0, stroke.length);
            out.add(stroke);
            start = end;
        }
        return out;
    }
}
