package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StrikeTallyTest {

    private static final int PRIMARY_ID = 42;
    private static final int BYSTANDER_ID = 43;
    private static final float PER_PULSE = 20.0f;
    private static final float WEAPON_ATTACK = 8.0f;

    @Test
    void thePrimaryCorrectionIsTakenOnceAndOnlyByTheTargetVanillaHit() {
        StrikeTally tally = new StrikeTally();

        assertTrue(tally.consumePrimaryCorrection(PRIMARY_ID, PRIMARY_ID));
        assertFalse(tally.consumePrimaryCorrection(PRIMARY_ID, PRIMARY_ID));
        assertFalse(tally.consumePrimaryCorrection(BYSTANDER_ID, PRIMARY_ID));
    }

    @Test
    void aBystanderNeverTakesTheCorrectionEvenBeforeThePrimaryIsHit() {
        StrikeTally tally = new StrikeTally();

        assertFalse(tally.consumePrimaryCorrection(BYSTANDER_ID, PRIMARY_ID));
        assertTrue(tally.consumePrimaryCorrection(PRIMARY_ID, PRIMARY_ID));
    }

    @Test
    void aStrikeWithNoPrimaryTargetNeverCorrectsAnything() {
        StrikeTally tally = new StrikeTally();

        assertFalse(tally.consumePrimaryCorrection(PRIMARY_ID, -1));
        assertFalse(tally.consumePrimaryCorrection(-1, -1));
    }

    /**
     * The regression this class exists for. A five-pulse heavy flurry re-runs the damage pipeline
     * against the same body once per pulse, but there was only ever one vanilla {@code Player#attack}
     * hit to cancel, so only the first pulse pays the correction.
     */
    @Test
    void aFivePulseFlurryPaysTheVanillaCorrectionOnlyOnceAgainstThePrimaryTarget() {
        StrikeTally tally = new StrikeTally();

        float primaryTotal = 0.0f;
        float bystanderTotal = 0.0f;
        for (int pulse = 0; pulse < 5; pulse++) {
            primaryTotal += pulseDamage(tally, PRIMARY_ID);
            bystanderTotal += pulseDamage(tally, BYSTANDER_ID);
        }

        // 12 for the corrected first pulse (max(20 - 8, 5)), then four uncorrected 20s.
        assertEquals(92.0f, primaryTotal, 1.0E-4f);
        assertEquals(100.0f, bystanderTotal, 1.0E-4f);
    }

    private static float pulseDamage(StrikeTally tally, int targetId) {
        return tally.consumePrimaryCorrection(targetId, PRIMARY_ID)
                ? ForgeStrikeMath.primaryTargetDamage(PER_PULSE, WEAPON_ATTACK)
                : PER_PULSE;
    }

    /**
     * The count both once-per-target rules read: the rider fires only on the first touch, and a
     * flurry's stacking Arts read the pulse number straight off it.
     */
    @Test
    void touchesAreCountedPerTargetAndAreIndependentBetweenTargets() {
        StrikeTally tally = new StrikeTally();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(0, tally.touches(first));
        assertEquals(1, tally.noteTargetHit(first));
        assertEquals(2, tally.noteTargetHit(first));
        assertEquals(3, tally.noteTargetHit(first));
        assertEquals(1, tally.noteTargetHit(second));
        assertEquals(3, tally.touches(first));
        assertEquals(1, tally.touches(second));
    }

    @Test
    void impactsAndLeechAccumulateAcrossTargets() {
        StrikeTally tally = new StrikeTally();

        assertEquals(0, tally.impacts());
        assertEquals(1, tally.noteImpact());
        assertEquals(2, tally.noteImpact());
        assertEquals(2, tally.impacts());

        assertEquals(0.0f, tally.leechHealed(), 1.0E-4f);
        tally.addLeechHealed(1.5f);
        tally.addLeechHealed(0.5f);
        assertEquals(2.0f, tally.leechHealed(), 1.0E-4f);
    }

    /**
     * The Gale Step rule. A light flurry pulses three times, and each pulse runs the Art against
     * every body it touched. Hanging the dash on the press-wide count would dash once for three
     * pulses; hanging it on nothing would dash nine times for three mobs and carry the wielder four
     * and a half blocks off one press. The per-pass count is what makes it three.
     */
    @Test
    void theFirstBodyOfEveryPassIsCountedOncePerPassAndNotOncePerBody() {
        StrikeTally tally = new StrikeTally();
        int pressWideFirsts = 0;
        int perPassFirsts = 0;

        for (int pulse = 0; pulse < 3; pulse++) {
            tally.beginPass();
            for (int body = 0; body < 3; body++) {
                int impacts = tally.noteImpact();
                if (impacts == 1) {
                    pressWideFirsts++;
                }
                if (tally.passImpacts() == 1) {
                    perPassFirsts++;
                }
            }
        }

        assertEquals(1, pressWideFirsts);
        assertEquals(3, perPassFirsts);
        assertEquals(9, tally.impacts());
        assertEquals(3, tally.passImpacts());
    }

    @Test
    void aSinglePassStrikeCannotTellTheTwoCountsApart() {
        StrikeTally tally = new StrikeTally();

        assertEquals(1, tally.noteImpact());
        assertEquals(1, tally.passImpacts());
        assertEquals(2, tally.noteImpact());
        assertEquals(2, tally.passImpacts());
    }
}
