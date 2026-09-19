package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import java.util.Collection;
import net.minecraft.world.phys.Vec3;

/**
 * The port of {@code extra_entities}, as arithmetic: what each behaviour does to a flight vector,
 * with no entity in it, so the rules can be held by a test and the entity only has to call them in
 * a fixed order. Anything random is a {@link #roll} of the body's seed and the tick, so every
 * client and the server agree on where an Errant body went.
 */
public final class VerseBehaviours {

    /** How far toward its target a Seeker turns each tick, as a fraction of the way. */
    public static final double SEEK_TURN = 0.22D;
    public static final double SERPENTINE_AMPLITUDE = 0.35D;
    public static final double SERPENTINE_PERIOD_TICKS = 10.0D;
    public static final int ERRANT_INTERVAL = 5;
    public static final double ERRANT_TURN_DEGREES = 35.0D;
    public static final double ERRANT_CLIMB = 0.3D;
    public static final double GYRE_RADIUS = 1.6D;
    /** Blocks of orbit radius a Gyre gains per tick. */
    public static final double GYRE_CLIMB = 0.10D;
    public static final double GYRE_TURN_RADIANS = 0.35D;
    public static final double GYRE_HEIGHT = 1.0D;
    public static final double TWIN_YAW_DEGREES = 12.0D;

    private VerseBehaviours() {
    }

    /**
     * Gravity, then the steering that keeps the speed: what the flight vector becomes this tick.
     * {@code toTarget} is the vector from the body to what a Seeker is after, or null.
     */
    public static Vec3 steer(Vec3 velocity, double gravity, Vec3 toTarget, boolean seeker, boolean errant, int tick, int seed) {
        Vec3 v = velocity.add(0.0D, -gravity, 0.0D);
        double speed = v.length();
        if (speed < 1.0E-6D) {
            return v;
        }
        if (seeker && toTarget != null && toTarget.lengthSqr() > 1.0E-6D) {
            Vec3 wanted = toTarget.normalize().scale(speed);
            v = v.add(wanted.subtract(v).scale(SEEK_TURN)).normalize().scale(speed);
        }
        if (errant && tick % ERRANT_INTERVAL == 0) {
            double yaw = Math.toRadians(roll(seed, tick, 1) * ERRANT_TURN_DEGREES);
            double climb = roll(seed, tick, 2) * ERRANT_CLIMB;
            v = v.yRot((float) yaw);
            v = new Vec3(v.x, v.y + climb * speed, v.z).normalize().scale(speed);
        }
        return v;
    }

    /** -1..1, decided by the seed, the tick and a salt, so it repeats exactly and never needs a level. */
    public static double roll(int seed, int tick, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + tick * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (2.0D / (1L << 53)) - 1.0D;
    }

    /**
     * Serpentine: the change this tick of a lateral sine beside the flight line, so it is added to
     * the step rather than the velocity and the body comes back to its line every period.
     */
    public static Vec3 serpentineOffset(Vec3 velocity, int tick) {
        Vec3 side = sideOf(velocity);
        double now = Math.sin(tick * 2.0D * Math.PI / SERPENTINE_PERIOD_TICKS);
        double before = Math.sin((tick - 1) * 2.0D * Math.PI / SERPENTINE_PERIOD_TICKS);
        return side.scale((now - before) * SERPENTINE_AMPLITUDE);
    }

    /** A unit vector beside the flight; a vertical flight takes +x as its side. */
    public static Vec3 sideOf(Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0E-9D) {
            return Vec3.ZERO;
        }
        Vec3 dir = velocity.normalize();
        Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = dir.cross(up);
        return side.lengthSqr() < 1.0E-9D ? Vec3.ZERO : side.normalize();
    }

    /** Gyre: where a body orbiting its caster stands this tick, relative to the caster's feet, starting on the launch bearing. */
    public static Vec3 gyreOffset(Vec3 launch, int tick) {
        double angle = Math.atan2(launch.x, launch.z) + tick * GYRE_TURN_RADIANS;
        double radius = GYRE_RADIUS + tick * GYRE_CLIMB;
        return new Vec3(Math.sin(angle) * radius, GYRE_HEIGHT, Math.cos(angle) * radius);
    }

    /** A reflection off a face, keeping the speed. */
    public static Vec3 bounce(Vec3 velocity, Vec3 normal) {
        return velocity.subtract(normal.scale(2.0D * velocity.dot(normal)));
    }

    /**
     * A twin's heading: the body's, turned {@link #TWIN_YAW_DEGREES} either way about the vertical
     * axis, with the same convention as {@code Vec3.yRot} but in double precision: {@code yRot}
     * takes its sine and cosine from float tables, and the two twins would otherwise differ by a
     * few parts in a hundred thousand instead of mirroring each other.
     */
    public static Vec3 twin(Vec3 direction, boolean left) {
        double radians = Math.toRadians(left ? TWIN_YAW_DEGREES : -TWIN_YAW_DEGREES);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3 d = direction.normalize();
        return new Vec3(d.x * cos + d.z * sin, d.y, d.z * cos - d.x * sin);
    }

    public static int mask(Collection<Behaviour> behaviours) {
        int mask = 0;
        for (Behaviour behaviour : behaviours) {
            mask |= 1 << behaviour.ordinal();
        }
        return mask;
    }

    public static boolean has(int mask, Behaviour behaviour) {
        return (mask & (1 << behaviour.ordinal())) != 0;
    }
}
