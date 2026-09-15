package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import org.junit.jupiter.api.Test;

/**
 * The shape of a forged blade, in numbers.
 *
 * <p>Every strike in the school is built from one arc of blade, and until now that arc was a sheet
 * of paper: {@code planar} lifted two in-plane coordinates into local space and left the third axis
 * at zero, so the whole ribbon lay in one plane and had no silhouette at all edge-on. A player who
 * was not looking straight at a swing saw nothing. These pin the cross-section that fixes it, and
 * the last test is the complaint itself: a blade must take up room in all three axes.
 */
class ForgeRibbonTest {

    private static final float EPSILON = 1.0E-5f;

    private static Sweep sweep(Plane plane) {
        return new Sweep(plane, 3.5f, 0.9f, -75.0f, 75.0f);
    }

    @Test
    void theCrossSectionIsSharpAtBothLipsAndThickestOnTheSpine() {
        assertEquals(0.0f, ForgeRibbon.crossSection(0.0f), EPSILON, "the inner lip must come to an edge");
        assertEquals(0.0f, ForgeRibbon.crossSection(1.0f), EPSILON, "the cutting edge must come to an edge");
        assertEquals(1.0f, ForgeRibbon.crossSection(0.5f), EPSILON, "the spine must be the full thickness");
        assertTrue(ForgeRibbon.crossSection(0.25f) > 0.0f, "a blade is solid between its lips");
        assertTrue(ForgeRibbon.crossSection(0.75f) > 0.0f, "a blade is solid between its lips");
    }

    @Test
    void theCrossSectionIsSymmetricAboutTheSpine() {
        for (float out = 0.0f; out <= 0.5f; out += 0.05f) {
            assertEquals(ForgeRibbon.crossSection(out), ForgeRibbon.crossSection(1.0f - out), EPSILON,
                    "the blade is lopsided at " + out);
        }
    }

    @Test
    void theCrossSectionNeverLeavesTheBlade() {
        // Fed a value off either end - a trail copy scaled past its lip, a rounding slip - it must
        // clamp rather than go negative, which would turn the solid inside out.
        assertEquals(0.0f, ForgeRibbon.crossSection(-0.4f), EPSILON);
        assertEquals(0.0f, ForgeRibbon.crossSection(1.4f), EPSILON);
    }

    @Test
    void aZeroOffsetPointIsExactlyWhereItAlwaysWas() {
        // The old two-argument call is the new one with no thickness, so every form that has not
        // been moved onto the solid yet keeps drawing precisely what it drew before.
        for (Plane plane : Plane.values()) {
            float[] flat = sweep(plane).at(0.3f, 0.7f);
            float[] offset = sweep(plane).at(0.3f, 0.7f, 0.0f);
            assertEquals(flat[0], offset[0], EPSILON, plane + " x moved");
            assertEquals(flat[1], offset[1], EPSILON, plane + " y moved");
            assertEquals(flat[2], offset[2], EPSILON, plane + " z moved");
        }
    }

    @Test
    void theThicknessGoesOnTheAxisThePlaneDoesNotUse() {
        // FORWARD spans y and z, so it thickens along x; UPRIGHT spans x and y, so along z;
        // GROUND spans x and z, so along y. Put it on an axis the plane already spans and the
        // blade shears along its own face instead of gaining a side.
        assertArrayEqualish(new float[] {0.6f, 0.0f, 0.0f}, ForgeRibbon.planar(Plane.FORWARD, 0.0f, 0.0f, 0.6f));
        assertArrayEqualish(new float[] {0.0f, 0.0f, 0.6f}, ForgeRibbon.planar(Plane.UPRIGHT, 0.0f, 0.0f, 0.6f));
        assertArrayEqualish(new float[] {0.0f, 0.6f, 0.0f}, ForgeRibbon.planar(Plane.GROUND, 0.0f, 0.0f, 0.6f));
    }

    @Test
    void aBladeTakesUpRoomInAllThreeAxes() {
        // The complaint, as an assertion. Walk the whole solid - along the arc, across the blade,
        // and both faces - and measure what it spans. A sheet of paper spans nothing on one axis
        // and vanishes when the camera lines up with it.
        for (Plane plane : Plane.values()) {
            Sweep sweep = sweep(plane);
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i <= 12; i++) {
                for (int j = 0; j <= 8; j++) {
                    float t = i / 12.0f;
                    float out = j / 8.0f;
                    for (int side = -1; side <= 1; side += 2) {
                        float[] point = sweep.at(t, out, side * ForgeRibbon.crossSection(out) * sweep.thickness());
                        for (int axis = 0; axis < 3; axis++) {
                            low[axis] = Math.min(low[axis], point[axis]);
                            high[axis] = Math.max(high[axis], point[axis]);
                        }
                    }
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(high[axis] - low[axis] > 0.01f,
                        plane + " blade is flat on axis " + axis + ": it disappears edge-on");
            }
        }
    }

    @Test
    void theSpineStandsProudOfBothLips() {
        // What gives the solid its silhouette: the middle of the blade is further off the plane
        // than either edge, at every point along the arc.
        Sweep sweep = sweep(Plane.GROUND);
        for (int i = 0; i <= 10; i++) {
            float t = i / 10.0f;
            float spine = sweep.at(t, 0.5f, ForgeRibbon.crossSection(0.5f) * sweep.thickness())[1];
            float lip = sweep.at(t, 1.0f, ForgeRibbon.crossSection(1.0f) * sweep.thickness())[1];
            assertTrue(spine > lip, "the blade has no spine at t=" + t);
        }
    }

    @Test
    void theSheathTurnsItsBroadSideToWhoeverIsLooking() {
        // The solid fixed the blade having no thickness; it did not fix a blade being a blade. Seen
        // down the line of its own swing an arc is still a line, because that is what a swing going
        // away from you looks like. The sheath is the glow around it, and a glow has to face the
        // viewer from every angle or it is one more sheet of paper.
        float[][] cameras = {{0.0f, 0.0f, -8.0f}, {6.0f, 2.0f, 1.0f}, {-3.0f, -5.0f, 4.0f}, {0.4f, 9.0f, 0.2f}};
        for (Plane plane : Plane.values()) {
            Sweep sweep = sweep(plane);
            for (float[] camera : cameras) {
                for (int i = 0; i <= 8; i++) {
                    float t = i / 8.0f;
                    float[] across = ForgeRibbon.facing(sweep, t, camera[0], camera[1], camera[2]);
                    assertEquals(1.0f, length(across), 1.0E-3f, plane + " sheath has no width at t=" + t);

                    float[] spine = sweep.at(t, 0.5f);
                    float[] view = {camera[0] - spine[0], camera[1] - spine[1], camera[2] - spine[2]};
                    assertEquals(0.0f, dot(across, view) / length(view), 1.0E-3f,
                            plane + " sheath leans toward the viewer at t=" + t);
                }
            }
        }
    }

    @Test
    void theSheathRunsAlongTheArcRatherThanAcrossIt() {
        // Square to the arc's own run as well as to the line of sight: a band that drifted along
        // the swing would bunch at the tips and gap in the middle.
        Sweep sweep = sweep(Plane.GROUND);
        for (int i = 1; i < 8; i++) {
            float t = i / 8.0f;
            float[] before = sweep.at(t - 0.02f, 0.5f);
            float[] after = sweep.at(t + 0.02f, 0.5f);
            float[] run = {after[0] - before[0], after[1] - before[1], after[2] - before[2]};
            float[] across = ForgeRibbon.facing(sweep, t, 0.0f, 3.0f, -7.0f);
            assertEquals(0.0f, dot(across, run) / length(run), 1.0E-2f, "the sheath drifts along the arc at t=" + t);
        }
    }

    @Test
    void theSheathStillHasAWidthWhenTheViewerSightsStraightDownTheArc() {
        // The one degenerate case: a camera sitting exactly on the line the arc is running along
        // leaves the two directions parallel and their cross product zero. A NaN here would erase
        // the whole strike rather than one band of glow.
        Sweep sweep = sweep(Plane.GROUND);
        float[] spine = sweep.at(0.5f, 0.5f);
        float[] before = sweep.at(0.48f, 0.5f);
        float[] camera = {spine[0] + (spine[0] - before[0]) * 200.0f, spine[1] + (spine[1] - before[1]) * 200.0f,
                spine[2] + (spine[2] - before[2]) * 200.0f};
        float[] across = ForgeRibbon.facing(sweep, 0.5f, camera[0], camera[1], camera[2]);
        assertEquals(1.0f, length(across), 1.0E-3f, "the sheath collapsed where the viewer sighted down it");
        for (float axis : across) {
            assertTrue(Float.isFinite(axis), "the sheath produced " + axis);
        }
    }

    @Test
    void theSheathIsAGlowRoundTheBladeAndNotABiggerBlade() {
        // It may claim a little more room than the steel, because a glow does; it may not claim so
        // much that the picture teaches a reach the hit shape will not honour.
        Sweep sweep = sweep(Plane.GROUND);
        float widest = 0.0f;
        for (int i = 0; i <= 20; i++) {
            float half = ForgeRibbon.sheathHalfWidth(sweep, i / 20.0f);
            assertTrue(half >= 0.0f, "the sheath inverted at t=" + i / 20.0f);
            widest = Math.max(widest, half);
        }
        assertTrue(widest > sweep.thickness() * 0.2f, "the sheath is too thin to be seen: " + widest);
        assertTrue(widest < sweep.thickness() * 1.5f, "the sheath is drawing a second, fatter blade: " + widest);
        assertEquals(0.0f, ForgeRibbon.sheathHalfWidth(sweep, 0.0f), EPSILON, "the sheath starts with a hard cap");
        assertEquals(0.0f, ForgeRibbon.sheathHalfWidth(sweep, 1.0f), EPSILON, "the sheath ends with a hard cap");
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(dot(v, v));
    }

    private static void assertArrayEqualish(float[] expected, float[] actual) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], EPSILON, "axis " + i);
        }
    }
}
