package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.visual.ForgeImpactForm;
import com.efkrdnz.magical.forge.visual.ForgeMatter;
import com.efkrdnz.magical.forge.visual.ForgeMatter.Emission;
import com.efkrdnz.magical.forge.visual.ForgeMatter.Ink;
import com.efkrdnz.magical.forge.visual.MatterKind;
import com.efkrdnz.magical.magic.sword.ImpactWave;
import com.efkrdnz.magical.magic.sword.ImpactWave.Kind;
import com.efkrdnz.magical.network.ForgeImpactPayload;
import com.efkrdnz.magical.particle.ForgeMatterOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A forged strike's impact, thrown as particles: a flash where the blade bit, the element's own
 * matter knocked out of what it struck, a small ring of dust across the surface, and for a wall the
 * crumbs of the block.
 *
 * <p>The ring and spark numbers are the sword school's ({@link ImpactWave}), which were settled
 * there and are pure: a forged cut into a body rings as a sword's does, a wave into stone clangs as
 * one does. What is the forge's own is the matter, off the element's table, and the flash. The
 * particle setting is asked by hand the way {@code SwordImpactParticles} asks it, because
 * {@link ParticleEngine#createParticle} skips {@code LevelRenderer}'s gate.
 */
public final class ForgeImpactParticles {

    /** Vanilla's own cut-off: 32 blocks. */
    private static final double MAX_DISTANCE_SQR = 1024.0D;
    /** How far off the struck surface the impact is born, so nothing is born inside it. */
    private static final double LIFT = 0.08D;
    /** How far out of a body's side, past half its width, the wound is. */
    private static final double BODY_GAP = 0.1D;
    /** The flash of a heavy blow against a light one. */
    private static final float HEAVY_FLASH = 1.3F;
    /** The speed of a piece of matter is its share's times somewhere in this range. */
    private static final double SLOWEST = 0.6D;
    private static final double SPREAD = 0.8D;

    private static final ImpactWave.Ledger LEDGER = new ImpactWave.Ledger();
    private static final ForgeElementKind[] ELEMENTS = ForgeElementKind.values();

    private ForgeImpactParticles() {}

    public static void spawn(ForgeImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        ParticleStatus status = minecraft.options.particles().get();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (status == ParticleStatus.MINIMAL
                || camera.distanceToSqr(payload.x(), payload.y(), payload.z()) > MAX_DISTANCE_SQR) {
            return;
        }
        ForgeImpactForm form = ForgeImpactForm.byOrdinal(payload.form());
        ForgeElementKind element = ELEMENTS[Math.min(payload.element(), ELEMENTS.length - 1)];
        Kind wave = form == ForgeImpactForm.BLOCK ? Kind.CLANG : Kind.CUT;
        // a flurry lands its pulses on one body a few ticks apart: the second is an echo of the first
        boolean merged = LEDGER.echo(payload.x(), payload.y(), payload.z(), level.getGameTime());
        boolean echo = merged || payload.echo();
        int stride = status == ParticleStatus.DECREASED ? 2 : 1;

        double[] n = unit(payload.nx(), payload.ny(), payload.nz());
        Vec3 at = wound(payload, n, camera);
        ParticleEngine engine = minecraft.particleEngine;
        RandomSource random = level.random;

        if (!merged) {
            flash(engine, payload, at);
            ring(engine, wave, echo, stride, at, n, random);
        }
        matter(engine, payload, element, echo, stride, at, n, random);
        BlockState struck = payload.block() == 0 ? null : Block.stateById(payload.block());
        if (struck != null && !struck.isAir()) {
            crumbs(engine, wave, echo, stride, at, n, struck, random);
        }
    }

    /**
     * Where on the struck thing the wound is. A body is struck at its middle and a wound there is
     * inside it; the side of it the viewer can see is where anything thrown out of it shows.
     */
    private static Vec3 wound(ForgeImpactPayload payload, double[] n, Vec3 camera) {
        Vec3 centre = new Vec3(payload.x(), payload.y(), payload.z());
        if (payload.radius() <= 0.0F) {
            return centre.add(n[0] * LIFT, n[1] * LIFT, n[2] * LIFT);
        }
        Vec3 toViewer = new Vec3(camera.x - centre.x, 0.0D, camera.z - centre.z);
        if (toViewer.lengthSqr() < 1.0E-6D) {
            toViewer = new Vec3(n[0], 0.0D, n[2]);
        }
        if (toViewer.lengthSqr() < 1.0E-6D) {
            return centre;
        }
        return centre.add(toViewer.normalize().scale(payload.radius() + BODY_GAP));
    }

    /** The bite: one bright crescent where the edge met, gone in a quarter of a second. */
    private static void flash(ParticleEngine engine, ForgeImpactPayload payload, Vec3 at) {
        ForgeMatterOptions nick = new ForgeMatterOptions(MagicalParticles.FORGE_MATTER.get(), MatterKind.NICK,
                0xFFFFFF, payload.edge(), payload.primary(), payload.heavy() ? HEAVY_FLASH : 1.0F);
        engine.createParticle(nick, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
    }

    /** The element's matter, out of the struck surface inside the spark cone; smoke and light rise. */
    private static void matter(ParticleEngine engine, ForgeImpactPayload payload, ForgeElementKind element,
            boolean echo, int stride, Vec3 at, double[] n, RandomSource random) {
        List<Emission> emissions = ForgeMatter.hit(element);
        int[] dealt = ForgeMatter.deal(emissions, ForgeMatter.hitCount(payload.grade(), payload.heavy(), echo));
        boolean[] kept = ForgeMatter.keep(dealt, emissions.size(), stride);
        for (int i = 0; i < dealt.length; i++) {
            if (!kept[i]) {
                continue;
            }
            Emission emission = emissions.get(dealt[i]);
            double[] d = emission.launch() == ForgeMatter.Launch.UP
                    ? ImpactWave.spark(0.0D, 1.0D, 0.0D, random.nextDouble(), random.nextDouble())
                    : ImpactWave.spark(n[0], n[1], n[2], random.nextDouble(), random.nextDouble());
            double speed = emission.speed() * (SLOWEST + SPREAD * random.nextDouble());
            ForgeMatterOptions options = new ForgeMatterOptions(MagicalParticles.FORGE_MATTER.get(), emission.kind(),
                    ink(payload, emission.stops().hot()), ink(payload, emission.stops().body()),
                    ink(payload, emission.stops().cool()), 1.0F);
            engine.createParticle(options, at.x, at.y, at.z, d[0] * speed, d[1] * speed, d[2] * speed);
        }
    }

    /** A small ring of dust run out across the struck surface. */
    private static void ring(ParticleEngine engine, Kind wave, boolean echo, int stride, Vec3 at, double[] n,
            RandomSource random) {
        int puffs = ImpactWave.puffs(wave, echo);
        if (puffs <= 0) {
            return;
        }
        double[][] ring = ImpactWave.ring(puffs, n[0], n[1], n[2], random.nextDouble() * Math.PI * 2.0D);
        double speed = ImpactWave.ringSpeed(wave.radius());
        for (int i = 0; i < ring.length; i += stride) {
            double[] d = ring[i];
            Particle puff = engine.createParticle(ParticleTypes.POOF, at.x + d[0] * ImpactWave.RING_START,
                    at.y + d[1] * ImpactWave.RING_START, at.z + d[2] * ImpactWave.RING_START, 0.0D, 0.0D, 0.0D);
            if (puff != null) {
                puff.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
                puff.setLifetime(ImpactWave.RING_LIFE);
                puff.scale(wave.puffScale());
            }
        }
    }

    /** Crumbs of the struck block, thrown back out of its face. */
    private static void crumbs(ParticleEngine engine, Kind wave, boolean echo, int stride, Vec3 at, double[] n,
            BlockState struck, RandomSource random) {
        int crumbs = ImpactWave.crumbs(wave, echo);
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.BLOCK, struck);
        for (int i = 0; i < crumbs; i += stride) {
            double[] d = ImpactWave.spark(n[0], n[1], n[2], random.nextDouble(), random.nextDouble());
            double speed = ImpactWave.CRUMB_SPEED * (0.6D + 0.6D * random.nextDouble());
            Particle crumb = engine.createParticle(grit, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
            if (crumb != null) {
                crumb.setParticleSpeed(d[0] * speed, d[1] * speed, d[2] * speed);
            }
        }
    }

    private static int ink(ForgeImpactPayload payload, Ink ink) {
        return ForgeMatter.ink(ink, payload.primary(), payload.secondary(), payload.edge());
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
