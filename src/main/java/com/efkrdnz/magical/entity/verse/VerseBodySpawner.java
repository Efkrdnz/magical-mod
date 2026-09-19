package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A shot plan into the world: the fan from the group's pattern, a deviation within the group's
 * spread per body, speed from the prototype times the stamped multiplier, statics a block ahead of
 * the hand (or at the release point) and dropped to the floor, Near Word bodies on the caster,
 * Sightline bodies at the crosshair, twins beside each other. {@link #release} is the same call from
 * a body's Latch, Fuse or Epitaph, and {@link #relay} the same from an impact. Never more than
 * {@link ReciteCaps#MAX_BODIES} per call, twins included.
 */
public final class VerseBodySpawner {

    public static final double STANDING_AHEAD = 1.0D;
    public static final int STANDING_DROP = 6;
    public static final double SIGHTLINE_RANGE = 64.0D;
    public static final double RELAY_STEP_BACK = 0.3D;

    private VerseBodySpawner() {
    }

    public static List<VerseBodyEntity> spawn(ServerLevel level, LivingEntity caster, ShotPlan shot, Vec3 origin, Vec3 aim, ResourceLocation skillId) {
        return spawnAll(level, caster, shot, origin, aim, skillId, true);
    }

    public static List<VerseBodyEntity> release(ServerLevel level, LivingEntity caster, ShotPlan payload, Vec3 at, Vec3 travel, ResourceLocation skillId) {
        return spawnAll(level, caster, payload, at, travel, skillId, false);
    }

    /** Relay: the same body again from the impact point, on a seeded heading, flattened so a relay off a wall runs along the ground; it will not relay again. */
    public static List<VerseBodyEntity> relay(ServerLevel level, VerseBodyEntity body, Vec3 at) {
        Vec3 heading = body.direction().yRot((float) (VerseBehaviours.roll(body.seed(), body.tickCount, 3) * Math.PI));
        heading = new Vec3(heading.x, Math.abs(heading.y) * 0.25D, heading.z);
        VerseBodyEntity next = VerseBodyEntity.spawn(level, body.livingOwner(), body.plan(),
                at.subtract(body.direction().scale(RELAY_STEP_BACK)), heading, body.skillId());
        next.markRelayed();
        return List.of(next);
    }

    private static List<VerseBodyEntity> spawnAll(ServerLevel level, LivingEntity caster, ShotPlan shot, Vec3 origin, Vec3 aim, ResourceLocation skillId, boolean fromHand) {
        List<VerseBodyEntity> spawned = new ArrayList<>();
        List<ProjectilePlan> bodies = shot.bodies();
        int count = Math.min(bodies.size(), ReciteCaps.MAX_BODIES);
        ShotState group = shot.state();
        Vec3 heading = aim.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
        double[] yaws = VerseFan.yaws(count, group.patternDegrees());
        for (int i = 0; i < count && spawned.size() < ReciteCaps.MAX_BODIES; i++) {
            ProjectilePlan body = bodies.get(i);
            Vec3 direction = headingOf(level, caster, body, heading, yaws[i], group.spreadDegrees());
            Vec3 at = placeOf(level, caster, body, origin, heading, fromHand);
            if (body.stamped().has(Behaviour.TWIN_PATH)) {
                spawned.add(VerseBodyEntity.spawn(level, caster, body, at, VerseBehaviours.twin(direction, false), skillId));
                if (spawned.size() < ReciteCaps.MAX_BODIES) {
                    spawned.add(VerseBodyEntity.spawn(level, caster, body, at, VerseBehaviours.twin(direction, true), skillId));
                }
            } else {
                spawned.add(VerseBodyEntity.spawn(level, caster, body, at, direction, skillId));
            }
        }
        return spawned;
    }

    /** Sightline aims once, at the crosshair, and ignores the fan; everything else takes its fan yaw and a deviation within the spread. */
    static Vec3 headingOf(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 heading, double fanYaw, double spread) {
        if (body.stamped().has(Behaviour.SIGHTLINE) && caster != null) {
            Vec3 point = AimResolver.resolve(level, caster, heading, SIGHTLINE_RANGE, 0.0D, false, 0, null).point();
            Vec3 line = point.subtract(caster.getEyePosition());
            return line.lengthSqr() < 1.0E-6D ? heading : line.normalize();
        }
        double yawDeviation = VerseFan.deviation(spread, level.random.nextDouble());
        double pitchDeviation = VerseFan.deviation(spread, level.random.nextDouble());
        return deviate(heading.yRot((float) Math.toRadians(fanYaw)), yawDeviation, pitchDeviation);
    }

    /** A yaw about the vertical axis, then a pitch about the side axis, both in degrees. */
    static Vec3 deviate(Vec3 direction, double yawDegrees, double pitchDegrees) {
        Vec3 d = direction.yRot((float) Math.toRadians(yawDegrees));
        Vec3 side = VerseBehaviours.sideOf(d);
        if (side.lengthSqr() < 1.0E-9D) {
            return d;
        }
        Vec3 up = side.cross(d).normalize();
        double pitch = Math.toRadians(pitchDegrees);
        return d.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch))).normalize();
    }

    /** Near Word on the caster; a flying body at the origin; a static a block ahead (or at the release point), dropped to the floor unless it is a held word. */
    static Vec3 placeOf(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 origin, Vec3 heading, boolean fromHand) {
        if (body.stamped().has(Behaviour.NEAR_WORD) && caster != null) {
            return caster.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
        }
        VersePrototype prototype = body.prototype();
        if (!prototype.isStatic()) {
            return origin;
        }
        Vec3 wanted = fromHand ? origin.add(heading.scale(STANDING_AHEAD)) : origin;
        if (prototype.look() == VersePrototype.Look.WORD) {
            return wanted;
        }
        Vec3 floor = AimResolver.groundBelow(level, wanted, STANDING_DROP);
        return floor != null ? floor.add(0.0D, 0.05D, 0.0D) : wanted;
    }
}
