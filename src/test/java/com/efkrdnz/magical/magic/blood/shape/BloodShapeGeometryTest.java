package com.efkrdnz.magical.magic.blood.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The geometry both sides run: clipping to reach, resampling, and the projection out of the canvas
 * into the world.
 *
 * <p>The clipping tests are here because of the anti-cheese rule, which is a promise about what
 * happens to a drawing when the reach behind it changes. The projection tests are here because the
 * canvas is a map seen from above and the world is not, so three sign conventions meet in one
 * method and there is no way to check them by reading it.
 */
class BloodShapeGeometryTest {

    private static final double EPS = 1.0E-9D;

    /** A stroke from block coordinates, the way the editor would have captured it. */
    private static int[] stroke(double... uv) {
        int[] packed = new int[uv.length / 2];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = BloodShapeRules.pack(
                    BloodShapeRules.toUnits(uv[i * 2]), BloodShapeRules.toUnits(uv[i * 2 + 1]));
        }
        return packed;
    }

    private static BloodShape shapeOf(int flags, int[]... strokes) {
        return BloodShape.of(List.of(strokes), BloodShapeRules.DEFAULT_HEIGHT_PERCENT,
                BloodShapeRules.DEFAULT_SPREAD_PERCENT, flags);
    }

    // ---------------------------------------------------------------- clipping

    @Test
    void aDrawingInsideTheCanvasComesBackExactlyAsItWasDrawn() {
        BloodShape shape = shapeOf(0, stroke(0, 0, 2, 0, 2, 3));
        List<double[]> clipped = BloodShapeGeometry.clip(shape, 6.0D);

        assertEquals(1, clipped.size());
        assertArrayCloseTo(new double[] {0, 0, 2, 0, 2, 3}, clipped.get(0));
    }

    @Test
    void aDrawingEntirelyBeyondTheCanvasIsGoneRatherThanPulledIn() {
        BloodShape shape = shapeOf(0, stroke(10, 10, 15, 15));
        assertTrue(BloodShapeGeometry.clip(shape, 6.0D).isEmpty(),
                "a shape out of reach must contribute nothing, not a stub at the edge");
        assertEquals(0.0D, BloodShapeGeometry.arcLength(shape, 6.0D));
    }

    @Test
    void aStrokeCrossingTheEdgeIsCutAtItRatherThanDroppedWhole() {
        // Point-wise clipping would lose the whole crossing segment and take the part that was
        // legitimately inside with it, so shrinking reach by a block could delete a lobe.
        BloodShape shape = shapeOf(0, stroke(0, 0, 18, 0));
        List<double[]> clipped = BloodShapeGeometry.clip(shape, 6.0D);

        assertEquals(1, clipped.size());
        assertArrayCloseTo(new double[] {0, 0, 6, 0}, clipped.get(0));
        assertEquals(6.0D, BloodShapeGeometry.arcLength(shape, 6.0D), EPS);
    }

    @Test
    void aStrokeThatLeavesAndComesBackReturnsAsTwoPathsNotOneWithAChordAcross() {
        BloodShape shape = shapeOf(0, stroke(0, 0, 10, 0, 10, 2, 0, 2));
        List<double[]> clipped = BloodShapeGeometry.clip(shape, 6.0D);

        assertEquals(2, clipped.size(), "the gap outside the canvas must break the path");
        assertArrayCloseTo(new double[] {0, 0, 6, 0}, clipped.get(0));
        assertArrayCloseTo(new double[] {6, 2, 0, 2}, clipped.get(1));
    }

    @Test
    void shrinkingTheReachIgnoresThePointsOutsideItRatherThanRescalingThemIn() {
        // This assertion is the anti-cheese rule itself. Draw at maximum reach, drop the reach, and
        // every point that was inside the smaller square has to still be exactly where it was. A
        // build that normalized to the canvas would pass every other test in this file and fail
        // this one, because the drawing would come back squeezed into the new square.
        BloodShape shape = shapeOf(0, stroke(0, 0, 18, 0));

        double[] wide = BloodShapeGeometry.resample(
                BloodShapeGeometry.clip(shape, 20.0D).get(0), 0.5D);
        double[] narrow = BloodShapeGeometry.resample(
                BloodShapeGeometry.clip(shape, 6.0D).get(0), 0.5D);

        assertEquals(37, wide.length / 2, "18 blocks at half-block spacing");
        assertEquals(13, narrow.length / 2, "6 blocks at half-block spacing");
        for (int i = 0; i < narrow.length; i++) {
            assertEquals(wide[i], narrow[i], EPS,
                    "point " + (i / 2) + " moved when the reach shrank - the shape was rescaled");
        }
    }

    @Test
    void buyingTheReachBackBringsTheWholeShapeBack() {
        // The other half of the same promise: ignored is not deleted. Clipping happens at cast time
        // against the reach of the moment, so the stored drawing is never edited by a respec.
        BloodShape shape = shapeOf(0, stroke(0, 0, 18, 0));

        assertEquals(6.0D, BloodShapeGeometry.arcLength(shape, 6.0D), EPS);
        assertEquals(18.0D, BloodShapeGeometry.arcLength(shape, 20.0D), EPS,
                "the far half must come back the moment the reach does");
    }

    // ---------------------------------------------------------------- resampling

    @Test
    void resamplingKeepsBothEndsAndSpacesEverythingBetweenThemEvenly() {
        double[] even = BloodShapeGeometry.resample(new double[] {0, 0, 3, 0, 3, 4}, 1.0D);

        assertEquals(0.0D, even[0], EPS);
        assertEquals(0.0D, even[1], EPS);
        assertEquals(3.0D, even[even.length - 2], EPS, "the last point is the one that was drawn");
        assertEquals(4.0D, even[even.length - 1], EPS);

        for (int i = 0; i + 3 < even.length; i += 2) {
            double dx = even[i + 2] - even[i];
            double dy = even[i + 3] - even[i + 1];
            assertEquals(1.0D, Math.sqrt(dx * dx + dy * dy), 1.0E-6D,
                    "uneven spacing means the voxels would bunch up at the corners");
        }
    }

    @Test
    void aDegenerateLineIsHandedBackRatherThanCrashing() {
        assertEquals(0, BloodShapeGeometry.resample(null, 1.0D).length);
        assertEquals(2, BloodShapeGeometry.resample(new double[] {1, 2}, 1.0D).length);
    }

    // ---------------------------------------------------------------- projection

    @Test
    void theWorldLockedCanvasPutsNorthAtTheTopWhicheverWayTheCasterIsFacing() {
        // This is what the N/S/W/E letters promise, and it is not a special case in the code: at a
        // reading yaw of 180 the general transform collapses to exactly this.
        BloodShape shape = shapeOf(0, stroke(0, 0, 1, 0));
        assertEquals(BloodShapeRules.COMPASS_YAW_DEGREES,
                BloodShapeGeometry.readingYaw(shape, 47.0F), "a world-locked canvas ignores the caster");

        Node right = BloodShapeGeometry.toWorld(1, 0, 0, 0, BloodShapeRules.COMPASS_YAW_DEGREES, 0);
        assertEquals(1.0D, right.x(), 1.0E-9D, "canvas right is east");
        assertEquals(0.0D, right.z(), 1.0E-9D);

        Node up = BloodShapeGeometry.toWorld(0, 0, 1, 0, BloodShapeRules.COMPASS_YAW_DEGREES, 0);
        assertEquals(-1.0D, up.z(), 1.0E-9D, "canvas up is north");
        assertEquals(0.0D, up.x(), 1.0E-9D);
    }

    @Test
    void trackingYawTurnsTheWholeShapeWithTheCaster() {
        BloodShape shape = shapeOf(BloodShapeRules.FLAG_TRACK_YAW, stroke(0, 0, 1, 0));
        assertEquals(47.0F, BloodShapeGeometry.readingYaw(shape, 47.0F));

        // Yaw 0 faces south in Minecraft, so canvas forward has to come out as +Z.
        Node facingSouth = BloodShapeGeometry.toWorld(0, 0, 1, 0, 0.0F, 0.0F);
        assertEquals(1.0D, facingSouth.z(), 1.0E-9D, "forward at yaw 0 is south");

        // Ninety degrees is west.
        Node facingWest = BloodShapeGeometry.toWorld(0, 0, 1, 0, 90.0F, 0.0F);
        assertEquals(-1.0D, facingWest.x(), 1.0E-9D, "forward at yaw 90 is west");

        // And the caster's right hand while facing south points west, not east.
        Node rightHand = BloodShapeGeometry.toWorld(1, 0, 0, 0, 0.0F, 0.0F);
        assertEquals(-1.0D, rightHand.x(), 1.0E-9D, "canvas right is the caster's right, not the map's");
    }

    @Test
    void trackingPitchTipsTheFarEdgeUpWhenTheCasterLooksUp() {
        // Minecraft pitch is negative upward, so this is the sign most likely to end up backwards -
        // and backwards means the shape aims at the floor when the player aims at the sky.
        BloodShape shape = shapeOf(BloodShapeRules.FLAG_TRACK_PITCH, stroke(0, 0, 1, 0));
        assertEquals(-30.0F, BloodShapeGeometry.readingPitch(shape, -30.0F));
        assertEquals(0.0F, BloodShapeGeometry.readingPitch(shapeOf(0, stroke(0, 0, 1, 0)), -30.0F),
                "without the flag the plane stays flat");

        Node ahead = BloodShapeGeometry.toWorld(0, 0, 5, 0, BloodShapeRules.COMPASS_YAW_DEGREES, -30.0F);
        assertTrue(ahead.y() > 0.0D, "looking up must raise the far end, not lower it");
        assertEquals(2.5D, ahead.y(), 1.0E-6D, "five blocks out at thirty degrees is two and a half up");
        assertEquals(-5.0D * Math.cos(Math.toRadians(30.0D)), ahead.z(), 1.0E-6D,
                "and it foreshortens rather than stretching");

        Node behind = BloodShapeGeometry.toWorld(0, 0, 5, 0, BloodShapeRules.COMPASS_YAW_DEGREES, 30.0F);
        assertTrue(behind.y() < 0.0D, "looking down must drop it by the same amount");
    }

    @Test
    void theHeightSliderMovesThePlaneFromTheFeetToTheCrown() {
        BloodShape feet = BloodShape.of(List.of(stroke(0, 0, 1, 0)), 0, 0, 0);
        BloodShape crown = BloodShape.of(List.of(stroke(0, 0, 1, 0)), 100, 0, 0);

        assertEquals(0.0D, BloodShapeGeometry.spine(feet, 6, 0.5, 1.8, 180, 0).get(0).y(), EPS);
        assertEquals(1.8D, BloodShapeGeometry.spine(crown, 6, 0.5, 1.8, 180, 0).get(0).y(), EPS);
    }

    @Test
    void theWallStandsStraightUpUntilThePlaneTiltsAndStaysAUnitLongThroughout() {
        Node flat = BloodShapeGeometry.columnAxis(180.0F, 0.0F);
        assertEquals(0.0D, flat.x(), 1.0E-9D);
        assertEquals(1.0D, flat.y(), 1.0E-9D, "a flat plane extrudes straight up");
        assertEquals(0.0D, flat.z(), 1.0E-9D);

        for (float pitch = -90.0F; pitch <= 90.0F; pitch += 15.0F) {
            Node axis = BloodShapeGeometry.columnAxis(37.0F, pitch);
            double length = Math.sqrt(axis.x() * axis.x() + axis.y() * axis.y() + axis.z() * axis.z());
            assertEquals(1.0D, length, 1.0E-6D, "the extrusion axis must stay a unit at pitch " + pitch);
        }
    }

    @Test
    void rankIsDistanceFromTheMiddleSoBothSidesFormInTheSameOrder() {
        // The client delays each voxel by its rank and the server advances a radius through the same
        // number. If these ever stopped agreeing, the damage would arrive somewhere the blood
        // visibly is not.
        BloodShape shape = shapeOf(0, stroke(0, 0, 5, 0));
        List<Node> spine = BloodShapeGeometry.spine(shape, 6, 1.0, 1.8, 180, 0);

        assertEquals(6, spine.size());
        double previous = -1.0D;
        for (Node node : spine) {
            assertTrue(node.rank() > previous, "a straight stroke out from the middle only gets further");
            previous = node.rank();
        }
        assertEquals(0.0D, spine.get(0).rank(), EPS, "the first point is the caster");
        assertEquals(5.0D, BloodShapeGeometry.maxRank(spine), EPS);
    }

    @Test
    void anEmptyShapeProducesNoSpineRatherThanOneNodeAtTheFeet() {
        assertTrue(BloodShapeGeometry.spine(BloodShape.EMPTY, 6, 0.5, 1.8, 180, 0).isEmpty());
        assertTrue(BloodShapeGeometry.spine(null, 6, 0.5, 1.8, 180, 0).isEmpty());
        assertEquals(0.0D, BloodShapeGeometry.maxRank(List.of()));
    }

    // ---------------------------------------------------------------- expansion

    @Test
    void expandingASpineBuildsAWallWithoutOverrunningWhatItWasGiven() {
        double[] spine = BloodShapeGeometry.resample(new double[] {0, 0, 4, 0}, 0.25D);
        float[] out = new float[3 * 4096];

        int written = BloodShapeGeometry.expand(spine, 1.0D, 0.08D, 0.25D, 7, 4096, out);
        assertTrue(written > 0, "a four block stroke with a one block wall is not empty");
        assertTrue(written <= BloodShapeGeometry.voxelDemand(spine.length / 2, 1.0D, 0.2D, 0.25D),
                "the frayed top may drop voxels but must never invent them");

        for (int i = 0; i < written; i++) {
            assertTrue(out[i * 3 + 1] >= -0.05F && out[i * 3 + 1] <= 1.05F,
                    "every voxel belongs between the plane and the top of the wall");
        }
    }

    @Test
    void aNegativeWallHangsBelowThePlaneInsteadOfStandingOnIt() {
        // The spread slider's second job. Getting this wrong does not fail anywhere - it silently
        // builds the shape the player asked to hang downward standing upward instead.
        double[] spine = BloodShapeGeometry.resample(new double[] {0, 0, 2, 0}, 0.25D);
        float[] out = new float[3 * 4096];

        int written = BloodShapeGeometry.expand(spine, -1.0D, 0.08D, 0.25D, 7, 4096, out);
        assertTrue(written > 0);
        double lowest = 0.0D;
        for (int i = 0; i < written; i++) {
            assertTrue(out[i * 3 + 1] <= 0.05F, "a downward wall must not rise above its plane");
            lowest = Math.min(lowest, out[i * 3 + 1]);
        }
        assertTrue(lowest < -0.7D, "and it must actually reach down, not just sit flat");
    }

    @Test
    void theWallIsFilledAcrossItsThicknessRatherThanScatteredThroughIt() {
        // The gap bug. One jittered cube per column covers a sliver of a band many times its own
        // width, and the eye reads the rest of the band as holes; filling it is what makes the
        // blood read as liquid. Asserted as lanes actually occupied, since that is the symptom.
        double thickness = 0.2D;
        double pitch = 0.05D;
        double[] spine = BloodShapeGeometry.resample(new double[] {0, 0, 1, 0}, pitch);
        float[] out = new float[3 * 8192];

        int written = BloodShapeGeometry.expand(spine, 0.2D, thickness, pitch, 3, 8192, out);
        assertTrue(written > 0);

        // The stroke runs along u, so its normal is v, and the lanes spread along v.
        boolean[] lane = new boolean[16];
        for (int i = 0; i < written; i++) {
            double across = out[i * 3 + 2];
            assertTrue(Math.abs(across) <= thickness + pitch,
                    "a voxel escaped the band it was meant to fill: " + across);
            int bucket = (int) ((across + thickness) / (thickness * 2.0D) * (lane.length - 1));
            lane[Math.max(0, Math.min(lane.length - 1, bucket))] = true;
        }
        int occupied = 0;
        for (boolean used : lane) {
            occupied += used ? 1 : 0;
        }
        assertTrue(occupied >= 8, "only " + occupied + " of 16 slices across the band were filled");
    }

    @Test
    void theCapIsRespectedRatherThanTrustedToBeBigEnough() {
        double[] spine = BloodShapeGeometry.resample(new double[] {0, 0, 40, 0}, 0.0625D);
        float[] out = new float[3 * 64];

        assertEquals(64, BloodShapeGeometry.expand(spine, 3.0D, 0.08D, 0.0625D, 1, 4096, out),
                "the output array is a cap in its own right");
        assertEquals(10, BloodShapeGeometry.expand(spine, 3.0D, 0.08D, 0.0625D, 1, 10, out),
                "and so is the one the caller asked for");
    }

    @Test
    void expansionIsTheSameEveryTimeForTheSameSeed() {
        // The voxels are placed by hash rather than stored, which is what makes the motion smooth
        // without any per-frame state. That only works if the hash is stable.
        double[] spine = BloodShapeGeometry.resample(new double[] {0, 0, 3, 1}, 0.25D);
        float[] first = new float[3 * 512];
        float[] second = new float[3 * 512];

        int a = BloodShapeGeometry.expand(spine, 0.75D, 0.06D, 0.25D, 42, 512, first);
        int b = BloodShapeGeometry.expand(spine, 0.75D, 0.06D, 0.25D, 42, 512, second);

        assertEquals(a, b);
        for (int i = 0; i < a * 3; i++) {
            assertEquals(first[i], second[i], "the same seed must place the same voxel");
        }
    }

    private static void assertArrayCloseTo(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length, "polyline length");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1.0E-6D, "at index " + i);
        }
    }
}
