package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ForgeGradeTest {

    @Test
    void byNameRoundTripsEveryConstantCaseInsensitively() {
        for (ForgeGrade grade : ForgeGrade.values()) {
            assertEquals(Optional.of(grade), ForgeGrade.byName(grade.name()));
            assertEquals(Optional.of(grade), ForgeGrade.byName(grade.name().toLowerCase()));
            assertEquals(Optional.of(grade), ForgeGrade.byName(grade.serializedName()));
        }
    }

    /**
     * The material requirement is written down twice - {@link ForgeGrade#materialFloor()}, which the
     * codex renders, and {@link ForgeMaterial#maxGrade()}, which {@link ForgeRules#checkGrade} is
     * what actually enforces. Nothing related them, so raising either one alone would have the codex
     * promise a forge the server refuses. DIVINE is included: its own branch of checkGrade demands a
     * material good for HIGH and a weapon already forged at HIGH or better, which its IRON floor and
     * the existing grade passed here both satisfy.
     */
    @Test
    void everyGradesDeclaredFloorMaterialIsOneTheRulesActuallyAccept() {
        for (ForgeGrade grade : ForgeGrade.values()) {
            assertEquals(Optional.empty(),
                    ForgeRules.checkGrade(grade.materialFloor(), grade, Optional.of(ForgeGrade.HIGH)),
                    grade + " declares a floor of " + grade.materialFloor()
                            + ", which caps at " + grade.materialFloor().maxGrade());
        }
    }

    @Test
    void byNameReturnsEmptyForUnknownOrNull() {
        assertTrue(ForgeGrade.byName("nope").isEmpty());
        assertTrue(ForgeGrade.byName(null).isEmpty());
    }

    @Test
    void crudeMatchesTableValues() {
        ForgeGrade crude = ForgeGrade.CRUDE;
        assertEquals(1, crude.formSlots());
        assertEquals(0, crude.modifierSlots());
        assertEquals(15, crude.manaPercent());
        assertEquals(6, crude.classXp());
        assertEquals(2.0f, crude.baseDamage());
        assertEquals(0.50f, crude.sigilAcceptScore());
        assertEquals(ForgeMaterial.WOOD, crude.materialFloor());
        assertEquals(200, crude.reforgeCooldownTicks());
    }

    @Test
    void divineMatchesTableValues() {
        ForgeGrade divine = ForgeGrade.DIVINE;
        assertEquals(4, divine.formSlots());
        assertEquals(3, divine.modifierSlots());
        assertEquals(100, divine.manaPercent());
        assertEquals(55, divine.classXp());
        assertEquals(11.0f, divine.baseDamage());
        assertEquals(0.80f, divine.sigilAcceptScore());
        assertEquals(ForgeMaterial.IRON, divine.materialFloor());
        assertEquals(6000, divine.reforgeCooldownTicks());
    }

    @Test
    void sigilAcceptScoreStrictlyIncreasesAcrossOrdinalOrder() {
        ForgeGrade[] grades = ForgeGrade.values();
        for (int i = 1; i < grades.length; i++) {
            int index = i;
            assertTrue(grades[i].sigilAcceptScore() > grades[i - 1].sigilAcceptScore(),
                    () -> "expected sigilAcceptScore to strictly increase at index " + index);
        }
    }

    @Test
    void reforgeCooldownTicksStrictlyIncreasesAcrossOrdinalOrder() {
        ForgeGrade[] grades = ForgeGrade.values();
        for (int i = 1; i < grades.length; i++) {
            int index = i;
            assertTrue(grades[i].reforgeCooldownTicks() > grades[i - 1].reforgeCooldownTicks(),
                    () -> "expected reforgeCooldownTicks to strictly increase at index " + index);
        }
    }

    @Test
    void byNameIsFalseForBlank() {
        assertFalse(ForgeGrade.byName("").isPresent());
    }
}
