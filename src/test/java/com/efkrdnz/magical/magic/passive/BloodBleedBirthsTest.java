package com.efkrdnz.magical.magic.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * When each drop of a fed pool left the body.
 *
 * <p>A pool that is being bled into grows a bite at a time, and the blood of a bite should be
 * seen leaving the body at that bite - a gush, not a trickle spread evenly over a feed that
 * has not happened yet. So the client remembers the tick each cube first appeared, and never
 * moves a birth once it is set. What was already there at first sight has already landed, unless
 * the pool is being fed at that moment, in which case it is the gush just seen.
 */
class BloodBleedBirthsTest {

    @Test
    void whatIsThereAtFirstSightHasAlreadyLandedUnlessThePoolIsBeingFed() {
        BloodBleedBirths settled = new BloodBleedBirths();
        settled.observe(16, 1.0F, false);
        assertEquals(16, settled.known());
        assertEquals(Float.NEGATIVE_INFINITY, settled.bornAt(0), "an old drop fell long ago");
        assertEquals(Float.NEGATIVE_INFINITY, settled.bornAt(15));

        BloodBleedBirths gushing = new BloodBleedBirths();
        gushing.observe(16, 1.0F, true);
        assertEquals(1.0F, gushing.bornAt(0), "a pool seen as it is fed is bleeding now");
        assertEquals(1.0F + 15 * BloodBleedBirths.STAGGER, gushing.bornAt(15), 1.0E-6F);
    }

    @Test
    void aBiteBirthsOnlyTheNewDropsAndNeverMovesAnOldBirth() {
        BloodBleedBirths births = new BloodBleedBirths();
        births.observe(0, 1.0F, true);
        assertEquals(0, births.known());
        births.observe(16, 10.0F, true);
        births.observe(32, 20.0F, true);
        assertEquals(32, births.known());
        assertEquals(10.0F, births.bornAt(0));
        assertEquals(10.0F + 3 * BloodBleedBirths.STAGGER, births.bornAt(3), 1.0E-6F);
        assertEquals(20.0F, births.bornAt(16), "the second bite's first drop leaves at the second bite");
        assertEquals(20.0F + 15 * BloodBleedBirths.STAGGER, births.bornAt(31), 1.0E-6F);
        assertEquals(Float.POSITIVE_INFINITY, births.bornAt(40), "a drop not yet shed is never born");
    }

    @Test
    void aGushIsOverBeforeTheNextBiteAtTheFastestBleed() {
        // Open Vein bites every ten ticks; a whole bite's worth of drops must have left the body
        // by then, or the next gush would be born on top of drops still falling.
        int drops = BloodHarvestRules.cubes(4);
        assertTrue((drops - 1) * BloodBleedBirths.STAGGER < 4.0F, "a bite gushes in well under a bite interval");
    }

    @Test
    void theCountNeverShrinksAndNeverPassesTheCap() {
        BloodBleedBirths births = new BloodBleedBirths();
        births.observe(32, 1.0F, true);
        births.observe(16, 5.0F, true);
        assertEquals(32, births.known(), "worth read low for a tick does not unbirth anything");
        births.observe(1000, 9.0F, true);
        assertEquals(BloodHarvestRules.MAX_CUBES, births.known());
        assertEquals(Float.POSITIVE_INFINITY, births.bornAt(BloodHarvestRules.MAX_CUBES), "past the cap is never drawn");
    }
}
