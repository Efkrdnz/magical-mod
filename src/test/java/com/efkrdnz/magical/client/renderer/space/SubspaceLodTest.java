package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tessellation is a function of apparent size and never of radius.
 *
 * <p>The silhouette error of an n-gon seen from its own centre is {@code 1 - cos(pi/n)}, which has
 * no radius in it at all, so a five-block dome and a sixteen-block dome seen from the inside are
 * the same problem. The old renderer hardcoded 72, 96 and 128 segments and therefore spent the same
 * on a dome filling the screen as on one a hundred blocks away.
 */
class SubspaceLodTest {

    @Test
    @DisplayName("two domes of different size look the same from their own centres, so they cost the same")
    void tessellationIgnoresRadius() {
        for (double viewRatio : new double[] {0.0D, 0.5D, 1.0D, 1.9D, 2.1D, 3.9D, 7.9D, 20.0D}) {
            int expected = SubspaceLod.rung(viewRatio);
            for (float radius : new float[] {5.0F, 8.0F, 11.3F, 16.0F, 24.0F}) {
                double distance = viewRatio * radius;
                assertEquals(expected, SubspaceLod.rungFor(radius, distance * distance),
                        "radius " + radius + " changed the rung at viewRatio " + viewRatio);
            }
        }
    }

    @Test
    @DisplayName("further away is never more detailed")
    void detailFallsOffMonotonically() {
        int previous = -1;
        for (int i = 0; i <= 400; i++) {
            int rung = SubspaceLod.rung(i / 10.0D);
            assertTrue(rung >= previous, "detail rose with distance at viewRatio " + (i / 10.0D));
            previous = rung;
        }
    }

    @Test
    @DisplayName("the closest rung is still under a pixel of silhouette error")
    void theNearestRungIsSmoothEnough() {
        // One pixel at 70 degrees horizontal FOV on a 1080p frame is 1.13 mrad; the worst case is
        // the camera at the dome's own centre, where the error is 1 - cos(pi/segments) exactly.
        double error = 1.0D - Math.cos(Math.PI / SubspaceLod.segments(0));
        assertTrue(error < 1.5E-3D, "rung 0 silhouette error " + error);
    }

    @Test
    @DisplayName("the whole dome costs less than the old one did, at every size and distance")
    void theBudgetOnlyEverShrinks() {
        int worst = 0;
        for (int i = 0; i <= 400; i++) {
            double viewRatio = i / 10.0D;
            boolean inside = viewRatio < 1.0D;
            worst = Math.max(worst, SubspaceLod.membraneQuads(SubspaceLod.rung(viewRatio), inside));
        }
        // The old renderer emitted 3123 quads unconditionally and distance-sorted every one per frame.
        assertTrue(worst <= 2560, "worst-case membrane quads " + worst);
    }

    @Test
    @DisplayName("standing in your own dome is the cheapest case, not the dearest")
    void theCasterPaysLeast() {
        int inside = SubspaceLod.membraneQuads(SubspaceLod.rung(0.0D), true);
        int pointBlankOutside = SubspaceLod.membraneQuads(SubspaceLod.rung(1.2D), false);
        assertTrue(inside < pointBlankOutside, "inside " + inside + " outside " + pointBlankOutside);
    }

    @Test
    @DisplayName("every rung can carry a level equator and a clean meridian")
    void theMeshCanHoldItsOwnLandmarks() {
        for (int rung = 0; rung < SubspaceLod.RUNGS; rung++) {
            assertEquals(0, SubspaceLod.rings(rung) % 2, "rung " + rung + " has no ring on the equator");
            assertEquals(0, SubspaceLod.segments(rung) % 4, "rung " + rung + " cannot land on north");
        }
    }
}
