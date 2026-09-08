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

public final class SkillClashEffectEntity extends Entity {
    private static final EntityDataAccessor<Integer> INCOMING_COLOR = SynchedEntityData.defineId(SkillClashEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNTER_COLOR = SynchedEntityData.defineId(SkillClashEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SkillClashEffectEntity.class, EntityDataSerializers.INT);

    /**
     * Whether the client has already thrown this clash's particles.
     *
     * <p>Not synced and not saved: the renderer fires the burst on the first frame it draws, and
     * the burst belongs to whoever is watching rather than to the entity.
     */
    private boolean burstSpawned;

    public SkillClashEffectEntity(EntityType<? extends SkillClashEffectEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SkillClashEffectEntity create(Level level, Vec3 position, int incomingColor, int counterColor, int life) {
        SkillClashEffectEntity effect = new SkillClashEffectEntity(MagicalEntities.SKILL_CLASH_EFFECT.get(), level);
        effect.setPos(position.x, position.y, position.z);
        effect.entityData.set(INCOMING_COLOR, incomingColor);
        effect.entityData.set(COUNTER_COLOR, counterColor);
        effect.entityData.set(LIFE, Math.max(6, life));
        return effect;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(INCOMING_COLOR, 0xF8FCFF);
        builder.define(COUNTER_COLOR, 0xA57DFF);
        builder.define(LIFE, 24);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide() && tickCount > life()) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(INCOMING_COLOR, tag.getInt("IncomingColor"));
        entityData.set(COUNTER_COLOR, tag.getInt("CounterColor"));
        entityData.set(LIFE, tag.getInt("Life"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("IncomingColor", incomingColor());
        tag.putInt("CounterColor", counterColor());
        tag.putInt("Life", life());
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

    /** True exactly once per clash, for the client-side particle burst. */
    public boolean takeClientBurst() {
        if (burstSpawned) {
            return false;
        }
        burstSpawned = true;
        return true;
    }

    public int incomingColor() {
        return entityData.get(INCOMING_COLOR);
    }

    public int counterColor() {
        return entityData.get(COUNTER_COLOR);
    }

    public int life() {
        return entityData.get(LIFE);
    }
}
