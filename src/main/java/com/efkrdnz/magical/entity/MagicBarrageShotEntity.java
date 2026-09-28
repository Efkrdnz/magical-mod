package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MagicBarrageShotEntity extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MagicBarrageShotEntity.class, EntityDataSerializers.INT);
    /** A volley can land several shots a tick, so a hit is a spark, not a burst. */
    private static final int HIT_MOTES = 3;
    private static final int HIT_SHARDS = 2;
    private static final int BLOCK_CRUMBS = 3;
    private UUID ownerUuid;
    private ResourceLocation sourceSkillId;

    public MagicBarrageShotEntity(EntityType<? extends MagicBarrageShotEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static MagicBarrageShotEntity create(ServerLevel level, LivingEntity owner, Vec3 start, LivingEntity target, ResourceLocation sourceSkillId, int color, float damage, float speed, float size) {
        MagicBarrageShotEntity shot = new MagicBarrageShotEntity(MagicalEntities.MAGIC_BARRAGE_SHOT.get(), level);
        shot.ownerUuid = owner.getUUID();
        shot.sourceSkillId = sourceSkillId;
        Vec3 aim = target.getBoundingBox().getCenter().subtract(start);
        if (aim.lengthSqr() < 1.0E-5D) {
            aim = new Vec3(0.0D, -1.0D, 0.0D);
        }
        Vec3 direction = aim.normalize();
        shot.setPos(start.x, start.y, start.z);
        shot.setDeltaMovement(direction.scale(speed));
        shot.entityData.set(COLOR, color);
        shot.entityData.set(DAMAGE, Math.max(1.0F, damage));
        shot.entityData.set(SIZE, Mth.clamp(size, 0.45F, 2.8F));
        shot.entityData.set(DIR_X, (float) direction.x);
        shot.entityData.set(DIR_Y, (float) direction.y);
        shot.entityData.set(DIR_Z, (float) direction.z);
        shot.entityData.set(LIFE, 70);
        return shot;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0x8E7BFF);
        builder.define(DAMAGE, 4.0F);
        builder.define(SIZE, 1.0F);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, -1.0F);
        builder.define(DIR_Z, 0.0F);
        builder.define(LIFE, 70);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 previous = position();
        Vec3 next = previous.add(getDeltaMovement());
        if (!level().isClientSide()) {
            BlockHitResult blockHit = level().clip(new ClipContext(previous, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                chipBlock(blockHit);
                discard();
                return;
            }
        }
        setPos(next.x, next.y, next.z);
        if (!level().isClientSide()) {
            hitTargets();
            if (tickCount > life()) {
                discard();
            }
        }
    }

    private void hitTargets() {
        Entity owner = ownerEntity();
        double radius = 0.38D + size() * 0.18D;
        AABB area = getBoundingBox().inflate(radius);
        for (Entity entity : level().getEntities(this, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            LivingEntity target = (LivingEntity) entity;
            target.invulnerableTime = 0;
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, owner == null ? this : owner), damage(), sourceSkillId);
            target.invulnerableTime = 0;
            Vec3 push = direction().scale(0.09D + size() * 0.035D);
            target.push(push.x, 0.035D + Math.max(0.0D, push.y) * 0.35D, push.z);
            hitSpark(target);
            discard();
            return;
        }
    }

    /**
     * A shot used to vanish into what it hit, so the dome's fire landed on nothing: a few motes of
     * the shot's own colour and a couple of shards of it where it struck the body.
     */
    private void hitSpark(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 at = target.getBoundingBox().getCenter().add(position()).scale(0.5D);
        serverLevel.sendParticles(new TintedParticleOptions(MagicalParticles.MOTE.get(), color(), 1.3F), at.x, at.y, at.z, HIT_MOTES, 0.12D, 0.12D, 0.12D, 0.06D);
        serverLevel.sendParticles(new TintedParticleOptions(MagicalParticles.SHARD.get(), color(), 1.0F), at.x, at.y, at.z, HIT_SHARDS, 0.08D, 0.08D, 0.08D, 0.12D);
    }

    /**
     * A shot that meets a block chips it. Not one born inside the ground: the dome fires from the
     * whole sphere, so half its shots start on the buried lower half and the clip reports them
     * inside the block they start in, where crumbs would only be thrown into solid stone.
     */
    private void chipBlock(BlockHitResult blockHit) {
        if (blockHit.isInside() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 at = blockHit.getLocation();
        BlockState struck = serverLevel.getBlockState(blockHit.getBlockPos());
        if (!struck.isAir()) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, struck), at.x, at.y, at.z, BLOCK_CRUMBS, 0.1D, 0.1D, 0.1D, 0.1D);
        }
        serverLevel.sendParticles(new TintedParticleOptions(MagicalParticles.MOTE.get(), color(), 1.1F), at.x, at.y, at.z, 2, 0.08D, 0.08D, 0.08D, 0.05D);
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
        entityData.set(DIR_X, tag.getFloat("DirX"));
        entityData.set(DIR_Y, tag.getFloat("DirY"));
        entityData.set(DIR_Z, tag.getFloat("DirZ"));
        entityData.set(LIFE, tag.getInt("Life"));
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
        tag.putFloat("DirX", entityData.get(DIR_X));
        tag.putFloat("DirY", entityData.get(DIR_Y));
        tag.putFloat("DirZ", entityData.get(DIR_Z));
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

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public Vec3 direction() {
        Vec3 direction = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return direction.lengthSqr() < 1.0E-5D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
    }

    private float damage() {
        return entityData.get(DAMAGE);
    }

    private int life() {
        return entityData.get(LIFE);
    }
}
