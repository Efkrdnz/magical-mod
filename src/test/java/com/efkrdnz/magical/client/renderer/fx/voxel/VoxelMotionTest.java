package com.efkrdnz.magical.client.renderer.fx.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * "They move smoothly with no stutter" as an assertion rather than a hope.
 *
 * <p>Smoothness here is a property of the arithmetic: every term is a continuous function of the
 * age it is handed, and that age already carries the partial tick, so there is no stored previous
 * position to fall out of step with. What can still break it is a discontinuity - a term that
 * switches on instead of fading in, or two ramps that fail to meet at the same value. This walks
 * the whole lifetime in hundredths of a tick and refuses any step a frame could show as a pop.
 */
class VoxelMotionTest {

    private static final VoxelStyle STYLE = VoxelStyle.STRIKE;
    private static final float FORM_TICKS = 9.0F;
    private static final float LIFE = 40.0F;
    private static final int SEED = 21;

    private static final float TARGET_X = 4.0F;
    private static final float TARGET_Y = 1.6F;
    private static final float TARGET_Z = -3.0F;

    /** Bound on how far one voxel may move in a hundredth of a tick, in blocks. */
    private static final float MAX_STEP = 0.08F;

    private static float[] positionAt(int index, float rank, float age) {
        float delay = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, rank, index, SEED);
        float progress = VoxelMotion.progress(STYLE.timing(), delay, age);
        float[] out = new float[3];
        VoxelMotion.place(STYLE, index, SEED, age, progress,
                TARGET_X * rank, TARGET_Y, TARGET_Z * rank, 0.0F, 1.0F, 0.0F, out);
        return out;
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void aVoxelNeverJumpsBetweenOneInstantAndTheNext() {
        for (int index : new int[] {0, 7, 130, 977}) {
            for (float rank : new float[] {0.0F, 0.25F, 0.7F, 1.0F}) {
                float[] previous = positionAt(index, rank, 0.0F);
                for (int step = 1; step <= 4000; step++) {
                    float age = step / 100.0F;
                    float[] now = positionAt(index, rank, age);
                    float moved = distance(previous, now);
                    assertTrue(moved < MAX_STEP, "voxel " + index + " at rank " + rank
                            + " jumped " + moved + " blocks at age " + age);
                    previous = now;
                }
            }
        }
    }

    @Test
    void theCubeNeverJumpsInSizeEither() {
        // Growth, arrival and dissolve all drive the same half extent, so their seams have to meet
        // as cleanly as the path's do. Fading is done by shrinking, which makes this the fade test.
        for (int index : new int[] {3, 411}) {
            float rank = index == 3 ? 0.1F : 0.95F;
            float delay = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, rank, index, SEED);
            float previous = 0.0F;
            for (int step = 0; step <= 4000; step++) {
                float age = step / 100.0F;
                float erosion = VoxelMotion.erosion(STYLE.timing(), rank, delay, age, LIFE);
                float half = VoxelMotion.halfExtent(STYLE, 1.0F / 16.0F, index, SEED, age, delay,
                        erosion, 1.0F);
                assertTrue(Math.abs(half - previous) < 0.01F,
                        "half extent jumped from " + previous + " to " + half + " at age " + age);
                previous = half;
            }
        }
    }

    @Test
    void theFieldFormsFromTheMiddleOutwards() {
        // The user's "progressively forming to the direction from middle to outwards": a voxel that
        // belongs nearer the caster must always start moving before one that belongs further out.
        for (int index = 0; index < 64; index++) {
            float near = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, 0.0F, index, SEED);
            float far = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, 1.0F, index, SEED);
            assertTrue(near < far, "voxel " + index + " leaves out of order: " + near + " vs " + far);
        }
    }

    @Test
    void theTipsDrainFirstOnTheWayOut() {
        // Mirroring the way they arrived last, so the field retreats toward the caster rather than
        // vanishing all over at once.
        float age = LIFE - STYLE.timing().dissolve();
        float tip = VoxelMotion.erosion(STYLE.timing(), 1.0F, 0.0F, age, LIFE);
        float root = VoxelMotion.erosion(STYLE.timing(), 0.0F, 0.0F, age, LIFE);
        assertTrue(tip > root, "the tip should be further gone than the root: " + tip + " vs " + root);
    }

    @Test
    void aVoxelIsWholeOnceItHasArrivedAndGoneByTheEnd() {
        float delay = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, 0.5F, 11, SEED);
        assertEquals(1.0F, VoxelMotion.erosion(STYLE.timing(), 0.5F, delay, delay, LIFE), 1.0E-6F);
        assertEquals(0.0F, VoxelMotion.erosion(STYLE.timing(), 0.5F, delay,
                delay + STYLE.timing().materialise(), LIFE), 1.0E-6F);
        assertEquals(1.0F, VoxelMotion.erosion(STYLE.timing(), 0.5F, delay, LIFE, LIFE), 1.0E-6F);
    }

    @Test
    void nothingIsLeftHangingWhenTheFieldIsDone() {
        // The whole field must reach zero size, at every rank, or cubes stay in the air forever.
        for (int index = 0; index < 200; index++) {
            float rank = index / 199.0F;
            float delay = VoxelMotion.delayFor(STYLE.timing(), FORM_TICKS, rank, index, SEED);
            float erosion = VoxelMotion.erosion(STYLE.timing(), rank, delay, LIFE, LIFE);
            float half = VoxelMotion.halfExtent(STYLE, 1.0F / 16.0F, index, SEED, LIFE, delay,
                    erosion, 1.0F);
            assertEquals(0.0F, half, 1.0E-6F, "voxel " + index + " is still drawn at the end");
        }
    }

    @Test
    void theDistanceCutIsStableAndMonotonic() {
        assertEquals(1.0F, VoxelMotion.lodKeep(0.0D));
        assertEquals(1.0F, VoxelMotion.lodKeep(16.0D));
        assertEquals(0.0F, VoxelMotion.lodKeep(64.0D));
        assertEquals(0.0F, VoxelMotion.lodKeep(200.0D));
        // Deliberately not FxBudget.lodForDistance, which returns exactly zero past forty-eight
        // blocks while these entities go on rendering to two hundred and fifty-six.
        assertTrue(VoxelMotion.lodKeep(48.0D) > 0.0F, "the field must survive past 48 blocks");

        float previous = 1.0F;
        for (int step = 0; step <= 800; step++) {
            float keep = VoxelMotion.lodKeep(step / 10.0D);
            assertTrue(keep <= previous, "keep rose with distance at " + (step / 10.0D));
            previous = keep;
        }
    }

    @Test
    void theDistanceCutQuantizesSoItChangesRarely() {
        // Sixteen steps, so walking a few centimetres cannot reshuffle hundreds of cubes.
        for (int step = 0; step <= 800; step++) {
            float keep = VoxelMotion.lodKeep(step / 10.0D);
            assertEquals(Math.round(keep * 16.0F), keep * 16.0F, 1.0E-4F);
        }
    }

    @Test
    void voxelsOnTheDistanceBoundaryShrinkRatherThanPop() {
        float keep = 0.5F;
        assertEquals(1.0F, VoxelMotion.lodEdge(0.0F, keep), 1.0E-6F);
        assertEquals(0.0F, VoxelMotion.lodEdge(0.9F, keep), 1.0E-6F);
        float edge = VoxelMotion.lodEdge(keep - 0.03F, keep);
        assertTrue(edge > 0.0F && edge < 1.0F, "the boundary should be a ramp, not a step: " + edge);
        // Everything survives once nothing is being cut, whatever a voxel's key happens to be.
        assertEquals(1.0F, VoxelMotion.lodEdge(0.99F, 1.0F), 1.0E-6F);
    }

    @Test
    void aCubeAlwaysShowsAtLeastOneFaceAndNeverMoreThanThree() {
        float half = 0.03F;
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    int mask = VoxelEmitter.faceMask(0.0F, 0.0F, 0.0F, half,
                            x * half, y * half, z * half);
                    int faces = Integer.bitCount(mask);
                    assertTrue(faces >= 1, "a cube vanished from " + x + "," + y + "," + z);
                    boolean inside = Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1;
                    assertTrue(inside || faces <= 3,
                            "too many faces (" + faces + ") from " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void twoTouchingCubesNeverBothDrawTheFaceTheyShare() {
        // The reason the mask removes z-fighting rather than merely halving the quad count: the
        // tests for the two sides of an axis are exactly opposite.
        float half = 0.03F;
        float camX = 5.0F;
        int left = VoxelEmitter.faceMask(0.0F, 0.0F, 0.0F, half, camX, 0.0F, 0.0F);
        int right = VoxelEmitter.faceMask(half * 2.0F, 0.0F, 0.0F, half, camX, 0.0F, 0.0F);
        // Bit 0 is +X and bit 1 is -X. The left cube's +X and the right cube's -X are the same
        // surface, and only one of the two may claim it.
        assertTrue((left & 1) == 0 || (right & (1 << 1)) == 0,
                "both cubes drew the seam between them");
    }
}
