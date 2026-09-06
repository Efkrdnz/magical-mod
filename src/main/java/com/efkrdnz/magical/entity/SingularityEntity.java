package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.SpacePocketService;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SingularityEntity extends Entity {
    private static final int FORMING_TICKS = 32;
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);

    public SingularityEntity(EntityType<? extends SingularityEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SingularityEntity create(ServerLevel level, LivingEntity owner, MagicSkillResolvedStats stats) {
        SingularityEntity entity = new SingularityEntity(MagicalEntities.SINGULARITY.get(), level);
        Vec3 look = owner.getLookAngle().normalize();
        entity.ownerUuid = owner.getUUID();
        entity.setDirection(look);
        entity.setPos(owner.getX() + look.x * 2.0D, owner.getEyeY() - 0.2D + look.y * 1.2D, owner.getZ() + look.z * 2.0D);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.entityData.set(DAMAGE, stats.damage());
        entity.entityData.set(RADIUS, 1.8F + stats.size() * 0.55F);
        entity.entityData.set(SPEED, entity.projectileSpeed(stats.speed()));
        entity.entityData.set(LIFE, FORMING_TICKS + Math.max(80, stats.durationTicks()));
        entity.entityData.set(CHARGE, FORMING_TICKS);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 32.0F);
        builder.define(RADIUS, 2.0F);
        builder.define(SPEED, 1.25F);
        builder.define(LIFE, 160);
        builder.define(CHARGE, FORMING_TICKS);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > life()) {
            if (!level().isClientSide()) {
                annihilateBlocks(position(), destroyRadius() * 0.75F);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 1.5F, 0.35F);
            }
            discard();
            return;
        }

        if (!isForming() && !level().isClientSide()) {
            tickServer();
        }

        if (isForming()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 movement = direction().scale(speed());
        setDeltaMovement(movement);
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
    }

    private void tickServer() {
        Vec3 from = position();
        Vec3 to = from.add(direction().scale(Math.max(0.15D, speed())));
        BlockHitResult blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            annihilateBlocks(blockHit.getLocation(), destroyRadius());
            level().playSound(null, blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 0.28F);
        } else if (tickCount % 3 == 0 && touchesBlock()) {
            annihilateBlocks(position(), destroyRadius() * 0.85F);
        }
        pullAndCrushEntities();
    }

    private void pullAndCrushEntities() {
        Entity owner = ownerEntity();
        double pullRadius = 8.5D + radius() * 2.2D;
        double coreRadius = 1.05D + radius() * 0.72D;
        AABB area = getBoundingBox().inflate(pullRadius);
        for (Entity entity : level().getEntities(this, area, target -> target.isAlive() && target != owner)) {
            Vec3 toCore = position().subtract(entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D));
            double distance = Math.max(0.35D, toCore.length());
            if (distance > pullRadius) {
                continue;
            }
            double strength = Mth.clamp((pullRadius - distance) / pullRadius, 0.0D, 1.0D);
            Vec3 pull = toCore.normalize().scale(0.035D + strength * (0.19D + radius() * 0.018D));
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.86D).add(pull));
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity && distance <= coreRadius && tickCount % 5 == 0) {
                float damage = damage() * (float) (0.32D + strength * 0.72D);
                MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage, MagicContent.SINGULARITY.id());
            }
        }
    }

    private boolean touchesBlock() {
        int check = Mth.ceil(radius() * 0.7F);
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-check, -check, -check), center.offset(check, check, check))) {
            if (!level().getBlockState(pos).isAir() && level().getBlockState(pos).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    private void annihilateBlocks(Vec3 center, float radius) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int range = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        int removed = 0;
        float radiusSqr = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range), origin.offset(range, range, range))) {
            if (removed > 420) {
                return;
            }
            if (pos.distToCenterSqr(center) > radiusSqr || serverLevel.getBlockState(pos).isAir() || SpacePocketService.isProtectedPocketShell(serverLevel, pos)) {
                continue;
            }
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            removed++;
        }
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    private Vec3 direction() {
        Vec3 synced = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        if (synced.lengthSqr() > 1.0E-6D) {
            return synced.normalize();
        }
        if (direction.lengthSqr() > 1.0E-6D) {
            return direction.normalize();
        }
        Vec3 motion = getDeltaMovement();
        return motion.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : motion.normalize();
    }

    private void setDirection(Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        this.direction = normalized;
        entityData.set(DIR_X, (float) normalized.x);
        entityData.set(DIR_Y, (float) normalized.y);
        entityData.set(DIR_Z, (float) normalized.z);
    }

    private float projectileSpeed(float resolvedSpeed) {
        return Mth.clamp(0.72F + resolvedSpeed * 0.5F, 0.95F, 2.2F);
    }

    public float chargeFactor() {
        int charge = chargeTicks();
        if (charge <= 0 || tickCount >= charge) {
            return 1.0F;
        }
        float progress = tickCount / (float) charge;
        return Mth.clamp(progress * progress * (3.0F - 2.0F * progress), 0.0F, 1.0F);
    }

    public boolean isForming() {
        return tickCount < chargeTicks();
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float visualRadius() {
        return radius() * 1.22F * (0.28F + chargeFactor() * 0.72F);
    }

    public float destroyRadius() {
        return 2.75F + radius() * 0.95F;
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float speed() {
        return entityData.get(SPEED);
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
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(SPEED, tag.getFloat("Speed"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(CHARGE, tag.getInt("Charge"));
        setDirection(new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Radius", radius());
        tag.putFloat("Speed", speed());
        tag.putInt("Life", life());
        tag.putInt("Charge", chargeTicks());
        Vec3 dir = direction();
        tag.putDouble("DirX", dir.x);
        tag.putDouble("DirY", dir.y);
        tag.putDouble("DirZ", dir.z);
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
