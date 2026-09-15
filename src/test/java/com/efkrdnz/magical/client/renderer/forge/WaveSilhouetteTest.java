package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.entity.forge.HitShape;
import com.efkrdnz.magical.entity.forge.HitShapes;
import com.efkrdnz.magical.forge.FormFamily;
import org.junit.jupiter.api.Test;

/**
 * Where a thrown wave is drawn, against where it actually catches people.
 *
 * <p>It was a ring band sitting between one and a half and three blocks out from the strike,
 * rolled forty degrees about the aim - so nothing at all was drawn on the thing that hits, and
 * what you could see hung up and to one side of the flight path. It read as a moon tilted up-right
 * rather than as a blade thrown forward, which is exactly what it was.
 *
 * <p>These measure the drawn steel against {@link HitShapes#forFamily} for the WAVE family, so the
 * picture cannot drift from the volume again. The glow around the blade and the wake behind it are
 * deliberately not measured: a glow is light, and a wake is where the wave has already been, which
 * the swept hit box covers anyway.
 */
class WaveSilhouetteTest {

    /** Light, heavy, and a couple of tempered weapons either side of them. */
    private static final float[] HALF_WIDTHS = {1.0f, 1.4f, 1.82f, 2.4f};
    private static final float REACH = 12.0f;

    private static final int ALONG_STEPS = 40;
    private static final int ACROSS_STEPS = 8;

    /** The axis-aligned box the drawn steel of one wave occupies, in the strike's local frame. */
    private record Box(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        float centreX() {
            return (minX + maxX) * 0.5f;
        }

        float centreY() {
            return (minY + maxY) * 0.5f;
        }
    }

    private static Box steel(float halfWidth) {
        Sweep sweep = WaveGeometry.crescent(halfWidth);
        float lift = WaveGeometry.lift(halfWidth);
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i <= ALONG_STEPS; i++) {
            for (int j = 0; j <= ACROSS_STEPS; j++) {
                float t = i / (float) ALONG_STEPS;
                float out = j / (float) ACROSS_STEPS;
                for (int side = -1; side <= 1; side += 2) {
                    float[] point = sweep.at(t, out, side * ForgeRibbon.halfThickness(sweep, t, out));
                    float[] world = {point[0], point[1] + lift, point[2]};
                    for (int axis = 0; axis < 3; axis++) {
                        low[axis] = Math.min(low[axis], world[axis]);
                        high[axis] = Math.max(high[axis], world[axis]);
                    }
                }
            }
        }
        return new Box(low[0], high[0], low[1], high[1], low[2], high[2]);
    }

    private static HitShape.Inflation volume(float halfWidth) {
        return HitShapes.forFamily(FormFamily.WAVE).broadInflation(halfWidth, REACH);
    }

    @Test
    void aWaveIsDrawnNoWiderThanTheCorridorItCatchesIn() {
        for (float halfWidth : HALF_WIDTHS) {
            Box box = steel(halfWidth);
            HitShape.Inflation volume = volume(halfWidth);
            assertTrue(box.maxX() <= volume.x(),
                    "half-width " + halfWidth + ": the wave is drawn " + box.maxX()
                            + " blocks right of the flight path but only catches within " + volume.x());
            assertTrue(-box.minX() <= volume.x(),
                    "half-width " + halfWidth + ": the wave is drawn " + -box.minX()
                            + " blocks left of the flight path but only catches within " + volume.x());
        }
    }

    @Test
    void aWaveIsDrawnNoTallerThanItCatches() {
        for (float halfWidth : HALF_WIDTHS) {
            Box box = steel(halfWidth);
            HitShape.Inflation volume = volume(halfWidth);
            assertTrue(Math.max(box.maxY(), -box.minY()) <= volume.y(),
                    "half-width " + halfWidth + ": the wave reaches "
                            + Math.max(box.maxY(), -box.minY()) + " blocks up or down and catches within "
                            + volume.y());
        }
    }

    @Test
    void aWaveSitsOnTheThingThatHitsRatherThanHangingOffIt() {
        // The whole bug, as a measurement. A crescent of radius R centred on the origin puts every
        // one of its pixels R blocks away from the strike and none of them on it.
        for (float halfWidth : HALF_WIDTHS) {
            Box box = steel(halfWidth);
            HitShape.Inflation volume = volume(halfWidth);
            assertTrue(Math.abs(box.centreX()) < volume.x() * 0.25f,
                    "half-width " + halfWidth + ": the wave hangs " + box.centreX() + " blocks to one side");
            assertTrue(Math.abs(box.centreY()) < volume.y() * 0.25f,
                    "half-width " + halfWidth + ": the wave hangs " + box.centreY() + " blocks above or below");
        }
    }

    @Test
    void aWaveLeadsWithItsMiddleAndTrailsItsEnds() {
        // What makes it read as thrown rather than as a decal facing you: the belly of the crescent
        // is further along the flight than either horn, so the shape itself points where it is going.
        for (float halfWidth : HALF_WIDTHS) {
            Sweep sweep = WaveGeometry.crescent(halfWidth);
            float belly = sweep.at(0.5f, 0.5f)[2];
            float left = sweep.at(0.0f, 0.5f)[2];
            float right = sweep.at(1.0f, 0.5f)[2];
            assertTrue(belly - Math.max(left, right) > halfWidth * 0.1f,
                    "half-width " + halfWidth + ": the wave is flat - belly at " + belly
                            + ", ends at " + left + " and " + right);
        }
    }

    @Test
    void aWaveIsNoDeeperThanTheSweepItCatchesWith() {
        for (float halfWidth : HALF_WIDTHS) {
            Box box = steel(halfWidth);
            HitShape.Inflation volume = volume(halfWidth);
            assertTrue(Math.max(box.maxZ(), -box.minZ()) <= volume.z(),
                    "half-width " + halfWidth + ": the wave is bowed "
                            + Math.max(box.maxZ(), -box.minZ()) + " blocks along the flight and catches within "
                            + volume.z());
        }
    }

    @Test
    void aWaveStillLeadsWithItsMiddleWhileItIsStillGrowing() {
        // It is drawn small at the hand and grows out to full size, and the growth must not flatten
        // the bow - a wave that is flat for the first half of its flight is flat for the half the
        // player is actually looking at it.
        for (float progress = 0.05f; progress <= 1.0f; progress += 0.05f) {
            Sweep growing = WaveGeometry.head(1.4f, progress);
            float belly = growing.at(0.5f, 0.5f)[2];
            float end = Math.max(growing.at(0.0f, 0.5f)[2], growing.at(1.0f, 0.5f)[2]);
            assertTrue(belly - end > 0.0f, "the wave went flat at progress " + progress);
        }
    }
}
