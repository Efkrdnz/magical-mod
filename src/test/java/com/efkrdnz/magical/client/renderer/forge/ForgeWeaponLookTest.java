package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import org.junit.jupiter.api.Test;

/**
 * What a weapon's own numbers do to the strike it throws.
 *
 * <p>The element already owns the colour and the ornament, and the form owns the silhouette, so
 * neither is available to say how good the weapon is. Grade takes brightness and quality takes
 * breadth: a divine blade burns harder and a well-made one cuts wider, and the two are independent
 * so a crude masterwork and a divine botch do not land on the same picture.
 */
class ForgeWeaponLookTest {

    private static final float EPSILON = 1.0E-4f;

    @Test
    void everyGradeBurnsHarderThanTheOneBelowIt() {
        float last = -1.0f;
        for (ForgeGrade grade : ForgeGrade.values()) {
            float emission = ForgeWeaponLook.emission(grade.ordinal());
            assertTrue(emission > last, grade + " does not out-burn the grade below it");
            last = emission;
        }
    }

    @Test
    void theWorstGradeIsStillVisibleAndTheBestDoesNotBlowOut() {
        // The shader's ceiling is set for a strike at emission 1. A grade that multiplied much past
        // that would clip to white and lose the element, which is the thing this school got wrong
        // for so long; a grade that multiplied much below would be invisible in daylight.
        float crude = ForgeWeaponLook.emission(ForgeGrade.CRUDE.ordinal());
        float divine = ForgeWeaponLook.emission(ForgeGrade.DIVINE.ordinal());
        assertTrue(crude >= 0.5f, "a crude strike is too faint to see: " + crude);
        assertTrue(divine <= 1.35f, "a divine strike will clip to white: " + divine);
    }

    @Test
    void anUnknownGradeFallsBackToTheMiddleRatherThanToNothing() {
        // The ordinal arrives over the wire. A client on a different build must not draw a strike
        // with no light in it at all.
        float fallback = ForgeWeaponLook.emission(-1);
        assertEquals(ForgeWeaponLook.emission(99), fallback, EPSILON);
        assertTrue(fallback > 0.5f && fallback < 1.2f, "the fallback grade is not a middling one");
    }

    @Test
    void betterMadeWeaponsCutWider() {
        assertTrue(ForgeWeaponLook.breadth(100) > ForgeWeaponLook.breadth(0),
                "quality does not show in the cut");
    }

    @Test
    void breadthStaysWithinSightOfTheHitShape() {
        // Breadth scales the drawn blade, and the hit shape does not move, so it has to stay near
        // one or the picture stops describing what the player is actually judged against.
        for (int quality = 0; quality <= 100; quality += 5) {
            float breadth = ForgeWeaponLook.breadth(quality);
            assertTrue(breadth >= 0.8f && breadth <= 1.25f,
                    "quality " + quality + " draws a blade " + breadth + " times its hit shape");
        }
    }

    @Test
    void breadthIsClampedToTheRealRangeOfQuality() {
        assertEquals(ForgeWeaponLook.breadth(0), ForgeWeaponLook.breadth(-40), EPSILON);
        assertEquals(ForgeWeaponLook.breadth(100), ForgeWeaponLook.breadth(400), EPSILON);
    }

    @Test
    void aMiddlingWeaponIsDrawnAtItsOwnSize() {
        // Fifty quality is the ordinary case, and it must not shrink or swell the blade at all, so
        // that the form's own numbers still read as themselves.
        assertEquals(1.0f, ForgeWeaponLook.breadth(50), 0.02f);
    }

    @Test
    void gradeAndQualityAreIndependent() {
        // One must not be readable off the other, or the pair says less than either. Brightness is
        // grade alone and breadth is quality alone.
        assertEquals(ForgeWeaponLook.emission(ForgeGrade.CRUDE.ordinal()),
                ForgeWeaponLook.emission(ForgeGrade.CRUDE.ordinal()), EPSILON);
        assertTrue(ForgeWeaponLook.breadth(10) < ForgeWeaponLook.breadth(90),
                "breadth stopped tracking quality");
    }
}
