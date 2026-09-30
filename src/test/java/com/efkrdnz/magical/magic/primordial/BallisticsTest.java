package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A lob integrated the way a thrown spell integrates lands exactly on its mark. */
class BallisticsTest {

    @Test
    void aLobLandsOnItsMark() {
        double gravity = 0.05D;
        double[][] marks = {{10.0D, 0.0D, 3.0D}, {-6.0D, -4.0D, 12.0D}, {0.5D, 5.0D, -2.0D}};
        for (double[] mark : marks) {
            int ticks = Ballistics.flightTicks(Math.hypot(mark[0], mark[2]));
            double[] v = Ballistics.lob(mark[0], mark[1], mark[2], gravity, ticks);
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;
            double vy = v[1];
            for (int i = 0; i < ticks; i++) {
                x += v[0];
                y += vy;
                z += v[2];
                vy -= gravity;
            }
            assertEquals(mark[0], x, 1.0E-9D);
            assertEquals(mark[1], y, 1.0E-9D);
            assertEquals(mark[2], z, 1.0E-9D);
        }
    }

    @Test
    void flightTimeIsBounded() {
        assertEquals(Ballistics.MIN_FLIGHT, Ballistics.flightTicks(0.0D));
        assertEquals(Ballistics.MAX_FLIGHT, Ballistics.flightTicks(500.0D));
    }

    @Test
    void aStarSpeedsUpAsItFalls() {
        assertEquals(0.0D, Ballistics.fall(0.0D), 1.0E-9D);
        assertEquals(1.0D, Ballistics.fall(1.0D), 1.0E-9D);
        assertTrue(Ballistics.fall(0.5D) < 0.5D, "the first half of the warning covers less than half the slant");
    }
}
