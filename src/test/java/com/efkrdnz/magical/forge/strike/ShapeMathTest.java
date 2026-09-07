package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.strike.ShapeMath.Basis;

class ShapeMathTest {

    private static final double DELTA = 1e-9;

    @Test
    void basisFromForwardNormalizesAndBuildsAnOrthonormalFrame() {
        Basis basis = Basis.fromForward(2, 0, 0);

        assertEquals(1.0, basis.fx(), DELTA);
        assertEquals(0.0, basis.fy(), DELTA);
        assertEquals(0.0, basis.fz(), DELTA);
        assertEquals(1.0, ShapeMath.length(basis.rx(), basis.ry(), basis.rz()), DELTA);
        assertEquals(1.0, ShapeMath.length(basis.ux(), basis.uy(), basis.uz()), DELTA);
        assertEquals(0.0, ShapeMath.dot(basis.fx(), basis.fy(), basis.fz(), basis.rx(), basis.ry(), basis.rz()),
                DELTA);
    }

    @Test
    void basisFromAVerticalForwardDoesNotProduceNanAndHasUnitRightAndUp() {
        Basis up = Basis.fromForward(0, 1, 0);
        assertNoNaN(up);
        assertEquals(1.0, ShapeMath.length(up.rx(), up.ry(), up.rz()), DELTA);
        assertEquals(1.0, ShapeMath.length(up.ux(), up.uy(), up.uz()), DELTA);

        Basis down = Basis.fromForward(0, -1, 0);
        assertNoNaN(down);
        assertEquals(1.0, ShapeMath.length(down.rx(), down.ry(), down.rz()), DELTA);
        assertEquals(1.0, ShapeMath.length(down.ux(), down.uy(), down.uz()), DELTA);
    }

    @Test
    void basisFromAZeroLengthForwardFallsBackToASaneDefaultBasis() {
        Basis basis = Basis.fromForward(0, 0, 0);
        assertNoNaN(basis);

        assertEquals(0.0, basis.fx(), DELTA);
        assertEquals(0.0, basis.fy(), DELTA);
        assertEquals(1.0, basis.fz(), DELTA);
        assertEquals(1.0, basis.rx(), DELTA);
        assertEquals(0.0, basis.ry(), DELTA);
        assertEquals(0.0, basis.rz(), DELTA);
        assertEquals(0.0, basis.ux(), DELTA);
        assertEquals(1.0, basis.uy(), DELTA);
        assertEquals(0.0, basis.uz(), DELTA);
    }

    @Test
    void basisFromANonFiniteForwardFallsBackToASaneDefaultBasis() {
        Basis basis = Basis.fromForward(Double.NaN, 0, 0);
        assertNoNaN(basis);
        assertEquals(1.0, ShapeMath.length(basis.fx(), basis.fy(), basis.fz()), DELTA);
        assertEquals(1.0, ShapeMath.length(basis.rx(), basis.ry(), basis.rz()), DELTA);
        assertEquals(1.0, ShapeMath.length(basis.ux(), basis.uy(), basis.uz()), DELTA);
    }

    private static void assertNoNaN(Basis basis) {
        assertFalse(Double.isNaN(basis.fx()) || Double.isNaN(basis.fy()) || Double.isNaN(basis.fz()));
        assertFalse(Double.isNaN(basis.rx()) || Double.isNaN(basis.ry()) || Double.isNaN(basis.rz()));
        assertFalse(Double.isNaN(basis.ux()) || Double.isNaN(basis.uy()) || Double.isNaN(basis.uz()));
    }

    @Test
    void coneAcceptsStraightAheadWithinReachAndRejectsBeyondReach() {
        Basis forward = Basis.fromForward(1, 0, 0);

        assertTrue(ShapeMath.cone(0, 0, 0, forward, 3, 0, 0, 5, 75, 0));
        assertFalse(ShapeMath.cone(0, 0, 0, forward, 6, 0, 0, 5, 75, 0));
    }

    @Test
    void coneRejectsOneHundredDegreesOffAxisForAOneFiftyDegreeArc() {
        Basis forward = Basis.fromForward(1, 0, 0);
        double halfArc = 75; // half of a 150-degree total arc

        // Target 100 degrees off the forward axis, within reach.
        double angleRad = Math.toRadians(100);
        double tx = Math.cos(angleRad) * 3;
        double tz = Math.sin(angleRad) * 3;

        assertFalse(ShapeMath.cone(0, 0, 0, forward, tx, 0, tz, 5, halfArc, 0));
    }

    @Test
    void coneAcceptsAnOffAxisTargetWhenSlackAllowsIt() {
        Basis forward = Basis.fromForward(1, 0, 0);
        double halfArc = 75;
        double slack = 0.05;

        // Pick an angle just past the half-arc boundary; the expected accept/reject boundary is
        // computed straight from the formula: cos(angle) >= cos(halfArc) - slack.
        double angleDegrees = 77;
        double angleRad = Math.toRadians(angleDegrees);
        double tx = Math.cos(angleRad) * 3;
        double tz = Math.sin(angleRad) * 3;

        double expectedThreshold = Math.cos(Math.toRadians(halfArc)) - slack;
        boolean shouldAccept = Math.cos(angleRad) >= expectedThreshold;

        assertTrue(shouldAccept, "test setup should exercise the slack-accepted branch");
        assertTrue(ShapeMath.cone(0, 0, 0, forward, tx, 0, tz, 5, halfArc, slack));
        // Without slack the same target is rejected.
        assertFalse(ShapeMath.cone(0, 0, 0, forward, tx, 0, tz, 5, halfArc, 0));
    }

    @Test
    void coneTreatsAZeroLengthTargetVectorAsInside() {
        Basis forward = Basis.fromForward(1, 0, 0);
        assertTrue(ShapeMath.cone(5, 2, -1, forward, 5, 2, -1, 5, 10, 0));
    }

    @Test
    void verticalPlaneAcceptsLateralWithinToleranceAndRejectsBeyondIt() {
        Basis forward = Basis.fromForward(1, 0, 0);

        assertTrue(ShapeMath.verticalPlane(0, 0, 0, forward, 2, 0, 0.2, 0.315, 5, -1, 1));
        assertFalse(ShapeMath.verticalPlane(0, 0, 0, forward, 2, 0, 0.5, 0.315, 5, -1, 1));
    }

    @Test
    void verticalPlaneRejectsOutsideReachAndOutsideTheUpBand() {
        Basis forward = Basis.fromForward(1, 0, 0);

        assertFalse(ShapeMath.verticalPlane(0, 0, 0, forward, 6, 0, 0, 0.315, 5, -1, 1));
        assertFalse(ShapeMath.verticalPlane(0, 0, 0, forward, 2, 2, 0, 0.315, 5, -1, 1));
        assertFalse(ShapeMath.verticalPlane(0, 0, 0, forward, -1, 0, 0, 0.315, 5, -1, 1));
    }

    @Test
    void capsuleDistanceFromTheMidpointOffsetIsTheOffsetDistance() {
        double distance = ShapeMath.capsuleDistance(0, 0, 0, 4, 0, 0, 2, 1, 0);
        assertEquals(1.0, distance, DELTA);
    }

    @Test
    void capsuleDistanceBeyondTheEndIsTheDistanceToTheEnd() {
        double distance = ShapeMath.capsuleDistance(0, 0, 0, 4, 0, 0, 6, 0, 0);
        assertEquals(2.0, distance, DELTA);
    }

    @Test
    void capsuleDistanceOnTheSegmentIsZero() {
        double distance = ShapeMath.capsuleDistance(0, 0, 0, 4, 0, 0, 2, 0, 0);
        assertEquals(0.0, distance, DELTA);
    }

    @Test
    void ringAcceptsInsideRadiusAndDyAndRejectsOutside() {
        assertTrue(ShapeMath.ring(0, 0, 0, 3, 0.5, 0, 5, 1));
        assertFalse(ShapeMath.ring(0, 0, 0, 6, 0.5, 0, 5, 1));
        assertFalse(ShapeMath.ring(0, 0, 0, 3, 2, 0, 5, 1));
    }

    @Test
    void discAcceptsInsideRadiusAndDyAndRejectsOutside() {
        assertTrue(ShapeMath.disc(0, 0, 0, 3, 0.5, 0, 5, 1));
        assertFalse(ShapeMath.disc(0, 0, 0, 6, 0.5, 0, 5, 1));
        assertFalse(ShapeMath.disc(0, 0, 0, 3, 2, 0, 5, 1));
    }

    @Test
    void horizontalDistanceIgnoresTheYAxis() {
        assertEquals(5.0, ShapeMath.horizontalDistance(0, 0, 3, 4), DELTA);
    }

    @Test
    void dotComputesTheThreeDimensionalDotProduct() {
        assertEquals(32.0, ShapeMath.dot(1, 2, 3, 4, 5, 6), DELTA);
    }

    @Test
    void lengthComputesTheEuclideanNorm() {
        assertEquals(5.0, ShapeMath.length(3, 4, 0), DELTA);
    }
}
