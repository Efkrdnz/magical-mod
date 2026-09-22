package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What Below is allowed to draw, measured against the volume it actually catches in.
 *
 * <p>The profile this replaced was green on every test in the project and photographed as one
 * saturated white ellipse with the terrain, the player and the sky all gone. That is the failure
 * this file exists to make impossible to ship again, and it is the same failure the subspace wall
 * had from the other side: a suite can certify a picture nobody can see, and it can equally
 * certify one nobody can see <em>past</em>. So both bounds are here - the drawing may never
 * outgrow the cylinder, and it may never shrink to nothing either.
 *
 * <p>Pure: nothing here opens a buffer or a level. A {@link VisualProfile} is plain data by
 * design - that is why it is safe on a dedicated server - so the numbers a painter will be handed
 * can be read straight off it.
 */
class BelowSilhouetteTest {

    /** Floats through {@code (float) 1.6D} and back through a 1.08 divide: a hair of slack. */
    private static final double EPSILON = 1.0E-5D;

    private static VisualProfile profile;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        profile = new BelowSkill().profile().build();
    }

    /** The one silhouette that is the object: the steel on its way up. */
    private static Silhouette steel() {
        return profile.primary();
    }

    /** The ring on the floor. The only thing drawn during the warning, so it is found by mode. */
    private static Silhouette telegraph() {
        for (Silhouette s : profile.silhouettes()) {
            if (s.family() == Silhouette.Family.MARK) {
                return s;
            }
        }
        throw new AssertionError("Below has no ground mark at all; the telegraph is the whole counterplay");
    }

    @Test
    void theSteelIsDrawnInsideTheCylinderThatCatches() {
        // SkillTargets.hostilesInCylinder measures radius ERUPT_RADIUS about the committed point
        // and y from -0.5 to +ERUPT_HEIGHT. A drawing wider than that promises a hit that never
        // lands; a drawing taller than that is a blade sticking out of the top of its own volume.
        Silhouette steel = steel();
        assertTrue(steel.sizeA() <= BelowSkill.ERUPT_RADIUS + EPSILON,
                "the eruption is drawn " + steel.sizeA() + " blocks wide but catches within "
                        + BelowSkill.ERUPT_RADIUS);
        double drawnTop = steel.sizeB() * BelowSkill.PLATE_FAN_OVERSHOOT;
        assertTrue(drawnTop <= BelowSkill.ERUPT_HEIGHT + EPSILON,
                "the eruption is drawn " + drawnTop + " blocks tall but catches only to "
                        + BelowSkill.ERUPT_HEIGHT);
    }

    @Test
    void theSteelCannotBeInvisible() {
        // The floor, and it is the half of this that a panic over the white-out would remove. A
        // drawing shrunk to a token is the subspace wall again: technically present, worth two
        // levels of 255, and certified by a green suite for months.
        Silhouette steel = steel();
        assertTrue(steel.sizeA() >= BelowSkill.ERUPT_RADIUS * 0.5D,
                "the eruption is drawn " + steel.sizeA() + " blocks wide in a "
                        + BelowSkill.ERUPT_RADIUS + " block cylinder, which is a token and not a strike");
        assertTrue(steel.sizeB() * BelowSkill.PLATE_FAN_OVERSHOOT >= BelowSkill.ERUPT_HEIGHT * 0.5D,
                "the eruption does not reach half the height it kills to");
        assertTrue(steel.count() >= 3,
                "a fan of " + steel.count() + " plates has a bearing it is edge-on from");
        assertTrue(steel.opacity() > 0.0F, "the eruption is drawn at zero opacity");
    }

    @Test
    void theSteelGrowsUpOutOfTheFloorAndIsASolid() {
        // Form.SPIKE_CLUSTER was the first answer and it is a sphere: FxMesh.spikeCluster lays its
        // spikes over a whole unit sphere on the golden angle, so at six plates three of them point
        // downward, and BodyPainter scales sizeA on all three axes and never reads sizeB - the
        // authored height was dead code and the drawing was a ball half buried in the ground.
        // PLATE_FAN is the only body form in the library that starts at y = 0 and spends both
        // extents, which is the whole of "erupts from the ground and stabs upward" in geometry.
        assertEquals(Silhouette.Form.PLATE_FAN, steel().form(),
                "the eruption has stopped being a thing that grows upward out of its own origin");
        // BODY is the depth-writing shard_body path. Every other family here adds: an eruption
        // drawn on an additive type is light, and light cannot be the object.
        assertEquals(Silhouette.Family.BODY, steel().family(),
                "the eruption is on an additive path, so it is a glow and not a blade");
    }

    @Test
    void theTelegraphIsTheRingThatCatchesAndNotTheTierDefault() {
        // Every tier below zero resolves to TierProfile.forTier(4), whose radius is 3.0 - 1.875
        // times the ring that actually catches, three and a half times its area, and then 1.4
        // times that again on a SLAM release. The circle at the committed point promised a
        // cylinder you could stand in and be missed by, and drew the promise over the terrain.
        assertEquals(BelowSkill.ERUPT_RADIUS, profile.tier().radius(), EPSILON,
                "the cast circle is not the hit ring");
        assertTrue(telegraph().sizeA() <= BelowSkill.ERUPT_RADIUS + EPSILON,
                "the ground mark is wider than the cylinder it is warning about");
        assertTrue(telegraph().sizeA() >= BelowSkill.ERUPT_RADIUS * 0.5D,
                "the ground mark is too small to be the sixteen ticks of warning");
    }

    @Test
    void theTelegraphIsOccludedByTheGroundItLiesOn() {
        // TierProfile 4 sets throughTerrain, which draws the windup circle with no depth test at
        // all. For a circle lying on the floor up to twenty blocks out that means it is painted
        // over every block and every body in front of it - including the wielder, and the one
        // reading this skill may never give is a mark on the wielder's own feet.
        assertFalse(profile.windupThroughTerrain(),
                "Below draws its telegraph over the world instead of on it");
    }

    @Test
    void theReleaseStampsNothingUnderTheWielder() {
        // ReleaseMode.SLAM hangs Mark.SIGIL_SLAM_FLASH flat under the caster's hand at
        // tier.radius() * 1.3. That kind is the one mark in the library that is a filled disc
        // rather than a stroke - rendertype_ground_mark lights it at 2.5 * (1 - phase) over a
        // colour mixed to vec3(1) for the first half of its life, on the additive twin - so at
        // the old tier radius it was a pure white disc 7.8 blocks across, hung a stride in front
        // of the caster's face. First person never caught it: that quad all but contains the eye
        // there and is seen edge-on.
        assertNotEquals(ReleaseMode.SLAM, profile.release().mode(),
                "Below stamps a filled white disc under the caster, for a strike that is elsewhere");
    }

    @Test
    void somethingIsDrawnOnEveryTickOfTheTimeline() {
        // The draw modes gate the ring off and the steel on, so a wrong mask is a skill that draws
        // nothing at all for a stretch of its own cast and fails no other test in the project.
        for (int age = 0; age <= BelowSkill.HIT_TICK; age++) {
            int mode = BelowSkill.drawModeAt(age);
            boolean any = false;
            for (Silhouette s : profile.silhouettes()) {
                any |= s.drawnIn(mode);
            }
            assertTrue(any, "nothing at all is drawn at tick " + age + " (draw mode " + mode + ")");
        }
    }

    @Test
    void theWarningIsTheRingAndTheRiseIsTheSteel() {
        int warning = BelowSkill.drawModeAt(BelowSkill.COMMIT_TICK - 1);
        int rise = BelowSkill.drawModeAt(BelowSkill.COMMIT_TICK);
        assertNotEquals(warning, rise, "the drawing does not change at the tick the strike locks");
        assertTrue(telegraph().drawnIn(warning), "the ring is not on the ground during the warning");
        assertFalse(steel().drawnIn(warning),
                "the blades are already standing in the world during the sixteen ticks of warning, "
                        + "so the dodge reads off the steel and not off the mark that stays put");
        assertTrue(steel().drawnIn(rise), "nothing comes up when the strike commits");
    }
}
