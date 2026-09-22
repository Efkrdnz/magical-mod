package com.efkrdnz.magical.magic.sword.stance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The six volley shapes, which is the whole of what a stance lends to Loose and Below.
 *
 * <p>Two of these are the same object with one axis swapped - a {@link Pattern#LINE} is wide and
 * shallow and a {@link Pattern#COLUMN} is narrow and deep - so an index mistaken between them
 * produces a volley that fires, damages, sounds and looks entirely plausible while being the
 * wrong skill. That pair is what {@link #aLineIsWideAndAColumnIsDeep} exists for; everything else
 * here is arithmetic that would otherwise only ever surface as "my last sword misses".
 */
class PatternTest {

    private static final int MAX = 12;
    private static final double REACH = 20.0D;

    @Test
    void everyBladeOfEveryVolleyTravelsSomewhere() {
        for (Pattern pattern : Pattern.values()) {
            for (int count = 1; count <= MAX; count++) {
                for (int i = 0; i < count; i++) {
                    Slot slot = Pattern.spread(pattern, i, count, REACH);
                    double length = Math.sqrt(slot.dx() * slot.dx() + slot.dy() * slot.dy()
                            + slot.dz() * slot.dz());
                    assertEquals(1.0D, length, 1.0E-9D, pattern + " blade " + i + " of " + count
                            + " travels a direction of length " + length);
                }
            }
        }
    }

    @Test
    void noTwoBladesOfAVolleyAimAtTheSamePoint() {
        for (Pattern pattern : Pattern.values()) {
            for (int count = 2; count <= MAX; count++) {
                for (int a = 0; a < count; a++) {
                    for (int b = a + 1; b < count; b++) {
                        double gap = Pattern.spread(pattern, a, count, REACH)
                                .gapTo(Pattern.spread(pattern, b, count, REACH));
                        assertTrue(gap > 0.2D, pattern + " at count " + count + " aims blades " + a
                                + " and " + b + " only " + gap + " apart - a volley whose blades"
                                + " converge is one blade's damage at six blades' cost");
                    }
                }
            }
        }
    }

    /**
     * A volley of one lands on the axis. Every pattern spreads about a centre, and the obvious
     * parameterisation - {@code i / (count - 1)} - is 0/0 at one and hard against one end at two,
     * so the single blade of a wielder down to their last sword would miss by half a span.
     */
    @Test
    void aVolleyOfOneIsOnTheAxis() {
        for (Pattern pattern : Pattern.values()) {
            Slot only = Pattern.spread(pattern, 0, 1, REACH);
            double off = Math.sqrt(only.x() * only.x() + only.z() * only.z());
            double allowed = switch (pattern) {
                // RING and FAN are stand-off patterns: their whole shape is a radius, so a single
                // blade stands on the ring rather than at the centre and points inward at the
                // target from there. That is the pattern working, not an off-centre blade.
                case RING, FAN -> Pattern.RING_RADIUS + 1.0E-6D;
                case COLUMN -> 0.3D;
                default -> 0.9D;
            };
            assertTrue(off <= allowed, pattern + " puts its only blade " + off
                    + " off the axis, past " + allowed);
        }
    }

    @Test
    void aLineIsWideAndAColumnIsDeep() {
        int count = 6;
        double lineWidth = extent(Pattern.LINE, count, true);
        double lineDepth = extent(Pattern.LINE, count, false);
        double columnWidth = extent(Pattern.COLUMN, count, true);
        double columnDepth = extent(Pattern.COLUMN, count, false);

        assertTrue(lineWidth > lineDepth * 4.0D, "LINE is " + lineWidth + " wide and " + lineDepth
                + " deep; a wall that is not much wider than it is deep is a cluster");
        assertTrue(columnDepth > columnWidth * 4.0D, "COLUMN is " + columnWidth + " wide and "
                + columnDepth + " deep; a spear that is not much deeper than it is wide is a line");
        assertTrue(lineWidth > columnWidth, "LINE is no wider than COLUMN, so the two stances that"
                + " are meant to feel least alike fire the same volley");
    }

    @Test
    void aRingSurroundsAndPointsInward() {
        int count = 8;
        for (int i = 0; i < count; i++) {
            Slot slot = Pattern.spread(Pattern.RING, i, count, REACH);
            double radius = Math.sqrt(slot.x() * slot.x() + slot.z() * slot.z());
            assertEquals(Pattern.RING_RADIUS, radius, 1.0E-9D,
                    "RING blade " + i + " stands off " + radius);
            double inward = -(slot.x() * slot.dx() + slot.z() * slot.dz()) / radius;
            assertTrue(inward > 0.99D, "RING blade " + i + " does not travel inward at the target");
        }
    }

    @Test
    void aFanHasBladesOnBothSides() {
        for (int count = 2; count <= MAX; count++) {
            boolean left = false;
            boolean right = false;
            for (int i = 0; i < count; i++) {
                double x = Pattern.spread(Pattern.FAN, i, count, REACH).x();
                left |= x > 0.1D;
                right |= x < -0.1D;
            }
            assertTrue(left && right, "FAN at count " + count + " has everything on one wing");
        }
    }

    @Test
    void whatFallsComesDown() {
        for (int count = 1; count <= MAX; count++) {
            for (int i = 0; i < count; i++) {
                Slot slot = Pattern.spread(Pattern.FALL, i, count, REACH);
                assertEquals(Pattern.FALL_HEIGHT, slot.y(), 1.0E-9D,
                        "FALL blade " + i + " does not start overhead");
                assertEquals(-1.0D, slot.dy(), 1.0E-9D, "FALL blade " + i + " does not come down");
            }
        }
    }

    @Test
    void aSprayScattersAcrossTheAimAndNotAlongIt() {
        int count = 9;
        double along = extent(Pattern.SPRAY, count, false);
        double across = extent(Pattern.SPRAY, count, true);
        assertTrue(across > 1.0D, "SPRAY is only " + across + " wide, which is a cluster");
        assertTrue(along < 1.0E-9D, "SPRAY has depth " + along + "; it is a disc square-on to the"
                + " aim, and depth in it means the pattern has been rotated into the wrong plane");
    }

    /** Peak-to-peak on x (across the aim) or z (along it). */
    private static double extent(Pattern pattern, int count, boolean across) {
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            Slot slot = Pattern.spread(pattern, i, count, REACH);
            double value = across ? slot.x() : slot.z();
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        return high - low;
    }
}
