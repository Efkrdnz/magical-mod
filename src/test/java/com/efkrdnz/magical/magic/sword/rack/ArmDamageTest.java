package com.efkrdnz.magical.magic.sword.rack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.sword.SwordMath;
import org.junit.jupiter.api.Test;

/**
 * A racked weapon hits for what it is: the kit's numbers are written in units of one plain sword
 * landing, and a weapon replaces that unit with its own hit.
 */
class ArmDamageTest {

    private static final double EPSILON = 1.0E-9D;

    /** The complaint: a sword of 230 hit like any other. One of its swords lands 230 now. */
    @Test
    void aWeaponLandsItsOwnDamage() {
        assertEquals(230.0D, SwordMath.bladeDamage() * ArmDamage.scale(230.0D), EPSILON,
                "a 230 weapon's sword landing");
        assertEquals(1.0D, ArmDamage.scale(SwordMath.BLADE_BASE), EPSILON,
                "a weapon exactly the kit's unit changes nothing");
        assertEquals(7.0D, SwordMath.bladeDamage() * ArmDamage.scale(7.0D), EPSILON, "a diamond sword");
    }

    /** The number on the tooltip: the wielder's base plus the item's modifiers, in vanilla's order. */
    @Test
    void theAttackIsTheNumberOnTheTooltip() {
        assertEquals(230.0D, ArmDamage.attack(1.0D, 229.0D, 0.0D, 1.0D), EPSILON, "the sword that started this");
        assertEquals(7.0D, ArmDamage.attack(1.0D, 6.0D, 0.0D, 1.0D), EPSILON, "a diamond sword");
        assertEquals(15.0D, ArmDamage.attack(1.0D, 9.0D, 0.5D, 1.0D), EPSILON,
                "a base multiplier works on the sum of the adds");
        assertEquals(18.0D, ArmDamage.attack(1.0D, 9.0D, 0.5D, 1.2D), EPSILON, "and a total one on everything");
        assertEquals(0.0D, ArmDamage.attack(1.0D, -40.0D, 0.0D, 1.0D), EPSILON, "never below nothing");
        assertEquals(ArmDamage.ATTACK_CEILING, ArmDamage.attack(1.0D, 1.0E9D, 0.0D, 1.0D), EPSILON,
                "the attribute's own ceiling, as vanilla sanitises it");
        assertEquals(0.0D, ArmDamage.attack(Double.NaN, 5.0D, 0.0D, 1.0D), EPSILON,
                "a NaN attack is the attribute's floor, as vanilla sanitises it");
    }

    @Test
    void nonsenseReadsAsTheUnitNotAsAMultiplier() {
        assertEquals(1.0D, ArmDamage.scale(Double.NaN), EPSILON);
        assertEquals(1.0D, ArmDamage.scale(Double.POSITIVE_INFINITY), EPSILON,
                "an infinite hit from another mod is not an infinite sword");
        assertEquals(0.0D, ArmDamage.scale(-5.0D), EPSILON, "a weapon of negative attack heals nobody");
    }

    /**
     * Below and One Blade bill the kit's unit once a sword, so the mean over the swords in a wound
     * is exactly what makes the wound land the sum of its weapons.
     */
    @Test
    void aWoundOfManySwordsLandsTheSumOfItsWeapons() {
        double[] scales = {ArmDamage.scale(230.0D), ArmDamage.scale(7.0D), ArmDamage.scale(4.0D)};
        double bill = 3 * SwordMath.bladeDamage();
        assertEquals(241.0D, bill * ArmDamage.mean(scales, 3), 1.0E-6D);
    }

    @Test
    void aSwordWithNoWeaponCountsAsTheUnit() {
        double big = ArmDamage.scale(230.0D);
        assertEquals(big, ArmDamage.mean(new double[] {big}, 1), EPSILON, "one sword");
        assertEquals((big + 1.0D) / 2.0D, ArmDamage.mean(new double[] {big, 1.0D}, 2), EPSILON);
        assertEquals((big + 11.0D) / 12.0D, ArmDamage.mean(new double[] {big}, 12), EPSILON,
                "one weapon among twelve swords");
        assertEquals(1.0D, ArmDamage.mean(new double[0], 12), EPSILON, "twelve with nothing racked");
        assertEquals(1.0D, ArmDamage.mean(new double[0], 0), EPSILON, "no swords at all");
    }
}
