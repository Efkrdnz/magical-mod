package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.Optional;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlackFlameProjectileEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> KNOCKBACK = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIELD_LIFE = SynchedEntityData.defineId(BlackFlameProjectileEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;

    public BlackFlameProjectileEntity(EntityType<? extends BlackFlameProjectileEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public BlackFlameProjectileEntity(Level level, LivingEntity owner, MagicSkillResolvedStats stats) {
        this(MagicalEntities.BLACK_FLAME_PROJECTILE.get(), level);
        ownerUuid = owner.getUUID();
        setPos(owner.getX(), owner.getEyeY() - 0.08D, owner.getZ());
        Vec3 direction = owner.getLookAngle().normalize();
        float speed = projectileSpeed(stats.speed());
        setDeltaMovement(direction.scale(speed));
        entityData.set(DAMAGE, stats.damage());
        entityData.set(RADIUS, stats.size());
        entityData.set(KNOCKBACK, stats.knockback());
        entityData.set(SPEED, speed);
        entityData.set(LIFE, Math.max(34, Math.round(stats.durationTicks() * 0.55F)));
        entityData.set(FIELD_LIFE, Math.max(80, stats.durationTicks()));
    }

    public static BlackFlameProjectileEntity scenarioProjectile(ServerLevel level, Vec3 position, Vec3 direction, MagicSkillResolvedStats stats) {
        BlackFlameProjectileEntity projectile = new BlackFlameProjectileEntity(MagicalEntities.BLACK_FLAME_PROJECTILE.get(), level);
        Vec3 normalized = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        float speed = projectileSpeed(stats.speed());
        projectile.setPos(position.x, position.y, position.z);
        projectile.setDeltaMovement(normalized.scale(speed));
        projectile.entityData.set(DAMAGE, stats.damage());
        projectile.entityData.set(RADIUS, stats.size());
        projectile.entityData.set(KNOCKBACK, stats.knockback());
        projectile.entityData.set(SPEED, speed);
        projectile.entityData.set(LIFE, Math.max(42, Math.round(stats.durationTicks() * 0.55F)));
        projectile.entityData.set(FIELD_LIFE, Math.max(80, stats.durationTicks()));
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 16.0F);
        builder.define(RADIUS, 1.45F);
        builder.define(KNOCKBACK, 0.25F);
        builder.define(SPEED, 1.45F);
        builder.define(LIFE, 64);
        builder.define(FIELD_LIFE, 118);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > entityData.get(LIFE)) {
            if (!level().isClientSide()) {
                detonate(position());
            }
            discard();
            return;
        }

        setDeltaMovement(constantVelocity());
        if (!level().isClientSide()) {
            Vec3 from = position();
            Vec3 movement = getDeltaMovement();
            Vec3 to = from.add(movement);
            BlockHitResult blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                detonate(blockHit.getLocation());
                discard();
                return;
            }
            offerApproachCounters(from, to);
            Entity hit = firstEntityHit(from, to);
            if (hit != null) {
                if (hit instanceof ServerPlayer player && MagicCounterService.hasActivePrompt(player, this)) {
                    MagicCounterService.expirePrompt(player, this);
                }
                detonate(new Vec3(hit.getX(), hit.getY(0.55D), hit.getZ()));
                discard();
                return;
            }
            if (tickCount % 4 == 0) {
                scorchWake();
            }
        }
        setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
    }

    private Entity firstEntityHit(Vec3 from, Vec3 to) {
        Entity owner = ownerEntity();
        AABB path = getBoundingBox().expandTowards(to.subtract(from)).inflate(0.65D + radius() * 0.22D);
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : level().getEntities(this, path, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.36D).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    private void offerApproachCounters(Vec3 from, Vec3 to) {
        Vec3 movement = getDeltaMovement();
        Vec3 lookAhead = movement.lengthSqr() < 1.0E-6D ? to : from.add(movement.normalize().scale(9.0D));
        AABB warningPath = new AABB(from, lookAhead).inflate(2.0D + radius() * 0.35D);
        for (Entity entity : level().getEntities(this, warningPath, target -> target instanceof ServerPlayer player && player.isAlive() && target != ownerEntity())) {
            ServerPlayer player = (ServerPlayer) entity;
            Vec3 toPlayer = player.getEyePosition().subtract(from);
            if (movement.lengthSqr() > 1.0E-6D && toPlayer.dot(movement.normalize()) < 0.0D) {
                continue;
            }
            MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.25D, 0.0D), 13);
        }
    }

    private void scorchWake() {
        Entity owner = ownerEntity();
        AABB area = getBoundingBox().inflate(0.5D + radius() * 0.16D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), Math.max(1.0F, damage() * 0.1F), MagicContent.BLACK_FLAMES_CAST.id());
            if (entity instanceof LivingEntity living) {
                applyCorrosion(living, owner);
            }
        }
    }

    private void detonate(Vec3 position) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setPos(position.x, position.y, position.z);
        Entity owner = ownerEntity();
        float impactRadius = impactRadius();
        serverLevel.addFreshEntity(BlackFlameFieldEntity.create(serverLevel, owner, position, impactRadius, Math.max(1.0F, damage() * 0.22F), entityData.get(FIELD_LIFE), knockback()));
        com.efkrdnz.magical.magic.visual.SpellFx.impact(serverLevel, com.efkrdnz.magical.magic.MagicContent.BLACK_FLAMES, position, new Vec3(0.0D, 1.0D, 0.0D), null, ownerEntity(), Math.max(1.0F, impactRadius * 0.5F));
        AABB area = new AABB(position, position).inflate(impactRadius);
        for (Entity entity : serverLevel.getEntities(this, area, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            double distance = Math.sqrt(entity.distanceToSqr(position));
            float falloff = (float) Math.max(0.28D, 1.0D - distance / impactRadius);
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * falloff, MagicContent.BLACK_FLAMES_CAST.id());
            if (entity instanceof LivingEntity living) {
                applyCorrosion(living, owner);
            }
            Vec3 push = entity.position().subtract(position);
            if (push.lengthSqr() > 1.0E-5D) {
                push = push.normalize().scale((0.14D + knockback() * 0.42D) * falloff);
                entity.push(push.x, 0.06D + 0.09D * falloff, push.z);
            }
        }
        serverLevel.playSound(null, position.x, position.y, position.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.15F, 0.48F);
        serverLevel.playSound(null, position.x, position.y, position.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.55F);
        serverLevel.playSound(null, position.x, position.y, position.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.32F, 0.82F);
    }

    public static void applyCorrosion(LivingEntity living, Entity owner) {
        living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), 110));
        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 130, 1, false, true), owner);
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 95, 1, false, true), owner);
        living.addEffect(new MobEffectInstance(MobEffects.WITHER, 70, 0, false, true), owner);
    }

    private Vec3 constantVelocity() {
        Vec3 movement = getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return movement.normalize().scale(entityData.get(SPEED));
    }

    private static float projectileSpeed(float resolvedSpeed) {
        return Mth.clamp(0.85F + resolvedSpeed * 0.54F, 1.15F, 2.75F);
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float impactRadius() {
        return 2.15F + radius() * 1.45F;
    }

    public float knockback() {
        return entityData.get(KNOCKBACK);
    }

    public Vec3 direction() {
        Vec3 motion = getDeltaMovement();
        return motion.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : motion.normalize();
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public net.minecraft.resources.ResourceLocation counterSkillId() {
        return MagicContent.BLACK_FLAMES_CAST.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.BLACK_FLAMES_CAST.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, MagicContent.BLACK_FLAMES_CAST.color(), counterSkill.color());
        level.playSound(null, clashPosition.x, clashPosition.y, clashPosition.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.85F, 0.42F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(KNOCKBACK, tag.getFloat("Knockback"));
        entityData.set(SPEED, tag.getFloat("Speed"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(FIELD_LIFE, tag.getInt("FieldLife"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Radius", radius());
        tag.putFloat("Knockback", knockback());
        tag.putFloat("Speed", entityData.get(SPEED));
        tag.putInt("Life", entityData.get(LIFE));
        tag.putInt("FieldLife", entityData.get(FIELD_LIFE));
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
