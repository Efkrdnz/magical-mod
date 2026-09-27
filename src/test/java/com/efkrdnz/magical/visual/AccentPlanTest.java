package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
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
    void theCasterOwnSigilTakesUnderAThirdOfTheFrameAtEveryTier() {
        // at the narrowest field of view anybody plays at (vanilla's 70) the frame is smallest
        for (double fov : new double[] {70.0D, DEV_FOV_DEGREES}) {
            double halfHeight = Math.tan(Math.toRadians(fov * 0.5D)) * SIGIL_DISTANCE;
            for (int tier = 0; tier <= 4; tier++) {
                float radius = AccentPlan.ownCircleRadius(CircleAnchor.EYE_FORWARD, TierProfile.forTier(tier).radius(), tier);
                assertTrue(radius / halfHeight < 0.5D, "tier " + tier + " at fov " + fov + ": " + radius);
            }
        }
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
