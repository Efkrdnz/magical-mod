package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the other four Sword skills are allowed to draw, measured against the things they do.
 *
 * <p>Companion to {@code BelowSilhouetteTest}, and written for the same reason: this kit has
 * produced four separate green-build-unlookable-frame defects, and every one of them was a number
 * in a {@code VisualProfile} that no test had an opinion about. A {@link VisualProfile} is plain
 * data by design - that is why it is safe on a dedicated server - so everything a painter will be
 * handed can be read straight off it without opening a buffer or a level.
 *
 * <p>Both bounds, always. A ceiling alone invites the panic fix, and the panic fix is how the
 * subspace wall came to be certified invisible by four green tests.
 */
class SwordKitProfileTest {

    /** Floats through a degree-to-radian trip: a hair of slack. */
    private static final double EPSILON = 1.0E-5D;

    /**
     * Tier 4 is where every tier below zero lands, and 3.0 is its radius. Four of these six
     * skills took it without saying so, which is the defect this file is mostly about.
     */
    private static final float TIER_DEFAULT_RADIUS = TierProfile.forTier(4).radius();

    /** The default field of view is 70 degrees, so the frame is 35 degrees from centre to edge. */
    private static final double HALF_FRAME_DEGREES = 35.0D;

    private static VisualProfile callTheBlade;
    private static VisualProfile theStance;
    private static VisualProfile theKeel;
    private static VisualProfile oneBlade;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        callTheBlade = new CallTheBladeSkill().profile().build();
        theStance = new SwordStanceSkill().profile().build();
        theKeel = new TheKeelSkill().profile().build();
        oneBlade = new OneBladeSkill().profile().build();
    }

    // ---- the cast circles ------------------------------------------------------------------

    @Test
    void noSwordCircleIsStillTheTierDefault() {
        // Not a style point. TierProfile.forTier(4) is the row every negative tier resolves to,
        // and its radius is 3.0 - a disc six blocks across for a class whose entire lattice runs
        // one to six blocks from the wielder's chest. A circle that size is not a mark on the
        // Array, it is a disc the Array fits inside.
        for (VisualProfile profile : new VisualProfile[] {callTheBlade, theStance, theKeel, oneBlade}) {
            assertNotEquals(TIER_DEFAULT_RADIUS, profile.tier().radius(), EPSILON,
                    profile.skillId() + " still draws its cast circle at the tier default of "
                            + TIER_DEFAULT_RADIUS + " blocks");
        }
    }

    @Test
    void theToggleMarkIsTheRingTheSwordsComeOutOf() {
        // Call the Blade raises a whole formation round the wielder, and a formation stands
        // inside Formation.MAX_EXTENT of them. A mark wider than that is a mark of somewhere the
        // swords are not; at the tier default of 3.0 it was most of a block wider than the
        // widest sword in the widest stance.
        assertTrue(callTheBlade.tier().radius() <= Formation.MAX_EXTENT + EPSILON,
                "the toggle's mark is " + callTheBlade.tier().radius()
                        + " blocks of radius where the formation itself reaches only "
                        + Formation.MAX_EXTENT);
        // The floor. The mark has to reach past the wielder's own body or it is a disc under
        // their feet rather than a ring the steel appears out of.
        assertTrue(callTheBlade.tier().radius() >= Formation.BODY_CLEARANCE * 2.0D,
                "the toggle's mark is " + callTheBlade.tier().radius()
                        + " blocks of radius, which does not clear the wielder standing in it");
    }

    @Test
    void aRingAtTheHandFitsInsideTheFrameItIsDrawnOn() {
        // An EYE_FORWARD circle is never placed in the world: SpellFx.windup hangs it
        // SwordStanceSkill.HAND_DISTANCE along the look from the eye and turns it to face the
        // camera, so its size is atan(radius / HAND_DISTANCE) and nothing else. At the tier
        // default that is 73 degrees - a disc running off all four edges of a 70-degree frame,
        // which is the picture the first Loose capture returned.
        double ceiling = SwordStanceSkill.HAND_DISTANCE * Math.tan(Math.toRadians(HALF_FRAME_DEGREES));
        double floor = SwordStanceSkill.HAND_DISTANCE * Math.tan(Math.toRadians(6.0D));
        for (VisualProfile profile : new VisualProfile[] {theStance, oneBlade}) {
            double radius = profile.tier().radius();
            assertTrue(radius <= ceiling + EPSILON,
                    profile.skillId() + " hangs a ring of radius " + radius + " at "
                            + SwordStanceSkill.HAND_DISTANCE + " blocks, which runs off the edge of the frame");
            assertTrue(radius >= floor,
                    profile.skillId() + " hangs a ring of radius " + radius + " at "
                            + SwordStanceSkill.HAND_DISTANCE + " blocks, which is a dot on the crosshair");
        }
    }

    @Test
    void theKeelMarksOnePlaceToStand() {
        // The Keel puts one sword under the feet. A ground mark wider than the wielder's own
        // clearance is a mark of the whole formation rather than of the one sword that is
        // bearing them.
        assertTrue(theKeel.tier().radius() <= Formation.BODY_CLEARANCE * 2.0D + EPSILON,
                "the Keel's footing mark is " + theKeel.tier().radius()
                        + " blocks of radius, wider than the wielder standing on it");
        assertTrue(theKeel.tier().radius() >= 0.4D,
                "the Keel's footing mark is " + theKeel.tier().radius()
                        + " blocks of radius, which is smaller than the wielder standing on it");
    }

    @Test
    void aCircleThatLiesOnASurfaceIsOccludedByIt() {
        // TierProfile 4 sets throughTerrain, which draws the windup circle with NO_DEPTH_TEST.
        // For a circle tilted onto the face the crosshair is on, or lying on the floor under the
        // caster, that means it is painted over every block and every body in front of it -
        // including the wielder. Below took this decision first, for the same reason. The two
        // EYE_FORWARD circles are deliberately not in this list: a ring 0.9 blocks off the eye
        // has nothing between it and the camera to be occluded by.
        assertFalse(callTheBlade.windupThroughTerrain(),
                "Call the Blade draws its bearing mark over the world instead of on the face it is written on");
        assertFalse(theKeel.windupThroughTerrain(),
                "the Keel draws its footing mark over the wielder's own legs");
    }

    @Test
    void noneOfTheseFourStampsAFilledDiscUnderTheWielder() {
        // ReleaseMode.SLAM hangs Mark.SIGIL_SLAM_FLASH flat under the caster's hand at
        // tier.radius() * 1.3. That kind is the one mark in the library that is a filled disc
        // rather than a stroke - rendertype_ground_mark lights it at 2.5 * (1 - phase) over a
        // colour mixed to vec3(1) for the first half of its life, on the additive twin. Below
        // shipped it and photographed as a white lightbox; these four must not acquire it.
        for (VisualProfile profile : new VisualProfile[] {callTheBlade, theStance, theKeel, oneBlade}) {
            assertNotEquals(ReleaseMode.SLAM, profile.release().mode(),
                    profile.skillId() + " has taken the filled-disc release flash");
        }
    }

    // ---- what the family painters are allowed to draw ---------------------------------------

    @Test
    void nothingOnTheArrayProfileReachesTheFamilyPainters() {
        // SwordArrayEntity and SwordBladeEntity both carry CALL_THE_BLADE's id - the formation
        // and every blade in flight wear this profile - and both renderers extend
        // ProfileRendererShell and call super.render, which walks profile.silhouettes() and
        // paints every one whose mode mask admits the entity's draw mode.
        //
        // There is no painter registered under "call_the_blade", so CustomPainters.paint took
        // its "visible fallback so a missing painter is noticed" branch: Orb.PLASMA at
        // max(0.4, sizeA * 0.5) on the additive plasma_orb type, whose PLASMA branch reaches
        // core 1.8, mixes 85% toward white and multiplies by the glow again - it clips to white
        // at any size. The Array's entity carries Life 0, so FxContext.fade() never falls: that
        // was a one-block white disc parked on the wielder's chest for as long as they held an
        // Array, and another on every blade in the air. Both renderers already draw everything
        // this profile is about, so the right number of silhouettes for them to add is none.
        for (Silhouette silhouette : callTheBlade.silhouettes()) {
            for (int mode = 0; mode < 8; mode++) {
                assertFalse(silhouette.drawnIn(mode),
                        "Call the Blade's " + silhouette.family() + " silhouette is painted in draw mode "
                                + mode + ", on top of the Array and on every blade in flight");
            }
        }
    }

    @Test
    void theStancePaintsNoFallbackEither() {
        // Nothing carries SWORD_STANCE onto an effect entity today, so this costs nothing - but
        // "sword_stance" is not a registered painter id either, and the day somebody hands this
        // profile to an entity the blown-white fallback above is what they get.
        for (Silhouette silhouette : theStance.silhouettes()) {
            for (int mode = 0; mode < 8; mode++) {
                assertFalse(silhouette.drawnIn(mode),
                        "Sword Stance's " + silhouette.family() + " silhouette is painted in draw mode " + mode);
            }
        }
    }

    // ---- the greatsword ----------------------------------------------------------------------

    @Test
    void theGreatswordIsNeverDrawnLongerThanTheBladeItIs() {
        // oneBladeReach is 2.5 + 0.18 * Edge, so the shortest fusion the skill can make - one
        // point of Edge off one station - is 2.68 blocks. The profile said 9.0, which is
        // oneBladeReach at Station.EDGE_MAX: every fusion in the game, down to the two-point
        // one, was drawn as the largest blade the class can make. A BODY silhouette cannot read
        // the synced radius that carries the real length (ProfileRendererShell feeds it to MARK,
        // SWARM, some FIELD forms and FILAMENT lengths only), so the drawing takes the one
        // length that is true of every fusion.
        double shortest = SwordMath.oneBladeReach(1);
        assertTrue(oneBlade.primary().sizeB() <= shortest + EPSILON,
                "the greatsword is drawn " + oneBlade.primary().sizeB()
                        + " blocks long and the shortest one the skill can make is " + shortest);
        // The floor. A vanilla sword's blade is about a block; the thing twelve of them fuse
        // into may not be drawn shorter than that, or the panic fix has replaced one wrong
        // reading with the opposite one.
        assertTrue(oneBlade.primary().sizeB() >= 1.5F,
                "the greatsword is drawn " + oneBlade.primary().sizeB() + " blocks long, which is a knife");
    }

    @Test
    void theGreatswordLiesAlongTheAimAndIsASolid() {
        // Form.PRISM was the first answer and it is a post: BodyPainter scales FxMesh.prism by
        // (sizeA, sizeB, sizeA) with the mesh spanning y = 0..1, and PRISM is not one of
        // ProfileRendererShell's travel forms, so the pose is never turned. Nine blocks of that
        // grew straight up out of a hand 0.6 blocks in front of the eye and a quarter of a block
        // below it - a bar forty degrees wide standing in the middle of first person for the
        // whole carry. CROSSED_BLADES is a travel form and runs z = 0..length, so it leaves the
        // hand along the aim.
        assertEquals(Silhouette.Form.CROSSED_BLADES, oneBlade.primary().form(),
                "the greatsword has stopped being a thing that lies along the direction it is pointed");
        // BODY is the depth-writing shard_body path. Every other family adds: a greatsword drawn
        // on an additive type is light, and light cannot be the object.
        assertEquals(Silhouette.Family.BODY, oneBlade.primary().family(),
                "the greatsword is on an additive path, so it is a glow and not a blade");
        assertTrue(oneBlade.primary().opacity() > 0.0F, "the greatsword is drawn at zero opacity");
    }

    @Test
    void theGreatswordIsBroadEnoughToBeSeenEdgeOn() {
        // BodyPainter takes a CROSSED_BLADES cross-section as 0.22 * sizeA on both axes, and the
        // caster is behind the blade looking down its own length, where a thin quad is a line.
        // A quarter of a block across the flat is the floor for the first-person read; the
        // ceiling is the blade being half as wide as it is long, which is a plank.
        float breadth = 0.22F * oneBlade.primary().sizeA();
        assertTrue(breadth >= 0.12F,
                "the greatsword is " + (2.0F * breadth) + " blocks across the flat, which is a line end-on");
        assertTrue(2.0F * breadth <= oneBlade.primary().sizeB() * 0.5F,
                "the greatsword is " + (2.0F * breadth) + " blocks across and only "
                        + oneBlade.primary().sizeB() + " long, which is a plank");
    }
}
