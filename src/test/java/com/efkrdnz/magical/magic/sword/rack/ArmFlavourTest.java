package com.efkrdnz.magical.magic.sword.rack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rack is flavour: a weapon colours the hit its sword lands, and never decides it.
 */
class ArmFlavourTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    void plainSteelAndAStickAddNothing() {
        assertEquals(1.0D, ArmFlavour.scale(0.0D, 0.0D, -1), EPSILON, "an empty socket is plain steel");
        assertEquals(1.0D, ArmFlavour.scale(3.0D, 0.0D, -1), EPSILON, "a wooden sword is an upgrade");
    }

    @Test
    void vanillaSwordsClimbInTheOrderTheirMaterialsDo() {
        double stone = ArmFlavour.scale(4.0D, 0.0D, -1);
        double iron = ArmFlavour.scale(5.0D, 0.0D, -1);
        double diamond = ArmFlavour.scale(6.0D, 0.0D, -1);
        double netherite = ArmFlavour.scale(7.0D, 0.0D, -1);
        assertTrue(1.0D < stone && stone < iron && iron < diamond && diamond < netherite,
                "stone " + stone + ", iron " + iron + ", diamond " + diamond + ", netherite " + netherite);
        assertEquals(1.0D + ArmFlavour.ATTACK_CAP, netherite, EPSILON, "netherite is the edge's own cap");
    }

    @Test
    void noWeaponLiftsItsSwordByMoreThanTheCap() {
        double everything = ArmFlavour.scale(40.0D, 30.0D, 99);
        assertEquals(1.0D + ArmFlavour.CAP, everything, EPSILON,
                "a weapon with every bonus the game has is worth " + everything + " of a sword");
        assertTrue(ArmFlavour.ATTACK_CAP + ArmFlavour.ENCHANT_CAP + ArmFlavour.GRADE_CAP >= ArmFlavour.CAP,
                "the three parts can never reach the cap, so the cap is a number nobody meets");
        assertTrue(ArmFlavour.CAP <= 0.15D + EPSILON, "the rack has become a damage stat");
    }

    @Test
    void sharpnessAndAForgeGradeEachAddTheirOwnSmallShare() {
        double sharp = ArmFlavour.scale(3.0D, 3.0D, -1);
        assertEquals(1.03D, sharp, EPSILON, "Sharpness V is three hundredths");
        double forged = ArmFlavour.scale(3.0D, 0.0D, 2);
        assertEquals(1.02D, forged, EPSILON, "a High forging is two hundredths");
    }

    @Test
    void nonsenseReadsAsNothingRatherThanAsAMultiplier() {
        assertEquals(1.0D, ArmFlavour.scale(Double.NaN, Double.NaN, -5), EPSILON);
        assertEquals(1.0D, ArmFlavour.scale(Double.NEGATIVE_INFINITY, -20.0D, -1), EPSILON);
        assertEquals(1.0D, ArmFlavour.scale(Double.POSITIVE_INFINITY, 0.0D, -1), EPSILON,
                "an infinite attack modifier from another mod is not an infinite sword");
    }

    @Test
    void aWoundOfManySwordsIsTheMeanOfWhatTheyCarry() {
        double diamond = ArmFlavour.scale(6.0D, 0.0D, -1);
        assertEquals(1.0D, ArmFlavour.mean(new double[0], 12), EPSILON, "twelve plain swords");
        assertEquals(1.0D + (diamond - 1.0D) / 12.0D, ArmFlavour.mean(new double[] {diamond}, 12), EPSILON,
                "one diamond blade in twelve");
        assertEquals(diamond, ArmFlavour.mean(new double[] {diamond, diamond}, 2), EPSILON);
        assertEquals(1.0D, ArmFlavour.mean(new double[0], 0), EPSILON, "no swords at all");
        assertEquals(1.0D + ArmFlavour.CAP, ArmFlavour.mean(new double[] {9.0D}, 1), EPSILON,
                "a forged scale past the cap is held to it");
    }
}
