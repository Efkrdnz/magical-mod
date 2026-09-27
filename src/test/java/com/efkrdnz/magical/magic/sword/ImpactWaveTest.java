package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.sword.ImpactWave.Kind;
import org.junit.jupiter.api.Test;

/**
 * What a sword hitting something is allowed to throw into the air, measured.
 *
 * <p>The school draws its steel and nothing else, so a hit is the one moment it may add anything
 * to the frame, and what it adds is vanilla: a ring of puffs running out across the struck
 * surface, the sparks or grit the hit throws back, nothing that glows. Every rule here is a
 * property the client spawner relies on and cannot check for itself - that the ring lies in the
 * surface it was struck across, that it stops at the radius it was asked for, that the sparks go
 * back the way the blade came - plus the one the old Loose flash was written for and never got to
 * enforce: up to {@link SwordBladeEntity#MAX_IN_FLIGHT} blades land inside the same handful of
 * ticks, so a volley into one body must read as a volley and not as a cloud.
 *
 * <p>Pure: {@link ImpactWave} has no Minecraft in it, and the two constants taken from
 * {@link SwordBladeEntity} are compile-time ints the compiler inlines.
 */
class ImpactWaveTest {

    /**
     * {@code TrackingEmitter}, which is what vanilla spawns for one critical hit: sixteen tries a
     * tick for three ticks. The yardstick for "a volley costs no more than one hit".
     */
    private static final int VANILLA_CRIT_ATTEMPTS = 48;

    /** Up, down, both horizontal axes, a diagonal, and one a hair off vertical. */
    private static final double[][] NORMALS = {
            {0.0D, 1.0D, 0.0D}, {0.0D, -1.0D, 0.0D}, {1.0D, 0.0D, 0.0D}, {0.0D, 0.0D, -1.0D},
            {1.0D, 1.0D, 1.0D}, {0.01D, 1.0D, 0.0D}, {-0.3D, 0.2D, 0.9D}};

    private static double[] unit(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double length(double[] v) {
        return Math.sqrt(dot(v, v));
    }

    // ---- the ring ---------------------------------------------------------------------------------

    @Test
    void everyRingLiesFlatInTheSurfaceItWasStruckAcross() {
        // A ring tilted out of its plane is a smear in first person and a crooked hoop from the
        // side; lying in the plane square to the normal is what makes it read as a wave running
        // out across the thing that was hit.
        for (double[] raw : NORMALS) {
            double[] n = unit(raw);
            for (double[] direction : ImpactWave.ring(12, raw[0], raw[1], raw[2], 0.3D)) {
                assertEquals(1.0D, length(direction), 1.0E-9D, "a ring direction is not a unit vector");
                assertEquals(0.0D, dot(direction, n), 1.0E-9D,
                        "a ring struck across " + java.util.Arrays.toString(raw) + " leaves its own plane");
            }
        }
    }

    @Test
    void aRingIsEvenlySpacedAndNeverDoublesAPuff() {
        double[][] ring = ImpactWave.ring(10, 0.0D, 1.0D, 0.0D, 0.0D);
        double step = Math.cos(2.0D * Math.PI / 10.0D);
        for (int i = 0; i < ring.length; i++) {
            double[] next = ring[(i + 1) % ring.length];
            assertEquals(step, dot(ring[i], next), 1.0E-9D, "neighbours " + i + " and " + (i + 1) + " are unevenly spaced");
            for (int j = i + 1; j < ring.length; j++) {
                assertTrue(dot(ring[i], ring[j]) < 1.0D - 1.0E-6D, "two puffs of one ring leave the same way");
            }
        }
    }

    @Test
    void aRingStopsAtTheRadiusItWasAskedFor() {
        // The puffs are vanilla POOF, which the spawner launches at ringSpeed and the particle
        // then drags at ExplodeParticle's friction for RING_LIFE ticks. A ring that overshoots is
        // a promise of reach the blade never had; one that falls short is a wave that never went
        // anywhere.
        for (double radius : new double[] {0.5D, 0.7D, 1.0D, 1.6D, 2.4D}) {
            double speed = ImpactWave.ringSpeed(radius);
            assertTrue(speed > 0.0D, "a ring of radius " + radius + " is launched at " + speed);
            assertEquals(radius, ImpactWave.reach(speed), 1.0E-9D, "a ring of radius " + radius + " stops elsewhere");
        }
    }

    @Test
    void aHitIsASmallWave() {
        // What the user asked for, and the bound that keeps it true: a hit is a wave about the
        // size of the body it went into. The two kinds with no radius of their own are Below's,
        // which take the cylinder that catches from the skill, because the ring on the ground is
        // the hit ring and nothing else.
        for (Kind kind : Kind.values()) {
            if (kind.radius() == 0.0D) {
                assertTrue(kind == Kind.ERUPTION || kind == Kind.TREMOR,
                        kind + " has no radius of its own and is not one of Below's");
                continue;
            }
            assertTrue(kind.radius() <= ImpactWave.MAX_HIT_RADIUS,
                    kind + " runs out to " + kind.radius() + " blocks, which is not a small wave");
            assertTrue(kind.radius() >= ImpactWave.RING_START * 3.0D,
                    kind + " runs out to " + kind.radius() + " blocks from a start of "
                            + ImpactWave.RING_START + ", which is a puff that never became a wave");
        }
    }

    @Test
    void theTremorSitsOnTheRimAndEverythingElseRunsOut() {
        // The tremor is the warning, not a hit: it marks where the cylinder's edge is, so it is
        // born on that edge and does not travel. Everything else is born at the centre and runs.
        assertTrue(Kind.TREMOR.rim(), "the tremor is drawn at the centre, not on the edge it warns of");
        for (Kind kind : Kind.values()) {
            if (kind != Kind.TREMOR) {
                assertFalse(kind.rim(), kind + " is born on its rim and never runs out");
            }
        }
    }

    // ---- sparks and grit ---------------------------------------------------------------------------

    @Test
    void sparksLeaveTheWayTheBladeCameFrom() {
        // The normal on the wire points out of the struck surface, back toward whatever struck
        // it, so every spark is inside the cone round it and none of them flies into the body.
        double cone = Math.cos(Math.toRadians(ImpactWave.SPARK_CONE_DEGREES)) - 1.0E-9D;
        for (double[] raw : NORMALS) {
            double[] n = unit(raw);
            for (double a = 0.0D; a < 1.0D; a += 0.125D) {
                for (double b = 0.0D; b < 1.0D; b += 0.0625D) {
                    double[] spark = ImpactWave.spark(raw[0], raw[1], raw[2], a, b);
                    assertEquals(1.0D, length(spark), 1.0E-9D, "a spark direction is not a unit vector");
                    assertTrue(dot(spark, n) >= cone,
                            "a spark leaves " + Math.toDegrees(Math.acos(dot(spark, n)))
                                    + " degrees off the normal, outside the cone");
                }
            }
        }
    }

    @Test
    void onlyStoneThrowsGritAndOnlySteelThrowsSparks() {
        // A blade into a body throws no grit, and Below's two kinds throw nothing that is not the
        // ground: sparks there would be steel meeting steel, which is not what happened.
        assertEquals(0, Kind.CUT.crumbs(), "a blade into a body throws grit");
        assertEquals(0, Kind.PARRY.crumbs(), "turning an arrow throws grit");
        assertTrue(Kind.CLANG.crumbs() > 0, "a blade into stone throws no grit");
        assertTrue(Kind.ERUPTION.crumbs() > 0, "Below comes up out of the ground and moves none of it");
        assertTrue(Kind.TREMOR.crumbs() > 0, "the ground trembles without a grain moving");
        assertEquals(0, Kind.ERUPTION.sparks(), "the ground sparks");
        assertEquals(0, Kind.TREMOR.sparks(), "the ground sparks");
        assertTrue(Kind.CUT.sparks() > 0 && Kind.CLANG.sparks() > 0 && Kind.PARRY.sparks() > 0,
                "a steel hit throws no sparks");
        assertTrue(Kind.PARRY.sparks() >= Kind.CUT.sparks(), "steel on steel sparks less than steel in a body");
        assertTrue(Kind.ERUPTION.pillar(), "Below's grit is not thrown up the way the ground is broken from beneath");
        assertTrue(Kind.SHEAR.sweep(), "an orbit through a body does not say it was a sweep");
    }

    // ---- a volley ---------------------------------------------------------------------------------

    @Test
    void aVolleyIntoOneBodyRingsOnceAndSparksForEveryBlade() {
        ImpactWave.Ledger ledger = new ImpactWave.Ledger();
        assertFalse(ledger.echo(10.0D, 64.0D, 10.0D, 100L), "the first blade in is an echo of nothing");
        for (int blade = 1; blade < SwordBladeEntity.MAX_IN_FLIGHT; blade++) {
            long tick = 100L + blade % 3;
            assertTrue(ledger.echo(10.0D + blade * 0.02D, 64.0D, 10.0D, tick),
                    "blade " + blade + " of a volley into one body rings again");
        }
        assertTrue(ImpactWave.sparks(Kind.CUT, true) >= 1, "a blade landing on the same spot shows nothing at all");
    }

    @Test
    void aSecondBodyAndTheNextVolleyRingOfTheirOwn() {
        ImpactWave.Ledger ledger = new ImpactWave.Ledger();
        assertFalse(ledger.echo(0.0D, 64.0D, 0.0D, 10L));
        assertFalse(ledger.echo(ImpactWave.Ledger.MERGE_RADIUS * 2.0D, 64.0D, 0.0D, 10L),
                "a body two merge radii away is folded into another body's wave");
        assertFalse(ledger.echo(0.0D, 64.0D, 0.0D, 10L + ImpactWave.Ledger.MERGE_TICKS + 1L),
                "the next volley into the same body is still the last one's echo");
    }

    @Test
    void aFullVolleyCostsNoMoreThanOneVanillaCriticalHit() {
        // The rule the old Loose flash test was written for, kept for the thing that replaced it.
        // Twelve blades land inside a handful of ticks; the first rings and sparks, the other
        // eleven only spark. Together they may spend what vanilla spends on one critical hit.
        int first = Kind.CUT.puffs() + ImpactWave.sparks(Kind.CUT, false);
        int echoes = (SwordBladeEntity.MAX_IN_FLIGHT - 1) * ImpactWave.sparks(Kind.CUT, true);
        int volley = first + echoes;
        assertTrue(volley <= VANILLA_CRIT_ATTEMPTS,
                "a full volley into one body throws " + volley + " particles where vanilla throws "
                        + VANILLA_CRIT_ATTEMPTS + " for one critical hit");
        // The floor: the first blade's ring must be a ring, and every blade after it must still
        // show, or the volley has been divided away rather than shared out.
        assertTrue(Kind.CUT.puffs() >= 8, "a ring of " + Kind.CUT.puffs() + " puffs is a scatter");
        assertTrue(echoes >= SwordBladeEntity.MAX_IN_FLIGHT - 1, "an echo shows nothing");
    }

    @Test
    void aVolleyOfRackedBladesCostsWhatAPlainOneDid() {
        // A fire blade rings with embers, and a volley of them into one body must still spend no
        // more than a plain volley does: every echo gives up a spark for its mote.
        int plainEcho = ImpactWave.sparks(Kind.CUT, true);
        int accentedEcho = ImpactWave.sparks(Kind.CUT, true, true) + ImpactWave.accents(Kind.CUT, true);
        assertEquals(plainEcho, accentedEcho, "an accented echo costs a different number of particles");
        assertTrue(ImpactWave.accents(Kind.CUT, true) >= 1, "a racked blade landing late shows no colour");
        int first = Kind.CUT.puffs() + ImpactWave.sparks(Kind.CUT, false, true) + ImpactWave.accents(Kind.CUT, false);
        int volley = first + (SwordBladeEntity.MAX_IN_FLIGHT - 1) * accentedEcho;
        assertTrue(volley <= VANILLA_CRIT_ATTEMPTS,
                "a volley of racked blades throws " + volley + " particles where vanilla throws "
                        + VANILLA_CRIT_ATTEMPTS + " for one critical hit");
    }

    @Test
    void theWarningCarriesNoWeapon() {
        assertEquals(0, ImpactWave.accents(Kind.TREMOR, false), "the tremor rings in a weapon's colour");
        for (Kind kind : Kind.values()) {
            if (kind != Kind.TREMOR) {
                assertTrue(ImpactWave.accents(kind, false) >= 2, kind + " shows a weapon's colour as a speck");
            }
            assertEquals(ImpactWave.sparks(kind, false), ImpactWave.sparks(kind, false, true),
                    kind + " gives up sparks on a first hit, which is not an echo");
        }
    }

    @Test
    void aForgedKindIsClampedAndNotThrown() {
        assertEquals(Kind.CUT, Kind.byOrdinal(-4));
        assertEquals(Kind.values()[Kind.values().length - 1], Kind.byOrdinal(99));
        for (Kind kind : Kind.values()) {
            assertEquals(kind, Kind.byOrdinal(kind.ordinal()));
        }
    }
}
