package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.BlackFlamesService;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BlackFlameBrandEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(BlackFlameBrandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(BlackFlameBrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BlackFlameBrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(BlackFlameBrandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(BlackFlameBrandEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;
    private UUID targetUuid;

    public BlackFlameBrandEntity(EntityType<? extends BlackFlameBrandEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static BlackFlameBrandEntity create(ServerLevel level, Entity owner, LivingEntity target, float damage, float radius, int durationTicks) {
        BlackFlameBrandEntity brand = new BlackFlameBrandEntity(MagicalEntities.BLACK_FLAME_BRAND.get(), level);
        brand.ownerUuid = owner == null ? null : owner.getUUID();
        brand.targetUuid = target.getUUID();
        brand.entityData.set(TARGET_ID, target.getId());
        brand.entityData.set(DAMAGE, Math.max(1.0F, damage));
        brand.entityData.set(RADIUS, Math.max(1.0F, radius));
        brand.entityData.set(DURATION, Math.max(60, durationTicks));
        brand.entityData.set(CHARGE, 28);
        brand.setPos(target.getX(), target.getY(0.6D), target.getZ());
        return brand;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, -1);
        builder.define(DAMAGE, 13.0F);
        builder.define(RADIUS, 3.0F);
        builder.define(DURATION, 220);
        builder.define(CHARGE, 28);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = targetEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved()) {
            discard();
            return;
        }
        setPos(target.getX(), target.getY(0.72D), target.getZ());
        if (tickCount == 1) {
            level.addFreshEntity(MagicCircleEffectEntity.createFollowing(level, living, 1.25F + radius() * 0.12F, 0x3C0458, chargeTicks() + 16, MagicCircleEffectEntity.STYLE_BLACK_FLAMES));
            level.playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.34F, 0.72F);
        }
        if (living instanceof ServerPlayer player && tickCount >= 5 && tickCount <= chargeTicks() - 3) {
            MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.25D, 0.0D), 13);
        }
        if (living instanceof ServerPlayer player && tickCount >= chargeTicks() - 1 && MagicCounterService.hasActivePrompt(player, this)) {
            MagicCounterService.expirePrompt(player, this);
        }
        if (tickCount >= chargeTicks()) {
            BlackFlamesService.applyBrand(level, ownerEntity(), living, damage(), radius(), durationTicks());
            discard();
        }
    }

    private Entity targetEntity() {
        Entity byId = level().getEntity(entityData.get(TARGET_ID));
        if (byId != null) {
            return byId;
        }
        if (!(level() instanceof ServerLevel level) || targetUuid == null) {
            return null;
        }
        return level.getEntity(targetUuid);
    }

    private Entity ownerEntity() {
        if (!(level() instanceof ServerLevel level) || ownerUuid == null) {
            return null;
        }
        return level.getEntity(ownerUuid);
    }

    private float damage() {
        return entityData.get(DAMAGE);
    }

    private float radius() {
        return entityData.get(RADIUS);
    }

    private int durationTicks() {
        return entityData.get(DURATION);
    }

    private int chargeTicks() {
        return entityData.get(CHARGE);
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public net.minecraft.resources.ResourceLocation counterSkillId() {
        return MagicContent.BLACK_FLAMES_BRAND.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.BLACK_FLAMES_BRAND.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, MagicContent.BLACK_FLAMES_BRAND.color(), counterSkill.color());
        level.playSound(null, clashPosition.x, clashPosition.y, clashPosition.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.85F, 0.42F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DURATION, tag.getInt("Duration"));
        entityData.set(CHARGE, tag.getInt("Charge"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putFloat("Damage", damage());
        tag.putFloat("Radius", radius());
        tag.putInt("Duration", durationTicks());
        tag.putInt("Charge", chargeTicks());
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
