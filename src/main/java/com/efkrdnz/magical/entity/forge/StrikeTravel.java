package com.efkrdnz.magical.entity.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How a strike that does not stay on its wielder moves: one clipped step for a travelling wave, the
 * gentle steer SEEKING adds to it, and the ground search a slam drops onto.
 */
public final class StrikeTravel {

    private static final double SEEKING_TURN_RADIANS = 0.12;
    private static final double SEEKING_RANGE = 8.0;
    private static final double SEEKING_CONE_DOT = 0.5; // a 60-degree half-cone
    private static final double EPSILON = 1.0E-6;

    private StrikeTravel() {}

    /** Where the strike ends up this tick, and whether a block is what stopped it there. */
    public record Step(Vec3 to, boolean blocked) {}

    public static Step advance(ServerLevel level, Entity self, Vec3 from, Vec3 direction, double speed) {
        Vec3 to = from.add(direction.scale(speed));
        BlockHitResult hit = level.clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS ? new Step(to, false) : new Step(hit.getLocation(), true);
    }

    /** The solid surface within {@code search} blocks below {@code from}, or null if there is none. */
    public static Vec3 groundUnder(ServerLevel level, Entity self, Vec3 from, double search) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.subtract(0.0, search, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /** Bends {@code direction} at most {@link #SEEKING_TURN_RADIANS} toward the nearest body ahead. */
    public static Vec3 steerToward(ServerLevel level, Entity self, Entity owner, Vec3 position, Vec3 direction) {
        LivingEntity target = nearestAhead(level, self, owner, position, direction);
        if (target == null) {
            return direction;
        }
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(position);
        if (toTarget.lengthSqr() < EPSILON) {
            return direction;
        }
        toTarget = toTarget.normalize();
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, direction.dot(toTarget))));
        if (angle < 1.0E-4) {
            return direction;
        }
        double step = Math.min(1.0, SEEKING_TURN_RADIANS / angle);
        Vec3 steered = direction.scale(1.0 - step).add(toTarget.scale(step));
        return steered.lengthSqr() < EPSILON ? direction : steered.normalize();
    }

    private static LivingEntity nearestAhead(ServerLevel level, Entity self, Entity owner, Vec3 position,
            Vec3 direction) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(self, self.getBoundingBox().inflate(SEEKING_RANGE),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(position);
            double distance = to.length();
            if (distance > SEEKING_RANGE || distance < EPSILON) {
                continue;
            }
            if (to.scale(1.0 / distance).dot(direction) < SEEKING_CONE_DOT || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = (LivingEntity) entity;
        }
        return best;
    }
}
