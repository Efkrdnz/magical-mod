package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SoulBondEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SoulBondEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SoulBondEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;
    private UUID targetUuid;
    private Vec3 ownerPoint = Vec3.ZERO;
    private Vec3 targetPoint = Vec3.ZERO;
    private float ownerRadius = 0.8F;
    private float targetRadius = 0.8F;

    public SoulBondEntity(EntityType<? extends SoulBondEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SoulBondEntity create(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        SoulBondEntity entity = new SoulBondEntity(MagicalEntities.SOUL_BOND.get(), level);
        entity.ownerUuid = owner.getUUID();
        entity.targetUuid = target.getUUID();
        entity.entityData.set(OWNER_ID, owner.getId());
        entity.entityData.set(TARGET_ID, target.getId());
        Vec3 midpoint = owner.position().add(target.position()).scale(0.5D);
        entity.setPos(midpoint.x, midpoint.y + 1.0D, midpoint.z);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(TARGET_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = ownerEntity();
        Entity target = targetEntity();
        if (!level().isClientSide()) {
            if (!(owner instanceof ServerPlayer player) || target == null || !target.isAlive()) {
                discard();
                return;
            }
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (!state.hasSoulBond() || !target.getUUID().equals(state.soulBondEntityUuid())) {
                discard();
                return;
            }
            refreshSoulVisuals(player, target);
        }
        if (owner != null && target != null) {
            ownerPoint = owner.position().add(0.0D, 0.04D, 0.0D);
            targetPoint = target.position().add(0.0D, 0.04D, 0.0D);
            if (owner instanceof LivingEntity ownerLiving) {
                ownerRadius = soulCircleRadius(ownerLiving);
            }
            if (target instanceof LivingEntity targetLiving) {
                targetRadius = soulCircleRadius(targetLiving);
            }
            Vec3 midpoint = ownerPoint.add(targetPoint).scale(0.5D);
            setPos(midpoint.x, midpoint.y, midpoint.z);
        }
    }

    private void refreshSoulVisuals(ServerPlayer owner, Entity target) {
        if (!(target instanceof LivingEntity livingTarget) || !(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount % 10 == 0) {
            owner.addEffect(new MobEffectInstance(MobEffects.GLOWING, 32, 0, true, false, false));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 32, 0, true, false, false));
        }
    }

    private static float soulCircleRadius(LivingEntity entity) {
        return Math.max(0.55F, entity.getBbWidth() * 0.78F + 0.24F);
    }

    private Entity ownerEntity() {
        Entity entity = level().getEntity(entityData.get(OWNER_ID));
        if (entity != null || ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return entity;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    private Entity targetEntity() {
        Entity entity = level().getEntity(entityData.get(TARGET_ID));
        if (entity != null || targetUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return entity;
        }
        return serverLevel.getEntity(targetUuid);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public Vec3 ownerPoint() {
        return ownerPoint;
    }

    public Vec3 targetPoint() {
        return targetPoint;
    }

    public float ownerRadius() {
        return ownerRadius;
    }

    public float targetRadius() {
        return targetRadius;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        entityData.set(OWNER_ID, tag.getInt("OwnerId"));
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putInt("OwnerId", entityData.get(OWNER_ID));
        tag.putInt("TargetId", entityData.get(TARGET_ID));
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
