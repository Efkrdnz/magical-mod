package com.efkrdnz.magical.client.renderer.fx.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Blood leaving a body for the ground: the harvest flight run backwards.
 *
 * <p>A fed pool is drawn drop by drop as the drops are born, each falling from the body that
 * bleeds to its own spot in the disc. What is pinned is the seam at the far end - a drop lands
 * exactly where the pooled branch would draw it, at full size - and that no drop is born before
 * its turn, after the feed has stopped, or all at once.
 */
class BloodBleedMotionTest {

    private static final VoxelStyle STYLE = VoxelStyle.HARVEST;
    private static final int SEED = 9;
    private static final int[] INDICES = {0, 1, 7, 31, 64, 95};
    private static final float CHEST_X = 0.3F;
    private static final float CHEST_Y = 1.2F;
    private static final float CHEST_Z = -0.2F;

    /** Bound on how far one drop may move in a hundredth of a tick, in blocks. */
    private static final float MAX_STEP = 0.08F;

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void dropsAreBornInOrderAndSpreadOverTheWholeFeed() {
        int cubes = 96;
        float feed = 140.0F;
        float previous = -1.0F;
        for (int i = 0; i < cubes; i++) {
            float born = BloodBleedMotion.bornAt(i, cubes, feed);
            assertTrue(born >= previous, "drop " + i + " is born before drop " + (i - 1));
            assertTrue(born >= 0.0F && born < feed, "drop " + i + " is born at " + born + ", outside the feed");
            previous = born;
        }
        assertEquals(0.0F, BloodBleedMotion.bornAt(0, cubes, feed), "the first drop falls at once");
        assertTrue(BloodBleedMotion.bornAt(cubes - 1, cubes, feed) > feed * 0.9F,
                "the last drop is born near the end, not bunched at the start");
    }

    @Test
    void noDropIsBornBeforeItsTurnOrAfterTheFeedStopped() {
        assertFalse(BloodBleedMotion.born(50.0F, 49.0F, Float.MAX_VALUE), "its turn has not come");
        assertTrue(BloodBleedMotion.born(50.0F, 50.0F, Float.MAX_VALUE));
        assertTrue(BloodBleedMotion.born(50.0F, 100.0F, 60.0F), "born before the feed stopped");
        assertFalse(BloodBleedMotion.born(50.0F, 100.0F, 40.0F), "the feed stopped before its turn: never born");
    }

    @Test
    void aDropLandsExactlyOnItsPoolSpotAtFullSize() {
        float[] spot = new float[3];
        float[] out = new float[3];
        float[] chest = {CHEST_X, CHEST_Y, CHEST_Z};
        for (int index : INDICES) {
            BloodHarvestMotion.poolSpot(STYLE, index, SEED, spot);
            BloodBleedMotion.fall(STYLE, index, SEED, 1.0F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, out);
            assertTrue(distance(spot, out) < 1.0E-6F, "drop " + index + " lands " + distance(spot, out) + " from its spot");
            assertEquals(0.0F, BloodBleedMotion.forming(STYLE.timing(), 1.0F, BloodBleedMotion.FALL_TICKS),
                    "a landed drop is whole");
            BloodBleedMotion.fall(STYLE, index, SEED, 0.0F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, out);
            assertTrue(distance(chest, out) <= BloodHarvestMotion.CHEST_SPREAD + 1.0E-6F,
                    "drop " + index + " is born " + distance(chest, out) + " from the body");
            assertEquals(1.0F, BloodBleedMotion.forming(STYLE.timing(), 0.0F, BloodBleedMotion.FALL_TICKS),
                    "a drop just born has no size yet - it grows out of the body");
        }
    }

    @Test
    void aDropNeverJumpsAsItFalls() {
        float[] spot = new float[3];
        float[] previous = new float[3];
        float[] now = new float[3];
        for (int index : INDICES) {
            BloodHarvestMotion.poolSpot(STYLE, index, SEED, spot);
            BloodBleedMotion.fall(STYLE, index, SEED, 0.0F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, previous);
            int steps = Math.round(BloodBleedMotion.FALL_TICKS * 100.0F);
            for (int step = 1; step <= steps; step++) {
                float progress = BloodBleedMotion.fallProgress(0.0F, step / 100.0F);
                BloodBleedMotion.fall(STYLE, index, SEED, progress, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, now);
                float moved = distance(previous, now);
                assertTrue(moved < MAX_STEP, "drop " + index + " jumped " + moved + " at step " + step);
                System.arraycopy(now, 0, previous, 0, 3);
            }
            assertEquals(1.0F, BloodBleedMotion.fallProgress(0.0F, BloodBleedMotion.FALL_TICKS));
            assertEquals(0.0F, BloodBleedMotion.fallProgress(10.0F, 4.0F), "nothing moves before birth");
        }
    }
}
