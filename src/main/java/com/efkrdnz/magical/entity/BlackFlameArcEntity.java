package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
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

public final class BlackFlameArcEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> KNOCKBACK = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIRECTION_X = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Y = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Z = SynchedEntityData.defineId(BlackFlameArcEntity.class, EntityDataSerializers.FLOAT);
    private final Set<UUID> hitTargets = new HashSet<>();
    private UUID ownerUuid;

    public BlackFlameArcEntity(EntityType<? extends BlackFlameArcEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static BlackFlameArcEntity create(ServerLevel level, LivingEntity owner, float damage, float knockback) {
        BlackFlameArcEntity arc = new BlackFlameArcEntity(MagicalEntities.BLACK_FLAME_ARC.get(), level);
        Vec3 direction = owner.getLookAngle().normalize();
        arc.ownerUuid = owner.getUUID();
        arc.setPos(owner.getX() + direction.x * 1.15D, owner.getEyeY() - 0.12D + direction.y * 1.15D, owner.getZ() + direction.z * 1.15D);
        arc.entityData.set(DAMAGE, Math.max(1.0F, damage));
        arc.entityData.set(KNOCKBACK, knockback);
        arc.entityData.set(SPEED, 1.58F);
        arc.entityData.set(WIDTH, 3.2F);
        arc.entityData.set(HEIGHT, 2.45F);
        arc.entityData.set(LIFE, 22);
        arc.entityData.set(DIRECTION_X, (float) direction.x);
        arc.entityData.set(DIRECTION_Y, (float) direction.y);
        arc.entityData.set(DIRECTION_Z, (float) direction.z);
        return arc;
    }

    public static BlackFlameArcEntity scenarioArc(ServerLevel level, Vec3 position, Vec3 direction, float damage, float knockback) {
        BlackFlameArcEntity arc = new BlackFlameArcEntity(MagicalEntities.BLACK_FLAME_ARC.get(), level);
        Vec3 normalized = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        arc.setPos(position.x, position.y, position.z);
        arc.entityData.set(DAMAGE, Math.max(1.0F, damage));
        arc.entityData.set(KNOCKBACK, knockback);
        arc.entityData.set(SPEED, 1.58F);
        arc.entityData.set(WIDTH, 3.2F);
        arc.entityData.set(HEIGHT, 2.45F);
        arc.entityData.set(LIFE, 28);
        arc.entityData.set(DIRECTION_X, (float) normalized.x);
        arc.entityData.set(DIRECTION_Y, (float) normalized.y);
        arc.entityData.set(DIRECTION_Z, (float) normalized.z);
        return arc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 5.0F);
        builder.define(KNOCKBACK, 0.25F);
        builder.define(SPEED, 1.58F);
        builder.define(WIDTH, 3.2F);
        builder.define(HEIGHT, 2.45F);
        builder.define(LIFE, 22);
        builder.define(DIRECTION_X, 0.0F);
        builder.define(DIRECTION_Y, 0.0F);
        builder.define(DIRECTION_Z, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 movement = direction().scale(speed());
        setDeltaMovement(movement);
        if (!level().isClientSide()) {
            offerApproachCounters(movement);
            damageTargets(movement);
            if (tickCount % 5 == 0 && level() instanceof ServerLevel serverLevel) {
                serverLevel.addFreshEntity(BlackFlameFieldEntity.create(serverLevel, ownerEntity(), position().add(0.0D, -0.08D, 0.0D), 1.1F, Math.max(1.0F, damage() * 0.08F), 28, knockback() * 0.45F));
            }
        }
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
        if (tickCount > life()) {
            discard();
        }
    }

    private void damageTargets(Vec3 movement) {
        Entity owner = ownerEntity();
        Vec3 from = position();
        Vec3 to = from.add(movement);
        AABB area = new AABB(from, to).inflate(width() * 0.52D, height() * 0.45D, width() * 0.52D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity living && living.isAlive() && target != owner)) {
            if (!hitTargets.add(entity.getUUID())) {
                continue;
            }
            if (entity instanceof ServerPlayer player && MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
            MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage(), MagicContent.BLACK_FLAMES_IMBUE.id());
            if (entity instanceof LivingEntity living) {
                BlackFlameProjectileEntity.applyCorrosion(living, owner);
            }
            Vec3 push = direction().scale(0.18D + knockback() * 0.34D);
            entity.push(push.x, 0.04D + knockback() * 0.08D, push.z);
        }
    }

    private void offerApproachCounters(Vec3 movement) {
        Entity owner = ownerEntity();
        Vec3 forward = direction();
        Vec3 lookAhead = position().add(forward.scale(7.5D + speed() * MagicCounterService.QTE_WINDOW_TICKS));
        AABB area = new AABB(position(), lookAhead).inflate(width() * 0.72D, height() * 0.55D, width() * 0.72D);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof ServerPlayer player && player.isAlive() && target != owner)) {
            ServerPlayer player = (ServerPlayer) entity;
            if (hitTargets.contains(player.getUUID())) {
                continue;
            }
            Vec3 toPlayer = player.getEyePosition().subtract(position());
            if (movement.lengthSqr() > 1.0E-6D && toPlayer.dot(forward) < 1.2D) {
                continue;
            }
            MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.2D, 0.0D), 12);
        }
    }

    public Vec3 direction() {
        Vec3 direction = new Vec3(entityData.get(DIRECTION_X), entityData.get(DIRECTION_Y), entityData.get(DIRECTION_Z));
        return direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    public float yaw() {
        Vec3 direction = direction();
        return (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
    }

    public float pitch() {
        Vec3 direction = direction();
        return (float) (-Mth.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * Mth.RAD_TO_DEG);
    }

    public float damage() {
        return entityData.get(DAMAGE);
    }

    public float knockback() {
        return entityData.get(KNOCKBACK);
    }

    public float speed() {
        return entityData.get(SPEED);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public net.minecraft.resources.ResourceLocation counterSkillId() {
        return MagicContent.BLACK_FLAMES_IMBUE.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.BLACK_FLAMES_IMBUE.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, MagicContent.BLACK_FLAMES_IMBUE.color(), counterSkill.color());
        level.playSound(null, clashPosition.x, clashPosition.y, clashPosition.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.42F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(KNOCKBACK, tag.getFloat("Knockback"));
        entityData.set(SPEED, tag.getFloat("Speed"));
        entityData.set(WIDTH, tag.getFloat("Width"));
        entityData.set(HEIGHT, tag.getFloat("Height"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(DIRECTION_X, tag.getFloat("DirectionX"));
        entityData.set(DIRECTION_Y, tag.getFloat("DirectionY"));
        entityData.set(DIRECTION_Z, tag.getFloat("DirectionZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Knockback", knockback());
        tag.putFloat("Speed", speed());
        tag.putFloat("Width", width());
        tag.putFloat("Height", height());
        tag.putInt("Life", life());
        Vec3 direction = direction();
        tag.putFloat("DirectionX", (float) direction.x);
        tag.putFloat("DirectionY", (float) direction.y);
        tag.putFloat("DirectionZ", (float) direction.z);
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
