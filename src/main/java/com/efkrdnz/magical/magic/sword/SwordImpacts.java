package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.magic.sword.ImpactWave.Kind;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.SwordImpactPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one door a sword hit goes out through: a small packet to everybody near, and each client
 * throws a wave of vanilla particles for it.
 *
 * <p>A hit is the only moment the school adds anything to the frame besides its steel, and it is
 * sent rather than spawned with {@code ServerLevel.sendParticles} because a ring is a dozen
 * particles each with its own position and velocity - a dozen packets a hit that way, and a
 * volley is twelve hits. One {@link SwordImpactPayload} carries the point, the struck surface's
 * normal, the radius, the kind and the struck block, and {@link ImpactWave} turns that into every
 * particle on the other side.
 *
 * <p>The normal handed on is the struck surface's outward one, back toward whatever struck it, so
 * callers that know a travel direction pass it here and this class turns it round.
 *
 * <p>An {@code accent} is the colour of the weapon the sword carried ({@code SwordArms.accent}),
 * 0 for plain steel: a racked fire blade rings with a few embers in the wave, and nothing else
 * about the hit changes.
 */
public final class SwordImpacts {

    /** Blocks a hit is sent out to. A wave smaller than a body is a pixel beyond this. */
    public static final double RANGE = 48.0D;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private SwordImpacts() {
    }

    /** A blade driven into a body at {@code at}, travelling along {@code travel}. */
    public static void cut(ServerLevel level, Vec3 at, Vec3 travel) {
        cut(level, at, travel, 0);
    }

    public static void cut(ServerLevel level, Vec3 at, Vec3 travel, int accent) {
        send(level, at, travel.scale(-1.0D), Kind.CUT, Kind.CUT.radius(), null, accent);
    }

    /** A blade meeting stone at {@code at}; {@code face} is the struck face's outward normal. */
    public static void clang(ServerLevel level, Vec3 at, Vec3 face, BlockState struck) {
        clang(level, at, face, struck, 0);
    }

    public static void clang(ServerLevel level, Vec3 at, Vec3 face, BlockState struck, int accent) {
        send(level, at, face, Kind.CLANG, Kind.CLANG.radius(), struck, accent);
    }

    /** A shot turned aside at {@code at}, which was travelling along {@code incoming}. */
    public static void parry(ServerLevel level, Vec3 at, Vec3 incoming) {
        parry(level, at, incoming, 0);
    }

    public static void parry(ServerLevel level, Vec3 at, Vec3 incoming, int accent) {
        send(level, at, incoming.scale(-1.0D), Kind.PARRY, Kind.PARRY.radius(), null, accent);
    }

    /** An orbit passing through a body, round its middle. The orbit is level, so the ring is too. */
    public static void shear(ServerLevel level, Vec3 at) {
        shear(level, at, 0);
    }

    public static void shear(ServerLevel level, Vec3 at, int accent) {
        send(level, at, UP, Kind.SHEAR, Kind.SHEAR.radius(), null, accent);
    }

    /** The air at a greatsword's point, driven along {@code look}. */
    public static void thrust(ServerLevel level, Vec3 at, Vec3 look) {
        thrust(level, at, look, 0);
    }

    public static void thrust(ServerLevel level, Vec3 at, Vec3 look, int accent) {
        send(level, at, look.scale(-1.0D), Kind.THRUST, Kind.THRUST.radius(), null, accent);
    }

    /** Below coming up through the ground at {@code at}, on the ring of {@code radius} that catches. */
    public static void eruption(ServerLevel level, Vec3 at, double radius) {
        eruption(level, at, radius, 0);
    }

    public static void eruption(ServerLevel level, Vec3 at, double radius, int accent) {
        send(level, at, UP, Kind.ERUPTION, radius, groundUnder(level, at), accent);
    }

    /** The ground over Below trembling on the edge of the ring of {@code radius} that will catch. */
    public static void tremor(ServerLevel level, Vec3 at, double radius) {
        send(level, at, UP, Kind.TREMOR, radius, groundUnder(level, at), 0);
    }

    /** The block a point on the floor is standing on: a quarter of a block down, so a face counts. */
    private static BlockState groundUnder(ServerLevel level, Vec3 at) {
        return level.getBlockState(BlockPos.containing(at.x, at.y - 0.25D, at.z));
    }

    private static void send(ServerLevel level, Vec3 at, Vec3 normal, Kind kind, double radius, BlockState struck,
            int accent) {
        int block = struck == null || struck.isAir() ? 0 : Block.getId(struck);
        MagicalNetwork.sendSwordImpact(level, at, RANGE, new SwordImpactPayload(at.x, at.y, at.z,
                (float) normal.x, (float) normal.y, (float) normal.z, (float) radius, kind.ordinal(), block,
                accent));
    }
}
