package com.efkrdnz.magical.magic.visual.sigil;

import com.efkrdnz.magical.magic.visual.sigil.SigilPlacement.Spawn;
import com.efkrdnz.magical.particle.SigilParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Sends a {@link SigilMark} out through a {@link SigilPlacement}: one {@code sendParticles} with a
 * count of zero per sigil, so each travels exactly its own velocity, seen by everybody in range
 * under their own particle setting.
 *
 * <pre>{@code
 * Sigils.pop(level, player, SigilMark.of(Sigil.HOURGLASS).ink(SigilInk.GOLD));
 * Sigils.crown(level, target, SigilMark.of(Sigil.runes()).ink(SigilInk.VIOLET).scale(1.5F), 8, 8);
 * }</pre>
 */
public final class Sigils {

    private Sigils() {
    }

    /** The particle for the {@code i}-th sigil of a mark. */
    public static SigilParticleOptions options(SigilMark mark, int i) {
        SigilInk ink = mark.inkAt(i);
        return new SigilParticleOptions(MagicalParticles.SIGIL.get(), mark.sigilAt(i), ink.core(), ink.glow(), mark.scale(), mark.motion());
    }

    public static void play(ServerLevel level, SigilMark mark, List<Spawn> spawns) {
        for (int i = 0; i < spawns.size(); i++) {
            Spawn spawn = spawns.get(i);
            level.sendParticles(options(mark, i), spawn.x(), spawn.y(), spawn.z(), 0, spawn.vx(), spawn.vy(), spawn.vz(), 1.0D);
        }
    }

    public static void crown(ServerLevel level, Entity body, SigilMark mark, int slots, int filled) {
        play(level, mark, SigilPlacement.crown(body.getX(), body.getY(), body.getZ(), body.getBbWidth(), body.getBbHeight(), slots, filled));
    }

    public static void pop(ServerLevel level, Entity body, SigilMark mark) {
        play(level, mark, List.of(SigilPlacement.pop(body.getX(), body.getY(), body.getZ(), body.getBbHeight())));
    }

    public static void halo(ServerLevel level, Entity body, SigilMark mark, int count) {
        double phase = level.random.nextDouble() * Math.PI * 2.0D;
        play(level, mark, SigilPlacement.halo(body.getX(), body.getY(), body.getZ(), body.getBbWidth(), body.getBbHeight(), count, phase));
    }

    public static void burst(ServerLevel level, Vec3 at, SigilMark mark, int count, double speed) {
        play(level, mark, SigilPlacement.burst(at.x, at.y, at.z, count, speed));
    }

    public static void rise(ServerLevel level, Vec3 centre, double radius, SigilMark mark, int count) {
        play(level, mark, SigilPlacement.rise(centre.x, centre.y, centre.z, radius, count, new Random(level.random.nextLong())));
    }
}
