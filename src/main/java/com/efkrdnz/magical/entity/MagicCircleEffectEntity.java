package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.registry.MagicalEntities;
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
import net.minecraft.world.phys.Vec3;

public final class MagicCircleEffectEntity extends Entity {
    public static final int STYLE_DIVINE_RESTORATION = 0;
    public static final int STYLE_MANA_FLIGHT = 1;
    public static final int STYLE_MIST_STEP = 2;
    public static final int STYLE_ANCHOR_SIGIL = 3;
    public static final int STYLE_CINDER_MARK = 4;
    public static final int STYLE_PRISM_GUARD = 5;
    public static final int STYLE_GLACIER_WAVE = 6;
    public static final int STYLE_BLACK_FLAMES = 7;
    public static final int STYLE_SOUL_VALLEY = 8;

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FOLLOW_TARGET = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DETAIL = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    /** Index into MagicContent.orderedSkillIds() for scripted (VisualProfile) circles; -1 = legacy style. */
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> CIRCLE_ROLE = SynchedEntityData.defineId(MagicCircleEffectEntity.class, EntityDataSerializers.BYTE);
    public static final int FULL_DETAIL = 3;
    public static final byte ROLE_CAST = 0;
    public static final byte ROLE_HOLD = 1;
    public static final byte ROLE_IMPACT = 2;
    public static final byte ROLE_TARGET = 3;
    public static final byte ROLE_LINGER = 4;
    private UUID targetUuid;

    /** A profile-driven circle: the client resolves the CircleScript from the skill index. */
    public static MagicCircleEffectEntity createScripted(Level level, int skillIndex, byte role, Vec3 position, float radius, int color, int life, float yaw, float pitch, float roll, int detail) {
        MagicCircleEffectEntity circle = createStatic(level, position, radius, color, life, 0, yaw, pitch, roll, detail);
        circle.entityData.set(SKILL_INDEX, skillIndex);
        circle.entityData.set(CIRCLE_ROLE, role);
        return circle;
    }

    public static MagicCircleEffectEntity createScriptedFollowing(Level level, int skillIndex, byte role, LivingEntity target, float radius, int color, int life, int detail) {
        MagicCircleEffectEntity circle = createFollowing(level, target, radius, color, life, 0, detail);
        circle.entityData.set(SKILL_INDEX, skillIndex);
        circle.entityData.set(CIRCLE_ROLE, role);
        return circle;
    }

    public int skillIndex() {
        return entityData.get(SKILL_INDEX);
    }

    public byte circleRole() {
        return entityData.get(CIRCLE_ROLE);
    }

    public boolean isScripted() {
        return skillIndex() >= 0;
    }

    public MagicCircleEffectEntity(EntityType<? extends MagicCircleEffectEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static MagicCircleEffectEntity createFollowing(Level level, LivingEntity target, float radius, int color, int life, int style) {
        return createFollowing(level, target, radius, color, life, style, FULL_DETAIL);
    }

    public static MagicCircleEffectEntity createFollowing(Level level, LivingEntity target, float radius, int color, int life, int style, int detail) {
        MagicCircleEffectEntity circle = createStatic(level, new Vec3(target.getX(), target.getY() + 0.04D, target.getZ()), radius, color, life, style, 0.0F, 90.0F, 0.0F, detail);
        circle.targetUuid = target.getUUID();
        circle.entityData.set(TARGET_ID, target.getId());
        circle.entityData.set(FOLLOW_TARGET, true);
        return circle;
    }

    public static MagicCircleEffectEntity createStatic(Level level, Vec3 position, float radius, int color, int life, int style, float yaw, float pitch, float roll) {
        return createStatic(level, position, radius, color, life, style, yaw, pitch, roll, FULL_DETAIL);
    }

    public static MagicCircleEffectEntity createStatic(Level level, Vec3 position, float radius, int color, int life, int style, float yaw, float pitch, float roll, int detail) {
        MagicCircleEffectEntity circle = new MagicCircleEffectEntity(MagicalEntities.MAGIC_CIRCLE_EFFECT.get(), level);
        circle.setPos(position.x, position.y, position.z);
        circle.entityData.set(RADIUS, radius);
        circle.entityData.set(COLOR, color);
        circle.entityData.set(LIFE, Math.max(1, life));
        circle.entityData.set(STYLE, style);
        circle.entityData.set(YAW, yaw);
        circle.entityData.set(PITCH, pitch);
        circle.entityData.set(ROLL, roll);
        circle.entityData.set(DETAIL, Math.max(0, Math.min(FULL_DETAIL, detail)));
        return circle;
    }

    public static int detailForTier(int tier) {
        return tier < 0 ? FULL_DETAIL : Math.min(FULL_DETAIL, tier);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 2.5F);
        builder.define(COLOR, 0xFFE65A);
        builder.define(LIFE, 60);
        builder.define(STYLE, STYLE_DIVINE_RESTORATION);
        builder.define(YAW, 0.0F);
        builder.define(PITCH, 90.0F);
        builder.define(ROLL, 0.0F);
        builder.define(FOLLOW_TARGET, false);
        builder.define(TARGET_ID, -1);
        builder.define(DETAIL, FULL_DETAIL);
        builder.define(SKILL_INDEX, -1);
        builder.define(CIRCLE_ROLE, ROLE_CAST);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && followsTarget()) {
            Entity target = level().getEntity(entityData.get(TARGET_ID));
            if (target != null && target.isAlive()) {
                setPos(target.getX(), target.getY() + 0.04D, target.getZ());
            }
        } else if (followsTarget() && targetUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity target = serverLevel.getEntity(targetUuid);
            if (target != null && target.isAlive()) {
                setPos(target.getX(), target.getY() + 0.04D, target.getZ());
            }
        }
        if (tickCount > life()) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(STYLE, tag.getInt("Style"));
        entityData.set(YAW, tag.getFloat("Yaw"));
        entityData.set(PITCH, tag.getFloat("Pitch"));
        entityData.set(ROLL, tag.getFloat("Roll"));
        entityData.set(FOLLOW_TARGET, tag.getBoolean("FollowTarget"));
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(DETAIL, tag.contains("Detail") ? tag.getInt("Detail") : FULL_DETAIL);
        entityData.set(SKILL_INDEX, tag.contains("SkillIndex") ? tag.getInt("SkillIndex") : -1);
        entityData.set(CIRCLE_ROLE, tag.contains("CircleRole") ? tag.getByte("CircleRole") : ROLE_CAST);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putFloat("Radius", radius());
        tag.putInt("Color", color());
        tag.putInt("Life", life());
        tag.putInt("Style", style());
        tag.putFloat("Yaw", yaw());
        tag.putFloat("Pitch", pitch());
        tag.putFloat("Roll", roll());
        tag.putBoolean("FollowTarget", followsTarget());
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putInt("Detail", detail());
        tag.putInt("SkillIndex", skillIndex());
        tag.putByte("CircleRole", circleRole());
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

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int style() {
        return entityData.get(STYLE);
    }

    public float yaw() {
        return entityData.get(YAW);
    }

    public float pitch() {
        return entityData.get(PITCH);
    }

    public float roll() {
        return entityData.get(ROLL);
    }

    public boolean followsTarget() {
        return entityData.get(FOLLOW_TARGET);
    }

    public int detail() {
        return entityData.get(DETAIL);
    }
}
