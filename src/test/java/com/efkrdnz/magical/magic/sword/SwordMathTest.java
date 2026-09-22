package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import org.junit.jupiter.api.Test;

/**
 * Every damage number in the kit, on exact values.
 *
 * <p>The apex volley has to be a number somebody signed off rather than something discovered in
 * play, and the only way that sentence means anything is if the caps are pinned from both sides -
 * the last input under a cap and the first input over it. Every cap {@link SwordMath} has gets
 * that treatment here.
 *
 * <p><b>There is one input now and it is a count of swords.</b> There used to be two, an Edge per
 * station and a strain over the whole Array, and the ceiling took a paragraph to state. Half of
 * this file is shorter for it, and that is the point rather than a side effect: a number a player
 * can work out is a number they can play against.
 */
class SwordMathTest {

    private static final double EXACT = 1.0E-9D;

    @Test
    void aSwordIsFiveAndThatIsTheWholeOfIt() {
        assertEquals(5.0D, SwordMath.bladeDamage(), EXACT);
        assertEquals(SwordMath.BLADE_BASE, SwordMath.bladeDamage(), EXACT,
                "flat, because the count is the only variable the kit has left");
    }

    @Test
    void aShedCostsWhoeverIsStandingOnTheLineItCutsHome() {
        assertEquals(3.0D, SwordMath.shedDamage(0.0D), EXACT);
        assertEquals(6.5D, SwordMath.shedDamage(10.0D), EXACT);
        assertEquals(24.0D, SwordMath.shedDamage(60.0D), EXACT, "60 blocks lands exactly on the cap");
        assertEquals(24.0D, SwordMath.shedDamage(1000.0D), EXACT);
        assertEquals(3.0D, SwordMath.shedDamage(-8.0D), EXACT, "and nothing here ever heals anybody");
    }

    @Test
    void theGuardTurnsAFlatFourWhicheverKindOfBlowItWas() {
        // One constant and no function, because both halves of Watch.INTERCEPT read it: the
        // projectile half in StanceWatchService and the melee half in SwordPassives. A ward that
        // was worth more against one of the two would be a stance that only answered arrows.
        assertEquals(4.0D, SwordMath.WARD_ABSORB, EXACT);
    }

    @Test
    void theGreatswordIsMadeOfTheSwordsThatCollapsedIntoIt() {
        assertEquals(6.0D, SwordMath.oneBladeSlash(0), EXACT, "no swords is the bare base");
        assertEquals(20.0D, SwordMath.oneBladeSlash(4), EXACT, "the base rung's four");
        assertEquals(48.0D, SwordMath.oneBladeSlash(12), EXACT, "the apex's twelve lands on the cap");
        assertEquals(48.0D, SwordMath.oneBladeSlash(99), EXACT, "and everything past it is the cap");
        assertEquals(6.0D, SwordMath.oneBladeSlash(-4), EXACT);

        assertEquals(30.0D, SwordMath.oneBladeBlast(4), EXACT);
        assertEquals(72.0D, SwordMath.oneBladeBlast(12), EXACT, "the slash cap times 1.5 is the blast cap");
        assertEquals(72.0D, SwordMath.oneBladeBlast(99), EXACT);
        assertTrue(SwordMath.oneBladeBlast(4) > SwordMath.oneBladeSlash(4),
                "the blast is the slash spent all at once, so it has to be worth more than one");
    }

    @Test
    void theGreatswordsLengthAndArcAreTheSameNumberReadTwice() {
        assertEquals(2.5D, SwordMath.oneBladeReach(0), EXACT);
        assertEquals(4.3D, SwordMath.oneBladeReach(4), EXACT, "four swords is four and a third blocks");
        assertEquals(7.9D, SwordMath.oneBladeReach(12), EXACT, "and the apex's twelve is nearly eight");

        assertEquals(60.0D, SwordMath.oneBladeArc(0), EXACT);
        assertEquals(88.0D, SwordMath.oneBladeArc(4), EXACT);
        assertEquals(144.0D, SwordMath.oneBladeArc(12), EXACT, "the apex is under the cap, deliberately");
        assertEquals(160.0D, SwordMath.oneBladeArc(99), EXACT, "so nothing in play ever reaches it");
    }

    @Test
    void yourPoolIsYourSwords() {
        assertEquals(0, SwordMath.bonusMaxMana(0), "a sheathed wielder is a thinner mage");
        assertEquals(3, SwordMath.bonusMaxMana(1));
        assertEquals(12, SwordMath.bonusMaxMana(4), "the base rung's four");
        assertEquals(36, SwordMath.bonusMaxMana(12), "twelve present is exactly the cap");
        assertEquals(36, SwordMath.bonusMaxMana(99), "and nothing can push it past that");
        assertEquals(0, SwordMath.bonusMaxMana(-3));
        assertEquals(SwordMath.SWORD_HEART_CAP,
                SwordMath.MANA_PER_SWORD * SwordRules.GOD.swords(),
                "the cap and a full complement are the same number, so neither is arbitrary");
        assertEquals(SwordMath.SWORD_HEART_CAP,
                SwordMath.MANA_PER_SWORD * SwordStance.RAIN.swords(SwordRules.GOD.swords()),
                "and a stance can still reach it, which is what keeps it from being decoration");
    }

    @Test
    void theApexVolleyIsANumberAndNotAFeeling() {
        // Twelve swords, if every one of them landed. There is no projection halving it any more
        // and no strain inflating it: the volley is the complement, so the ceiling is a
        // multiplication. The number goes in the commit message.
        assertEquals(60.0D, SwordRules.GOD.swords() * SwordMath.bladeDamage(), EXACT);
        assertEquals(20.0D, SwordRules.SUMMONER.swords() * SwordMath.bladeDamage(), EXACT,
                "and the base rung's whole volley is four swords, which is a third of it");
    }

    /**
     * The apex is a stance as well as a rung, and the spread between them is the trade.
     *
     * <p>A complement is the rung's offer capped by the stance's shape, so the 60 above is not a
     * number an apex wielder simply has - it is a number they have <em>in Rain</em>. Vanguard,
     * the tightest cluster in the kit and the one drawn on the crosshair, fields five of the same
     * twelve and lands 25 - the price of that silhouette, stated in damage rather than in
     * adjectives. Both ends are pinned because a cap that quietly became the only complement
     * would make the whole feature a nerf with no upside.
     *
     * <p>The floor is a <em>rung 0</em> stance and the ceiling a rung 2 one, which is the shape
     * of the whole thing: the two postures a wielder starts in stay tight for the rest of the
     * game, and climbing the chain buys steel as well as verbs.
     */
    @Test
    void whatAnApexWielderLandsDependsOnHowTheyAreStanding() {
        int apex = SwordRules.GOD.swords();
        assertEquals(60.0D, SwordStance.RAIN.swords(apex) * SwordMath.bladeDamage(), EXACT,
                "Rain is the stance that reaches the ceiling, so the ceiling is reachable");
        assertEquals(25.0D, SwordStance.VANGUARD.swords(apex) * SwordMath.bladeDamage(), EXACT,
                "and Vanguard is the floor: five swords on the aim line, for the tightest read");
        assertEquals(30.0D, SwordStance.GUARD.swords(apex) * SwordMath.bladeDamage(), EXACT,
                "with Guard just above it - the two the Summoner starts with are the two small ones");
        for (SwordStance stance : SwordStance.values()) {
            double volley = stance.swords(apex) * SwordMath.bladeDamage();
            assertTrue(volley >= 25.0D && volley <= 60.0D,
                    stance + " lands " + volley + " at the apex, outside the 25..60 the two ends"
                            + " above claim is the whole spread");
        }
    }
}
