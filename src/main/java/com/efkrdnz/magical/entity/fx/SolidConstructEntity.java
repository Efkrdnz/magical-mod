package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A conjured SOLID: collidable (the boat / shulker blocking idiom), pickable so melee, arrows and
 * spells can chip it, with an integrity pool. Size is set per cast; it behaves like a temporary
 * block of terrain the roster can raise, wall with or stand on.
 */
public class SolidConstructEntity extends SpellEffectEntity {
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INTEGRITY = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_INTEGRITY = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEGMENT = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SOLID = SynchedEntityData.defineId(SolidConstructEntity.class, EntityDataSerializers.BOOLEAN);

    public SolidConstructEntity(EntityType<? extends SolidConstructEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SolidConstructEntity create(ServerLevel level, SpellEffectEntity template, Vec3 pos, float width, float height, float integrity, int segment) {
        SolidConstructEntity entity = new SolidConstructEntity(MagicalEntities.SOLID_CONSTRUCT.get(), level);
        entity.copyFrom(template);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.entityData.set(WIDTH, width);
        entity.entityData.set(HEIGHT, height);
        entity.entityData.set(INTEGRITY, integrity);
        entity.entityData.set(MAX_INTEGRITY, integrity);
        entity.entityData.set(SEGMENT, segment);
        entity.refreshDimensions();
        return entity;
    }

    /** Copy the skill, owner, stats, seed and direction of another effect (children of a controller). */
    public void copyFrom(SpellEffectEntity template) {
        CompoundTag tag = new CompoundTag();
        template.addAdditionalSaveData(tag);
        tag.remove("Synced");
        tag.remove("Data");
        readAdditionalSaveData(tag);
        setLife(template.life());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WIDTH, 1.0F);
        builder.define(HEIGHT, 1.0F);
        builder.define(INTEGRITY, 20.0F);
        builder.define(MAX_INTEGRITY, 20.0F);
        builder.define(SEGMENT, 0);
        builder.define(SOLID, true);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WIDTH.equals(key) || HEIGHT.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(Math.max(0.1F, entityData.get(WIDTH)), Math.max(0.1F, entityData.get(HEIGHT)));
    }

    @Override
    public boolean canBeCollidedWith() {
        return entityData.get(SOLID);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return canBeCollidedWith() && entity != owner();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float remaining = integrity() - amount;
        entityData.set(INTEGRITY, remaining);
        behavior().tick(this);
        if (remaining <= 0.0F) {
            finish();
        }
        return true;
    }

    /** Integrity as a 0..1 factor; the painter's phase (shard_body INTEGRITY channel) reads it. */
    @Override
    public float effectPhase() {
        float max = Math.max(1.0F, entityData.get(MAX_INTEGRITY));
        float integrityFrac = Math.max(0.0F, integrity()) / max;
        float spawn = Math.min(1.0F, tickCount / 6.0F);
        // reversed integrity while crystallizing in, then cracks as integrity drops
        return spawn < 1.0F ? 1.0F - spawn : 0.7F * (1.0F - integrityFrac) + 0.0F;
    }

    public float integrity() {
        return entityData.get(INTEGRITY);
    }

    public int segment() {
        return entityData.get(SEGMENT);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public void setSolid(boolean solid) {
        entityData.set(SOLID, solid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Width")) {
            entityData.set(WIDTH, tag.getFloat("Width"));
            entityData.set(HEIGHT, tag.getFloat("Height"));
            entityData.set(INTEGRITY, tag.getFloat("Integrity"));
            entityData.set(MAX_INTEGRITY, tag.getFloat("MaxIntegrity"));
            entityData.set(SEGMENT, tag.getInt("Segment"));
            entityData.set(SOLID, tag.getBoolean("Solid"));
            refreshDimensions();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Width", width());
        tag.putFloat("Height", height());
        tag.putFloat("Integrity", integrity());
        tag.putFloat("MaxIntegrity", entityData.get(MAX_INTEGRITY));
        tag.putInt("Segment", segment());
        tag.putBoolean("Solid", entityData.get(SOLID));
    }
}
