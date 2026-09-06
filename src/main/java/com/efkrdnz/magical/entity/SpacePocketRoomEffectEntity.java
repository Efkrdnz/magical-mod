package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpacePocketRoomEffectEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpacePocketRoomEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(SpacePocketRoomEffectEntity.class, EntityDataSerializers.FLOAT);

    public SpacePocketRoomEffectEntity(EntityType<? extends SpacePocketRoomEffectEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SpacePocketRoomEffectEntity create(Level level, Vec3 position, float radius, float height) {
        SpacePocketRoomEffectEntity effect = new SpacePocketRoomEffectEntity(MagicalEntities.SPACE_POCKET_ROOM_EFFECT.get(), level);
        effect.setPos(position.x, position.y, position.z);
        effect.entityData.set(RADIUS, radius);
        effect.entityData.set(HEIGHT, height);
        return effect;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 18.0F);
        builder.define(HEIGHT, 13.0F);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(HEIGHT, tag.getFloat("Height"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", radius());
        tag.putFloat("Height", height());
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
