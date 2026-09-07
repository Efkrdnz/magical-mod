package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.registry.MagicalEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The only visual carrier forged combat has. Impacts, element riders, the charge telegraph and the
 * storm chain all spawn one of these instead of a vanilla particle, so every forge visual is a real
 * synced entity the renderer owns end to end.
 */
public final class ForgeEffectEntity extends Entity {

    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR2 = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HEAVY = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(ForgeEffectEntity.class, EntityDataSerializers.FLOAT);

    private static final int NO_TARGET = -1;
    private static final int FORK_COLOR2 = 0xFFFFFF;

    public ForgeEffectEntity(EntityType<? extends ForgeEffectEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    /** A one-shot burst at a point: the standard on-hit visual. */
    public static ForgeEffectEntity impact(ServerLevel level, Vec3 pos, ForgeEffectStyle style, int color, int color2,
            float scale, int life) {
        ForgeEffectEntity effect = new ForgeEffectEntity(MagicalEntities.FORGE_EFFECT.get(), level);
        effect.setPos(pos.x, pos.y, pos.z);
        effect.entityData.set(STYLE, style.ordinal());
        effect.entityData.set(COLOR, color);
        effect.entityData.set(COLOR2, color2);
        effect.entityData.set(SCALE, Math.max(0.1F, scale));
        effect.entityData.set(LIFE, Math.max(1, life));
        level.addFreshEntity(effect);
        return effect;
    }

    /** The storm chain: a bolt drawn from one point to another. */
    public static ForgeEffectEntity fork(ServerLevel level, Vec3 from, Vec3 to, int color, int life) {
        ForgeEffectEntity effect = new ForgeEffectEntity(MagicalEntities.FORGE_EFFECT.get(), level);
        effect.setPos(from.x, from.y, from.z);
        effect.entityData.set(STYLE, ForgeEffectStyle.STORM_FORK.ordinal());
        effect.entityData.set(COLOR, color);
        effect.entityData.set(COLOR2, FORK_COLOR2);
        effect.entityData.set(SCALE, 1.0F);
        effect.entityData.set(LIFE, Math.max(1, life));
        effect.entityData.set(END_X, (float) to.x);
        effect.entityData.set(END_Y, (float) to.y);
        effect.entityData.set(END_Z, (float) to.z);
        level.addFreshEntity(effect);
        return effect;
    }

    /** A visual that rides an entity, e.g. the charge telegraph on the wielder. */
    public static ForgeEffectEntity following(ServerLevel level, Entity target, ForgeEffectStyle style, int color,
            int life) {
        ForgeEffectEntity effect = new ForgeEffectEntity(MagicalEntities.FORGE_EFFECT.get(), level);
        effect.setPos(target.getX(), target.getY(), target.getZ());
        effect.entityData.set(STYLE, style.ordinal());
        effect.entityData.set(COLOR, color);
        effect.entityData.set(COLOR2, color);
        effect.entityData.set(SCALE, 1.0F);
        effect.entityData.set(LIFE, Math.max(1, life));
        effect.entityData.set(TARGET_ID, target.getId());
        level.addFreshEntity(effect);
        return effect;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, ForgeEffectStyle.FIRE_BLOOM.ordinal());
        builder.define(COLOR, 0xFFFFFF);
        builder.define(COLOR2, 0xFFFFFF);
        builder.define(SCALE, 1.0F);
        builder.define(LIFE, 8);
        builder.define(HEAVY, false);
        builder.define(TARGET_ID, NO_TARGET);
        builder.define(END_X, 0.0F);
        builder.define(END_Y, 0.0F);
        builder.define(END_Z, 0.0F);
    }

    public ForgeEffectEntity withHeavy(boolean heavy) {
        entityData.set(HEAVY, heavy);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        Entity followed = followedEntity();
        if (followed != null) {
            setPos(followed.getX(), followed.getY(), followed.getZ());
        } else if (entityData.get(TARGET_ID) != NO_TARGET && !level().isClientSide()) {
            discard();
            return;
        }
        if (tickCount > life() && !level().isClientSide()) {
            discard();
        }
    }

    /** The body this visual rides, so the renderer can draw it on that body's own frame position. */
    public int targetId() {
        return entityData.get(TARGET_ID);
    }

    private Entity followedEntity() {
        int id = entityData.get(TARGET_ID);
        return id == NO_TARGET ? null : level().getEntity(id);
    }

    public ForgeEffectStyle style() {
        return ForgeEffectStyle.byOrdinal(entityData.get(STYLE));
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int secondaryColor() {
        return entityData.get(COLOR2);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public boolean heavy() {
        return entityData.get(HEAVY);
    }

    public Vec3 end() {
        return new Vec3(entityData.get(END_X), entityData.get(END_Y), entityData.get(END_Z));
    }

    /** Purely visual and short lived: nothing about it is worth restoring from disk. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // intentionally empty
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // intentionally empty
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
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
