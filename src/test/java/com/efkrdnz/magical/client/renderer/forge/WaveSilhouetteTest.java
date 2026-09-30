package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.entity.forge.HitShape;
import com.efkrdnz.magical.entity.forge.HitShapes;
import com.efkrdnz.magical.forge.FormFamily;
import org.junit.jupiter.api.Test;

/**
 * The shape of a thrown wave: a slash let go of, bending toward where it is going.
 *
 * <p>It has been wrong three times. A ring band hanging off the flight path, tipped up and to the
 * right; a crescent bowed belly-down; and a dome about the flight line, which the thrower saw as a
 * circle. What it is meant to be is the swing's own blade, thrown: its belly leads the flight, its
 * horns trail, and it sits on the line it flies along. These tests say so in numbers, and measure it
 * against {@code HitShapes} so the picture cannot outgrow the volume that actually catches people.
 */
class WaveSilhouetteTest {

    /**
     * Light, heavy, a couple of tempered weapons either side of them, and the widest a heavy wave
     * with three copies of REACH makes: REACH widens a wave, and there is no ceiling on it.
     */
    private static final float[] HALF_WIDTHS = {1.0f, 1.4f, 1.82f, 2.4f, 3.4f, 4.4f};
    private static final float REACH = 12.0f;
    private static final float EPSILON = 1.0E-4f;
    private static final int STEPS = 40;

    private static HitShape.Inflation volume(float halfWidth) {
        return HitShapes.forFamily(FormFamily.WAVE).broadInflation(halfWidth, REACH);
    }

    @Test
    void theBellyLeadsAndTheHornsTrail() {
        // The complaint that started this: a thrown slash is a crescent flying belly-first, not a
        // disc. The middle of the lip is the furthest-forward point on the blade and both horns are
        // well behind it.
        Sweep blade = WaveGeometry.blade(1.4f, 1.0f);
        float[] belly = WaveGeometry.point(blade, 0.5f, 1.0f);
        for (int i = 0; i <= STEPS; i++) {
            for (int j = 0; j <= 4; j++) {
                assertTrue(WaveGeometry.point(blade, i / (float) STEPS, j / 4.0f)[2] <= belly[2] + EPSILON,
                        "the blade leads with something other than its belly");
            }
        }
        float[] horn = WaveGeometry.point(blade, 0.0f, 1.0f);
        assertTrue(belly[2] - horn[2] > WaveGeometry.radius(1.4f) * 0.4f, "the horns barely trail: it is a bar");
    }

    @Test
    void theCrescentLiesAlongItsFlightAndNeverStandsUp() {
        // Swept down behind it, the horns stood the crescent up as an arch facing the sky. It lies
        // in the plane of the swing that threw it: rolled about its flight, never pitched off it,
        // so the belly and both horns sit on one plane that contains the flight line.
        Sweep blade = WaveGeometry.blade(1.4f, 1.0f);
        float[] belly = WaveGeometry.point(blade, 0.5f, 1.0f);
        float[] left = WaveGeometry.point(blade, 0.0f, 1.0f);
        float[] right = WaveGeometry.point(blade, 1.0f, 1.0f);
        assertEquals(0.0f, belly[0], EPSILON, "the belly is off the flight line");
        assertEquals(0.0f, belly[1], EPSILON, "the belly is off the flight line");
        assertEquals(0.0f, left[1] + right[1], EPSILON, "the horns fall or rise together: it is pitched, not rolled");
        assertEquals(left[2], right[2], EPSILON, "one horn trails further than the other");
    }

    @Test
    void theLipIsTheSameOnBothSides() {
        // The blade itself is thickest a third of the way along, as a swung blade is; its edge is
        // a true crescent, so neither horn flies further out, higher or further back.
        Sweep blade = WaveGeometry.blade(1.4f, 1.0f);
        for (int i = 0; i <= STEPS; i++) {
            float t = i / (float) STEPS;
            float[] left = WaveGeometry.point(blade, t, 1.0f);
            float[] right = WaveGeometry.point(blade, 1.0f - t, 1.0f);
            assertEquals(-left[0], right[0], EPSILON, "the wave is wider on one side at " + t);
            assertEquals(-left[1], right[1], EPSILON, "the wave is rolled unevenly at " + t);
            assertEquals(left[2], right[2], EPSILON, "one horn trails further than the other at " + t);
        }
    }

    @Test
    void theWaveStraddlesTheLineItFliesAlong() {
        // An arc is measured from the centre of its own circle, which put a crescent built at
        // radius R a whole R off the strike. This one is framed onto the strike: as much of it
        // above the flight line as below, as much ahead as behind.
        for (float halfWidth : HALF_WIDTHS) {
            float[] box = bounds(WaveGeometry.blade(halfWidth, 1.0f));
            // float error grows with the blade, so the tolerance is a fraction of its size
            float tolerance = EPSILON * halfWidth * 10.0f;
            assertEquals(0.0f, box[2] + box[3], tolerance, "half-width " + halfWidth + " hangs above or below");
            assertEquals(0.0f, box[4] + box[5], tolerance, "half-width " + halfWidth + " stands ahead or behind");
            assertEquals(0.0f, box[0] + box[1], tolerance, "half-width " + halfWidth + " hangs to one side");
        }
    }

    @Test
    void aWaveIsDrawnInsideTheVolumeItCatchesWith() {
        for (float halfWidth : HALF_WIDTHS) {
            HitShape.Inflation volume = volume(halfWidth);
            float[] box = bounds(WaveGeometry.blade(halfWidth, 1.0f));
            assertTrue(box[1] <= volume.x() + EPSILON, "half-width " + halfWidth + ": drawn " + box[1]
                    + " blocks off the flight path but catches within " + volume.x());
            assertTrue(box[3] <= volume.y() + EPSILON, "half-width " + halfWidth + ": drawn " + box[3]
                    + " blocks up and down but catches within " + volume.y());
            assertTrue(box[5] <= volume.z() + EPSILON, "half-width " + halfWidth + ": drawn " + box[5]
                    + " blocks along the flight but catches within " + volume.z());
        }
    }

    @Test
    void aWiderWaveIsALargerSlash() {
        float last = 0.0f;
        for (float halfWidth : HALF_WIDTHS) {
            float[] box = bounds(WaveGeometry.blade(halfWidth, 1.0f));
            assertTrue(box[1] > last, "half-width " + halfWidth + " draws no larger a slash than the one before");
            last = box[1];
        }
    }

    @Test
    void aWaveGrowsOutOfTheHandWithoutEverFlattening() {
        for (float progress = 0.0f; progress <= 1.0f; progress += 0.05f) {
            float scale = WaveGeometry.grown(progress);
            assertTrue(scale > 0.0f, "the wave has no size at progress " + progress);
            assertTrue(scale <= 1.0f, "the wave outgrows itself at progress " + progress);
            Sweep blade = WaveGeometry.blade(1.4f, scale);
            assertTrue(WaveGeometry.point(blade, 0.5f, 1.0f)[2] > WaveGeometry.point(blade, 0.0f, 1.0f)[2],
                    "the wave went flat at progress " + progress);
        }
    }

    /** {min x, max x, min y, max y, min z, max z} over the blade's drawn surface. */
    private static float[] bounds(Sweep blade) {
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE};
        for (int i = 0; i <= STEPS; i++) {
            for (int j = 0; j <= 4; j++) {
                float[] p = WaveGeometry.point(blade, i / (float) STEPS, j / 4.0f);
                for (int axis = 0; axis < 3; axis++) {
                    box[axis * 2] = Math.min(box[axis * 2], p[axis]);
                    box[axis * 2 + 1] = Math.max(box[axis * 2 + 1], p[axis]);
                }
            }
        }
        return box;
    }
}
