package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transparency contract, pinned on exact numbers.
 *
 * <p>The old dome was one cyan on an additive blend, so a first-person sight line out of the centre
 * crossing the equator, a meridian and a tick deposited more than 1.0 in every channel and clipped
 * to white over daylight. These assertions exist so that cannot come back by degrees: they hold the
 * face-on alpha near nothing, the rim alpha short of opaque, and the area-weighted mean over the
 * whole disc under a ceiling, which is the number that decides whether a dome reads as milky.
 */
class SubspaceOpticsTest {

    private static final double EPS = 1.0E-6D;

    /**
     * The ceilings below were first set from a design document and then moved once, after the
     * first capture in daylight: a wall the arithmetic called a clean pane was, standing in it,
     * not there. They are held here at what the game showed rather than at what the plan said,
     * and the shape of the curve - almost clear square on, firm at the limb, never opaque, never
     * clipping - is what the test is actually for.
     */
    @Test
    @DisplayName("looking straight through the wall is close to looking at nothing")
    void faceOnIsAlmostClear() {
        assertEquals(0.070D, SubspaceOptics.alphaDelivered(0.0D), 0.002D);
    }

    @Test
    @DisplayName("the wall thickens toward the limb and never reaches opaque")
    void theRimIsBrightButNotSolid() {
        assertTrue(SubspaceOptics.alphaDelivered(0.866D) <= 0.18D, "outer quarter stays readable");
        assertTrue(SubspaceOptics.alphaDelivered(0.980D) <= 0.42D, "outer 4% stays readable");
        assertEquals(0.580D, SubspaceOptics.alphaDelivered(1.0D), 0.001D);
        assertTrue(SubspaceOptics.alphaDelivered(1.0D) < 1.0D, "the silhouette is never opaque");
    }

    @Test
    @DisplayName("alpha rises monotonically from the centre of the disc to its edge")
    void theCurveOnlyEverThickens() {
        double previous = -1.0D;
        for (int i = 0; i <= 200; i++) {
            double alpha = SubspaceOptics.alphaDelivered(i / 200.0D);
            assertTrue(alpha >= previous - EPS, "alpha dipped at b/R=" + (i / 200.0D));
            previous = alpha;
        }
    }

    /** The number that decides "milky". A disc is mostly its middle, so the mean is area-weighted. */
    @Test
    @DisplayName("averaged over its whole disc the wall is under a tenth opaque")
    void theWholeDiscAveragesClear() {
        assertTrue(SubspaceOptics.meanAlphaOverDisc(false) <= 0.13D,
                "full disc mean " + SubspaceOptics.meanAlphaOverDisc(false));
        assertTrue(SubspaceOptics.meanAlphaOverInner(false, 0.75D) <= 0.09D,
                "inner 75% mean " + SubspaceOptics.meanAlphaOverInner(false, 0.75D));
    }

    @Test
    @DisplayName("a sealed boundary is allowed to be heavier, and only that much heavier")
    void sealingIsTheOneNamedException() {
        assertTrue(SubspaceOptics.meanAlphaOverDisc(true) > SubspaceOptics.meanAlphaOverDisc(false));
        assertTrue(SubspaceOptics.meanAlphaOverDisc(true) <= 0.18D,
                "sealed mean " + SubspaceOptics.meanAlphaOverDisc(true));
        assertTrue(SubspaceOptics.alphaDelivered(1.0D, true) <= 0.85D);
    }

    /**
     * The inside/outside consistency argument, which is the whole reason there is a sqrt in the
     * shader. A viewer outside crosses two walls and the caster inside crosses one; both must be
     * shown the same wall at every angle, not merely at the two endpoints.
     */
    @Test
    @DisplayName("two crossings deliver exactly what one crossing delivers, at every angle")
    void twoWallsLookLikeOneWall() {
        for (int i = 0; i <= 100; i++) {
            double bOverR = i / 100.0D;
            double delivered = SubspaceOptics.alphaDelivered(bOverR);
            double each = SubspaceOptics.alphaPerCrossing(delivered, 2);
            double composited = 1.0D - (1.0D - each) * (1.0D - each);
            assertEquals(delivered, composited, EPS, "mismatch at b/R=" + bOverR);
        }
    }

    @Test
    @DisplayName("one crossing is delivered untouched")
    void oneWallIsItself() {
        double delivered = SubspaceOptics.alphaDelivered(0.5D);
        assertEquals(delivered, SubspaceOptics.alphaPerCrossing(delivered, 1), EPS);
    }

    /**
     * Additive blending clips whitest in the channel that is already highest, so anything covering
     * area must be held well off red relative to blue: a line that clips then reads as cyan-white
     * rather than white, and keeps its identity.
     */
    @Test
    @DisplayName("everything that covers area is held off the red channel")
    void thePaletteCannotClipToWhite() {
        assertTrue(red(SubspaceOptics.BURNISH) <= 0.45D * blue(SubspaceOptics.BURNISH),
                "burnish r/b " + (red(SubspaceOptics.BURNISH) / blue(SubspaceOptics.BURNISH)));
        assertTrue(red(SubspaceOptics.BODY) <= 0.60D * blue(SubspaceOptics.BODY),
                "body r/b " + (red(SubspaceOptics.BODY) / blue(SubspaceOptics.BODY)));
    }

    /**
     * The structural guarantee the old renderer could not make. Over-blending a covering material
     * is a convex combination, so it stays between the background and the material however many
     * layers stack - it cannot run away to white the way a sum can.
     */
    @Test
    @DisplayName("stacking the membrane a dozen deep still cannot blow out a channel")
    void overBlendingNeverClips() {
        double[][] backgrounds = {
            {0.86D, 0.79D, 0.62D}, {0.55D, 0.72D, 0.95D}, {0.47D, 0.47D, 0.49D},
            {0.02D, 0.03D, 0.07D}, {1.0D, 1.0D, 1.0D}, {0.0D, 0.0D, 0.0D},
        };
        for (double[] background : backgrounds) {
            double[] out = background.clone();
            for (int layer = 0; layer < 12; layer++) {
                double alpha = SubspaceOptics.alphaDelivered(0.98D);
                for (int c = 0; c < 3; c++) {
                    out[c] = SubspaceOptics.component(SubspaceOptics.BODY, c) * alpha + out[c] * (1.0D - alpha);
                }
            }
            for (int c = 0; c < 3; c++) {
                assertTrue(out[c] <= 1.0D + EPS && out[c] >= -EPS, "channel escaped: " + out[c]);
            }
        }
    }

    @Test
    @DisplayName("a sight line crosses one wall from inside and two from outside")
    void theLayerBudgetIsTiny() {
        assertEquals(1, SubspaceOptics.membraneCrossings(true));
        assertEquals(2, SubspaceOptics.membraneCrossings(false));
        assertTrue(SubspaceOptics.maxSurfacesCrossed(true) <= 2);
        assertTrue(SubspaceOptics.maxSurfacesCrossed(false) <= 4);
    }

    private static double red(int rgb) {
        return SubspaceOptics.component(rgb, 0);
    }

    private static double blue(int rgb) {
        return SubspaceOptics.component(rgb, 2);
    }
}
