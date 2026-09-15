package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import org.junit.jupiter.api.Test;

/**
 * How a strike draws itself over the few ticks it is alive.
 *
 * <p>Every form used to appear whole on its first frame and then fade, which reads as a decal
 * switched on rather than a blade going through something. Opening the arc along its own span, and
 * growing a thrown crescent out to its reach, is what makes it read as a swing.
 *
 * <p>The two ceilings matter more than the easing. A drawing that ran ahead of its own span or its
 * own radius would be promising reach the hit shape does not have, and a player would learn to
 * trust the picture over the hitbox and be wrong.
 */
class ForgeMotionTest {

    private static final float EPSILON = 1.0E-5f;

    private static Sweep full() {
        return new Sweep(Plane.GROUND, 3.5f, 0.9f, -75.0f, 75.0f);
    }

    @Test
    void theSweepStartsAtNothingAndEndsWhole() {
        assertEquals(0.0f, ForgeMotion.swept(0.0f), EPSILON);
        assertEquals(1.0f, ForgeMotion.swept(1.0f), EPSILON);
    }

    @Test
    void theSweepOnlyEverMovesForward() {
        float last = -1.0f;
        for (int i = 0; i <= 20; i++) {
            float value = ForgeMotion.swept(i / 20.0f);
            assertTrue(value >= last, "the sweep went backwards at " + i / 20.0f);
            last = value;
        }
    }

    @Test
    void theSweepLeadsTheClock() {
        // A blade is quickest through the middle of its arc and settles at the end. Linear reads
        // mechanical, and the last frames are the ones the eye actually has time to catch.
        assertTrue(ForgeMotion.swept(0.5f) > 0.5f, "the swing is dawdling through its own middle");
    }

    @Test
    void anUnstartedArcHasNoSpanAndAFinishedOneHasAllOfIt() {
        Sweep opening = ForgeMotion.opening(full(), 0.0f);
        assertEquals(opening.fromDegrees(), opening.toDegrees(), EPSILON, "an unstarted arc is already open");

        Sweep done = ForgeMotion.opening(full(), 1.0f);
        assertEquals(full().fromDegrees(), done.fromDegrees(), EPSILON);
        assertEquals(full().toDegrees(), done.toDegrees(), EPSILON);
    }

    @Test
    void anOpeningArcIsAnchoredWhereTheSwingBegan() {
        // It opens away from its start rather than growing out of its middle: a swing travels.
        for (int i = 0; i <= 10; i++) {
            Sweep opening = ForgeMotion.opening(full(), i / 10.0f);
            assertEquals(full().fromDegrees(), opening.fromDegrees(), EPSILON, "the arc slid off its start");
        }
    }

    @Test
    void anOpeningArcNeverClaimsMoreThanItsSpan() {
        float span = Math.abs(full().toDegrees() - full().fromDegrees());
        for (int i = 0; i <= 20; i++) {
            Sweep opening = ForgeMotion.opening(full(), i / 20.0f);
            float drawn = Math.abs(opening.toDegrees() - opening.fromDegrees());
            assertTrue(drawn <= span + EPSILON,
                    "the drawing claims " + drawn + " degrees of a " + span + " degree hit shape");
        }
    }

    @Test
    void aReachingArcGrowsFromItsFractionOutToTheWholeReach() {
        Sweep start = ForgeMotion.reaching(full(), 0.25f, 0.0f);
        assertEquals(full().radius() * 0.25f, start.radius(), EPSILON);

        Sweep end = ForgeMotion.reaching(full(), 0.25f, 1.0f);
        assertEquals(full().radius(), end.radius(), EPSILON);
    }

    @Test
    void aReachingArcNeverOutrunsItsOwnReach() {
        for (int i = 0; i <= 20; i++) {
            Sweep reaching = ForgeMotion.reaching(full(), 0.25f, i / 20.0f);
            assertTrue(reaching.radius() <= full().radius() + EPSILON,
                    "the drawing reaches " + reaching.radius() + " past a hit shape of " + full().radius());
        }
    }

    @Test
    void aReachingArcKeepsItsBladeInProportion() {
        // Radius and thickness grow together, or a young crescent is a stubby paddle and an old one
        // a razor. Sweep.scaled already pairs them; this pins that reaching uses it.
        Sweep half = ForgeMotion.reaching(full(), 0.0f, ForgeMotion.progressForRadius(0.5f));
        assertEquals(half.radius() / full().radius(), half.thickness() / full().thickness(), 1.0E-3f,
                "the blade lost its proportions partway out");
    }

    @Test
    void progressIsClampedAtBothEnds() {
        // ageInTicks divided by life overshoots by a partial tick on the last frame, and an echo
        // can arrive a tick early. Neither may turn the arc inside out.
        assertEquals(0.0f, ForgeMotion.swept(-0.3f), EPSILON);
        assertEquals(1.0f, ForgeMotion.swept(1.4f), EPSILON);

        Sweep over = ForgeMotion.opening(full(), 1.6f);
        assertEquals(full().toDegrees(), over.toDegrees(), EPSILON);
    }
}
