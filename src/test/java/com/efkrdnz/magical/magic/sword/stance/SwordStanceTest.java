package com.efkrdnz.magical.magic.sword.stance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.SwordRules;
import org.junit.jupiter.api.Test;

/**
 * The two numbers a stance carries that nothing else in the package would notice were wrong.
 *
 * <p>{@link FormationTest} measures where blades go and {@code SwordMathTest} measures what they
 * land, and both sweep every count 1..12 - so both stay green whatever the caps say. A cap of
 * zero, a cap of forty, a half-life of nothing: every one of them produces a perfectly legal
 * formation of a perfectly wrong size, or a formation that does not lag at all, and no other test
 * in the project has an opinion. These are those opinions.
 */
class SwordStanceTest {

    /** The best any rung offers, which is the widest a stance is ever asked to be. */
    private static final int APEX = SwordRules.GOD.swords();

    @Test
    void everyCapIsACountAWielderCouldActuallyHave() {
        for (SwordStance stance : SwordStance.values()) {
            assertTrue(stance.swordCap() >= 1,
                    stance + " fields " + stance.swordCap() + " swords, and a stance nobody can"
                            + " stand in is not a stance");
            assertTrue(stance.swordCap() <= APEX,
                    stance + " fields " + stance.swordCap() + ", over the apex rung's " + APEX
                            + " - SwordMath.SWORD_HEART_CAP and the Formation sweep are both"
                            + " built on that being the ceiling");
        }
    }

    @Test
    void theCapNeverGivesAWielderMoreThanTheRungDid() {
        for (SwordStance stance : SwordStance.values()) {
            for (int complement = 1; complement <= APEX; complement++) {
                int fielded = stance.swords(complement);
                assertTrue(fielded <= complement,
                        stance + " turns " + complement + " swords into " + fielded);
                assertTrue(fielded <= stance.swordCap(),
                        stance + " fielded " + fielded + ", over its own cap " + stance.swordCap());
                assertTrue(fielded >= 1, stance + " fielded nothing from " + complement);
            }
        }
    }

    /**
     * A stance must not greet you already capped.
     *
     * <p>The cap is meant to be a thing you meet by <em>growing</em> - you climb a rung, the other
     * stances widen, and this one does not - so the moment a rung opens a stance it has to field
     * everything that rung offers. A stance whose cap bit on the day you unlocked it would read as
     * a broken reward: you paid for a posture and it handed back fewer swords than the one you
     * were already standing in.
     */
    @Test
    void aStanceFieldsEverythingTheRungThatOpensItOffers() {
        for (SwordStance stance : SwordStance.values()) {
            SwordRules opening = SwordRules.forRung(stance.rung());
            assertTrue(opening.allows(stance),
                    stance + " says it opens at rung " + stance.rung() + ", which does not open it");
            assertEquals(opening.swords(), stance.swords(opening.swords()),
                    stance + " caps at " + stance.swordCap() + ", under the " + opening.swords()
                            + " the rung that unlocks it already gave you");
        }
    }

    /** Otherwise {@code SwordMath.SWORD_HEART_CAP} is a number in the source and nowhere else. */
    @Test
    void someStanceStillReachesTheApex() {
        int widest = 0;
        for (SwordStance stance : SwordStance.values()) {
            widest = Math.max(widest, stance.swordCap());
        }
        assertEquals(APEX, widest, "no stance fields a full complement, so the apex rung's last"
                + " two swords, the Sword Heart cap and the 60-damage volley are all unreachable");
    }

    @Test
    void everyStanceActuallyLags() {
        for (SwordStance stance : SwordStance.values()) {
            assertTrue(stance.followHalfLife() > 0.0D,
                    stance + " has a half-life of " + stance.followHalfLife() + "; FrameEase reads"
                            + " anything at or below zero as a snap, which switches the easing off"
                            + " silently rather than failing");
        }
    }

    /**
     * And catches up inside a second, which is the other half of the same number.
     *
     * <p>A long half-life is not a heavier formation, it is a formation that is somewhere else:
     * after a 180 degree turn - a wielder spinning to face someone behind them, which is the
     * commonest hard turn there is - twenty ticks later every stance has to be pointing roughly
     * where its wielder is. 20 degrees is about the width of the blade at Guard's radius.
     */
    @Test
    void noStanceIsStillTurningASecondLater() {
        double limit = 20.0D;
        for (SwordStance stance : SwordStance.values()) {
            double behind = 180.0D * Math.pow(0.5D, 20.0D / stance.followHalfLife());
            assertTrue(behind <= limit, stance + " is still " + behind + " degrees behind a second"
                    + " after a half turn, over " + limit + " - at a half-life of "
                    + stance.followHalfLife() + " ticks the formation is not lagging, it is lost");
        }
    }

    /**
     * The aim line is the one place lag is a lie rather than a flourish.
     *
     * <p>A LOOK-anchored stance is drawn on the crosshair, so its swords are read as part of the
     * aiming: a cluster that trails a fifth of a second behind the reticle is telling the wielder
     * they are pointing somewhere they are not. Everything hung off the body may swing.
     */
    @Test
    void aStanceOnTheCrosshairTracksTighterThanOneOnTheBody() {
        for (SwordStance look : SwordStance.values()) {
            if (look.anchor() != SwordStance.Anchor.LOOK) {
                continue;
            }
            for (SwordStance body : SwordStance.values()) {
                if (body.anchor() == SwordStance.Anchor.LOOK) {
                    continue;
                }
                assertTrue(look.followHalfLife() < body.followHalfLife(),
                        look + " is anchored on the look and lags " + look.followHalfLife()
                                + " ticks, no tighter than " + body + "'s " + body.followHalfLife()
                                + " on the body");
            }
        }
    }
}
