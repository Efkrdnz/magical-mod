package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Where each body of one shot points, as §9 of the design lays it down: below 180 the fan is
 * inclusive, -p..+p in N-1 steps; at 180 it is the full circle exclusive; one body points straight.
 * Spread is a deviation within half the spread either way, decided by a roll the spawner supplies.
 */
class VerseFanTest {

    private static final double EPS = 1.0E-9D;

    @Test
    void columnIsMinusNinetyZeroAndPlusNinety() {
        assertArrayEquals(new double[] {-90.0D, 0.0D, 90.0D}, VerseFan.yaws(3, 90.0D), EPS);
    }

    @Test
    void cleftIsMinusAndPlusFortyFive() {
        assertArrayEquals(new double[] {-45.0D, 45.0D}, VerseFan.yaws(2, 45.0D), EPS);
    }

    @Test
    void tridentIsMinusTwentyZeroAndPlusTwenty() {
        assertArrayEquals(new double[] {-20.0D, 0.0D, 20.0D}, VerseFan.yaws(3, 20.0D), EPS);
    }

    @Test
    void mirrorIsAheadAndBehind() {
        assertArrayEquals(new double[] {0.0D, 180.0D}, VerseFan.yaws(2, 180.0D), EPS);
    }

    @Test
    void hexadIsEverySixtyDegreesStartingAhead() {
        assertArrayEquals(new double[] {0.0D, 60.0D, 120.0D, 180.0D, 240.0D, 300.0D}, VerseFan.yaws(6, 180.0D), EPS);
    }

    @Test
    void pentacleIsEverySeventyTwo() {
        assertArrayEquals(new double[] {0.0D, 72.0D, 144.0D, 216.0D, 288.0D}, VerseFan.yaws(5, 180.0D), EPS);
    }

    @Test
    void oneBodyOrNoPatternPointsStraight() {
        assertArrayEquals(new double[] {0.0D}, VerseFan.yaws(1, 45.0D), EPS);
        assertArrayEquals(new double[] {0.0D, 0.0D, 0.0D, 0.0D}, VerseFan.yaws(4, 0.0D), EPS);
        assertArrayEquals(new double[0], VerseFan.yaws(0, 45.0D), EPS);
    }

    @Test
    void deviationSpansHalfTheSpreadEitherWayAndNothingWithoutASpread() {
        assertEquals(-10.0D, VerseFan.deviation(20.0D, 0.0D), EPS);
        assertEquals(10.0D, VerseFan.deviation(20.0D, 1.0D), EPS);
        assertEquals(0.0D, VerseFan.deviation(20.0D, 0.5D), EPS);
        assertEquals(0.0D, VerseFan.deviation(0.0D, 0.9D), EPS);
        assertEquals(0.0D, VerseFan.deviation(-8.0D, 0.9D), EPS);
    }
}
