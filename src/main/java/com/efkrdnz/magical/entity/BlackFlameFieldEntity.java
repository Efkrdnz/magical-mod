package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BlackFlameFieldEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BlackFlameFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(BlackFlameFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> KNOCKBACK = SynchedEntityData.defineId(BlackFlameFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BlackFlameFieldEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;

    public BlackFlameFieldEntity(EntityType<? extends BlackFlameFieldEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static BlackFlameFieldEntity create(Level level, Entity owner, Vec3 position, float radius, float damage, int life, float knockback) {
        BlackFlameFieldEntity field = new BlackFlameFieldEntity(MagicalEntities.BLACK_FLAME_FIELD.get(), level);
        field.setPos(position.x, position.y, position.z);
        field.ownerUuid = owner == null ? null : owner.getUUID();
        field.entityData.set(RADIUS, Math.max(1.5F, radius));
        field.entityData.set(DAMAGE, Math.max(0.5F, damage));
        field.entityData.set(LIFE, Math.max(30, life));
        field.entityData.set(KNOCKBACK, Math.max(0.0F, knockback));
        return field;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4.0F);
        builder.define(DAMAGE, 3.5F);
        builder.define(KNOCKBACK, 0.2F);
        builder.define(LIFE, 120);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide()) {
            if (tickCount % 7 == 1) {
                burn();
            }
            if (tickCount % 3 == 0 && level() instanceof ServerLevel serverLevel) {
                emitParticles(serverLevel);
            }
        }
        if (tickCount > life()) {
            discard();
        }
    }

    private void burn() {
        Entity owner = ownerEntity();
        AABB area = getBoundingBox().inflate(radius(), 2.4D, radius());
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity living && living.isAlive() && target != owner)) {
            double horizontal = horizontalDistance(entity.position());
            if (horizontal > radius()) {
                continue;
            }
            float falloff = (float) Math.max(0.28D, 1.0D - horizontal / radius());
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * falloff, MagicContent.BLACK_FLAMES_CAST.id());
            if (entity instanceof LivingEntity living) {
                BlackFlameProjectileEntity.applyCorrosion(living, owner);
            }
            Vec3 pull = position().subtract(entity.position());
            if (pull.lengthSqr() > 1.0E-5D) {
                Vec3 horizontalPull = new Vec3(pull.x, 0.0D, pull.z).normalize().scale(0.018D + knockback() * 0.018D);
                entity.push(horizontalPull.x, 0.01D, horizontalPull.z);
            }
        }
    }

    private void emitParticles(ServerLevel serverLevel) {
        for (int i = 0; i < 8; i++) {
            double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(serverLevel.random.nextDouble()) * radius();
            double x = getX() + Math.cos(angle) * distance;
            double z = getZ() + Math.sin(angle) * distance;
            double y = getY() + 0.12D + serverLevel.random.nextDouble() * 0.8D;
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.05D, 0.14D, 0.05D, 0.012D);
            if (serverLevel.random.nextBoolean()) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.08D, z, 1, 0.03D, 0.12D, 0.03D, 0.01D);
            }
        }
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    private double horizontalDistance(Vec3 position) {
        double dx = position.x - getX();
        double dz = position.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(KNOCKBACK, tag.getFloat("Knockback"));
        entityData.set(LIFE, tag.getInt("Life"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Radius", radius());
        tag.putFloat("Damage", damage());
        tag.putFloat("Knockback", knockback());
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

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float knockback() {
        return entityData.get(KNOCKBACK);
    }

    public int life() {
        return entityData.get(LIFE);
    }
}
