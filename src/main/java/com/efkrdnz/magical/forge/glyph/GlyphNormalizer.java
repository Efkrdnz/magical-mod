package com.efkrdnz.magical.forge.glyph;

import java.util.ArrayList;
import java.util.List;

/**
 * The $P normalization pipeline: resample to a fixed point count, scale uniformly into the unit
 * box and translate so the centroid sits at the origin. There is deliberately no rotation
 * normalization -- glyph orientation is meaningful in the forge grammar.
 */
public final class GlyphNormalizer {

    /** Number of points every normalized glyph cloud is resampled to. */
    public static final int N = 32;

    private GlyphNormalizer() {
    }

    /** Full pipeline: resample to {@link #N} points, uniform scale, centroid translate. */
    public static List<GlyphPoint> normalize(List<List<GlyphPoint>> strokes) {
        return translateToCentroid(scaleToUnitBox(resample(strokes, N)));
    }

    /**
     * Resamples a multi-stroke glyph to exactly {@code n} points spaced by the same arc length.
     * Segment lengths are only accumulated within a stroke; the first point of every stroke is
     * emitted verbatim and restarts the accumulator, so no point is ever interpolated across the
     * gap between two strokes.
     *
     * <p>Because each of the {@code S} non-empty strokes contributes one verbatim start point on
     * top of its interval-spaced points, the spacing is {@code I = L / (n - S)} rather than
     * {@code L / (n - 1)}. That makes the emission count land on {@code n} for any stroke count,
     * so the padding and trimming below only ever correct floating-point error -- a glyph with
     * many short strokes no longer overshoots and loses its final stroke off the tail. The
     * denominator is clamped to at least 1 for the degenerate case of more strokes than points.
     */
    public static List<GlyphPoint> resample(List<List<GlyphPoint>> strokes, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("resample count must be >= 1, was " + n);
        }
        List<List<GlyphPoint>> nonEmpty = withoutEmptyStrokes(strokes);
        if (nonEmpty.isEmpty()) {
            throw new IllegalArgumentException("cannot resample a glyph without any point");
        }
        float interval = pathLength(nonEmpty) / Math.max(1, n - nonEmpty.size());
        List<GlyphPoint> out = new ArrayList<>(n);
        if (interval <= 0f) {
            return padTo(out, nonEmpty.get(0).get(0), n);
        }
        for (List<GlyphPoint> stroke : nonEmpty) {
            resampleInto(out, stroke, interval);
        }
        return trimTo(padTo(out, lastPointOf(nonEmpty), n), n);
    }

    /** Resamples one stroke on its own -- used to cap a freshly drawn stroke at a point budget. */
    public static List<GlyphPoint> resampleStroke(List<GlyphPoint> stroke, int n) {
        return resample(List.of(stroke), n);
    }

    /** Divides x and y by the larger bounding-box dimension, so the aspect ratio is preserved. */
    public static List<GlyphPoint> scaleToUnitBox(List<GlyphPoint> points) {
        if (points.isEmpty()) {
            return List.of();
        }
        float[] box = boundingBox(points);
        float size = Math.max(box[2] - box[0], box[3] - box[1]);
        if (size <= 0f) {
            return List.copyOf(points);
        }
        List<GlyphPoint> out = new ArrayList<>(points.size());
        for (GlyphPoint p : points) {
            out.add(new GlyphPoint(p.x() / size, p.y() / size, p.strokeId()));
        }
        return out;
    }

    /** Subtracts the centroid so that the cloud's centroid becomes (0, 0). */
    public static List<GlyphPoint> translateToCentroid(List<GlyphPoint> points) {
        if (points.isEmpty()) {
            return List.of();
        }
        double sumX = 0;
        double sumY = 0;
        for (GlyphPoint p : points) {
            sumX += p.x();
            sumY += p.y();
        }
        float cx = (float) (sumX / points.size());
        float cy = (float) (sumY / points.size());
        List<GlyphPoint> out = new ArrayList<>(points.size());
        for (GlyphPoint p : points) {
            out.add(new GlyphPoint(p.x() - cx, p.y() - cy, p.strokeId()));
        }
        return out;
    }

    /** Larger dimension of the bounding box of every point of every stroke, or 0 when there is none. */
    public static float boundingSize(List<List<GlyphPoint>> strokes) {
        List<GlyphPoint> all = new ArrayList<>();
        for (List<GlyphPoint> stroke : strokes) {
            all.addAll(stroke);
        }
        if (all.isEmpty()) {
            return 0f;
        }
        float[] box = boundingBox(all);
        return Math.max(box[2] - box[0], box[3] - box[1]);
    }

    /** Sum of the segment lengths inside each stroke; gaps between strokes contribute nothing. */
    public static float pathLength(List<List<GlyphPoint>> strokes) {
        double total = 0;
        for (List<GlyphPoint> stroke : strokes) {
            for (int i = 1; i < stroke.size(); i++) {
                total += euclid(stroke.get(i - 1), stroke.get(i));
            }
        }
        return (float) total;
    }

    private static void resampleInto(List<GlyphPoint> out, List<GlyphPoint> stroke, float interval) {
        GlyphPoint prev = stroke.get(0);
        out.add(prev);
        float carried = 0f;
        for (int i = 1; i < stroke.size(); i++) {
            GlyphPoint cur = stroke.get(i);
            float d = euclid(prev, cur);
            while (carried + d >= interval && d > 0f) {
                float t = (interval - carried) / d;
                GlyphPoint emitted = new GlyphPoint(
                        prev.x() + t * (cur.x() - prev.x()),
                        prev.y() + t * (cur.y() - prev.y()),
                        cur.strokeId());
                out.add(emitted);
                d -= interval - carried;
                prev = emitted;
                carried = 0f;
            }
            carried += Math.max(d, 0f);
            prev = cur;
        }
    }

    private static List<List<GlyphPoint>> withoutEmptyStrokes(List<List<GlyphPoint>> strokes) {
        List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
        for (List<GlyphPoint> stroke : strokes) {
            if (!stroke.isEmpty()) {
                out.add(stroke);
            }
        }
        return out;
    }

    private static GlyphPoint lastPointOf(List<List<GlyphPoint>> strokes) {
        List<GlyphPoint> last = strokes.get(strokes.size() - 1);
        return last.get(last.size() - 1);
    }

    private static List<GlyphPoint> padTo(List<GlyphPoint> points, GlyphPoint filler, int n) {
        while (points.size() < n) {
            points.add(filler);
        }
        return points;
    }

    private static List<GlyphPoint> trimTo(List<GlyphPoint> points, int n) {
        while (points.size() > n) {
            points.remove(points.size() - 1);
        }
        return points;
    }

    private static float[] boundingBox(List<GlyphPoint> points) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (GlyphPoint p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new float[] {minX, minY, maxX, maxY};
    }

    private static float euclid(GlyphPoint a, GlyphPoint b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
