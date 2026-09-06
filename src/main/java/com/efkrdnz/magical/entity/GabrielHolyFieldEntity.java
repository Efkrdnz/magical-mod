package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GabrielHolyFieldEntity extends Entity {
    public static final int SEED_TICKS = 20;
    public static final int CROSS_TICKS = 28;
    public static final int X_TICKS = 28;
    public static final int FINAL_FORM_TICKS = 24;
    public static final int FINAL_FORM_START_TICKS = SEED_TICKS + CROSS_TICKS + X_TICKS;
    public static final int FORMATION_TICKS = FINAL_FORM_START_TICKS + FINAL_FORM_TICKS;
    private static final int FADE_TICKS = 28;
    private static final int BASE_STRIKE_INTERVAL = 24;

    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICKS = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST_STRIKE_TICK = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LAST_STRIKE_X = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAST_STRIKE_Z = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STRIKE_INTERVAL = SynchedEntityData.defineId(GabrielHolyFieldEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;

    public GabrielHolyFieldEntity(EntityType<? extends GabrielHolyFieldEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static GabrielHolyFieldEntity create(ServerLevel level, ServerPlayer caster, Vec3 center, MagicSkillResolvedStats stats) {
        GabrielHolyFieldEntity field = new GabrielHolyFieldEntity(MagicalEntities.GABRIEL_HOLY_FIELD.get(), level);
        field.casterUuid = caster.getUUID();
        field.setPos(center.x, center.y + 0.06D, center.z);
        field.entityData.set(DAMAGE, stats.damage());
        field.entityData.set(RADIUS, Math.max(7.0F, 5.8F + stats.size() * 1.85F));
        field.entityData.set(COLOR, stats.definition().color());
        field.entityData.set(LIFE_TICKS, FORMATION_TICKS + Math.max(80, stats.durationTicks()) + FADE_TICKS);
        field.entityData.set(STRIKE_INTERVAL, strikeIntervalFor(stats));
        return field;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 34.0F);
        builder.define(RADIUS, 12.0F);
        builder.define(COLOR, 0xFFF1A8);
        builder.define(LIFE_TICKS, FORMATION_TICKS + 170 + FADE_TICKS);
        builder.define(LAST_STRIKE_TICK, -1000);
        builder.define(LAST_STRIKE_X, 0.0F);
        builder.define(LAST_STRIKE_Z, 0.0F);
        builder.define(STRIKE_INTERVAL, BASE_STRIKE_INTERVAL);
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
        if (tickCount == 1 && level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0F, 0.74F);
            level.playSound(null, blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 3.0F, 0.48F);
        }
        if (tickCount == FORMATION_TICKS && level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6.0F, 1.52F);
            level.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 5.0F, 0.62F);
        }
        if (tickCount < FORMATION_TICKS || tickCount > lifeTicks() - FADE_TICKS) {
            return;
        }
        if ((tickCount - FORMATION_TICKS) % strikeInterval() == 0) {
            strikeTargets();
        }
    }

    private static int strikeIntervalFor(MagicSkillResolvedStats stats) {
        return Mth.clamp(BASE_STRIKE_INTERVAL - stats.tuning().speed() * 2, 8, 44);
    }

    private void strikeTargets() {
        Entity caster = casterEntity();
        float activeRadius = currentRadius();
        AABB area = new AABB(getX() - activeRadius, getY() - 2.0D, getZ() - activeRadius, getX() + activeRadius, getY() + 18.0D, getZ() + activeRadius);
        List<LivingEntity> targets = level().getEntities(this, area, candidate -> candidate instanceof LivingEntity living
                        && living.isAlive()
                        && candidate != caster)
                .stream()
                .map(LivingEntity.class::cast)
                .filter(this::insideField)
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> isAttackingCaster(entity, caster) ? 0 : 1)
                        .thenComparingDouble(entity -> entity.distanceToSqr(position())))
                .limit(1)
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        LivingEntity first = targets.getFirst();
        entityData.set(LAST_STRIKE_TICK, tickCount);
        entityData.set(LAST_STRIKE_X, (float) (first.getX() - getX()));
        entityData.set(LAST_STRIKE_Z, (float) (first.getZ() - getZ()));
        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            float heightScale = target.onGround() ? 1.0F : 1.18F;
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, caster == null ? this : caster), damage() * heightScale, MagicContent.GABRIEL_HOLY_FIELD.id());
            target.invulnerableTime = 0;
            target.push(0.0D, target.onGround() ? 0.18D : -0.08D, 0.0D);
            if (target instanceof ServerPlayer player) {
                MagicalNetwork.playFirstPersonImpact(player, color(), 10, 0.22F, 5, 0.2F, 0, -0.8F);
            }
        }
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.1F, 1.85F);
            level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6F, 1.35F);
        }
    }

    private boolean isAttackingCaster(LivingEntity entity, Entity caster) {
        if (!(caster instanceof LivingEntity livingCaster)) {
            return false;
        }
        if (entity instanceof Mob mob && mob.getTarget() == livingCaster) {
            return true;
        }
        if (livingCaster.getLastHurtByMob() == entity) {
            return true;
        }
        return entity.getLastHurtMob() == livingCaster && entity.tickCount - entity.getLastHurtMobTimestamp() <= 120;
    }

    private boolean insideField(LivingEntity entity) {
        double dx = entity.getX() - getX();
        double dz = entity.getZ() - getZ();
        float activeRadius = currentRadius();
        return dx * dx + dz * dz <= activeRadius * activeRadius;
    }

    private Entity casterEntity() {
        if (casterUuid == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(casterUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Caster")) {
            casterUuid = tag.getUUID("Caster");
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(LIFE_TICKS, tag.getInt("LifeTicks"));
        entityData.set(LAST_STRIKE_TICK, tag.getInt("LastStrikeTick"));
        entityData.set(LAST_STRIKE_X, tag.getFloat("LastStrikeX"));
        entityData.set(LAST_STRIKE_Z, tag.getFloat("LastStrikeZ"));
        entityData.set(STRIKE_INTERVAL, tag.contains("StrikeInterval") ? tag.getInt("StrikeInterval") : BASE_STRIKE_INTERVAL);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (casterUuid != null) {
            tag.putUUID("Caster", casterUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Radius", radius());
        tag.putInt("Color", color());
        tag.putInt("LifeTicks", lifeTicks());
        tag.putInt("LastStrikeTick", lastStrikeTick());
        tag.putFloat("LastStrikeX", lastStrikeX());
        tag.putFloat("LastStrikeZ", lastStrikeZ());
        tag.putInt("StrikeInterval", strikeInterval());
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

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float currentRadius() {
        return currentRadius(tickCount);
    }

    public float currentRadius(float age) {
        if (age < FORMATION_TICKS) {
            if (age < FINAL_FORM_START_TICKS) {
                float earlyProgress = Mth.clamp(age / Math.max(1.0F, FINAL_FORM_START_TICKS), 0.0F, 1.0F);
                return radius() * Mth.lerp(earlyProgress, 0.34F, 1.0F);
            }
            float formProgress = Mth.clamp((age - FINAL_FORM_START_TICKS) / (float) FINAL_FORM_TICKS, 0.0F, 1.0F);
            float eased = formProgress * formProgress * (3.0F - 2.0F * formProgress);
            return Mth.lerp(eased, radius(), radius() * 3.0F);
        }
        float activeTicks = Math.max(1.0F, lifeTicks() - FADE_TICKS - FORMATION_TICKS);
        float progress = Mth.clamp((age - FORMATION_TICKS) / activeTicks, 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(eased, radius() * 3.0F, radius());
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int lifeTicks() {
        return entityData.get(LIFE_TICKS);
    }

    public int lastStrikeTick() {
        return entityData.get(LAST_STRIKE_TICK);
    }

    public float lastStrikeX() {
        return entityData.get(LAST_STRIKE_X);
    }

    public float lastStrikeZ() {
        return entityData.get(LAST_STRIKE_Z);
    }

    public int strikeInterval() {
        return Math.max(1, entityData.get(STRIKE_INTERVAL));
    }

    public float fadeOutProgress() {
        return Mth.clamp((tickCount - (lifeTicks() - FADE_TICKS)) / (float) FADE_TICKS, 0.0F, 1.0F);
    }
}
