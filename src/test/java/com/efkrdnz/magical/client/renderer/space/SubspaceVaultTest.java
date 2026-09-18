package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The vault: what a boundary looks like from the one place anybody ever looks at it from.
 *
 * <p>The dome this replaces was tested thoroughly and every one of those tests was about the
 * wrong viewer. They measured the mean alpha over the projected disc, the exactness of the
 * two-crossing identity and the fact that a dozen layers cannot clip - all properties of a viewer
 * standing outside, which is a viewer who cannot exist in single player, because a domain
 * discards itself the moment its owner leaves it. The caster stands at the centre, and from the
 * centre of a sphere every sight line runs along the surface normal, so the angle-dependent
 * transparency the whole wall was built on varied by ten decimal places of nothing and the wall
 * was a flat seven percent that moved a dark background by two values out of two hundred and
 * fifty-five. Four green tests certified it.
 *
 * <p>So everything here is a property of the view from the middle, and {@link
 * #theWallCannotBeInvisible()} is the one that would have failed.
 */
class SubspaceVaultTest {

    /** The radii the skill can actually raise, from {@code BASE_RADIUS} to {@code MAX_RADIUS}. */
    private static final double[] RADII = {5.0, 6.5, 8.0, 10.0, 12.0, 14.0, 16.0};

    /**
     * The floor a stroke must clear against any background at all, in eight-bit levels.
     *
     * <p>Under about four levels a change is invisible on a busy background and under eight it is
     * easy to miss. The shipping wall delivered three against blackstone.
     */
    private static final double VISIBLE_LEVELS = 48.0;

    @Test
    @DisplayName("no background in the game can hide a stroke")
    void theWallCannotBeInvisible() {
        for (int channel = 0; channel < 3; channel++) {
            double floor = SubspaceOptics.chamferFloor(channel) * 255.0;
            assertTrue(floor >= VISIBLE_LEVELS,
                    "channel " + channel + " can be swallowed: worst case " + floor + " levels");
        }
        // The closed form is a claim about every background there is, so walk them all and check
        // it. A stroke has a lip that darkens and a lip that lightens; they move a background in
        // opposite directions, so whichever one the background defeats, the other one carries.
        for (int channel = 0; channel < 3; channel++) {
            double body = SubspaceOptics.component(SubspaceOptics.BODY, channel);
            double glaze = SubspaceOptics.component(SubspaceOptics.GLAZE, channel);
            double floor = SubspaceOptics.chamferFloor(channel);
            for (int level = 0; level <= 255; level++) {
                double background = level / 255.0;
                double shadow = Math.abs(SubspaceOptics.A_SHADOW * (body - background));
                double highlight = Math.abs(SubspaceOptics.A_HIGHLIGHT * (glaze - background));
                assertTrue(Math.max(shadow, highlight) >= floor - 1.0E-9D,
                        "background " + level + " on channel " + channel + " beats the closed form");
            }
        }
    }

    @Test
    @DisplayName("every stroke is wide enough to have two lips")
    void everyLipResolves() {
        double[] widths = {
                SubspaceOptics.PX_RIB, SubspaceOptics.PX_RIB * SubspaceOptics.RIB_LIT_GAIN,
                SubspaceOptics.PX_SPRING, SubspaceOptics.PX_FOOT, SubspaceOptics.PX_OCULUS,
                SubspaceOptics.PX_NOTCH, SubspaceOptics.PX_GLYPH, SubspaceOptics.PX_RIPPLE};
        for (double pixels : widths) {
            // Below a pixel and a half each, the two lips average into one mid-blue line and the
            // whole mechanism - a lit side and a shadowed side of opposite sign - is gone.
            assertTrue(SubspaceOptics.lipCorePixels(pixels) >= SubspaceOptics.MIN_LIP_PIXELS,
                    pixels + "px leaves only " + SubspaceOptics.lipCorePixels(pixels) + "px of lip");
        }
    }

    @Test
    @DisplayName("a rib leans, and how far it leans is how much law is in force")
    void theLeanIsTheLaw() {
        double previous = -1.0;
        for (int laws = 0; laws <= SubspaceVault.RIB_COUNT; laws++) {
            double lean = SubspaceVault.leanDegrees(SubspaceVault.springLatitudeDegrees(16.0), laws);
            assertTrue(lean > previous, "writing a law did not wind the domain any tighter");
            previous = lean;
        }
        assertTrue(SubspaceVault.leanDegrees(SubspaceVault.springLatitudeDegrees(16.0), 0) >= 18.0,
                "an empty domain stands too near vertical to read as anything but bars");
        assertTrue(SubspaceVault.leanDegrees(SubspaceVault.springLatitudeDegrees(16.0), SubspaceVault.RIB_COUNT) >= 45.0,
                "a fully legislated domain is not visibly wound");
    }

    /**
     * The failure this whole design reacts to: a member drawn on a meridian projects to an exactly
     * straight line - not nearly straight, exactly - because the eye sits on the polar axis and
     * every meridian plane therefore contains it. A straight near-vertical bar at twenty blocks is
     * a tree trunk, a fence post or a portal jamb. A rib has to leave its own meridian.
     */
    @Test
    @DisplayName("a rib is not a meridian at any radius or any law count")
    void aRibIsNotAMeridian() {
        for (double radius : RADII) {
            double spring = SubspaceVault.springLatitudeDegrees(radius);
            for (int laws = 0; laws <= SubspaceVault.RIB_COUNT; laws++) {
                double at = SubspaceVault.ribBearingDegrees(0, spring, radius, laws);
                double top = SubspaceVault.ribBearingDegrees(0, SubspaceVault.OCULUS_LATITUDE_DEGREES, radius, laws);
                assertEquals(0.0, at, 1.0E-9D, "a rib does not leave its slot bearing at the springing");
                assertTrue(Math.abs(top - at) >= 25.0,
                        "R=" + radius + " with " + laws + " laws sweeps only " + Math.abs(top - at) + " degrees");
            }
        }
    }

    @Test
    @DisplayName("the springing course stands at the same height however big the domain is")
    void theCourseStandsAtOneHeight() {
        for (double radius : RADII) {
            double latitude = SubspaceVault.springLatitudeDegrees(radius);
            assertEquals(SubspaceVault.SPRING_ELEVATION_DEGREES,
                    SubspaceVault.apparentElevationDegrees(latitude, radius), 1.0E-6D,
                    "the course drifts at R=" + radius);
            // And it has to clear the floor, or the reading is buried: the sphere is centred on the
            // caster chest, so a course at a fixed latitude sinks underground as the radius grows.
            assertTrue(radius * Math.sin(Math.toRadians(latitude)) - 0.90 > 0.5,
                    "the course is within half a block of the ground at R=" + radius);
        }
    }

    @Test
    @DisplayName("the footprint is where the wall actually meets the world")
    void theFootprintIsOnTheGround() {
        for (double radius : RADII) {
            double latitude = SubspaceVault.footprintLatitudeDegrees(radius);
            assertEquals(-SubspaceVault.FOOTPRINT_DROP, radius * Math.sin(Math.toRadians(latitude)), 1.0E-9D,
                    "the footprint is not on the floor at R=" + radius);
            double elevation = SubspaceVault.apparentElevationDegrees(latitude, radius);
            assertTrue(elevation < 0.0 && elevation > -25.0,
                    "the footprint is off the bottom of the frame at R=" + radius + ": " + elevation);
        }
    }

    /**
     * Nothing may sit on the line the player aims along. This is a test the replaced design needed
     * and did not have: it checked the height of one ring and never checked the uprights, which ran
     * from pole to pole straight through the reticle.
     */
    @Test
    @DisplayName("nothing is drawn on the aiming line")
    void theAimingLineIsClear() {
        for (double radius : RADII) {
            double[] latitudes = {
                    SubspaceVault.footprintLatitudeDegrees(radius),
                    SubspaceVault.springLatitudeDegrees(radius),
                    SubspaceVault.bossLatitudeDegrees(radius),
                    SubspaceVault.OCULUS_LATITUDE_DEGREES};
            for (double latitude : latitudes) {
                double elevation = SubspaceVault.apparentElevationDegrees(latitude, radius);
                assertTrue(Math.abs(elevation) >= SubspaceVault.AIM_CLEARANCE_DEGREES,
                        "latitude " + latitude + " at R=" + radius + " sits " + elevation + " degrees off the sight line");
            }
            // A rib never dips below the course it springs from, so the whole colonnade is above
            // the aiming line by construction rather than by luck.
            assertTrue(SubspaceVault.springLatitudeDegrees(radius) < SubspaceVault.bossLatitudeDegrees(radius),
                    "the boss is below the springing at R=" + radius);
        }
    }

    @Test
    @DisplayName("there is a colonnade in frame whichever way you face")
    void everyBearingHasAColonnade() {
        // The reading the old dome failed hardest: all twelve of its marks lived in a ninety-nine
        // degree wedge round north, so from three quarters of all bearings a fully legislated
        // domain and an empty one were the same picture.
        assertTrue(SubspaceVault.ribsInView() >= 3,
                "fewer than three ribs are in frame from some bearing");
        assertEquals(0.0, 360.0 % SubspaceVault.RIB_PITCH_DEGREES, 1.0E-9D, "the ribs do not close the circle");
        assertEquals(SubspaceVault.RIB_COUNT, (int) Math.round(360.0 / SubspaceVault.RIB_PITCH_DEGREES),
                "the pitch and the count disagree");
    }

    @Test
    @DisplayName("a boss sits on its own rib")
    void aBossSitsOnItsOwnRib() {
        for (double radius : RADII) {
            double spring = SubspaceVault.springLatitudeDegrees(radius);
            double boss = SubspaceVault.bossLatitudeDegrees(radius);
            assertTrue(boss > spring && boss < SubspaceVault.OCULUS_LATITUDE_DEGREES,
                    "the boss is not on the rib at R=" + radius);
            for (int laws = 1; laws <= SubspaceVault.RIB_COUNT; laws++) {
                for (int slot = 0; slot < SubspaceVault.RIB_COUNT; slot++) {
                    assertEquals(SubspaceVault.ribBearingDegrees(slot, boss, radius, laws),
                            SubspaceVault.bossBearingDegrees(slot, radius, laws), 1.0E-9D,
                            "the boss has drifted off its rib");
                }
            }
            // It also has to stay on screen: a reading nobody can see is not a reading.
            assertTrue(SubspaceVault.apparentElevationDegrees(boss, radius) < 34.0,
                    "the boss is above the top of the frame at R=" + radius);
        }
    }

    @Test
    @DisplayName("a slot bearing belongs to its category for good")
    void aBearingIsOwnedForGood() {
        for (int slot = 0; slot < SubspaceVault.RIB_COUNT; slot++) {
            assertEquals(slot * SubspaceVault.RIB_PITCH_DEGREES, SubspaceLedger.slotBearingDegrees(slot), 1.0E-9D,
                    "slot " + slot + " does not own its own bearing");
        }
        // Gravity owns north, because gravity is the law with a second reading, that reading is a
        // level line, and a level line is what everything else is counted from.
        assertEquals(0.0, SubspaceLedger.slotBearingDegrees(0), 1.0E-9D, "gravity does not stand due north");
    }
}
