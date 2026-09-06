package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.network.MagicalNetwork;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class JudgementBeamEntity extends Entity implements CounterableSkillThreat {
    private static final int DEFAULT_CHARGE_TICKS = 80;
    private static final int DEFAULT_FADE_TICKS = 34;
    private static final int COUNTER_WINDOW_TICKS = 8;

    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AOE_RADIUS = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_HEIGHT = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE_TICKS = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICKS = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IMPACTED = SynchedEntityData.defineId(JudgementBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID casterUuid;
    private UUID targetUuid;
    private double lockedX;
    private double lockedY;
    private double lockedZ;

    public JudgementBeamEntity(EntityType<? extends JudgementBeamEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static JudgementBeamEntity create(Level level, LivingEntity caster, LivingEntity target, MagicSkillResolvedStats stats) {
        JudgementBeamEntity beam = new JudgementBeamEntity(MagicalEntities.JUDGEMENT_BEAM.get(), level);
        beam.casterUuid = caster.getUUID();
        beam.initialize(target, stats);
        return beam;
    }

    public static JudgementBeamEntity createScenario(Level level, LivingEntity target, MagicSkillResolvedStats stats) {
        JudgementBeamEntity beam = new JudgementBeamEntity(MagicalEntities.JUDGEMENT_BEAM.get(), level);
        beam.initialize(target, stats);
        return beam;
    }

    private void initialize(LivingEntity target, MagicSkillResolvedStats stats) {
        targetUuid = target.getUUID();
        lockedX = target.getX();
        lockedY = target.getY();
        lockedZ = target.getZ();
        setPos(lockedX, lockedY, lockedZ);
        entityData.set(DAMAGE, stats.damage());
        entityData.set(AOE_RADIUS, Math.max(3.5F, 3.0F + stats.size() * 0.55F));
        entityData.set(TARGET_HEIGHT, target.getBbHeight());
        entityData.set(COLOR, stats.definition().color());
        entityData.set(CHARGE_TICKS, DEFAULT_CHARGE_TICKS);
        entityData.set(LIFE_TICKS, DEFAULT_CHARGE_TICKS + DEFAULT_FADE_TICKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 500.0F);
        builder.define(AOE_RADIUS, 4.5F);
        builder.define(TARGET_HEIGHT, 1.8F);
        builder.define(COLOR, 0xFFDC38);
        builder.define(CHARGE_TICKS, DEFAULT_CHARGE_TICKS);
        builder.define(LIFE_TICKS, DEFAULT_CHARGE_TICKS + DEFAULT_FADE_TICKS);
        builder.define(IMPACTED, false);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide()) {
            tickServer();
        }
        if (tickCount > lifeTicks()) {
            discard();
        }
    }

    private void tickServer() {
        Entity target = targetEntity();
        if (!impacted()) {
            if (!(target instanceof LivingEntity livingTarget) || !livingTarget.isAlive()) {
                discard();
                return;
            }
            if (livingTarget instanceof ServerPlayer player && tickCount >= chargeTicks() - COUNTER_WINDOW_TICKS - 2 && tickCount < chargeTicks() - 1) {
                MagicCounterService.offerCounter(player, this, player.position().add(0.0D, player.getBbHeight() + 1.2D, 0.0D), COUNTER_WINDOW_TICKS);
            }
            freeze(livingTarget);
            if (livingTarget instanceof ServerPlayer player && tickCount >= chargeTicks() - 1 && MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
            if (tickCount >= chargeTicks()) {
                impact(livingTarget);
            }
        }
    }

    private void freeze(LivingEntity target) {
        target.setDeltaMovement(Vec3.ZERO);
        target.teleportTo(lockedX, lockedY, lockedZ);
        target.fallDistance = 0.0F;
        setPos(lockedX, lockedY, lockedZ);
    }

    private void impact(LivingEntity target) {
        entityData.set(IMPACTED, true);
        Entity caster = casterEntity();
        MagicDamageService.hurt(target, damageSources().indirectMagic(this, caster == null ? this : caster), damage(), MagicContent.GABRIEL_JUDGEMENT.id());
        if (target instanceof ServerPlayer player) {
            MagicalNetwork.playFirstPersonImpact(player, color(), 24, 0.42F, 16, 0.85F, 2, -4.0F);
        }

        AABB area = new AABB(
                getX() - aoeRadius(),
                getY() - 1.0D,
                getZ() - aoeRadius(),
                getX() + aoeRadius(),
                getY() + targetHeight() + 3.0D,
                getZ() + aoeRadius());
        for (Entity entity : level().getEntities(this, area, candidate -> candidate instanceof LivingEntity && candidate.isAlive() && candidate != target && candidate != caster)) {
            double distance = Math.sqrt(entity.distanceToSqr(position()));
            float falloff = (float) Mth.clamp(1.0D - distance / Math.max(0.1D, aoeRadius()), 0.18D, 1.0D);
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, caster == null ? this : caster), damage() * 0.35F * falloff, MagicContent.GABRIEL_JUDGEMENT.id());
            if (entity instanceof ServerPlayer player) {
                MagicalNetwork.playFirstPersonImpact(player, color(), 16, 0.26F, 9, 0.42F, 0, -1.5F);
            }
            Vec3 push = entity.position().subtract(position());
            if (push.lengthSqr() > 1.0E-6D) {
                push = push.normalize().scale(0.45D * falloff);
                entity.push(push.x, 0.18D + falloff * 0.24D, push.z);
            }
        }

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 10.0F, 0.52F);
            serverLevel.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 8.0F, 0.62F);
            serverLevel.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 7.0F, 0.42F);
        }
    }

    private Entity casterEntity() {
        if (casterUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(casterUuid);
    }

    private Entity targetEntity() {
        if (targetUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(targetUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Caster")) {
            casterUuid = tag.getUUID("Caster");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        lockedX = tag.getDouble("LockedX");
        lockedY = tag.getDouble("LockedY");
        lockedZ = tag.getDouble("LockedZ");
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(AOE_RADIUS, tag.getFloat("AoeRadius"));
        entityData.set(TARGET_HEIGHT, tag.getFloat("TargetHeight"));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(CHARGE_TICKS, tag.getInt("ChargeTicks"));
        entityData.set(LIFE_TICKS, tag.getInt("LifeTicks"));
        entityData.set(IMPACTED, tag.getBoolean("Impacted"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (casterUuid != null) {
            tag.putUUID("Caster", casterUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putDouble("LockedX", lockedX);
        tag.putDouble("LockedY", lockedY);
        tag.putDouble("LockedZ", lockedZ);
        tag.putFloat("Damage", damage());
        tag.putFloat("AoeRadius", aoeRadius());
        tag.putFloat("TargetHeight", targetHeight());
        tag.putInt("Color", color());
        tag.putInt("ChargeTicks", chargeTicks());
        tag.putInt("LifeTicks", lifeTicks());
        tag.putBoolean("Impacted", impacted());
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

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public net.minecraft.resources.ResourceLocation counterSkillId() {
        return MagicContent.GABRIEL_JUDGEMENT.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.GABRIEL_JUDGEMENT.attribute();
    }

    @Override
    public Entity counterOwner() {
        return casterEntity();
    }

    @Override
    public boolean canBeCounteredBy(LivingEntity defender) {
        return CounterableSkillThreat.super.canBeCounteredBy(defender) && targetUuid != null && targetUuid.equals(defender.getUUID()) && !impacted();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, color(), counterSkill.color());
        level.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 7.0F, 1.45F);
        discard();
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float aoeRadius() {
        return entityData.get(AOE_RADIUS);
    }

    public float targetHeight() {
        return entityData.get(TARGET_HEIGHT);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int chargeTicks() {
        return entityData.get(CHARGE_TICKS);
    }

    public int lifeTicks() {
        return entityData.get(LIFE_TICKS);
    }

    public boolean impacted() {
        return entityData.get(IMPACTED);
    }
}
