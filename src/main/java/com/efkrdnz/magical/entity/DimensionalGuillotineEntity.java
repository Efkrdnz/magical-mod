package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DimensionalGuillotineEntity extends Entity {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(DimensionalGuillotineEntity.class, EntityDataSerializers.INT);
    private final Set<UUID> hitTargets = new HashSet<>();
    private UUID ownerUuid;

    public DimensionalGuillotineEntity(EntityType<? extends DimensionalGuillotineEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static DimensionalGuillotineEntity create(ServerLevel level, LivingEntity owner, MagicSkillResolvedStats stats) {
        Vec3 origin = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 target = origin.add(look.scale(34.0D + stats.size() * 2.5D));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (blockHit.getType() != HitResult.Type.MISS) {
            target = blockHit.getLocation().subtract(look.scale(0.6D));
        }
        DimensionalGuillotineEntity entity = new DimensionalGuillotineEntity(MagicalEntities.DIMENSIONAL_GUILLOTINE.get(), level);
        entity.ownerUuid = owner.getUUID();
        entity.setPos(target.x, target.y, target.z);
        entity.entityData.set(DAMAGE, stats.damage());
        entity.entityData.set(WIDTH, 8.0F + stats.size() * 2.6F);
        entity.entityData.set(HEIGHT, 9.0F + stats.size() * 2.9F);
        entity.entityData.set(YAW, owner.getYRot());
        entity.entityData.set(LIFE, Math.max(32, stats.durationTicks()));
        entity.entityData.set(CHARGE, Mth.clamp(18 - Math.round(stats.speed() * 1.5F), 8, 18));
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 42.0F);
        builder.define(WIDTH, 12.0F);
        builder.define(HEIGHT, 14.0F);
        builder.define(YAW, 0.0F);
        builder.define(LIFE, 42);
        builder.define(CHARGE, 14);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (tickCount == chargeTicks()) {
                closeRift();
            } else if (tickCount == 1) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.35F);
            }
        }
        if (tickCount > life()) {
            discard();
        }
    }

    private void closeRift() {
        Entity owner = ownerEntity();
        Vec3 right = rightVector();
        Vec3 forward = forwardVector();
        double halfWidth = width() * 0.5D;
        double halfHeight = height() * 0.5D;
        double thickness = 1.35D + width() * 0.035D;
        AABB area = new AABB(getX() - halfWidth - 2.0D, getY() - halfHeight - 2.0D, getZ() - halfWidth - 2.0D,
                getX() + halfWidth + 2.0D, getY() + halfHeight + 2.0D, getZ() + halfWidth + 2.0D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            Vec3 point = entity.position().add(0.0D, entity.getBbHeight() * 0.52D, 0.0D).subtract(position());
            double lateral = Math.abs(point.dot(right));
            double depth = Math.abs(point.dot(forward));
            double vertical = Math.abs(point.y);
            if (lateral > halfWidth || depth > thickness || vertical > halfHeight || !hitTargets.add(entity.getUUID())) {
                continue;
            }
            float falloff = (float) Mth.clamp(1.0D - lateral / Math.max(1.0D, halfWidth) * 0.34D, 0.62D, 1.0D);
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * falloff, MagicContent.DIMENSIONAL_GUILLOTINE.id());
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2, false, true), owner);
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 1, false, true), owner);
            }
            Vec3 snap = position().subtract(entity.position()).multiply(0.16D, 0.04D, 0.16D);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.18D).add(snap).add(forward.scale(0.75D)));
            entity.hurtMarked = true;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.1F, 1.55F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.25F, 0.38F);
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    private Vec3 rightVector() {
        float radians = yaw() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(radians), 0.0D, Mth.sin(radians)).normalize();
    }

    private Vec3 forwardVector() {
        float radians = yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians)).normalize();
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public float yaw() {
        return entityData.get(YAW);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int chargeTicks() {
        return entityData.get(CHARGE);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(WIDTH, tag.getFloat("Width"));
        entityData.set(HEIGHT, tag.getFloat("Height"));
        entityData.set(YAW, tag.getFloat("Yaw"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(CHARGE, tag.getInt("Charge"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Width", width());
        tag.putFloat("Height", height());
        tag.putFloat("Yaw", yaw());
        tag.putInt("Life", life());
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
