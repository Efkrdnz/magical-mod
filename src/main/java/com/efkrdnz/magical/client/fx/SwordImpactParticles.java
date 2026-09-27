package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.magic.sword.ImpactWave;
import com.efkrdnz.magical.magic.sword.ImpactWave.Kind;
import com.efkrdnz.magical.network.SwordImpactPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sword hit, thrown as vanilla particles: a ring of {@code POOF} running out across the struck
 * surface, {@code CRIT} sparks back the way the blade came, crumbs of the struck block, and for an
 * orbit through a body vanilla's own sweep crescent. Nothing here glows or is drawn by the mod's FX
 * library; the school's only picture of its own is its steel.
 *
 * <p>Every number comes from {@link ImpactWave}, which is pure and tested. What lives here is the
 * mapping onto vanilla types and one fact about them that makes the ring possible at all: every
 * vanilla provider jitters or re-rolls the velocity it is handed ({@code ExplodeParticle} adds
 * 0.05 a block of noise per axis, twice the speed of a small ring), so each particle is made
 * through {@link ParticleEngine#createParticle}, which hands it back, and its speed and life are
 * then set exactly. That also skips {@code LevelRenderer}'s own particle-setting gate, so the gate
 * is asked here instead, the same way: nothing at Minimal, every other particle at Decreased,
 * nothing past 32 blocks.
 */
public final class SwordImpactParticles {

    /** Vanilla's own cut-off, from {@code LevelRenderer.addParticleInternal}: 32 blocks. */
    private static final double MAX_DISTANCE_SQR = 1024.0D;

    /** How far off the struck surface the wave is born, so a puff on the floor is not born in it. */
    private static final double LIFT = 0.08D;

    /**
     * {@code AttackSweepParticle} reads the x speed it is handed as a size, {@code 1 - x / 2}: this
     * is a crescent 1.1 blocks across, a little over half of a player's own sweep.
     */
    private static final double SWEEP_SIZE = 0.9D;

    /** Sideways drift on a trembling crumb, so the rim reads as shaken rather than as a row. */
    private static final double TREMOR_DRIFT = 0.02D;

    /** How big a mote of the weapon's colour is drawn: vanilla's redstone dust is 1. */
    private static final float ACCENT_SIZE = 0.9F;

    private static final ImpactWave.Ledger LEDGER = new ImpactWave.Ledger();

    private SwordImpactParticles() {
    }

    public static void spawn(SwordImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        ParticleStatus status = minecraft.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return;
        }
        if (minecraft.gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(payload.x(), payload.y(), payload.z()) > MAX_DISTANCE_SQR) {
            return;
        }
        Kind kind = Kind.byOrdinal(payload.kind());
        boolean echo = kind.merges() && LEDGER.echo(payload.x(), payload.y(), payload.z(), level.getGameTime());
        int stride = status == ParticleStatus.DECREASED ? 2 : 1;

        double[] n = unit(payload.nx(), payload.ny(), payload.nz());
        double x = payload.x() + n[0] * LIFT;
        double y = payload.y() + n[1] * LIFT;
        double z = payload.z() + n[2] * LIFT;
        ParticleEngine engine = minecraft.particleEngine;
        RandomSource random = level.random;

        boolean accented = payload.accent() != 0;
        ring(engine, kind, echo, stride, x, y, z, n, payload.radius(), random);
        sparks(engine, kind, echo, accented, stride, x, y, z, n, random);
        if (accented) {
            accents(engine, kind, echo, payload.accent(), x, y, z, n, random);
        }
        BlockState struck = payload.block() == 0 ? null : Block.stateById(payload.block());
        if (struck != null && !struck.isAir()) {
            crumbs(engine, kind, echo, stride, x, y, z, n, payload.radius(), struck, random);
        }
        if (kind.sweep() && !echo) {
            engine.createParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, SWEEP_SIZE, 0.0D, 0.0D);
        }
    }

    /** The wave: puffs born near the hit and run out to the radius, or sat on the rim for a tremor. */
    private static void ring(ParticleEngine engine, Kind kind, boolean echo, int stride,
            double x, double y, double z, double[] n, double radius, RandomSource random) {
        int puffs = ImpactWave.puffs(kind, echo);
        if (puffs <= 0 || radius <= 0.0D) {
            return;
        }
        double[][] ring = ImpactWave.ring(puffs, n[0], n[1], n[2], random.nextDouble() * Math.PI * 2.0D);
        double start = kind.rim() ? radius : ImpactWave.RING_START;
        double speed = kind.rim() ? 0.0D : ImpactWave.ringSpeed(radius);
        for (int i = 0; i < ring.length; i += stride) {
            double[] d = ring[i];
            Particle puff = engine.createParticle(ParticleTypes.POOF,
                    x + d[0] * start, y + d[1] * start, z + d[2] * start, 0.0D, 0.0D, 0.0D);
            if (puff == null) {
                continue;
            }
            puff.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
            puff.setLifetime(ImpactWave.RING_LIFE);
            puff.scale(kind.puffScale());
        }
    }

    /** Sparks back the way the blade came, inside the cone round the normal. */
    private static void sparks(ParticleEngine engine, Kind kind, boolean echo, boolean accented, int stride,
            double x, double y, double z, double[] n, RandomSource random) {
        int sparks = ImpactWave.sparks(kind, echo, accented);
        for (int i = 0; i < sparks; i += stride) {
            double[] d = ImpactWave.spark(n[0], n[1], n[2], random.nextDouble(), random.nextDouble());
            double speed = ImpactWave.SPARK_SPEED * (0.6D + 0.6D * random.nextDouble());
            Particle spark = engine.createParticle(ParticleTypes.CRIT, x, y, z, 0.0D, 0.0D, 0.0D);
            if (spark != null) {
                spark.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
            }
        }
    }

    /**
     * Motes in the racked weapon's colour, thrown the way the sparks are. Not thinned at Decreased:
     * there are two or three, and an echo's one stands in for a spark it already gave up.
     */
    private static void accents(ParticleEngine engine, Kind kind, boolean echo, int accent,
            double x, double y, double z, double[] n, RandomSource random) {
        int motes = ImpactWave.accents(kind, echo);
        if (motes <= 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(accent, ACCENT_SIZE);
        for (int i = 0; i < motes; i++) {
            double[] d = ImpactWave.spark(n[0], n[1], n[2], random.nextDouble(), random.nextDouble());
            double speed = ImpactWave.SPARK_SPEED * (0.5D + 0.5D * random.nextDouble());
            Particle mote = engine.createParticle(dust, x, y, z, 0.0D, 0.0D, 0.0D);
            if (mote != null) {
                mote.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
            }
        }
    }

    /**
     * Crumbs of the struck block: thrown out of a wall, hopping on the rim of a tremor, or broken up
     * through the ground across the whole eruption the way a mace smash breaks it.
     */
    private static void crumbs(ParticleEngine engine, Kind kind, boolean echo, int stride,
            double x, double y, double z, double[] n, double radius, BlockState struck, RandomSource random) {
        int crumbs = ImpactWave.crumbs(kind, echo);
        if (crumbs <= 0) {
            return;
        }
        if (kind.pillar()) {
            ParticleOptions pillar = new BlockParticleOption(ParticleTypes.DUST_PILLAR, struck);
            for (int i = 0; i < crumbs; i += stride) {
                double r = radius * Math.sqrt(random.nextDouble());
                double a = random.nextDouble() * Math.PI * 2.0D;
                // The provider rolls its own scatter round the lift it is handed, so nothing is
                // set after it: this one is meant to be loose.
                engine.createParticle(pillar, x + Math.cos(a) * r, y, z + Math.sin(a) * r,
                        0.0D, ImpactWave.PILLAR_LIFT, 0.0D);
            }
            return;
        }
        ParticleOptions grit = new BlockParticleOption(ParticleTypes.BLOCK, struck);
        if (kind.rim()) {
            double[][] rim = ImpactWave.ring(crumbs, n[0], n[1], n[2], random.nextDouble() * Math.PI * 2.0D);
            for (int i = 0; i < rim.length; i += stride) {
                double[] d = rim[i];
                Particle crumb = engine.createParticle(grit, x + d[0] * radius, y + d[1] * radius,
                        z + d[2] * radius, 0.0D, 0.0D, 0.0D);
                if (crumb != null) {
                    double hop = ImpactWave.TREMOR_HOP * (0.7D + 0.6D * random.nextDouble());
                    crumb.setParticleSpeed(d[0] * TREMOR_DRIFT + n[0] * hop, d[1] * TREMOR_DRIFT + n[1] * hop,
                            d[2] * TREMOR_DRIFT + n[2] * hop);
                }
            }
            return;
        }
        for (int i = 0; i < crumbs; i += stride) {
            double[] d = ImpactWave.spark(n[0], n[1], n[2], random.nextDouble(), random.nextDouble());
            double speed = ImpactWave.CRUMB_SPEED * (0.6D + 0.6D * random.nextDouble());
            Particle crumb = engine.createParticle(grit, x, y, z, 0.0D, 0.0D, 0.0D);
            if (crumb != null) {
                crumb.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
            }
        }
    }

    /** The normal the payload carried, made unit; a degenerate one is read as up. */
    private static double[] unit(float nx, float ny, float nz) {
        double length = Math.sqrt((double) nx * nx + (double) ny * ny + (double) nz * nz);
        if (length < 1.0E-6D) {
            return new double[] {0.0D, 1.0D, 0.0D};
        }
        return new double[] {nx / length, ny / length, nz / length};
    }
}
