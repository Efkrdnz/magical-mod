package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForgeRulesTest {

    @Test
    void manaCostScalesWithTheMaximumManaPool() {
        assertEquals(15, ForgeRules.manaCost(ForgeGrade.CRUDE, 0, 100));
        assertEquals(3, ForgeRules.manaCost(ForgeGrade.CRUDE, 0, 20));
        assertEquals(75, ForgeRules.manaCost(ForgeGrade.CRUDE, 0, 500));
    }

    @Test
    void manaCostAddsTheModifierDeltas() {
        assertEquals(60, ForgeRules.manaCost(ForgeGrade.HIGH, 18, 100));
    }

    @Test
    void manaCostIsCappedAtTheWholePoolAndFlooredAtOne() {
        assertEquals(70, ForgeRules.manaCost(ForgeGrade.DIVINE, 0, 100));
        assertEquals(100, ForgeRules.manaCost(ForgeGrade.DIVINE, 40, 100), "still clamps at the pool");
        assertEquals(85, ForgeRules.manaCost(ForgeGrade.DIVINE, 15, 100),
                "and below the clamp the per-rune mana is visible, which is the point of 70");
        assertEquals(1, ForgeRules.manaCost(ForgeGrade.CRUDE, -15, 100));
        assertEquals(0, ForgeRules.manaCost(ForgeGrade.CRUDE, 0, 0));
    }

    @Test
    void qualityAddsStabilityAndClampsToTheScale() {
        assertEquals(66, ForgeRules.quality(80, -14));
        assertEquals(0, ForgeRules.quality(10, -20));
        assertEquals(100, ForgeRules.quality(95, 8));
    }

    @Test
    void woodAndStoneCapAtFine() {
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.WOOD, ForgeGrade.CRUDE, Optional.empty()));
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.WOOD, ForgeGrade.FINE, Optional.empty()));
        assertEquals(Optional.of(ForgeError.MATERIAL_CAP),
                ForgeRules.checkGrade(ForgeMaterial.WOOD, ForgeGrade.HIGH, Optional.empty()));
    }

    @Test
    void ironAndUnknownCapAtHigh() {
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.IRON, ForgeGrade.HIGH, Optional.empty()));
        assertEquals(Optional.of(ForgeError.MATERIAL_CAP),
                ForgeRules.checkGrade(ForgeMaterial.IRON, ForgeGrade.MASTER, Optional.empty()));
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.UNKNOWN, ForgeGrade.HIGH, Optional.empty()));
        assertEquals(Optional.of(ForgeError.MATERIAL_CAP),
                ForgeRules.checkGrade(ForgeMaterial.UNKNOWN, ForgeGrade.MASTER, Optional.empty()));
    }

    @Test
    void diamondCapsAtMasterAndNetheriteAtMythic() {
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.DIAMOND, ForgeGrade.MASTER, Optional.empty()));
        assertEquals(Optional.of(ForgeError.MATERIAL_CAP),
                ForgeRules.checkGrade(ForgeMaterial.DIAMOND, ForgeGrade.MYTHIC, Optional.empty()));
        assertEquals(Optional.empty(), ForgeRules.checkGrade(ForgeMaterial.NETHERITE, ForgeGrade.MYTHIC, Optional.empty()));
    }

    @Test
    void divineNeedsBothAStrongMaterialAndAnAlreadyHighForge() {
        assertEquals(Optional.of(ForgeError.MATERIAL_CAP),
                ForgeRules.checkGrade(ForgeMaterial.WOOD, ForgeGrade.DIVINE, Optional.of(ForgeGrade.HIGH)));
        assertEquals(Optional.of(ForgeError.DIVINE_REQUIRES_FORGED),
                ForgeRules.checkGrade(ForgeMaterial.IRON, ForgeGrade.DIVINE, Optional.empty()));
        assertEquals(Optional.of(ForgeError.DIVINE_REQUIRES_FORGED),
                ForgeRules.checkGrade(ForgeMaterial.IRON, ForgeGrade.DIVINE, Optional.of(ForgeGrade.FINE)));
        assertEquals(Optional.empty(),
                ForgeRules.checkGrade(ForgeMaterial.IRON, ForgeGrade.DIVINE, Optional.of(ForgeGrade.HIGH)));
    }

    @Test
    void remainingCooldownCountsDownFromTheLastForge() {
        assertEquals(0L, ForgeRules.remainingCooldown(0L, ForgeGrade.FINE, 5000L));
        assertEquals(300L, ForgeRules.remainingCooldown(1000L, ForgeGrade.FINE, 1100L));
        assertEquals(0L, ForgeRules.remainingCooldown(1000L, ForgeGrade.FINE, 1400L));
    }

    @Test
    void classXpIsQuarteredWhenReforgingDownwards() {
        assertEquals(20, ForgeRules.classXp(ForgeGrade.HIGH, Optional.of(ForgeGrade.FINE)));
        assertEquals(3, ForgeRules.classXp(ForgeGrade.FINE, Optional.of(ForgeGrade.HIGH)));
        assertEquals(ForgeGrade.MASTER.classXp(), ForgeRules.classXp(ForgeGrade.MASTER, Optional.empty()));
        assertEquals(ForgeGrade.HIGH.classXp(), ForgeRules.classXp(ForgeGrade.HIGH, Optional.of(ForgeGrade.HIGH)));
    }

    @Test
    void misfireHappensBelowTheQualityFloor() {
        assertTrue(ForgeRules.isMisfire(24));
        assertTrue(ForgeRules.isMisfire(0));
        assertEquals(false, ForgeRules.isMisfire(25));
        assertEquals(false, ForgeRules.isMisfire(100));
    }

    @Test
    void payloadLimitsAreFixed() {
        assertEquals(16, ForgeRules.MAX_GLYPHS);
        assertEquals(6, ForgeRules.MAX_STROKES_PER_GLYPH);
        assertEquals(64, ForgeRules.MAX_POINTS_PER_STROKE);
        assertEquals(1024, ForgeRules.CANVAS_UNITS);
        assertEquals(25, ForgeRules.MISFIRE_QUALITY);
    }
}
