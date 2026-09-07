package com.efkrdnz.magical.forge.glyph;

import java.util.List;

/**
 * The $P greedy point-cloud matcher (Vatavu, Anthony and Wobbrock, 2012).
 *
 * <p>Both clouds must already be normalized to the same point count.
 */
public final class PointCloudMatcher {

    /** Exponent that controls how many greedy start offsets are tried. */
    public static final float EPSILON = 0.5f;

    private PointCloudMatcher() {
    }

    /**
     * Greedy cloud distance: the minimum over a handful of start offsets, in both directions.
     *
     * @throws IllegalArgumentException when the clouds are empty or of different sizes
     */
    public static float distance(List<GlyphPoint> points, List<GlyphPoint> template) {
        int n = points.size();
        if (n == 0 || template.size() != n) {
            throw new IllegalArgumentException(
                    "clouds must be non-empty and of equal size, were " + n + " and " + template.size());
        }
        int step = Math.max(1, (int) Math.floor(Math.pow(n, 1 - EPSILON)));
        float min = Float.POSITIVE_INFINITY;
        for (int i = 0; i < n; i += step) {
            float forward = cloudDistance(points, template, i);
            float backward = cloudDistance(template, points, i);
            min = Math.min(min, Math.min(forward, backward));
        }
        return min;
    }

    /**
     * Walks {@code first} from {@code start}, greedily matching each point to the closest still
     * unmatched point of {@code second} and weighting early matches more heavily.
     */
    static float cloudDistance(List<GlyphPoint> first, List<GlyphPoint> second, int start) {
        int n = first.size();
        boolean[] matched = new boolean[n];
        double sum = 0;
        int i = start;
        do {
            int index = nearestUnmatched(first.get(i), second, matched);
            float minDistance = euclid(first.get(i), second.get(index));
            matched[index] = true;
            float weight = 1f - ((i - start + n) % n) / (float) n;
            sum += weight * minDistance;
            i = (i + 1) % n;
        } while (i != start);
        return (float) sum;
    }

    private static int nearestUnmatched(GlyphPoint from, List<GlyphPoint> candidates, boolean[] matched) {
        int index = -1;
        float min = Float.POSITIVE_INFINITY;
        for (int j = 0; j < candidates.size(); j++) {
            if (matched[j]) {
                continue;
            }
            float d = euclid(from, candidates.get(j));
            if (d < min) {
                min = d;
                index = j;
            }
        }
        if (index < 0) {
            throw new IllegalStateException("greedy match ran out of unmatched template points");
        }
        return index;
    }

    private static float euclid(GlyphPoint a, GlyphPoint b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
