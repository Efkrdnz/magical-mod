package com.efkrdnz.magical.magic.cast;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The one aim idiom: a living entity near the look ray (within tolerance, nearest to the caster)
 * beats the struck block face, which beats the ray end (optionally dropped to the ground). The
 * result always has a point and a normal, so no skill ever fails to cast.
 */
public final class AimResolver {
    private AimResolver() {}

    public record Result(Vec3 point, Vec3 normal, Entity entity, BlockHitResult block, Vec3 origin, Vec3 look, double distance) {
        public boolean hitEntity() {
            return entity != null;
        }

        public boolean hitBlock() {
            return block != null && block.getType() == HitResult.Type.BLOCK;
        }

        public LivingEntity living() {
            return entity instanceof LivingEntity living ? living : null;
        }

        public BlockPos blockPos() {
            return block != null ? block.getBlockPos() : BlockPos.containing(point);
        }

        /** The block face direction (UP when nothing was struck). */
        public Direction face() {
            return block != null ? block.getDirection() : Direction.UP;
        }
    }

    public static Result resolve(ServerLevel level, LivingEntity caster, Vec3 look, double range, double tolerance, boolean dropToGround, int maxDrop, Predicate<Entity> filter) {
        Vec3 from = caster.getEyePosition();
        Vec3 dir = look.normalize();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation().distanceTo(from) : range;

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        if (tolerance > 0.0D) {
            AABB sweep = new AABB(from, from.add(dir.scale(blockDist))).inflate(tolerance);
            for (Entity candidate : level.getEntities(caster, sweep, e -> e instanceof LivingEntity && e.isAlive() && e != caster && (filter == null || filter.test(e)))) {
                Vec3 centre = candidate.getBoundingBox().getCenter();
                Vec3 rel = centre.subtract(from);
                double along = rel.dot(dir);
                if (along < 0.0D || along > blockDist + 0.5D) {
                    continue;
                }
                double off = rel.subtract(dir.scale(along)).length();
                double allowed = tolerance + candidate.getBbWidth() * 0.5D;
                if (off <= allowed && along < bestDist) {
                    bestDist = along;
                    best = candidate;
                }
            }
        }
        if (best != null) {
            Vec3 point = best.getBoundingBox().getCenter();
            Vec3 normal = from.subtract(point);
            normal = normal.lengthSqr() > 1.0E-6D ? normal.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
            return new Result(point, normal, best, blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null, from, dir, bestDist);
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Direction d = blockHit.getDirection();
            Vec3 n = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
            Vec3 point = blockHit.getLocation().add(n.scale(0.02D));
            if (dropToGround && blockHit.getDirection() != Direction.UP) {
                Vec3 dropped = groundBelow(level, point, maxDrop);
                if (dropped != null) {
                    return new Result(dropped, new Vec3(0.0D, 1.0D, 0.0D), null, blockHit, from, dir, dropped.distanceTo(from));
                }
            }
            return new Result(point, n, null, blockHit, from, dir, blockDist);
        }
        Vec3 end = to;
        if (dropToGround) {
            Vec3 dropped = groundBelow(level, end, maxDrop);
            if (dropped != null) {
                return new Result(dropped, new Vec3(0.0D, 1.0D, 0.0D), null, null, from, dir, dropped.distanceTo(from));
            }
        }
        return new Result(end, dir.scale(-1.0D), null, null, from, dir, range);
    }

    public static Result resolve(ServerLevel level, LivingEntity caster, double range, double tolerance, boolean dropToGround) {
        return resolve(level, caster, caster.getLookAngle(), range, tolerance, dropToGround, 8, null);
    }

    /** First solid top face at or below the point (null if none within maxDrop). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 point, int maxDrop) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(point).mutable();
        for (int i = 0; i <= maxDrop; i++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return new Vec3(point.x, cursor.getY() + 1.0D, point.z);
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
