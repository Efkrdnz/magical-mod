package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MagicBarrageBeamEntity extends Entity {
    private static final int CHARGE_TICKS = 20;
    private static final int FADE_TICKS = 12;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_X = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Y = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Z = SynchedEntityData.defineId(MagicBarrageBeamEntity.class, EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    private ResourceLocation sourceSkillId;
    private final Set<UUID> hitTargets = new HashSet<>();

    public MagicBarrageBeamEntity(EntityType<? extends MagicBarrageBeamEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static MagicBarrageBeamEntity create(ServerLevel level, LivingEntity owner, Vec3 start, Vec3 lockedPosition, ResourceLocation sourceSkillId, int color, float damage, float size) {
        MagicBarrageBeamEntity beam = new MagicBarrageBeamEntity(MagicalEntities.MAGIC_BARRAGE_BEAM.get(), level);
        beam.ownerUuid = owner.getUUID();
        beam.sourceSkillId = sourceSkillId;
        beam.setPos(lockedPosition.x, lockedPosition.y, lockedPosition.z);
        Vec3 offset = start.subtract(lockedPosition);
        beam.entityData.set(START_X, (float) offset.x);
        beam.entityData.set(START_Y, (float) offset.y);
        beam.entityData.set(START_Z, (float) offset.z);
        beam.entityData.set(COLOR, color);
        beam.entityData.set(DAMAGE, Math.max(1.0F, damage));
        beam.entityData.set(SIZE, Mth.clamp(size, 0.65F, 3.2F));
        return beam;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0x8E7BFF);
        builder.define(DAMAGE, 5.0F);
        builder.define(SIZE, 1.0F);
        builder.define(START_X, 0.0F);
        builder.define(START_Y, 8.0F);
        builder.define(START_Z, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide() && tickCount == CHARGE_TICKS) {
            impact();
        }
        if (tickCount > CHARGE_TICKS + FADE_TICKS) {
            discard();
        }
    }

    private void impact() {
        Entity owner = ownerEntity();
        double radius = 0.95D + size() * 0.62D;
        AABB area = new AABB(getX() - radius, getY() - radius, getZ() - radius, getX() + radius, getY() + radius, getZ() + radius);
        for (Entity entity : level().getEntities(this, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            LivingEntity target = (LivingEntity) entity;
            double distance = target.getBoundingBox().getCenter().distanceTo(position());
            if (distance > radius || !hitTargets.add(target.getUUID())) {
                continue;
            }
            float falloff = Mth.clamp(1.0F - (float) distance / (float) radius * 0.45F, 0.45F, 1.0F);
            target.invulnerableTime = 0;
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * falloff, sourceSkillId);
            target.invulnerableTime = 0;
            Vec3 push = target.position().subtract(position());
            if (push.lengthSqr() > 1.0E-5D) {
                Vec3 shove = push.normalize().scale(0.18D + size() * 0.035D);
                target.push(shove.x, 0.05D + size() * 0.018D, shove.z);
            }
        }
    }

    private Entity ownerEntity() {
        return ownerUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(ownerUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.contains("SourceSkill")) {
            sourceSkillId = ResourceLocation.parse(tag.getString("SourceSkill"));
        }
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(SIZE, tag.getFloat("Size"));
        entityData.set(START_X, tag.getFloat("StartX"));
        entityData.set(START_Y, tag.getFloat("StartY"));
        entityData.set(START_Z, tag.getFloat("StartZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (sourceSkillId != null) {
            tag.putString("SourceSkill", sourceSkillId.toString());
        }
        tag.putInt("Color", color());
        tag.putFloat("Damage", damage());
        tag.putFloat("Size", size());
        tag.putFloat("StartX", entityData.get(START_X));
        tag.putFloat("StartY", entityData.get(START_Y));
        tag.putFloat("StartZ", entityData.get(START_Z));
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

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public Vec3 startOffset() {
        return new Vec3(entityData.get(START_X), entityData.get(START_Y), entityData.get(START_Z));
    }

    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    public int fadeTicks() {
        return FADE_TICKS;
    }

    private float damage() {
        return entityData.get(DAMAGE);
    }
}
