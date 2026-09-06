package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.SphericalBlockRemover;
import com.efkrdnz.magical.registry.MagicalEntities;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
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

public final class SpaceSummonEntity extends Entity {
    public static final int DYING_NEUTRON_STAR = 0;
    public static final int WHITE_HOLE_PULSE = 1;
    public static final int EVENT_HORIZON = 2;
    private static final double NEUTRON_FALL_SPEED = 0.05D;
    private static final double NEUTRON_DROP_HEIGHT = 42.0D;
    private static final int NEUTRON_FULL_CHARGE_TICKS = 560;
    private static final int NEUTRON_NUKE_TICKS = 480;
    private static final int NEUTRON_BLOCKS_PER_TICK = 700;
    private static final int NEUTRON_SCAN_BUDGET = 6000;
    private static final int NEUTRON_SCAN_STEP = 4099;
    private static final float NEUTRON_NUKE_RADIUS = 42.0F;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLLAPSE_TICKS = SynchedEntityData.defineId(SpaceSummonEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;
    private int destroyCursor;

    public SpaceSummonEntity(EntityType<? extends SpaceSummonEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SpaceSummonEntity create(ServerLevel level, LivingEntity owner, int mode) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 target = eye.add(look.scale(24.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() != HitResult.Type.MISS) {
            target = hit.getLocation().subtract(look.scale(0.8D));
        }
        SpaceSummonEntity entity = new SpaceSummonEntity(MagicalEntities.SPACE_SUMMON.get(), level);
        entity.ownerUuid = owner.getUUID();
        int selectedMode = Math.floorMod(mode, 3);
        if (selectedMode == DYING_NEUTRON_STAR) {
            double spawnY = Math.min((level.getMaxY() + 1) - 6.0D, target.y + NEUTRON_DROP_HEIGHT);
            target = new Vec3(target.x, spawnY, target.z);
        }
        entity.setPos(target);
        entity.entityData.set(MODE, selectedMode);
        entity.entityData.set(CHARGE, selectedMode == DYING_NEUTRON_STAR ? NEUTRON_FULL_CHARGE_TICKS : selectedMode == WHITE_HOLE_PULSE ? 24 : 34);
        entity.entityData.set(LIFE, selectedMode == DYING_NEUTRON_STAR ? NEUTRON_FULL_CHARGE_TICKS + NEUTRON_NUKE_TICKS + 120 : selectedMode == EVENT_HORIZON ? 150 : 130);
        entity.entityData.set(RADIUS, selectedMode == DYING_NEUTRON_STAR ? NEUTRON_NUKE_RADIUS : selectedMode == EVENT_HORIZON ? 13.0F : 11.0F);
        entity.entityData.set(DAMAGE, selectedMode == DYING_NEUTRON_STAR ? 28.0F : 5.0F);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, DYING_NEUTRON_STAR);
        builder.define(LIFE, 130);
        builder.define(CHARGE, 34);
        builder.define(RADIUS, 11.0F);
        builder.define(DAMAGE, 6.0F);
        builder.define(COLLAPSE_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide() && mode() == DYING_NEUTRON_STAR) {
            tickClientNeutronVisuals();
        }
        if (!level().isClientSide()) {
            if (tickCount == 1) {
                level().playSound(null, blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.45F);
            }
            if (mode() == DYING_NEUTRON_STAR) {
                tickDyingNeutronStar();
                return;
            }
            if (tickCount > chargeTicks()) {
                tickActive();
            }
            if (tickCount > life()) {
                finish();
                discard();
            }
        }
    }

    private void tickClientNeutronVisuals() {
        float charge = chargeFactor();
        int sparks = 2 + Mth.floor(charge * 7.0F);
        for (int i = 0; i < sparks; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double height = (random.nextDouble() - 0.5D) * (1.4D + charge);
            double distance = 0.6D + random.nextDouble() * (2.0D + charge * 2.8D);
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            level().addParticle(ParticleTypes.END_ROD, getX() + x, getY() + height, getZ() + z, -x * 0.025D, -height * 0.015D, -z * 0.025D);
        }
        if (random.nextFloat() < 0.45F + charge * 0.45F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + random.nextDouble() * (2.0D + charge * 3.0D);
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(angle) * distance, getY() + (random.nextDouble() - 0.5D) * 2.0D, getZ() + Math.sin(angle) * distance, 0.0D, 0.0D, 0.0D);
        }
        if (collapseTicks() > 0 && random.nextFloat() < 0.8F) {
            level().addParticle(ParticleTypes.EXPLOSION, getX() + (random.nextDouble() - 0.5D) * 5.0D, getY() + (random.nextDouble() - 0.5D) * 2.0D, getZ() + (random.nextDouble() - 0.5D) * 5.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void tickDyingNeutronStar() {
        if (collapseTicks() > 0) {
            tickNeutronCollapse();
            return;
        }
        tickFallingGravity();
        if (hitGroundThisTick()) {
            if (tickCount < chargeTicks()) {
                fizzle();
                discard();
                return;
            }
            entityData.set(COLLAPSE_TICKS, 1);
            destroyCursor = 0;
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.28F);
            level().playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2.8F, 0.18F);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 4, 1.5D, 1.5D, 1.5D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 240, 4.0D, 2.0D, 4.0D, 0.35D);
            }
            return;
        }
        setPos(getX(), getY() - NEUTRON_FALL_SPEED, getZ());
        if (tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            float charge = chargeFactor();
            serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 24 + Mth.floor(charge * 46.0F), 1.2D + charge * 2.6D, 1.2D, 1.2D + charge * 2.6D, 0.05D + charge * 0.08D);
            level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.28F + charge * 0.32F, 0.35F + charge * 0.25F);
        }
    }

    private void tickFallingGravity() {
        float charge = chargeFactor();
        double pullRadius = 9.0D + charge * 24.0D;
        Entity owner = ownerEntity();
        AABB area = getBoundingBox().inflate(pullRadius);
        for (Entity entity : level().getEntities(this, area, target -> target.isAlive() && target != owner)) {
            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D);
            Vec3 offset = position().subtract(center);
            double distance = Math.max(0.45D, offset.length());
            if (distance > pullRadius) {
                continue;
            }
            double strength = Mth.clamp((pullRadius - distance) / pullRadius, 0.0D, 1.0D);
            Vec3 impulse = offset.normalize().scale((0.025D + charge * 0.09D) * strength);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.82D - charge * 0.18D).add(impulse));
            entity.hasImpulse = true;
            if (charge >= 0.85F && entity instanceof LivingEntity living && tickCount % 16 == Math.floorMod(entity.getId(), 16)) {
                living.hurt(damageSources().indirectMagic(this, owner == null ? this : owner), 3.0F + charge * 5.0F);
            }
        }
    }

    private boolean hitGroundThisTick() {
        return !level().noCollision(this, getBoundingBox().move(0.0D, -NEUTRON_FALL_SPEED, 0.0D));
    }

    private void fizzle() {
        level().playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 2.2F, 0.48F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 120, 2.2D, 1.5D, 2.2D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 90, 2.8D, 1.2D, 2.8D, 0.12D);
        }
    }

    private void tickNeutronCollapse() {
        int age = collapseTicks();
        float progress = Mth.clamp(age / (float) NEUTRON_NUKE_TICKS, 0.0F, 1.0F);
        damageCollapseEntities(progress);
        destroyCollapseBlocks(progress);
        if (level() instanceof ServerLevel serverLevel && age % 4 == 0) {
            double spread = 4.0D + radius() * progress * 0.65D;
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 10 + Mth.floor(progress * 35.0F), spread, spread * 0.35D, spread, 0.0D);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 60, spread * 0.55D, spread * 0.25D, spread * 0.55D, 0.2D);
        }
        if (age % 40 == 0) {
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.42F + progress * 0.15F);
        }
        if (age >= NEUTRON_NUKE_TICKS) {
            discard();
            return;
        }
        entityData.set(COLLAPSE_TICKS, age + 1);
    }

    private void damageCollapseEntities(float progress) {
        Entity owner = ownerEntity();
        double activeRadius = radius() * Mth.clamp(0.18F + progress * 0.9F, 0.18F, 1.0F);
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(activeRadius), target -> target.isAlive() && target != owner)) {
            Vec3 offset = entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D).subtract(position());
            double distance = Math.max(0.35D, offset.length());
            if (distance > activeRadius) {
                continue;
            }
            double strength = Mth.clamp(1.0D - distance / activeRadius, 0.0D, 1.0D);
            Vec3 pull = offset.normalize().scale(-0.5D - strength * 1.4D);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.22D).add(pull));
            entity.hasImpulse = true;
            if (entity instanceof LivingEntity living && tickCount % 5 == Math.floorMod(entity.getId(), 5)) {
                living.hurt(damageSources().indirectMagic(this, owner == null ? this : owner), damage() * (float) (0.25D + strength * 0.65D));
            }
        }
    }

    private void destroyCollapseBlocks(float progress) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int range = Mth.ceil(radius());
        int diameter = range * 2 + 1;
        int total = diameter * diameter * diameter;
        if (destroyCursor >= total) {
            return;
        }
        BlockPos origin = blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double blastRadiusSqr = radius() * radius();
        int removed = 0;
        int scanned = 0;
        while (destroyCursor < total && removed < NEUTRON_BLOCKS_PER_TICK && scanned < NEUTRON_SCAN_BUDGET) {
            int cursor = Math.floorMod((int) ((destroyCursor * (long) NEUTRON_SCAN_STEP) % total), total);
            destroyCursor++;
            scanned++;
            int dx = cursor % diameter - range;
            int dy = cursor / diameter % diameter - range;
            int dz = cursor / (diameter * diameter) - range;
            if (dx * dx + dy * dy + dz * dz > blastRadiusSqr) {
                continue;
            }
            mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
            if (!serverLevel.getBlockState(mutable).isAir()) {
                serverLevel.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
    }

    private void tickActive() {
        int mode = mode();
        double areaRadius = radius();
        Entity owner = ownerEntity();
        AABB area = getBoundingBox().inflate(areaRadius);
        for (Entity entity : level().getEntities(this, area, target -> target.isAlive() && target != owner)) {
            Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D);
            Vec3 offset = position().subtract(center);
            double distance = Math.max(0.35D, offset.length());
            if (distance > areaRadius) {
                continue;
            }
            double strength = Mth.clamp((areaRadius - distance) / areaRadius, 0.0D, 1.0D);
            Vec3 impulse = switch (mode) {
                case WHITE_HOLE_PULSE -> offset.normalize().scale(-0.08D - strength * 0.22D);
                case EVENT_HORIZON -> offset.normalize().scale(0.035D + strength * 0.09D);
                default -> offset.normalize().scale(0.06D + strength * 0.18D);
            };
            entity.setDeltaMovement(entity.getDeltaMovement().scale(mode == EVENT_HORIZON ? 0.52D : 0.76D).add(impulse));
            entity.hasImpulse = true;
            if (entity instanceof LivingEntity living && tickCount % (mode == DYING_NEUTRON_STAR ? 8 : 12) == Math.floorMod(entity.getId(), mode == DYING_NEUTRON_STAR ? 8 : 12)) {
                living.hurt(damageSources().indirectMagic(this, owner == null ? this : owner), damage() * (float) (0.45D + strength * 0.75D));
            }
        }
        if (mode == DYING_NEUTRON_STAR && tickCount % 18 == 0) {
            crushBlocks(radius() * 0.28F, 42);
        }
    }

    private void finish() {
        int mode = mode();
        Entity owner = ownerEntity();
        double burstRadius = radius() * (mode == WHITE_HOLE_PULSE ? 1.35D : 1.0D);
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(burstRadius), target -> target.isAlive() && target != owner)) {
            Vec3 offset = entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D).subtract(position());
            double distance = Math.max(0.4D, offset.length());
            if (distance > burstRadius) {
                continue;
            }
            double strength = Mth.clamp(1.0D - distance / burstRadius, 0.0D, 1.0D);
            Vec3 direction = offset.normalize();
            Vec3 impulse = mode == WHITE_HOLE_PULSE ? direction.scale(1.65D + strength * 1.8D) : direction.scale(-0.95D - strength * 1.4D);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2D).add(impulse));
            entity.hasImpulse = true;
            if (entity instanceof LivingEntity living) {
                living.hurt(damageSources().indirectMagic(this, owner == null ? this : owner), damage() * (float) (1.1D + strength * 1.5D));
            }
        }
        crushBlocks(radius() * (mode == WHITE_HOLE_PULSE ? 0.18F : 0.45F), mode == EVENT_HORIZON ? 1400 : 160);
        level().playSound(null, blockPosition(), mode == WHITE_HOLE_PULSE ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.6F, mode == WHITE_HOLE_PULSE ? 1.45F : 0.32F);
    }

    private void crushBlocks(float blockRadius, int maxRemoved) {
        if (!(level() instanceof ServerLevel serverLevel) || blockRadius <= 0.0F) {
            return;
        }
        SphericalBlockRemover.removeFromCenterOut(serverLevel, position(), blockRadius, maxRemoved);
    }

    private Entity ownerEntity() {
        return ownerUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(ownerUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(MODE, tag.getInt("Mode"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(CHARGE, tag.getInt("Charge"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(COLLAPSE_TICKS, tag.getInt("CollapseTicks"));
        destroyCursor = tag.getInt("DestroyCursor");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Mode", mode());
        tag.putInt("Life", life());
        tag.putInt("Charge", chargeTicks());
        tag.putFloat("Radius", radius());
        tag.putFloat("Damage", damage());
        tag.putInt("CollapseTicks", collapseTicks());
        tag.putInt("DestroyCursor", destroyCursor);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int chargeTicks() {
        return entityData.get(CHARGE);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public int collapseTicks() {
        return entityData.get(COLLAPSE_TICKS);
    }

    public float chargeFactor() {
        return Mth.clamp(tickCount / (float) Math.max(1, chargeTicks()), 0.0F, 1.0F);
    }

    public float fadeFactor() {
        return 1.0F - Mth.clamp((tickCount - chargeTicks()) / (float) Math.max(1, life() - chargeTicks()), 0.0F, 1.0F);
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
