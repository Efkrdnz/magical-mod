package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a press lands on one target that every body meets: a hit per flying body, a pulse per
 * interval of a static's life, an end explosion at full strength, and only the payloads that
 * release at the target - a Latch on a flying body, a Fuse or an Epitaph on a static.
 */
class LandingTest {

    private static final double EPS = 1.0E-9D;

    private static ProjectilePlan body(VersePrototype prototype, ShotState stamped, PayloadKind kind, ShotPlan payload) {
        return new ProjectilePlan(prototype, prototype.id(), stamped, kind, 0, payload);
    }

    private static ProjectilePlan body(VersePrototype prototype) {
        return body(prototype, new ShotState(), PayloadKind.NONE, null);
    }

    private static ShotPlan shot(ProjectilePlan... bodies) {
        return new ShotPlan(List.of(bodies), new ShotState());
    }

    private static double pulsesOf(VersePrototype prototype) {
        return prototype.durationTicks() / prototype.pulseIntervalTicks();
    }

    @Test
    void everyFlyingBodyLandsItsHit() {
        Landing landing = Landing.of(shot(body(VersePrototypes.NEEDLE), body(VersePrototypes.NEEDLE), body(VersePrototypes.NEEDLE)));
        assertEquals(3.0D * VersePrototypes.NEEDLE.damage(), landing.damage(), EPS);
        assertEquals(0.0D, landing.healing(), EPS);
    }

    @Test
    void aStampedAddAndAnEndExplosionCount() {
        ShotState stamped = new ShotState();
        stamped.addDamage(2.0D);
        Landing landing = Landing.of(shot(body(VersePrototypes.EMBER, stamped, PayloadKind.NONE, null)));
        assertEquals(VersePrototypes.EMBER.damage() + 2.0D + VersePrototypes.EMBER.explosionDamage(), landing.damage(), EPS);
    }

    @Test
    void aStaticLandsAPulsePerIntervalOfItsLifeAndHealsTheSameWay() {
        Landing storm = Landing.of(shot(body(VersePrototypes.RING_STORM)));
        assertEquals(VersePrototypes.RING_STORM.damage() * pulsesOf(VersePrototypes.RING_STORM), storm.damage(), EPS);
        Landing balm = Landing.of(shot(body(VersePrototypes.RING_BALM)));
        assertEquals(0.0D, balm.damage(), EPS);
        assertEquals(VersePrototypes.RING_BALM.healing() * pulsesOf(VersePrototypes.RING_BALM), balm.healing(), EPS);
        Landing burst = Landing.of(shot(body(VersePrototypes.BURST)));
        assertEquals(VersePrototypes.BURST.explosionDamage(), burst.damage(), EPS, "a burst is its explosion");
    }

    @Test
    void onlyThePayloadsThatReleaseAtTheTargetCount() {
        ShotPlan ring = shot(body(VersePrototypes.RING_STORM));
        double ringLands = Landing.of(ring).damage();
        double needle = VersePrototypes.NEEDLE.damage();
        assertEquals(needle + ringLands, Landing.of(shot(body(VersePrototypes.NEEDLE, new ShotState(), PayloadKind.LATCH, ring))).damage(), EPS, "a Latch releases on the hit");
        assertEquals(needle, Landing.of(shot(body(VersePrototypes.NEEDLE, new ShotState(), PayloadKind.FUSE, ring))).damage(), EPS, "a Fuse on a flying body goes off wherever it is");
        assertEquals(needle, Landing.of(shot(body(VersePrototypes.NEEDLE, new ShotState(), PayloadKind.EPITAPH, ring))).damage(), EPS, "an Epitaph on a flying body, wherever it expired");
        ShotPlan burst = shot(body(VersePrototypes.BURST));
        double burstLands = VersePrototypes.BURST.explosionDamage();
        assertEquals(ringLands + burstLands, Landing.of(shot(body(VersePrototypes.RING_STORM, new ShotState(), PayloadKind.EPITAPH, burst))).damage(), EPS, "an Epitaph on a static releases where it stood");
        assertEquals(ringLands + burstLands, Landing.of(shot(body(VersePrototypes.RING_STORM, new ShotState(), PayloadKind.FUSE, burst))).damage(), EPS, "so does a Fuse on one");
        assertEquals(ringLands, Landing.of(shot(body(VersePrototypes.RING_STORM, new ShotState(), PayloadKind.LATCH, burst))).damage(), EPS, "a static never hits");
    }

    @Test
    void nulledDamageLandsNothingAndAWordIsNothing() {
        ShotState nulled = new ShotState();
        nulled.nullDamage();
        assertTrue(Landing.of(shot(body(VersePrototypes.EMBER, nulled, PayloadKind.NONE, null))).isNothing(), "nulled damage nulls the hit and the explosion");
        assertTrue(Landing.of(shot(body(VersePrototypes.WORD_FAR))).isNothing());
        assertFalse(Landing.of(shot(body(VersePrototypes.NEEDLE))).isNothing());
    }

    @Test
    void aTridentOfNeedlesLandsThreeNeedlesWorth() {
        RecitePlan plan = press(session("trident", "needle", "needle", "needle"));
        assertEquals(3, plan.bodies().size());
        assertEquals(3.0D * VersePrototypes.NEEDLE.damage(), Landing.of(plan.root()).damage(), EPS);
    }

    @Test
    void amountsReadWholeWhereTheyAreWholeAndToATenthOtherwise() {
        assertEquals("9", Landing.amount(9.0D));
        assertEquals("8.5", Landing.amount(8.5D));
        assertEquals("2.3", Landing.amount(2.26D));
        assertEquals("0", Landing.amount(0.04D));
    }
}
