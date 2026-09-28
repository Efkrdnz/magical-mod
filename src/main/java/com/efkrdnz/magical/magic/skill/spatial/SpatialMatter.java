package com.efkrdnz.magical.magic.skill.spatial;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Spatial school's matter, thrown as real particles. A fold is light - the rift, the frame, the
 * stitch stay shader - but what a fold does to the world is stuff: the floor it creases is kicked
 * up as the floor's own dust, and the space a body is pulled out of breaks where it stood, a few
 * pale motes hanging in its outline and a little glass falling out of it. Nothing here touches a
 * body, a block or a number; every call is a packet of particles (or a sound) and nothing else.
 *
 * <p>Most of what is here is <em>directed</em>: a mote handed a velocity (a count-0 send) rather than
 * a scatter, because space being pulled in, pushed out or run along a line is a direction, and a
 * gaussian puff of the same motes reads as nothing but sparkle.
 */
final class SpatialMatter {
    /** Motes hanging in the outline a displaced body leaves behind. */
    private static final int VACATED_MOTES = 7;

    /** Shards of the vacated space, falling out of it. */
    private static final int VACATED_SHARDS = 3;

    /** How far below a point the floor is looked for before the point is skipped. */
    private static final int FLOOR_SEARCH = 4;

    /**
     * A mote keeps 0.88 of its speed a tick (TintedSpriteParticle.Motion.MOTE) and lives 12 to 20
     * ticks, so over an average life it travels about 0.87 of speed / 0.12: this is the starting
     * speed, per block it is meant to cover, that lands it where it was aimed.
     */
    private static final double MOTE_SPEED_PER_BLOCK = 0.14D;

    /** Size of the directed motes: a touch over the matter layer's own, so a line of them reads at range. */
    private static final float LINE_MOTE_SCALE = 1.4F;

    /** How near a thread's mote may come to the caster's own eye, in blocks. */
    private static final double EYE_CLEARANCE = 1.6D;

    /** The golden angle, for spreading a handful of points evenly over a sphere. */
    private static final double GOLDEN_ANGLE = 2.399963229728653D;

    private SpatialMatter() {
    }

    /** Where a body was taken from: its outline in pale motes, and a few shards of it falling. */
    static void vacated(ServerLevel level, MagicSkillDefinition definition, Vec3 feet, float width, float height) {
        VisualProfile profile = VisualProfiles.of(definition);
        double y = feet.y + height * 0.5D;
        level.sendParticles(new TintedParticleOptions(MagicalParticles.MOTE.get(), profile.color(ColorRole.BRIGHT), 1.4F),
                feet.x, y, feet.z, VACATED_MOTES, width * 0.3D, height * 0.28D, width * 0.3D, 0.01D);
        level.sendParticles(new TintedParticleOptions(MagicalParticles.SHARD.get(), profile.color(ColorRole.BASE), 1.1F),
                feet.x, y, feet.z, VACATED_SHARDS, width * 0.25D, height * 0.2D, width * 0.25D, 0.04D);
    }

    /**
     * The floor along a line kicked up as its own dust, one pillar every {@code points} along it:
     * a crease shutting, an arm of a star tearing open. A point over no floor throws nothing.
     */
    static void groundLine(ServerLevel level, Vec3 from, Vec3 to, int points, int perPoint) {
        for (int i = 0; i < points; i++) {
            Vec3 at = points == 1 ? from : from.lerp(to, i / (double) (points - 1));
            BlockPos floor = floorUnder(level, at);
            if (floor == null) {
                continue;
            }
            BlockState state = level.getBlockState(floor);
            level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state),
                    at.x, floor.getY() + 1.02D, at.z, perPoint, 0.25D, 0.0D, 0.25D, 0.0D);
        }
    }

    /**
     * Space round a point pulled in (or, {@code to > from}, pushed out): {@code count} motes on a
     * shell of radius {@code from}, each sent straight at radius {@code to}. {@code flat} lays the
     * shell as a level ring, which is how a reach reads from inside it; otherwise the points are
     * spread over a sphere, so the shell closes the same from wherever it is watched.
     */
    static void converge(ServerLevel level, MagicSkillDefinition definition, Vec3 centre, double from, double to, int count, boolean flat) {
        ParticleOptions mote = mote(definition, ColorRole.BRIGHT, LINE_MOTE_SCALE);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        double speed = (to - from) * MOTE_SPEED_PER_BLOCK;
        for (int i = 0; i < count; i++) {
            Vec3 dir;
            if (flat) {
                double a = offset + i * Math.PI * 2.0D / count;
                dir = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            } else {
                double y = 1.0D - 2.0D * (i + 0.5D) / count;
                double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
                double a = offset + i * GOLDEN_ANGLE;
                dir = new Vec3(r * Math.cos(a), y, r * Math.sin(a));
            }
            Vec3 at = centre.add(dir.scale(from));
            level.sendParticles(mote, at.x, at.y, at.z, 0, dir.x * speed, dir.y * speed, dir.z * speed, 1.0D);
        }
    }

    /**
     * A dotted thread of motes from one point to another - the path a body was carried along - each
     * drifting {@code drift} blocks a tick toward {@code to}, so the thread reads as a direction.
     * One mote every {@code spacing} blocks, never fewer than two nor more than {@code max}. A mote
     * that would hang within {@link #EYE_CLEARANCE} of {@code eye} (the caster's own camera, or
     * null) is left out: a path that runs past the caster is still a path without a blot on the lens.
     */
    static void thread(ServerLevel level, MagicSkillDefinition definition, Vec3 from, Vec3 to, double spacing, int max, double drift, Vec3 eye) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 1.0E-3D) {
            return;
        }
        Vec3 unit = span.scale(1.0D / length);
        int n = Math.max(2, Math.min(max, (int) Math.round(length / spacing)));
        ParticleOptions mote = mote(definition, ColorRole.BRIGHT, LINE_MOTE_SCALE);
        for (int i = 0; i < n; i++) {
            Vec3 at = from.lerp(to, (i + 0.5D) / n);
            if (eye != null && at.distanceToSqr(eye) < EYE_CLEARANCE * EYE_CLEARANCE) {
                continue;
            }
            // a little faster toward the far end, so the thread stretches the way the body went
            double s = drift * (0.6D + 0.8D * i / Math.max(1, n - 1));
            level.sendParticles(mote, at.x, at.y, at.z, 0, unit.x * s, unit.y * s, unit.z * s, 1.0D);
        }
    }

    /** One mote sent from a point along a unit direction, aimed to come to rest about {@code distance} blocks on. */
    static void launch(ServerLevel level, MagicSkillDefinition definition, Vec3 from, Vec3 unit, double distance) {
        double s = distance * MOTE_SPEED_PER_BLOCK;
        level.sendParticles(mote(definition, ColorRole.BRIGHT, LINE_MOTE_SCALE), from.x, from.y, from.z, 0, unit.x * s, unit.y * s, unit.z * s, 1.0D);
    }

    /**
     * Motes lifting off the floor at {@code count} points along a line - a seam, an arm of a star -
     * each rising {@code lift} blocks a tick. {@code random} picks the points at random along the
     * line (a telegraph that glitters); otherwise they are evenly spaced (a line snapping at once).
     */
    static void seam(ServerLevel level, MagicSkillDefinition definition, Vec3 from, Vec3 to, int count, double lift, boolean random) {
        ParticleOptions mote = mote(definition, ColorRole.BRIGHT, LINE_MOTE_SCALE);
        for (int i = 0; i < count; i++) {
            double f = random ? level.random.nextDouble() : count == 1 ? 0.5D : i / (double) (count - 1);
            Vec3 at = from.lerp(to, f);
            BlockPos floor = floorUnder(level, at);
            double y = floor != null ? floor.getY() + 1.12D : at.y + 0.12D;
            double rise = lift * (0.75D + 0.5D * level.random.nextDouble());
            level.sendParticles(mote, at.x, y, at.z, 0, 0.0D, rise, 0.0D, 1.0D);
        }
    }

    /** A crackle of the fold's electric sparks at a point: a flicker, two or three ticks of life. */
    static void crackle(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, spread, spread * 0.5D, spread, 0.06D);
    }

    /** The skill's own impact sound at a point, quieter: a beat that is heard but lands on nobody. */
    static void sound(ServerLevel level, MagicSkillDefinition definition, Vec3 at, float volumeScale) {
        ProfileCues.SoundCue cue = VisualProfiles.of(definition).sounds().impact();
        if (cue == null) {
            return;
        }
        level.playSound(null, at.x, at.y, at.z, cue.sound(), SoundSource.PLAYERS, cue.volume() * volumeScale, Mth.clamp(cue.pitch(), 0.5F, 2.0F));
    }

    private static ParticleOptions mote(MagicSkillDefinition definition, ColorRole role, float scale) {
        return new TintedParticleOptions(MagicalParticles.MOTE.get(), VisualProfiles.of(definition).color(role), scale);
    }

    /** The first block under a point that something could stand on, or null within the search. */
    private static BlockPos floorUnder(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(at.x, at.y + 1.0D, at.z).mutable();
        for (int i = 0; i < FLOOR_SEARCH; i++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
