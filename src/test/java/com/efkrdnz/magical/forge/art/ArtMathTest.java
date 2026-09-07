package com.efkrdnz.magical.forge.art;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;

/**
 * The two flurry Arts that build up on the same body, held to the numbers in the Art table:
 * Kindling's "max 3" and Fang Storm's "max 4; amplifier +1 per 2 stacks".
 */
class ArtMathTest {

    @Test
    void stacksCountThePulsesThatLandedAndStopAtTheCap() {
        assertEquals(0, ArtMath.stacks(0, ArtMath.FANG_MAX_STACKS));
        assertEquals(1, ArtMath.stacks(1, ArtMath.FANG_MAX_STACKS));
        assertEquals(4, ArtMath.stacks(4, ArtMath.FANG_MAX_STACKS));
        assertEquals(4, ArtMath.stacks(9, ArtMath.FANG_MAX_STACKS));
        assertEquals(3, ArtMath.stacks(9, ArtMath.KINDLING_MAX_STACKS));
        assertEquals(0, ArtMath.stacks(-1, ArtMath.KINDLING_MAX_STACKS));
    }

    /** The table: "amplifier +1 per 2 stacks", so two fangs are Poison II and four are Poison III. */
    @Test
    void everySecondFangDeepensThePoisonByOneLevel() {
        assertEquals(0, ArtMath.fangAmplifier(1));
        assertEquals(1, ArtMath.fangAmplifier(2));
        assertEquals(1, ArtMath.fangAmplifier(3));
        assertEquals(2, ArtMath.fangAmplifier(ArtMath.FANG_MAX_STACKS));
    }

    @Test
    void noFangsMeansNoDeepening() {
        assertEquals(0, ArtMath.fangAmplifier(0));
        assertEquals(0, ArtMath.fangAmplifier(-3));
    }

    /**
     * What Fang Storm actually reaches in play. Its trigger is "light", and a light flurry pulses
     * three times, so the last fang a player can land is the third and the poison it leaves is
     * Poison II. The cap stays at the table's four - it is the stack cap, not a prediction - and a
     * fourth pulse would be worth the level the table promises rather than nothing.
     */
    @Test
    void aLightFlurryReachesThreeFangsAndPoisonTwo() {
        int pulses = ForgeStrikeMath.flurryPulseTicks(false).length;

        assertEquals(3, pulses);
        assertEquals(3, ArtMath.stacks(pulses, ArtMath.FANG_MAX_STACKS));
        assertEquals(1, ArtMath.fangAmplifier(ArtMath.stacks(pulses, ArtMath.FANG_MAX_STACKS)));
    }
}
