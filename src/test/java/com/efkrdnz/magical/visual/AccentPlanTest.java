package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.TierProfile;
import org.junit.jupiter.api.Test;

/** The matter layer's numbers, and the lighter shader pass they buy. */
class AccentPlanTest {

    /** The vertical field of view the dev client runs at: {@code fov:0.55} is 70 + 0.55 x 40 degrees. */
    private static final double DEV_FOV_DEGREES = 92.0D;

    /** How far in front of the eyes {@code SpellFx.windup} hangs an EYE_FORWARD circle. */
    private static final double SIGIL_DISTANCE = 0.9D;

    @Test
    void anAccentedFlashIsLessThanHalfTheLegacyOneAndShorterLived() {
        assertTrue(AccentPlan.FLASH_SCALE * 2.0F < AccentPlan.LEGACY_FLASH_SCALE);
        assertTrue(AccentPlan.FLASH_TICKS < 10);
        assertTrue(AccentPlan.FLASH_OPACITY < 1.0F);
    }

    @Test
    void theCasterOwnSigilTakesUnderAnEighthOfTheFrameAtEveryTier() {
        // at the narrowest field of view anybody plays at (vanilla's 70) the frame is smallest
        for (double fov : new double[] {70.0D, DEV_FOV_DEGREES}) {
            double halfHeight = Math.tan(Math.toRadians(fov * 0.5D)) * SIGIL_DISTANCE;
            for (int tier = 0; tier <= 4; tier++) {
                float radius = AccentPlan.ownCircleRadius(CircleAnchor.EYE_FORWARD, TierProfile.forTier(tier).radius(), tier);
                assertTrue(radius / halfHeight < 0.25D, "tier " + tier + " at fov " + fov + ": " + radius);
            }
        }
    }

    @Test
    void theCasterOwnSigilNeverCoversTheCrosshair() {
        // the sigil's middle sits off the line of sight by a share of its distance; its rim must
        // stay clear of the line at every tier, or the aim point is under a ring again
        double offCentre = Math.hypot(AccentPlan.OWN_SIGIL_RIGHT, AccentPlan.OWN_SIGIL_DOWN) * SIGIL_DISTANCE;
        for (int tier = 0; tier <= 4; tier++) {
            float radius = AccentPlan.ownCircleRadius(CircleAnchor.EYE_FORWARD, TierProfile.forTier(tier).radius(), tier);
            assertTrue(radius < offCentre, "tier " + tier + ": " + radius + " against " + offCentre);
        }
        // and it is dim enough not to be the brightest thing in a daylight frame
        assertTrue(AccentPlan.ownCircleOpacity(CircleAnchor.EYE_FORWARD) <= 0.4F);
        assertTrue(AccentPlan.OWN_HAND_ORB_OPACITY <= 0.4F);
    }

    @Test
    void theCasterOwnSigilStillGrowsWithTier() {
        float previous = 0.0F;
        for (int tier = 0; tier <= 4; tier++) {
            float radius = AccentPlan.ownCircleRadius(CircleAnchor.EYE_FORWARD, TierProfile.forTier(tier).radius(), tier);
            assertTrue(radius > previous, "tier " + tier);
            previous = radius;
        }
    }

    @Test
    void aCircleNobodyIsStandingInIsDrawnWhole() {
        assertEquals(2.4F, AccentPlan.ownCircleRadius(CircleAnchor.AIM_SURFACE, 2.4F, 3));
        assertEquals(2.4F, AccentPlan.ownCircleRadius(CircleAnchor.SKY, 2.4F, 3));
        assertEquals(1.0F, AccentPlan.ownCircleOpacity(CircleAnchor.AIM_SURFACE));
    }

    @Test
    void theCasterOwnGroundCircleStaysUnderTheBottomOfALevelView() {
        // the eye is 1.62 over the feet, the circle 0.06 over them; a level view at the dev fov
        // ends 46 degrees below the horizon, and only a rim steeper than that is out of frame
        for (int tier = 0; tier <= 4; tier++) {
            float radius = AccentPlan.ownCircleRadius(CircleAnchor.GROUND, TierProfile.forTier(tier).radius(), tier);
            double below = Math.toDegrees(Math.atan2(1.56D, radius));
            assertTrue(below > 40.0D, "tier " + tier + ": the far rim sits " + below + " degrees down");
        }
        assertTrue(AccentPlan.ownCircleOpacity(CircleAnchor.GROUND) <= 0.3F);
    }

    @Test
    void theCasterOwnMuzzleFlashIsASparkAtTheHand() {
        double halfHeight = Math.tan(Math.toRadians(35.0D)) * AccentPlan.HAND_REACH;
        float previous = 0.0F;
        for (int tier = 0; tier <= 4; tier++) {
            float radius = AccentPlan.ownMuzzleRadius(tier);
            assertTrue(radius / halfHeight < 0.3D, "tier " + tier + ": " + radius);
            assertTrue(radius > previous, "tier " + tier);
            previous = radius;
            // everybody else still sees the flash the release always threw, at the share it keeps
            assertTrue(radius < (0.5F + tier * 0.15F) * AccentPlan.MUZZLE_SCALE, "tier " + tier);
        }
        assertTrue(AccentPlan.OWN_MUZZLE_OPACITY < 0.7F);
    }

    @Test
    void theCasterOwnSlamLiesOnTheFloorAndFaint() {
        // the flash is taken from the hand the release is sent from down to the feet it slams
        assertTrue(AccentPlan.EYE_HEIGHT - AccentPlan.HAND_DROP > 1.2D);
        assertEquals(AccentPlan.ownCircleRadius(CircleAnchor.GROUND, 2.0F, 2) / 2.0F, AccentPlan.OWN_SLAM_SCALE);
        assertEquals(AccentPlan.ownCircleOpacity(CircleAnchor.GROUND), AccentPlan.OWN_SLAM_OPACITY);
    }

    @Test
    void theShaderKeepsUnderAThirdOfTheMatterItUsedToThrow() {
        assertTrue(AccentPlan.SHADER_MATTER_SHARE <= 1.0F / 3.0F);
        // tier four threw 12 + 12 x 4 additive motes; the share of that is under twenty
        assertTrue(Math.round(60 * AccentPlan.SHADER_MATTER_SHARE) < 20);
    }

    @Test
    void particleCountsGrowWithTierAndScaleAndStayBounded() {
        for (int tier = 0; tier < 4; tier++) {
            assertTrue(AccentPlan.impactCount(tier + 1, 1.0F) > AccentPlan.impactCount(tier, 1.0F));
            assertTrue(AccentPlan.crumbCount(tier + 1, 1.0F) > AccentPlan.crumbCount(tier, 1.0F));
        }
        assertTrue(AccentPlan.impactCount(2, 2.0F) > AccentPlan.impactCount(2, 1.0F));
        // a scale the server sends by mistake cannot flood the particle engine
        assertEquals(AccentPlan.impactCount(4, 2.0F), AccentPlan.impactCount(4, 50.0F));
        assertTrue(AccentPlan.impactCount(4, 2.0F) <= 40);
    }

    @Test
    void aNegativeTierIsReadAsTheTopOfTheTable() {
        assertEquals(4, AccentPlan.clampTier(-6));
        assertEquals(AccentPlan.impactCount(4, 1.0F), AccentPlan.impactCount(-2, 1.0F));
    }

    @Test
    void onlyHeavyHitsBlowUp() {
        assertFalse(AccentPlan.heavy(0, 1.0F));
        assertFalse(AccentPlan.heavy(2, 1.0F));
        assertTrue(AccentPlan.heavy(3, 1.0F));
        assertTrue(AccentPlan.heavy(1, 1.5F));
        assertEquals(0, AccentPlan.puffCount(1, 1.0F));
        assertTrue(AccentPlan.puffCount(4, 1.0F) > 0);
    }

    @Test
    void aLightSpriteIsNeverDarkerThanItsFloor() {
        // the Void school's own colours are the darkest in the game; black is the limit
        for (int base : new int[] {0x24102E, 0x2E1042, 0x3B1F55, 0x5A1C6E, 0x000000}) {
            int lifted = AccentPlan.lift(Palette.derive(base).bright(), AccentPlan.LIGHT_SPRITE_FLOOR);
            assertTrue(Palette.luminance(lifted) >= AccentPlan.LIGHT_SPRITE_FLOOR - 0.01F, Integer.toHexString(lifted));
        }
        // lifted toward white, not to grey: a purple stays bluer than it is green
        int purple = AccentPlan.lift(Palette.derive(0x24102E).bright(), AccentPlan.LIGHT_SPRITE_FLOOR);
        assertTrue((purple & 0xFF) > ((purple >> 8) & 0xFF), Integer.toHexString(purple));
        // a colour already bright enough is drawn as asked
        assertEquals(0xFFF7C2, AccentPlan.lift(0xFFF7C2, AccentPlan.LIGHT_SPRITE_FLOOR));
        assertTrue(AccentPlan.SHARD_FLOOR < AccentPlan.LIGHT_SPRITE_FLOOR);
    }

    @Test
    void smokeIsNeverABlackCard() {
        // the dim of the darkest school in the game, its ink, and black itself
        Palette voidPalette = Palette.derive(0x24102E);
        for (int dark : new int[] {voidPalette.dim(), voidPalette.ink(), 0x000000}) {
            int lifted = AccentPlan.lift(dark, AccentPlan.WISP_FLOOR);
            assertTrue(Palette.luminance(lifted) >= AccentPlan.WISP_FLOOR - 0.01F, Integer.toHexString(lifted));
        }
        // smoke you can see through, and never lifted as far as light
        assertTrue(AccentPlan.WISP_OPACITY <= 0.5F);
        assertTrue(AccentPlan.WISP_FLOOR < AccentPlan.LIGHT_SPRITE_FLOOR);
    }

    @Test
    void aHitMarkIsNoLongerASheet() {
        // the largest legacy mark: tier four at scale two, over two blocks each way at 0.8
        float legacy = (0.8F + 0.35F * 2.0F) * (1.0F + 4 * 0.25F);
        assertTrue(legacy * AccentPlan.MARK_SCALE < 2.0F);
        assertTrue(AccentPlan.MARK_OPACITY <= 0.5F);
        // the caster's own release is thrown clear of the sprites the lens refuses
        assertTrue(AccentPlan.OWN_RELEASE_PUSH + 0.8D > AccentPlan.SPRITE_NEAR_CAMERA);
    }

    @Test
    void aVolleyIntoOneBodyBlastsOnce() {
        assertTrue(AccentPlan.blastEchoes(5, 1.0D));
        assertFalse(AccentPlan.blastEchoes(AccentPlan.BLAST_ECHO_TICKS, 1.0D));
        double far = AccentPlan.BLAST_ECHO_RADIUS + 0.5D;
        assertFalse(AccentPlan.blastEchoes(5, far * far));
        // a blast remembered from a tick that has not come yet (a new world, a rewound clock) is no echo
        assertFalse(AccentPlan.blastEchoes(-3, 1.0D));
        // gilded chain bites every ten ticks; a bite must not blow up again
        assertTrue(AccentPlan.BLAST_ECHO_TICKS > 10);
    }

    @Test
    void onlyLightIsLeftToTheShader() {
        for (FxKinds.Smoke kind : FxKinds.Smoke.values()) {
            assertFalse(kind.dark() && kind.glint(), kind.name());
        }
        assertTrue(FxKinds.Smoke.SPARK_STREAK.glint());
        assertFalse(FxKinds.Smoke.HEX_FRAGMENT.glint());
        assertFalse(FxKinds.Smoke.MIST_WISP.glint());
    }

    @Test
    void onlySomethingFlyingTrails() {
        assertEquals(0, AccentPlan.trailCount(0.0D));
        assertEquals(0, AccentPlan.trailCount(AccentPlan.TRAIL_MIN_SPEED * 0.9D));
        assertEquals(1, AccentPlan.trailCount(AccentPlan.TRAIL_MIN_SPEED));
        assertEquals(3, AccentPlan.trailCount(40.0D));
    }

    @Test
    void aWindupThrowsFewerRunesThanItThrewMotes() {
        for (int tier = 0; tier <= 4; tier++) {
            assertTrue(AccentPlan.windupRunes(tier) < 3 + tier, "tier " + tier);
            assertTrue(AccentPlan.windupRunes(tier) >= 2, "tier " + tier);
        }
    }
}
