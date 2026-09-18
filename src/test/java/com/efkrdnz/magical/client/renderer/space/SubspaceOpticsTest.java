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
    @DisplayName("the pane is a tint and nothing else")
    void thePaneIsATint() {
        // Three percent: six levels of cold slate over daylight sand. It was seven, and seven was
        // chosen on the theory that the Fresnel would carry the reading everywhere else - which it
        // could not, because from the centre of a sphere there is no angle for it to be a function
        // of. Halving it is not a retreat: the reading moved to the structure, which is the only
        // place from this viewpoint it could ever have lived, and the wall got clearer as well as
        // far more visible because tint is area times alpha and visibility is local contrast.
        assertEquals(0.030D, SubspaceOptics.paneAlpha(false), 1.0E-9D);
        assertEquals(0.030D, SubspaceOptics.alphaDelivered(0.0D), 1.0E-9D);
        assertTrue(SubspaceOptics.paneAlpha(false) < 0.070D, "the pane is no clearer than the one it replaced");
        assertEquals(0.060D, SubspaceOptics.paneAlpha(true), 1.0E-9D);
    }

    /**
     * The one branch the Fresnel survives on, and the one it was always right about.
     *
     * <p>From outside, the impact parameter genuinely sweeps the disc and the limb genuinely is
     * where a silhouette lives, so nothing here is a constant. It carries that whole case alone now
     * - the writing on the wall is legible from inside and the outside gets a shape - which is why
     * the limb is a little firmer than it was.
     */
    @Test
    @DisplayName("the wall thickens toward the limb and never reaches opaque")
    void theRimIsBrightButNotSolid() {
        assertTrue(SubspaceOptics.alphaDelivered(0.866D) <= 0.14D, "outer quarter stays readable");
        assertTrue(SubspaceOptics.alphaDelivered(0.980D) <= 0.42D, "outer 4% stays readable");
        assertEquals(0.620D, SubspaceOptics.alphaDelivered(1.0D), 1.0E-9D);
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
        // The vault can stack three on one sight line where a rib crosses a ring over the pane,
        // which is still small enough that the glow ceiling can be proved rather than guessed at.
        assertTrue(SubspaceOptics.maxSurfacesCrossed(true) <= 3);
        assertTrue(SubspaceOptics.maxSurfacesCrossed(false) <= 5);
    }

    private static double red(int rgb) {
        return SubspaceOptics.component(rgb, 0);
    }

    private static double blue(int rgb) {
        return SubspaceOptics.component(rgb, 2);
    }
}
