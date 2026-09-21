package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Every damage number in the kit, on exact values.
 *
 * <p>The apex volley has to be a number somebody signed off rather than something discovered in
 * play, and the only way that sentence means anything is if the caps are pinned from both sides -
 * the last input under a cap and the first input over it. Both of {@link SwordMath}'s two caps and
 * the strain term's own cap get that treatment here.
 */
class SwordMathTest {

    private static final double EXACT = 1.0E-9D;

    @Test
    void aBladeIsTwoAndTwoAnEdge() {
        assertEquals(2.0D, SwordMath.bladeDamage(0, 0), EXACT, "an unmanned bearing is worth the base");
        assertEquals(4.0D, SwordMath.bladeDamage(1, 0), EXACT);
        assertEquals(8.0D, SwordMath.bladeDamage(3, 0), EXACT);
        assertEquals(74.0D, SwordMath.bladeDamage(36, 0), EXACT, "the whole of a Sword God in one blade");
        assertEquals(2.0D, SwordMath.bladeDamage(-4, -9), EXACT, "and nothing here ever heals anybody");
    }

    @Test
    void theStrainTermHasItsOwnCapAndMetalAlwaysBeatsIt() {
        assertEquals(23.0D, SwordMath.bladeDamage(3, 60), EXACT, "8 plus a quarter of 60");
        assertEquals(29.0D, SwordMath.bladeDamage(3, 84), EXACT, "84 is the apex's whole draw");
        assertEquals(32.0D, SwordMath.bladeDamage(3, 96), EXACT, "96 quarters is exactly the +24 ceiling");
        assertEquals(32.0D, SwordMath.bladeDamage(3, 400), EXACT, "and nothing past it moves at all");
        assertEquals(24.0D, SwordMath.STRAIN_CAP, EXACT);
        assertTrue(SwordMath.bladeDamage(4, 400) > SwordMath.bladeDamage(3, 400),
                "an Array that stopped growing stopped hurting more, so metal is the only lever left");
    }

    @Test
    void aReflectionCarriesHalfTheMetalAndNeverNothing() {
        assertEquals(SwordMath.bladeDamage(3, 0), SwordMath.mirrorDamage(6, 0), EXACT);
        assertEquals(SwordMath.bladeDamage(3, 0), SwordMath.mirrorDamage(7, 0), EXACT, "the halving floors");
        assertEquals(2.0D, SwordMath.mirrorDamage(1, 0), EXACT, "a single Edge reflects as the bare base");
        assertEquals(2.0D, SwordMath.mirrorDamage(0, 0), EXACT, "so the floor of one never actually bites");
        assertEquals(38.0D, SwordMath.mirrorDamage(36, 0), EXACT);
    }

    @Test
    void aShedCostsWhoeverIsStandingOnTheLineItCutsHome() {
        assertEquals(3.0D, SwordMath.shedDamage(0.0D), EXACT);
        assertEquals(6.5D, SwordMath.shedDamage(10.0D), EXACT);
        assertEquals(24.0D, SwordMath.shedDamage(60.0D), EXACT, "60 blocks lands exactly on the cap");
        assertEquals(24.0D, SwordMath.shedDamage(1000.0D), EXACT);
        assertEquals(3.0D, SwordMath.shedDamage(-8.0D), EXACT);
    }

    @Test
    void wardTurnsTwoAndAHalfEachWayOfAnEdge() {
        assertEquals(2.0D, SwordMath.wardAbsorb(0), EXACT);
        assertEquals(3.5D, SwordMath.wardAbsorb(1), EXACT);
        assertEquals(6.5D, SwordMath.wardAbsorb(3), EXACT);
        assertEquals(20.0D, SwordMath.wardAbsorb(12), EXACT);
        assertEquals(56.0D, SwordMath.wardAbsorb(36), EXACT);
    }

    @Test
    void theGreatswordIsMadeOfTheEdgeThatCollapsedIntoIt() {
        assertEquals(18.0D, SwordMath.oneBladeSlash(8, 0), EXACT);
        assertEquals(36.0D, SwordMath.oneBladeSlash(20, 0), EXACT);
        assertEquals(42.0D, SwordMath.oneBladeSlash(20, 24), EXACT);
        assertEquals(48.0D, SwordMath.oneBladeSlash(28, 0), EXACT, "28 Edge lands exactly on the cap");
        assertEquals(48.0D, SwordMath.oneBladeSlash(36, 84), EXACT, "and everything past it is the cap");

        assertEquals(27.0D, SwordMath.oneBladeBlast(8, 0), EXACT);
        assertEquals(54.0D, SwordMath.oneBladeBlast(20, 0), EXACT);
        assertEquals(72.0D, SwordMath.oneBladeBlast(28, 0), EXACT, "the slash cap times 1.5 is the blast cap");
        assertEquals(72.0D, SwordMath.oneBladeBlast(36, 400), EXACT);
    }

    @Test
    void theGreatswordsLengthAndArcAreTheSameNumberReadTwice() {
        assertEquals(2.5D, SwordMath.oneBladeReach(0), EXACT);
        assertEquals(3.94D, SwordMath.oneBladeReach(8), EXACT);
        assertEquals(8.98D, SwordMath.oneBladeReach(36), EXACT, "the full apex Array is nine blocks of sword");

        assertEquals(60.0D, SwordMath.oneBladeArc(0), EXACT);
        assertEquals(84.0D, SwordMath.oneBladeArc(8), EXACT);
        assertEquals(159.0D, SwordMath.oneBladeArc(33), EXACT, "the last Edge under the cap");
        assertEquals(160.0D, SwordMath.oneBladeArc(34), EXACT, "and the first one over it");
        assertEquals(160.0D, SwordMath.oneBladeArc(36), EXACT);
    }

    @Test
    void yourPoolIsYourBlades() {
        assertEquals(0, SwordMath.bonusMaxMana(0), "an empty Array is a thinner mage");
        assertEquals(3, SwordMath.bonusMaxMana(1));
        assertEquals(12, SwordMath.bonusMaxMana(4), "the base rung's four stations");
        assertEquals(36, SwordMath.bonusMaxMana(12), "twelve manned is exactly the cap");
        assertEquals(36, SwordMath.bonusMaxMana(99), "and nothing can push it past that");
        assertEquals(0, SwordMath.bonusMaxMana(-3));
        assertEquals(SwordMath.SWORD_HEART_CAP, SwordMath.MANA_PER_STATION * SwordArray.MAX_STATIONS,
                "the cap and a full ring are the same number, which is why neither is arbitrary");
    }

    @Test
    void theApexVolleyIsANumberAndNotAFeeling() {
        // Twelve stations at 3 Edge with 60 strain, if every blade landed, which it cannot: the
        // forward projection is at most half of a ring. The number goes in the commit message.
        assertEquals(276.0D, 12 * SwordMath.bladeDamage(3, 60), EXACT);
        assertEquals(138.0D, 6 * SwordMath.bladeDamage(3, 60), EXACT, "half a ring is what actually flies");
    }
}
