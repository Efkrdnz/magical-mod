package com.efkrdnz.magical.client.renderer.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer.Geometry;
import org.junit.jupiter.api.Test;

/**
 * The shape of a thrown blade, and the one thing it is not allowed to point at.
 *
 * <p>The thrower's eye <em>is</em> the flight line. A long thin thing seen down its own axis
 * projects to its cross-section and nothing else, so a blade flown point-first is a one-to-two
 * pixel vertical line to the person who threw it - and a flat plate is worse rather than better,
 * because it has no symmetry left to collapse into and flickers as the perspective divide fights
 * its normal. That is this mod's own bolt problem and {@code ForgeWaveFront}'s written verdict
 * arriving together, and the answer is the same both times: give the drawn thing a real
 * cross-section, and point it somewhere other than where it is going.
 *
 * <p>So these are the two halves of that, in numbers, measured against the volume the raycast
 * actually catches in so the picture can never outgrow the hit - the way
 * {@code WaveSilhouetteTest} measures the wave. Pure geometry: nothing here opens a buffer, and
 * {@code SwordBladeRenderer$Geometry} is a nested class precisely so that measuring a silhouette
 * never drags {@code EntityRenderer} into a unit test.
 */
class SwordSilhouetteTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    void aBladeIsNeverFlownNoseOn() {
        // The cant is a rotation in the flight frame, so it is the same angle at every bearing and
        // every pitch there is - including straight up and straight down, where orientAlong's yaw
        // is undefined and the composition has to survive it anyway.
        for (float yaw = 0.0f; yaw < 360.0f; yaw += 17.0f) {
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 15.0f) {
                double[] flight = look(yaw, pitch);
                double cant = Geometry.cantDegrees(flight[0], flight[1], flight[2]);
                assertTrue(cant >= Geometry.MIN_CANT_DEGREES,
                        "a blade flown at yaw " + yaw + " pitch " + pitch + " leans only " + cant
                                + " degrees off its own flight line, which is a line to the thrower");
                assertEquals(Geometry.CANT_YAW, cant, 1.0E-6D,
                        "the cant is not the same at yaw " + yaw + " pitch " + pitch);
            }
        }
    }

    @Test
    void theCantIsWhatTheThrowerSees() {
        // The whole of the fix, as a ratio: what the thrower gets with the cant on, against what
        // they would have got with it off. Point-first, all they see is the section.
        double noseOn = Geometry.noseOnArea();
        double canted = Geometry.cantedArea();
        assertTrue(noseOn > 0.0D, "the blade has no section at all");
        assertTrue(canted >= noseOn * 5.0D,
                "the cant buys only " + (canted / noseOn) + " times the nose-on silhouette");
    }

    @Test
    void aBladeIsASolidAndNotASheet() {
        // A sheet has a view from which its projected area is exactly zero, which is how a swing
        // seen along its own plane came to disappear before ForgeRibbon grew a cross-section. A
        // diamond prism has no such view: its worst is its own section, seen end-on, and the
        // previous test is what keeps the thrower away from that one.
        double worst = Double.MAX_VALUE;
        for (int a = 0; a < 72; a++) {
            for (int b = 0; b <= 36; b++) {
                double azimuth = Math.toRadians(a * 5.0D);
                double elevation = Math.toRadians(-90.0D + b * 5.0D);
                double flat = Math.cos(elevation);
                worst = Math.min(worst, Geometry.silhouetteArea(
                        Math.cos(azimuth) * flat, Math.sin(azimuth) * flat, Math.sin(elevation)));
            }
        }
        assertTrue(worst > 0.0D, "there is a view from which the blade is not drawn at all");
        assertEquals(Geometry.noseOnArea(), worst, 1.0E-6D,
                "the blade's worst view is not its own section, so something else has gone thin");
    }

    @Test
    void aBladeIsDrawnNoWiderThanTheCorridorItCatchesIn() {
        // An arc measured from the centre of its own circle put every one of the wave's pixels a
        // radius off the strike; a blade canted too hard does the same thing in miniature. The
        // steel has to sit inside what the raycast reaches sideways, whatever the Edge on it -
        // which is why the section does not grow with the metal and only the palette does.
        double reach = Geometry.lateralReach();
        assertTrue(reach <= Geometry.CATCH_HALF_EXTENT,
                "the blade is drawn " + reach + " blocks off its flight line but only catches within "
                        + Geometry.CATCH_HALF_EXTENT);
        // And it is a constant, not a function of the metal: Edge buys three bits of palette ramp
        // and nothing else, because the volume that catches is a property of the entity.
        assertEquals(Geometry.WIDTH * 0.5D, Geometry.HALF_WIDTH, EPSILON,
                "the section has stopped being the width the hit volume was measured against");
    }

    @Test
    void aBladeIsDrawnNoLongerThanOneStepOfItsOwnRaycast() {
        // A blade flies by explicit raycast, one step a tick. Drawn longer than a step, the steel
        // arrives somewhere the hit test has not been yet, which reads as a miss on contact.
        double reach = Geometry.axialReach();
        assertTrue(reach <= Geometry.FLIGHT_PER_TICK,
                "the blade is drawn " + reach + " blocks along its flight and steps only "
                        + Geometry.FLIGHT_PER_TICK + " a tick");
    }

    @Test
    void theCantIsBoundedFromBothSides() {
        // Both bounds are real and they pull opposite ways: under the floor the thrower is looking
        // down a line, over the ceiling the drawn steel hangs outside the volume that catches. The
        // ceiling is what the lateral reach works out to, so state it as the angle.
        assertTrue(Geometry.CANT_YAW > Geometry.MIN_CANT_DEGREES,
                "the cant is at or under its own floor");
        double ceiling = Math.toDegrees(Math.asin(
                (Geometry.CATCH_HALF_EXTENT - Geometry.HALF_WIDTH) / (Geometry.LENGTH * 0.5D)));
        assertTrue(Geometry.CANT_YAW < ceiling,
                "a cant of " + Geometry.CANT_YAW + " degrees reaches past the " + ceiling
                        + " degrees the hit volume allows");
    }

    @Test
    void theBladeIsOrientedOntoTheFlightVectorAndNotBesideIt() {
        // orientAlong's job, restated: local +Z lands exactly on the flight vector. The cant is
        // measured against that, so a sign error here would move every blade in the kit at once
        // and leave the cant test perfectly green.
        for (float yaw = 0.0f; yaw < 360.0f; yaw += 23.0f) {
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 18.0f) {
                double[] flight = look(yaw, pitch);
                double[] forward = Geometry.orient(0.0D, 0.0D, 1.0D, flight[0], flight[1], flight[2]);
                assertEquals(flight[0], forward[0], 1.0E-9D, "x at yaw " + yaw + " pitch " + pitch);
                assertEquals(flight[1], forward[1], 1.0E-9D, "y at yaw " + yaw + " pitch " + pitch);
                assertEquals(flight[2], forward[2], 1.0E-9D, "z at yaw " + yaw + " pitch " + pitch);
                double[] axis = Geometry.bladeAxis(flight[0], flight[1], flight[2]);
                assertEquals(1.0D, Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]),
                        1.0E-9D, "the blade's axis is not a unit vector at yaw " + yaw);
            }
        }
    }

    /** Minecraft's own look vector, so the test asks the question in the game's handedness. */
    private static double[] look(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }
}
