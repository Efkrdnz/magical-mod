package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.forge.HitShape;
import com.efkrdnz.magical.entity.forge.HitShapes;
import com.efkrdnz.magical.forge.FormFamily;
import org.junit.jupiter.api.Test;

/**
 * The shape of a thrown wave, and the one thing it is allowed to lean toward.
 *
 * <p>It has been wrong twice for the same reason. First it was a ring band hanging a radius off the
 * flight path, rolled forty degrees, so it read as a moon tipped up and to the right. Then it was a
 * crescent sitting on the flight path but with its belly pointing down - still a direction, still
 * arbitrary, still not the way the thing was travelling.
 *
 * <p>A wave curves toward where it is going and toward nothing else. That makes it a surface of
 * revolution about the aim: the middle leads, the rim trails, and it is the same shape whichever
 * way you roll it about the line of flight. There is no up on it to get wrong. These tests say so
 * in numbers, and they measure it against {@code HitShapes} so the picture cannot outgrow the
 * volume that actually catches people.
 */
class WaveSilhouetteTest {

    /** Light, heavy, and a couple of tempered weapons either side of them. */
    private static final float[] HALF_WIDTHS = {1.0f, 1.4f, 1.82f, 2.4f};
    private static final float REACH = 12.0f;
    private static final float EPSILON = 1.0E-4f;

    private static HitShape.Inflation volume(float halfWidth) {
        return HitShapes.forFamily(FormFamily.WAVE).broadInflation(halfWidth, REACH);
    }

    @Test
    void theWaveIsTheSameShapeWhicheverWayYouRollItAboutItsFlight() {
        // The complaint, as an assertion. A crescent has a belly and therefore a direction, and
        // whichever direction that is - down, right, up-right - it is arbitrary and it is not the
        // way the wave is going. A surface of revolution has no such direction to get wrong.
        float radius = WaveGeometry.radius(1.4f);
        float depth = WaveGeometry.depth(1.4f);
        for (int step = 0; step <= 10; step++) {
            float across = step / 10.0f;
            float[] reference = ForgeWaveFront.point(radius, depth, 0.0f, across);
            float referenceRadius = (float) Math.hypot(reference[0], reference[1]);
            for (float angle = 0.0f; angle < 360.0f; angle += 17.0f) {
                float[] point = ForgeWaveFront.point(radius, depth, angle, across);
                assertEquals(referenceRadius, (float) Math.hypot(point[0], point[1]), EPSILON,
                        "the wave is a different width at " + angle + " degrees round it");
                assertEquals(reference[2], point[2], EPSILON,
                        "the wave leans toward " + angle + " degrees instead of toward its flight");
            }
        }
    }

    @Test
    void theWaveCurvesTowardWhereItIsGoing() {
        // Forward is the only axis it is allowed to bend along, and it has to actually bend: the
        // middle stands ahead of the rim, and every step out from the middle falls behind the last.
        float radius = WaveGeometry.radius(1.4f);
        float depth = WaveGeometry.depth(1.4f);
        float last = Float.MAX_VALUE;
        for (int step = 0; step <= 20; step++) {
            float across = step / 20.0f;
            float forward = ForgeWaveFront.point(radius, depth, 37.0f, across)[2];
            assertTrue(forward < last, "the wave stops falling back at " + across);
            last = forward;
        }
        assertTrue(ForgeWaveFront.point(radius, depth, 0.0f, 0.0f)[2] > depth * 0.9f,
                "the middle of the wave does not lead");
        assertEquals(0.0f, ForgeWaveFront.point(radius, depth, 0.0f, 1.0f)[2], EPSILON,
                "the rim of the wave does not trail");
    }

    @Test
    void theWaveIsCentredOnTheThingThatHits() {
        // An arc is measured from the centre of its own circle, so a crescent built at radius R put
        // every one of its pixels R blocks from the strike and none of them on it. A front is
        // measured from its own axis, which is the flight line, so this is true by construction -
        // and it is worth a test because it is the bug that started all of this.
        float radius = WaveGeometry.radius(1.4f);
        float depth = WaveGeometry.depth(1.4f);
        for (float angle = 0.0f; angle < 360.0f; angle += 29.0f) {
            float[] near = ForgeWaveFront.point(radius, depth, angle, 1.0f);
            float[] far = ForgeWaveFront.point(radius, depth, angle + 180.0f, 1.0f);
            assertEquals(0.0f, near[0] + far[0], EPSILON, "the wave hangs to one side at " + angle);
            assertEquals(0.0f, near[1] + far[1], EPSILON, "the wave hangs above or below at " + angle);
        }
    }

    @Test
    void aWaveIsDrawnNoWiderThanTheCorridorItCatchesIn() {
        for (float halfWidth : HALF_WIDTHS) {
            HitShape.Inflation volume = volume(halfWidth);
            float radius = WaveGeometry.radius(halfWidth);
            assertTrue(radius <= volume.x(), "half-width " + halfWidth + ": the wave is drawn " + radius
                    + " blocks off the flight path but only catches within " + volume.x());
            assertTrue(radius <= volume.y(), "half-width " + halfWidth + ": the wave reaches " + radius
                    + " blocks up and down and catches within " + volume.y());
        }
    }

    @Test
    void aWaveIsNoDeeperThanTheSweepItCatchesWith() {
        for (float halfWidth : HALF_WIDTHS) {
            HitShape.Inflation volume = volume(halfWidth);
            assertTrue(WaveGeometry.depth(halfWidth) <= volume.z(),
                    "half-width " + halfWidth + ": the wave bulges " + WaveGeometry.depth(halfWidth)
                            + " blocks along the flight and catches within " + volume.z());
        }
    }

    @Test
    void aWaveGrowsOutOfTheHandWithoutEverFlattening() {
        // It leaves small and opens out, and the growth must not iron the curve out of it: a wave
        // that is flat early is flat for the half of its flight the thrower is looking at.
        for (float progress = 0.0f; progress <= 1.0f; progress += 0.05f) {
            float scale = WaveGeometry.grown(progress);
            assertTrue(scale > 0.0f, "the wave has no size at progress " + progress);
            assertTrue(scale <= 1.0f, "the wave outgrows itself at progress " + progress);
            float radius = WaveGeometry.radius(1.4f) * scale;
            float depth = WaveGeometry.depth(1.4f) * scale;
            assertTrue(ForgeWaveFront.point(radius, depth, 0.0f, 0.0f)[2]
                            > ForgeWaveFront.point(radius, depth, 0.0f, 1.0f)[2],
                    "the wave went flat at progress " + progress);
        }
    }
}
