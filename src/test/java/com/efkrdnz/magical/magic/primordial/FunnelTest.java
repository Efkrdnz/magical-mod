package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The funnel widens upward, draws in from outside, turns counter-clockwise seen from above, lifts inside and flings out from the crown. */
class FunnelTest {
    private static final Funnel FUNNEL = new Funnel(3.0D, 8.0D);

    @Test
    void itIsNarrowAtTheFootAndWideAtTheCrown() {
        assertEquals(3.0D * Funnel.FOOT, FUNNEL.radiusAt(0.0D), 1.0E-9D);
        assertEquals(3.0D, FUNNEL.radiusAt(8.0D), 1.0E-9D);
        double last = 0.0D;
        for (double y = 0.0D; y <= 8.0D; y += 0.25D) {
            double r = FUNNEL.radiusAt(y);
            assertTrue(r >= last, "the funnel never narrows going up");
            last = r;
        }
    }

    @Test
    void aBodyOutsideTheWallIsDrawnIn() {
        double[] v = FUNNEL.pull(4.0D, 1.0D, 0.0D, 1.0D);
        assertTrue(v[0] < 0.0D, "east of the eye it is pulled west, toward the axis");
        assertEquals(0.0D, v[1], 1.0E-9D, "nothing outside the wall is lifted");
    }

    @Test
    void itTurnsCounterClockwiseSeenFromAbove() {
        // East of the eye, counter-clockwise seen from above (x east, z south) moves north: -z.
        double[] v = FUNNEL.pull(2.0D, 6.0D, 0.0D, 1.0D);
        assertTrue(v[2] < 0.0D, "a body east of the eye is carried north");
        double before = Math.atan2(0.0D, 2.0D);
        double after = Math.atan2(0.0D + v[2], 2.0D + v[0]);
        assertEquals(Funnel.SPIN, (int) Math.signum(after - before), "SPIN is the sign the client turns its particles by");
    }

    @Test
    void aBodyInsideIsLifted() {
        assertTrue(FUNNEL.inside(0.5D, 1.0D, 0.0D));
        assertTrue(FUNNEL.pull(0.5D, 1.0D, 0.0D, 1.0D)[1] > 0.0D);
        assertFalse(FUNNEL.inside(0.0D, 9.0D, 0.0D), "above the crown is not inside");
    }

    @Test
    void theCrownFlingsOutward() {
        assertTrue(FUNNEL.atCrown(7.5D));
        assertFalse(FUNNEL.atCrown(3.0D));
        double[] f = FUNNEL.fling(0.0D, -1.0D, 1.0D);
        assertTrue(f[2] < 0.0D, "north of the eye flies further north");
        assertTrue(f[1] > 0.0D);
    }

    @Test
    void theReachIsPastTheWall() {
        assertTrue(FUNNEL.inReach(4.5D, 0.0D, 0.0D));
        assertFalse(FUNNEL.inReach(5.0D, 0.0D, 0.0D));
    }
}
