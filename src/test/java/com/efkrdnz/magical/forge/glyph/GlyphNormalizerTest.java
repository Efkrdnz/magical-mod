package com.efkrdnz.magical.forge.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GlyphNormalizerTest {

    private static float distance(GlyphPoint a, GlyphPoint b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Test
    void resampleOfTwoPointLineYieldsExactlyNEvenlySpacedPoints() {
        List<List<GlyphPoint>> strokes =
                List.of(List.of(new GlyphPoint(0f, 0f, 0), new GlyphPoint(1f, 0f, 0)));

        List<GlyphPoint> resampled = GlyphNormalizer.resample(strokes, GlyphNormalizer.N);

        assertEquals(GlyphNormalizer.N, resampled.size());
        float expectedGap = 1f / (GlyphNormalizer.N - 1);
        for (int i = 1; i < resampled.size(); i++) {
            assertEquals(expectedGap, distance(resampled.get(i - 1), resampled.get(i)), 1e-4f,
                    "gap between point " + (i - 1) + " and " + i);
        }
    }

    @Test
    void resampleKeepsTheFirstPointOfEverySubsequentStrokeVerbatimAndSkipsTheGap() {
        List<List<GlyphPoint>> strokes = List.of(
                List.of(new GlyphPoint(0f, 0f, 0), new GlyphPoint(0.4f, 0f, 0)),
                List.of(new GlyphPoint(0.6f, 1f, 1), new GlyphPoint(1f, 1f, 1)));

        List<GlyphPoint> resampled = GlyphNormalizer.resample(strokes, GlyphNormalizer.N);

        assertEquals(GlyphNormalizer.N, resampled.size());
        assertEquals(new GlyphPoint(0f, 0f, 0), resampled.get(0));
        long verbatimSecondStrokeStarts = resampled.stream()
                .filter(p -> p.strokeId() == 1 && p.x() == 0.6f && p.y() == 1f)
                .count();
        assertEquals(1, verbatimSecondStrokeStarts, "second stroke must start verbatim exactly once");
        for (GlyphPoint p : resampled) {
            assertTrue(p.y() == 0f || p.y() == 1f, "no point may be interpolated across the stroke gap: " + p);
            assertTrue(p.strokeId() == 0 ? p.x() <= 0.4f + 1e-5f : p.x() >= 0.6f - 1e-5f,
                    "no point may lie inside the gap: " + p);
        }
    }

    /**
     * The spacing is {@code L / (n - strokeCount)}, so a glyph made of many short strokes emits
     * about n points instead of overshooting and losing its final stroke off the trimmed tail.
     */
    @Test
    void resampleOfAFiveStrokeGlyphKeepsTheEndOfItsLastStroke() {
        List<List<GlyphPoint>> strokes = ForgeGlyphLibrary.byId("divine").orElseThrow().strokes();
        assertEquals(5, strokes.size(), "the divine sigil is drawn with five strokes");
        List<GlyphPoint> lastStroke = strokes.get(strokes.size() - 1);
        GlyphPoint lastPoint = lastStroke.get(lastStroke.size() - 1);

        List<GlyphPoint> resampled = GlyphNormalizer.resample(strokes, GlyphNormalizer.N);

        assertEquals(GlyphNormalizer.N, resampled.size());
        float nearest = Float.MAX_VALUE;
        for (GlyphPoint p : resampled) {
            nearest = Math.min(nearest, distance(p, lastPoint));
        }
        assertTrue(nearest <= 0.02f,
                "the last stroke's end " + lastPoint + " is " + nearest + " away from every resampled point");
    }

    @Test
    void scaleToUnitBoxIsUniformAndPreservesAspect() {
        List<GlyphPoint> points = List.of(
                new GlyphPoint(0f, 0f, 0),
                new GlyphPoint(0.5f, 0f, 0),
                new GlyphPoint(0.5f, 0.25f, 0),
                new GlyphPoint(0f, 0.25f, 0));

        List<GlyphPoint> scaled = GlyphNormalizer.scaleToUnitBox(points);

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GlyphPoint p : scaled) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        assertEquals(1.0f, maxX - minX, 1e-5f);
        assertEquals(0.5f, maxY - minY, 1e-5f);
    }

    @Test
    void scaleToUnitBoxReturnsPointsUnchangedForADegenerateBox() {
        List<GlyphPoint> points = List.of(new GlyphPoint(0.4f, 0.7f, 0), new GlyphPoint(0.4f, 0.7f, 0));

        assertEquals(points, GlyphNormalizer.scaleToUnitBox(points));
    }

    @Test
    void translateToCentroidPutsTheCentroidAtTheOrigin() {
        List<GlyphPoint> points = List.of(
                new GlyphPoint(2f, 5f, 0),
                new GlyphPoint(4f, 9f, 0),
                new GlyphPoint(-3f, 1f, 1));

        List<GlyphPoint> translated = GlyphNormalizer.translateToCentroid(points);

        float sumX = 0f;
        float sumY = 0f;
        for (GlyphPoint p : translated) {
            sumX += p.x();
            sumY += p.y();
        }
        assertEquals(0f, sumX / translated.size(), 1e-5f);
        assertEquals(0f, sumY / translated.size(), 1e-5f);
        assertEquals(1, translated.get(2).strokeId());
    }

    @Test
    void degenerateSinglePointInputResamplesToNCopies() {
        List<List<GlyphPoint>> strokes = List.of(List.of(new GlyphPoint(0.25f, 0.75f, 0)));

        List<GlyphPoint> resampled = GlyphNormalizer.resample(strokes, GlyphNormalizer.N);

        assertEquals(GlyphNormalizer.N, resampled.size());
        for (GlyphPoint p : resampled) {
            assertEquals(new GlyphPoint(0.25f, 0.75f, 0), p);
        }
    }

    @Test
    void resampleStrokeCapsASingleStrokeAtTheRequestedCount() {
        java.util.List<GlyphPoint> stroke = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            stroke.add(new GlyphPoint(i / 199f, 0f, 3));
        }

        List<GlyphPoint> resampled = GlyphNormalizer.resampleStroke(stroke, 64);

        assertEquals(64, resampled.size());
        assertEquals(3, resampled.get(0).strokeId());
        assertEquals(0f, resampled.get(0).x(), 1e-5f);
        assertEquals(1f, resampled.get(63).x(), 1e-3f);
    }

    @Test
    void boundingSizeIsTheLargerBoundingBoxDimension() {
        List<List<GlyphPoint>> strokes = List.of(
                List.of(new GlyphPoint(0.1f, 0.2f, 0), new GlyphPoint(0.4f, 0.2f, 0)),
                List.of(new GlyphPoint(0.2f, 0.1f, 1), new GlyphPoint(0.2f, 0.9f, 1)));

        assertEquals(0.8f, GlyphNormalizer.boundingSize(strokes), 1e-5f);
    }

    @Test
    void normalizeProducesNPointsCentredOnTheOrigin() {
        List<List<GlyphPoint>> strokes =
                List.of(List.of(new GlyphPoint(0.1f, 0.1f, 0), new GlyphPoint(0.9f, 0.5f, 0)));

        List<GlyphPoint> cloud = GlyphNormalizer.normalize(strokes);

        assertEquals(GlyphNormalizer.N, cloud.size());
        float sumX = 0f;
        float sumY = 0f;
        for (GlyphPoint p : cloud) {
            sumX += p.x();
            sumY += p.y();
        }
        assertEquals(0f, sumX / cloud.size(), 1e-5f);
        assertEquals(0f, sumY / cloud.size(), 1e-5f);
    }
}
