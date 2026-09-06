package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.SphericalBlockRemover;
import com.efkrdnz.magical.registry.MagicalEntities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpacePortalEntity extends Entity {
    private static final int LIFE_TICKS = 20 * 30;
    private static final EntityDataAccessor<Float> PORTAL_YAW = SynchedEntityData.defineId(SpacePortalEntity.class, EntityDataSerializers.FLOAT);
    private final Set<UUID> teleported = new HashSet<>();
    private double destinationX;
    private double destinationY;
    private double destinationZ;

    public SpacePortalEntity(EntityType<? extends SpacePortalEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SpacePortalEntity create(ServerLevel level, LivingEntity owner, Vec3 destination) {
        SpacePortalEntity entity = new SpacePortalEntity(MagicalEntities.SPACE_PORTAL.get(), level);
        Vec3 forward = owner.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-5D) {
            forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        }
        forward = forward.normalize();
        entity.setPos(owner.getX() + forward.x * 2.4D, owner.getY() + 1.2D, owner.getZ() + forward.z * 2.4D);
        entity.setYRot(owner.getYRot());
        entity.entityData.set(PORTAL_YAW, owner.getYRot());
        entity.destinationX = destination.x;
        entity.destinationY = destination.y;
        entity.destinationZ = destination.z;
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PORTAL_YAW, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (tickCount > LIFE_TICKS) {
            discard();
            return;
        }
        if (level().isClientSide()) {
            return;
        }
        AABB touchBox = getBoundingBox().inflate(0.55D, 0.25D, 0.55D);
        for (Entity entity : level().getEntities(this, touchBox, target -> target.isAlive() && target != this && !(target instanceof SpacePortalEntity))) {
            if (!teleported.add(entity.getUUID())) {
                continue;
            }
            entity.teleportTo(destinationX, destinationY, destinationZ);
            entity.fallDistance = 0.0F;
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        destinationX = tag.getDouble("DestinationX");
        destinationY = tag.getDouble("DestinationY");
        destinationZ = tag.getDouble("DestinationZ");
        entityData.set(PORTAL_YAW, tag.getFloat("PortalYaw"));
        setYRot(entityData.get(PORTAL_YAW));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("DestinationX", destinationX);
        tag.putDouble("DestinationY", destinationY);
        tag.putDouble("DestinationZ", destinationZ);
        tag.putFloat("PortalYaw", entityData.get(PORTAL_YAW));
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    public float portalYaw() {
        return entityData.get(PORTAL_YAW);
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
        return false;
    }
}
