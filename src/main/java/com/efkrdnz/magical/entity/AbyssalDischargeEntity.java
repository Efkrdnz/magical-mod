package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.service.Bodies;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AbyssalDischargeEntity extends Entity {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(AbyssalDischargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(AbyssalDischargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(AbyssalDischargeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AbyssalDischargeEntity.class, EntityDataSerializers.INT);
    private static final int WAVEFRONT_INTERVAL = 4;
    private static final int WAVEFRONT_MAX_POINTS = 8;
    private static final float WAVEFRONT_MIN_RADIUS = 3.5F;
    /** The wave's breath: a dark violet, lit by the world, so it reads as smoke rather than light. */
    private static final int WAVEFRONT_BREATH_RGB = 0x4B1C7A;
    /** Two thirds of a block when it rolls out, and it grows: at 3.2 it was a dark card as wide as a door. */
    private static final float WAVEFRONT_BREATH_SCALE = 2.0F;
    /** The corrosion's sparks: full bright, one on every other point of the front. */
    private static final int WAVEFRONT_SPARK_RGB = 0xA66BFF;
    private static final float WAVEFRONT_SPARK_SCALE = 1.3F;
    private final Set<UUID> corrodedTargets = new HashSet<>();
    private UUID ownerUuid;

    public AbyssalDischargeEntity(EntityType<? extends AbyssalDischargeEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static AbyssalDischargeEntity create(ServerLevel level, LivingEntity owner, MagicSkillResolvedStats stats) {
        AbyssalDischargeEntity discharge = new AbyssalDischargeEntity(MagicalEntities.ABYSSAL_DISCHARGE.get(), level);
        discharge.ownerUuid = owner.getUUID();
        discharge.setPos(owner.getX(), owner.getY() + 0.04D, owner.getZ());
        discharge.entityData.set(DAMAGE, stats.damage());
        discharge.entityData.set(RADIUS, 6.0F + stats.size() * 1.45F);
        discharge.entityData.set(LIFE, Math.max(30, stats.durationTicks()));
        discharge.entityData.set(COLOR, stats.definition().color());
        return discharge;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 16.0F);
        builder.define(RADIUS, 12.0F);
        builder.define(LIFE, 36);
        builder.define(COLOR, 0x341052);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide()) {
            corrodeTargets();
        }
        if (tickCount > life()) {
            discard();
        }
    }

    private void corrodeTargets() {
        Entity owner = ownerEntity();
        float expansion = Mth.clamp((tickCount - 2.0F) / Math.max(1.0F, life() * 0.48F), 0.0F, 1.0F);
        float currentRadius = radius() * expansion;
        if (currentRadius <= 0.05F) {
            return;
        }
        if (expansion < 1.0F && tickCount % WAVEFRONT_INTERVAL == 0 && level() instanceof ServerLevel serverLevel) {
            scourWavefront(serverLevel, currentRadius);
        }
        AABB area = new AABB(
                getX() - currentRadius,
                getY() - 2.0D,
                getZ() - currentRadius,
                getX() + currentRadius,
                getY() + 4.0D,
                getZ() + currentRadius);
        for (Entity entity : Bodies.around(level(), this, area, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            double distance = entity.position().distanceTo(position());
            if (distance > currentRadius || !corrodedTargets.add(entity.getUUID())) {
                continue;
            }
            LivingEntity target = (LivingEntity) entity;
            float falloff = (float) Mth.clamp(1.0D - distance / Math.max(0.1D, radius()), 0.35D, 1.0D);
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, owner == null ? this : owner), damage() * falloff, MagicContent.ABYSSAL_DISCHARGE.id());
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 170, 2, false, true), owner);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 190, 2, false, true), owner);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 150, 3, false, true), owner);
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 180, 2, false, true), owner);
            Vec3 push = target.position().subtract(position());
            if (push.lengthSqr() > 1.0E-6D) {
                push = push.normalize().scale(0.28D + falloff * 0.35D);
                target.push(push.x, 0.05D + falloff * 0.08D, push.z);
            }
        }
    }

    /**
     * The ring is drawn on the ground and nothing it passed over used to move. At the front of the
     * wave a dark violet breath rolls outward along the floor, every other point throws up the
     * ground it scours and the rest lift a violet spark of the corrosion. It used to roll squid ink,
     * which is an opaque black square half a block wide and read as black blocks hanging beside the
     * target rather than a wave. Nothing closer than {@link #WAVEFRONT_MIN_RADIUS}: nearer than that
     * the breath rolls through the bottom of the caster's own view.
     */
    private void scourWavefront(ServerLevel serverLevel, float currentRadius) {
        if (currentRadius < WAVEFRONT_MIN_RADIUS) {
            return;
        }
        int points = Mth.clamp(Math.round(currentRadius * 0.8F), 6, WAVEFRONT_MAX_POINTS);
        double phase = serverLevel.random.nextDouble() * Mth.TWO_PI;
        TintedParticleOptions breath = new TintedParticleOptions(MagicalParticles.WISP.get(), WAVEFRONT_BREATH_RGB, WAVEFRONT_BREATH_SCALE);
        TintedParticleOptions spark = new TintedParticleOptions(MagicalParticles.MOTE.get(), WAVEFRONT_SPARK_RGB, WAVEFRONT_SPARK_SCALE);
        for (int i = 0; i < points; i++) {
            double angle = phase + Mth.TWO_PI * i / points;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double x = getX() + dx * currentRadius;
            double z = getZ() + dz * currentRadius;
            // count 0: one particle with exactly this velocity, outward the way the wave is going
            serverLevel.sendParticles(breath, x, getY() + 0.25D, z, 0, dx, 0.05D, dz, 0.16D);
            if (i % 2 == 0) {
                BlockState ground = serverLevel.getBlockState(BlockPos.containing(x, getY() - 0.3D, z));
                if (!ground.isAir()) {
                    serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, getY() + 0.1D, z, 2, 0.2D, 0.05D, 0.2D, 0.15D);
                }
            } else {
                serverLevel.sendParticles(spark, x, getY() + 0.3D, z, 0, dx * 0.4D, 1.0D, dz * 0.4D, 0.08D);
            }
        }
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(COLOR, tag.getInt("Color"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Radius", radius());
        tag.putInt("Life", life());
        tag.putInt("Color", color());
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

    public int life() {
        return entityData.get(LIFE);
    }

    public int color() {
        return entityData.get(COLOR);
    }
}
