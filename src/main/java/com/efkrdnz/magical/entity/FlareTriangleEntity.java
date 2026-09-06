package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FlareTriangleEntity extends Entity {
    private static final EntityDataAccessor<Integer> POINT_COUNT = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> P1X = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P1Y = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P1Z = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P2X = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P2Y = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P2Z = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P3X = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P3Y = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> P3Z = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> P1_ERUPTION = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> P2_ERUPTION = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> P3_ERUPTION = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WALL_THICKNESS = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BASE_Y = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FlareTriangleEntity.class, EntityDataSerializers.INT);
    private static final int PARTIAL_TIMEOUT = 150;
    private static final int ERUPTION_TICKS = 28;

    private UUID ownerUuid;
    private int idleTicks;
    private int completedTicks;

    public FlareTriangleEntity(EntityType<? extends FlareTriangleEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static FlareTriangleEntity create(ServerLevel level, ServerPlayer owner, MagicSkillResolvedStats stats, Vec3 firstPoint) {
        FlareTriangleEntity triangle = new FlareTriangleEntity(MagicalEntities.FLARE_TRIANGLE.get(), level);
        triangle.ownerUuid = owner.getUUID();
        triangle.entityData.set(DAMAGE, stats.damage());
        triangle.entityData.set(WALL_THICKNESS, 0.72F + stats.size() * 0.18F);
        triangle.entityData.set(LIFE, Math.max(120, stats.durationTicks() + 95));
        triangle.addPoint(firstPoint);
        return triangle;
    }

    public boolean addPoint(Vec3 point) {
        int count = pointCount();
        if (count >= 3) {
            return false;
        }
        setPoint(count, point);
        entityData.set(POINT_COUNT, count + 1);
        setEruption(count, ERUPTION_TICKS);
        idleTicks = 0;
        refreshCenter();
        if (!level().isClientSide()) {
            eruptAt(point, count + 1);
            if (count + 1 >= 3) {
                entityData.set(BASE_Y, lowestSurfaceYInsideTriangle());
            }
        }
        return count + 1 >= 3;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POINT_COUNT, 0);
        builder.define(P1X, 0.0F);
        builder.define(P1Y, 0.0F);
        builder.define(P1Z, 0.0F);
        builder.define(P2X, 0.0F);
        builder.define(P2Y, 0.0F);
        builder.define(P2Z, 0.0F);
        builder.define(P3X, 0.0F);
        builder.define(P3Y, 0.0F);
        builder.define(P3Z, 0.0F);
        builder.define(P1_ERUPTION, 0);
        builder.define(P2_ERUPTION, 0);
        builder.define(P3_ERUPTION, 0);
        builder.define(DAMAGE, 7.0F);
        builder.define(WALL_THICKNESS, 1.0F);
        builder.define(BASE_Y, 0.0F);
        builder.define(LIFE, 130);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        decayEruptions();
        if (level().isClientSide()) {
            return;
        }

        if (pointCount() < 3) {
            if (tickCount % 2 == 0) {
                smokePartialPoints();
            }
            idleTicks++;
            if (idleTicks > PARTIAL_TIMEOUT) {
                discard();
            }
            return;
        }

        completedTicks++;
        if (completedTicks > life()) {
            discard();
            return;
        }
        spawnFlameParticles();
        if (tickCount % 5 == 0) {
            burnWallsAndInterior();
        }
    }

    private void burnWallsAndInterior() {
        ServerPlayer owner = owner();
        AABB area = bounds().inflate(2.5D, 3.0D, 2.5D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity living && living.isAlive() && target != owner)) {
            LivingEntity living = (LivingEntity) entity;
            Vec3 pos = living.position();
            double edgeDistance = minEdgeDistance(pos);
            boolean inTriangle = insideTriangle(pos);
            boolean inWall = edgeDistance <= wallThickness();
            if (!inTriangle && !inWall) {
                continue;
            }

            float heat = inTriangle ? damage() * 0.28F : damage() * 0.42F;
            if (completedTicks > life() * 0.65F && inTriangle) {
                heat *= 1.45F;
            }
            MagicDamageService.hurt(living, damageSources().indirectMagic(this, owner == null ? this : owner), heat, MagicContent.FLARE_RING.id());
            living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), inWall ? 95 : 55));
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 28, 0, false, true), owner);
            if (inWall) {
                Vec3 push = living.position().subtract(closestPointOnEdges(pos));
                if (push.lengthSqr() < 0.01D) {
                    push = living.position().subtract(center());
                }
                if (push.lengthSqr() > 0.01D) {
                    Vec3 normal = push.normalize().scale(0.18D + wallThickness() * 0.06D);
                    living.push(normal.x, 0.08D, normal.z);
                    living.hasImpulse = true;
                }
            }
        }
    }

    private void eruptAt(Vec3 point, int pointIndex) {
        level().playSound(null, point.x, point.y, point.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.95F, 0.6F + pointIndex * 0.07F);
        level().playSound(null, point.x, point.y, point.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.72F, 0.48F + pointIndex * 0.08F);
        if (level() instanceof ServerLevel serverLevel) {
            // The old PyroclasmExplosionEntity went away with the rework; the eruption is a
            // profile-driven impact cue now, so it picks up Flare Ring's own visual identity.
            com.efkrdnz.magical.magic.visual.SpellFx.impact(serverLevel, MagicContent.FLARE_RING,
                    point.add(0.0D, 0.14D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, owner(),
                    1.7F + pointIndex * 0.18F + wallThickness() * 0.35F);
            serverLevel.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.55D, point.z, 70, 0.55D, 0.75D, 0.55D, 0.055D);
            serverLevel.sendParticles(ParticleTypes.LAVA, point.x, point.y + 0.15D, point.z, 18, 0.42D, 0.18D, 0.42D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y + 0.65D, point.z, 16, 0.52D, 0.48D, 0.52D, 0.025D);
        }
        ServerPlayer owner = owner();
        AABB area = new AABB(point, point).inflate(1.55D + wallThickness() * 0.45D, 2.3D, 1.55D + wallThickness() * 0.45D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity living && living.isAlive() && target != owner)) {
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * 0.65F, MagicContent.FLARE_RING.id());
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 70));
            entity.push(0.0D, 0.34D, 0.0D);
        }
    }

    private void smokePartialPoints() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int i = 0; i < pointCount(); i++) {
            Vec3 point = point(i);
            serverLevel.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.15D, point.z, 3, 0.22D, 0.18D, 0.22D, 0.015D);
            if (serverLevel.random.nextFloat() < 0.32F) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, point.x, point.y + 0.25D, point.z, 1, 0.18D, 0.12D, 0.18D, 0.01D);
            }
        }
    }

    private void spawnFlameParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 a = point(0);
        Vec3 b = point(1);
        Vec3 c = point(2);
        for (int edge = 0; edge < 3; edge++) {
            Vec3 start = edge == 0 ? a : edge == 1 ? b : c;
            Vec3 end = edge == 0 ? b : edge == 1 ? c : a;
            int samples = Math.max(3, Mth.ceil(start.distanceTo(end) * 0.7D));
            for (int i = 0; i < samples; i++) {
                double t = (i + serverLevel.random.nextDouble()) / samples;
                double x = Mth.lerp(t, start.x, end.x);
                double z = Mth.lerp(t, start.z, end.z);
                double y = surfaceYAt(x, z);
                serverLevel.sendParticles(ParticleTypes.FLAME, x, y + 0.18D, z, 2, 0.12D + wallThickness() * 0.05D, 0.26D, 0.12D + wallThickness() * 0.05D, 0.018D);
                if (serverLevel.random.nextFloat() < 0.16F) {
                    serverLevel.sendParticles(ParticleTypes.LAVA, x, y + 0.08D, z, 1, 0.08D, 0.04D, 0.08D, 0.04D);
                }
            }
        }

        double base = baseY();
        int interiorSamples = 5 + serverLevel.random.nextInt(5);
        for (int i = 0; i < interiorSamples; i++) {
            Vec3 sample = randomPointInsideTriangle(serverLevel);
            serverLevel.sendParticles(ParticleTypes.FLAME, sample.x, base + 0.18D, sample.z, 1 + serverLevel.random.nextInt(2), 0.18D, 0.34D, 0.18D, 0.018D);
            if (serverLevel.random.nextFloat() < 0.20F) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, sample.x, base + 0.45D, sample.z, 1, 0.2D, 0.18D, 0.2D, 0.008D);
            }
        }
    }

    private Vec3 randomPointInsideTriangle(ServerLevel level) {
        double u = level.random.nextDouble();
        double v = level.random.nextDouble();
        if (u + v > 1.0D) {
            u = 1.0D - u;
            v = 1.0D - v;
        }
        Vec3 a = point(0);
        Vec3 ab = point(1).subtract(a);
        Vec3 ac = point(2).subtract(a);
        return a.add(ab.scale(u)).add(ac.scale(v));
    }

    private float lowestSurfaceYInsideTriangle() {
        if (!(level() instanceof ServerLevel serverLevel) || pointCount() < 3) {
            return (float) Math.min(point(0).y, Math.min(point(1).y, point(2).y));
        }
        AABB bounds = bounds();
        int minX = Mth.floor(bounds.minX);
        int maxX = Mth.ceil(bounds.maxX);
        int minZ = Mth.floor(bounds.minZ);
        int maxZ = Mth.ceil(bounds.maxZ);
        double lowest = Double.MAX_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec3 sample = new Vec3(x + 0.5D, 0.0D, z + 0.5D);
                if (!insideTriangle(sample)) {
                    continue;
                }
                lowest = Math.min(lowest, surfaceYAt(x + 0.5D, z + 0.5D));
            }
        }
        if (lowest == Double.MAX_VALUE) {
            lowest = Math.min(point(0).y, Math.min(point(1).y, point(2).y));
        }
        return (float) lowest;
    }

    private double surfaceYAt(double x, double z) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return baseY();
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int topY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        BlockPos top = new BlockPos(blockX, topY, blockZ);
        if (!serverLevel.getBlockState(top).isAir()) {
            return topY + 1.0D;
        }
        return topY;
    }

    private AABB bounds() {
        Vec3 min = point(0);
        Vec3 max = point(0);
        for (int i = 1; i < pointCount(); i++) {
            Vec3 point = point(i);
            min = new Vec3(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z));
            max = new Vec3(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z));
        }
        return new AABB(min, max);
    }

    private double minEdgeDistance(Vec3 point) {
        int count = pointCount();
        if (count < 2) {
            return Double.MAX_VALUE;
        }
        double min = distanceToSegment2d(point, point(0), point(1));
        if (count >= 3) {
            min = Math.min(min, distanceToSegment2d(point, point(1), point(2)));
            min = Math.min(min, distanceToSegment2d(point, point(2), point(0)));
        }
        return min;
    }

    private Vec3 closestPointOnEdges(Vec3 point) {
        Vec3 closest = closestPointOnSegment2d(point, point(0), point(1));
        double best = point.distanceToSqr(closest);
        if (pointCount() >= 3) {
            Vec3 candidate = closestPointOnSegment2d(point, point(1), point(2));
            double distance = point.distanceToSqr(candidate);
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
            candidate = closestPointOnSegment2d(point, point(2), point(0));
            if (point.distanceToSqr(candidate) < best) {
                closest = candidate;
            }
        }
        return closest;
    }

    private static double distanceToSegment2d(Vec3 point, Vec3 a, Vec3 b) {
        return point.distanceTo(closestPointOnSegment2d(point, a, b));
    }

    private static Vec3 closestPointOnSegment2d(Vec3 point, Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < 1.0E-6D) {
            return new Vec3(a.x, point.y, a.z);
        }
        double t = Mth.clamp(((point.x - a.x) * dx + (point.z - a.z) * dz) / lengthSqr, 0.0D, 1.0D);
        return new Vec3(a.x + dx * t, point.y, a.z + dz * t);
    }

    private boolean insideTriangle(Vec3 point) {
        if (pointCount() < 3) {
            return false;
        }
        Vec3 a = point(0);
        Vec3 b = point(1);
        Vec3 c = point(2);
        double d1 = sign(point, a, b);
        double d2 = sign(point, b, c);
        double d3 = sign(point, c, a);
        boolean hasNegative = d1 < 0.0D || d2 < 0.0D || d3 < 0.0D;
        boolean hasPositive = d1 > 0.0D || d2 > 0.0D || d3 > 0.0D;
        return !(hasNegative && hasPositive);
    }

    private static double sign(Vec3 point, Vec3 a, Vec3 b) {
        return (point.x - b.x) * (a.z - b.z) - (a.x - b.x) * (point.z - b.z);
    }

    private void decayEruptions() {
        entityData.set(P1_ERUPTION, Math.max(0, entityData.get(P1_ERUPTION) - 1));
        entityData.set(P2_ERUPTION, Math.max(0, entityData.get(P2_ERUPTION) - 1));
        entityData.set(P3_ERUPTION, Math.max(0, entityData.get(P3_ERUPTION) - 1));
    }

    private void setEruption(int index, int ticks) {
        switch (index) {
            case 0 -> entityData.set(P1_ERUPTION, ticks);
            case 1 -> entityData.set(P2_ERUPTION, ticks);
            case 2 -> entityData.set(P3_ERUPTION, ticks);
            default -> {
            }
        }
    }

    private void setPoint(int index, Vec3 point) {
        switch (index) {
            case 0 -> {
                entityData.set(P1X, (float) point.x);
                entityData.set(P1Y, (float) point.y);
                entityData.set(P1Z, (float) point.z);
            }
            case 1 -> {
                entityData.set(P2X, (float) point.x);
                entityData.set(P2Y, (float) point.y);
                entityData.set(P2Z, (float) point.z);
            }
            case 2 -> {
                entityData.set(P3X, (float) point.x);
                entityData.set(P3Y, (float) point.y);
                entityData.set(P3Z, (float) point.z);
            }
            default -> {
            }
        }
    }

    private void refreshCenter() {
        Vec3 center = center();
        setPos(center.x, center.y, center.z);
    }

    public Vec3 center() {
        int count = Math.max(1, pointCount());
        Vec3 sum = Vec3.ZERO;
        for (int i = 0; i < count; i++) {
            sum = sum.add(point(i));
        }
        return sum.scale(1.0D / count);
    }

    public Vec3 point(int index) {
        return switch (index) {
            case 0 -> new Vec3(entityData.get(P1X), entityData.get(P1Y), entityData.get(P1Z));
            case 1 -> new Vec3(entityData.get(P2X), entityData.get(P2Y), entityData.get(P2Z));
            case 2 -> new Vec3(entityData.get(P3X), entityData.get(P3Y), entityData.get(P3Z));
            default -> Vec3.ZERO;
        };
    }

    public int eruptionTicks(int index) {
        return switch (index) {
            case 0 -> entityData.get(P1_ERUPTION);
            case 1 -> entityData.get(P2_ERUPTION);
            case 2 -> entityData.get(P3_ERUPTION);
            default -> 0;
        };
    }

    public int pointCount() {
        return entityData.get(POINT_COUNT);
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float wallThickness() {
        return entityData.get(WALL_THICKNESS);
    }

    public float baseY() {
        float base = entityData.get(BASE_Y);
        if (base == 0.0F && pointCount() > 0) {
            return (float) point(0).y;
        }
        return base;
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float completedProgress(float partialTick) {
        return pointCount() >= 3 ? Mth.clamp((completedTicks + partialTick) / Math.max(1.0F, life()), 0.0F, 1.0F) : 0.0F;
    }

    private ServerPlayer owner() {
        if (!(level() instanceof ServerLevel serverLevel) || ownerUuid == null) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(POINT_COUNT, tag.getInt("PointCount"));
        for (int i = 0; i < 3; i++) {
            setPoint(i, new Vec3(tag.getDouble("P" + i + "X"), tag.getDouble("P" + i + "Y"), tag.getDouble("P" + i + "Z")));
            setEruption(i, tag.getInt("P" + i + "Eruption"));
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(WALL_THICKNESS, tag.getFloat("WallThickness"));
        entityData.set(BASE_Y, tag.contains("BaseY") ? tag.getFloat("BaseY") : (float) Math.min(point(0).y, Math.min(point(1).y, point(2).y)));
        entityData.set(LIFE, tag.getInt("Life"));
        idleTicks = tag.getInt("IdleTicks");
        completedTicks = tag.getInt("CompletedTicks");
        refreshCenter();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("PointCount", pointCount());
        for (int i = 0; i < 3; i++) {
            Vec3 point = point(i);
            tag.putDouble("P" + i + "X", point.x);
            tag.putDouble("P" + i + "Y", point.y);
            tag.putDouble("P" + i + "Z", point.z);
            tag.putInt("P" + i + "Eruption", eruptionTicks(i));
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("WallThickness", wallThickness());
        tag.putFloat("BaseY", baseY());
        tag.putInt("Life", life());
        tag.putInt("IdleTicks", idleTicks);
        tag.putInt("CompletedTicks", completedTicks);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
        return false;
    }
}
