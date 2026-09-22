package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The formation's lag, on exact values.
 *
 * <p>Easing arithmetic is the kind that looks obviously right and is wrong in two specific ways,
 * both of which are invisible until somebody plays: <b>angles wrap</b>, so a naive lerp from 179
 * to -179 travels 358 degrees the long way round and a wielder who walks past due south watches
 * twelve swords whip round them; and <b>a fixed step is not a rate</b>, so a formation eased by
 * "ten per cent of the gap each tick" moves at a speed that depends on the tick rate and runs in
 * slow motion on a struggling server. Both are pinned here rather than reasoned about.
 */
class FrameEaseTest {

    private static final double EXACT = 1.0E-9D;

    @Test
    void aHalfLifeClosesHalfTheGapInItsOwnTime() {
        // The definition, and the reason the parameter is named what it is.
        assertEquals(5.0D, FrameEase.approach(0.0D, 10.0D, 3.0D, 3.0D), EXACT);
        assertEquals(7.5D, FrameEase.approach(0.0D, 10.0D, 3.0D, 6.0D), EXACT);
        assertEquals(8.75D, FrameEase.approach(0.0D, 10.0D, 3.0D, 9.0D), EXACT);
    }

    @Test
    void theRateDoesNotDependOnHowOftenItIsAsked() {
        // The whole point of a half-life over a per-tick fraction. Twenty small steps and one
        // big one have to land in the same place, or the formation moves at one speed on a
        // healthy server and another on a server that is behind - and at a third on a client
        // running the same call once a render frame.
        double coarse = FrameEase.approach(0.0D, 100.0D, 4.0D, 20.0D);
        double fine = 0.0D;
        for (int i = 0; i < 20; i++) {
            fine = FrameEase.approach(fine, 100.0D, 4.0D, 1.0D);
        }
        assertEquals(coarse, fine, 1.0E-9D, "the ease is not rate independent");

        double finer = 0.0D;
        for (int i = 0; i < 200; i++) {
            finer = FrameEase.approach(finer, 100.0D, 4.0D, 0.1D);
        }
        assertEquals(coarse, finer, 1.0E-9D, "ten times the resolution moved it a different distance");
    }

    @Test
    void anEaseNeverOvershootsAndNeverStopsShort() {
        assertEquals(10.0D, FrameEase.approach(10.0D, 10.0D, 3.0D, 1.0D), EXACT, "already there");
        assertTrue(FrameEase.approach(0.0D, 10.0D, 3.0D, 1.0D) < 10.0D, "a single tick arrived");
        assertTrue(FrameEase.approach(0.0D, 10.0D, 3.0D, 1.0D) > 0.0D, "a single tick moved nothing");
        // A thousand ticks is 333 half-lives and lands on the target to every bit a double has.
        assertEquals(10.0D, FrameEase.approach(0.0D, 10.0D, 3.0D, 1000.0D), EXACT);
    }

    @Test
    void aHalfLifeOfNothingSnaps() {
        // What a fresh draw and a change of dimension both want: no swinging in from wherever
        // the last formation happened to be pointing.
        assertEquals(10.0D, FrameEase.approach(0.0D, 10.0D, 0.0D, 1.0D), EXACT);
        assertEquals(10.0D, FrameEase.approach(0.0D, 10.0D, -3.0D, 1.0D), EXACT);
        assertEquals(10.0D, FrameEase.approach(0.0D, 10.0D, 3.0D, 0.0D), EXACT, "no time passed");
    }

    @Test
    void anAngleTakesTheShortWayRoundTheWrap() {
        // The defect this exists for. Due south is 180, and a wielder turning through it moves
        // the yaw from 179 to -179: two degrees, not 358.
        assertEquals(2.0D, FrameEase.difference(179.0D, -179.0D), EXACT);
        assertEquals(-2.0D, FrameEase.difference(-179.0D, 179.0D), EXACT);
        assertEquals(0.0D, FrameEase.difference(180.0D, -180.0D), EXACT, "the same bearing twice");
        // Half of two degrees, forward across the wrap, and the answer is wrapped too.
        assertEquals(-180.0F, FrameEase.approachAngle(179.0F, -179.0F, 1.0D, 1.0D), 1.0E-4D);
    }

    @Test
    void anEasedAngleStaysInsideOneTurn() {
        // It is fed back in every tick, so an unwrapped answer accumulates: a wielder spinning
        // one way for a minute would be carrying a yaw in the thousands, and the float the
        // entity syncs it through loses a bit of precision for every doubling.
        float yaw = 0.0F;
        for (int i = 0; i < 400; i++) {
            // A target that keeps running away, which is a wielder turning on the spot.
            yaw = FrameEase.approachAngle(yaw, FrameEase.wrap(i * 37.0F), 2.0D, 1.0D);
            assertTrue(yaw >= -180.0F && yaw < 180.0F, "the eased yaw left one turn: " + yaw);
        }
    }

    @Test
    void theWrapIsHalfOpenAtBothEnds() {
        assertEquals(0.0F, FrameEase.wrap(360.0F), 1.0E-4D);
        assertEquals(-180.0F, FrameEase.wrap(180.0F), 1.0E-4D, "180 belongs to the bottom end");
        assertEquals(-180.0F, FrameEase.wrap(-180.0F), 1.0E-4D);
        assertEquals(179.0F, FrameEase.wrap(-181.0F), 1.0E-4D);
        assertEquals(-90.0F, FrameEase.wrap(990.0F), 1.0E-4D, "two and three quarter turns");
    }

    @Test
    void theInterpolationIsClampedToItsOwnTick() {
        // partialTick is meant to be 0..1 and is handed straight to this by the renderer. A
        // value outside it would extrapolate the formation past where the server has actually
        // put it, which is a formation that arrives before the tick that moved it.
        assertEquals(10.0F, FrameEase.lerpAngle(0.0F, 20.0F, 0.5F), 1.0E-4D);
        assertEquals(0.0F, FrameEase.lerpAngle(0.0F, 20.0F, -3.0F), 1.0E-4D);
        assertEquals(20.0F, FrameEase.lerpAngle(0.0F, 20.0F, 7.0F), 1.0E-4D);
        assertEquals(-180.0F, FrameEase.lerpAngle(179.0F, -179.0F, 0.5F), 1.0E-4D, "across the wrap");
    }
}
