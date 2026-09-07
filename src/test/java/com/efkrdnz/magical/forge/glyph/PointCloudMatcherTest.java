package com.efkrdnz.magical.forge.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PointCloudMatcherTest {

    /** An asymmetric L shape, so that a 90 degree rotation is clearly a different cloud. */
    private static List<GlyphPoint> lShapeCloud() {
        return GlyphNormalizer.normalize(List.of(List.of(
                new GlyphPoint(0.10f, 0.10f, 0),
                new GlyphPoint(0.10f, 0.90f, 0),
                new GlyphPoint(0.70f, 0.90f, 0))));
    }

    private static List<GlyphPoint> rotatedQuarterTurn(List<GlyphPoint> cloud) {
        List<GlyphPoint> out = new ArrayList<>(cloud.size());
        for (GlyphPoint p : cloud) {
            out.add(new GlyphPoint(-p.y(), p.x(), p.strokeId()));
        }
        return out;
    }

    private static List<GlyphPoint> jittered(List<GlyphPoint> cloud, float amount, long seed) {
        Random random = new Random(seed);
        List<GlyphPoint> out = new ArrayList<>(cloud.size());
        for (GlyphPoint p : cloud) {
            out.add(new GlyphPoint(
                    p.x() + (float) (random.nextDouble() * 2 - 1) * amount,
                    p.y() + (float) (random.nextDouble() * 2 - 1) * amount,
                    p.strokeId()));
        }
        return out;
    }

    @Test
    void distanceToAnIdenticalCloudIsZero() {
        List<GlyphPoint> cloud = lShapeCloud();

        assertEquals(0f, PointCloudMatcher.distance(cloud, cloud), 1e-6f);
        assertEquals(0f, PointCloudMatcher.distance(cloud, new ArrayList<>(cloud)), 1e-6f);
    }

    @Test
    void distanceIsSymmetric() {
        List<GlyphPoint> a = lShapeCloud();
        List<GlyphPoint> b = jittered(a, 0.09f, 77L);

        assertEquals(PointCloudMatcher.distance(a, b), PointCloudMatcher.distance(b, a), 1e-5f);
    }

    @Test
    void rotationCostsMoreThanJitterBecauseOrientationIsNotNormalized() {
        List<GlyphPoint> cloud = lShapeCloud();

        float rotatedDistance = PointCloudMatcher.distance(cloud, rotatedQuarterTurn(cloud));
        float jitteredDistance = PointCloudMatcher.distance(cloud, jittered(cloud, 0.02f, 1234L));

        assertTrue(rotatedDistance > jitteredDistance,
                "rotated=" + rotatedDistance + " must exceed jittered=" + jitteredDistance);
    }

    @Test
    void mismatchedCloudSizesAreRejected() {
        List<GlyphPoint> cloud = lShapeCloud();
        List<GlyphPoint> shorter = cloud.subList(0, cloud.size() - 1);

        assertThrows(IllegalArgumentException.class, () -> PointCloudMatcher.distance(cloud, shorter));
    }

    @Test
    void stepFollowsTheEpsilonRule() {
        assertEquals(0.5f, PointCloudMatcher.EPSILON, 0f);
        assertEquals(5, (int) Math.floor(Math.pow(GlyphNormalizer.N, 1 - PointCloudMatcher.EPSILON)));
    }
}
