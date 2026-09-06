package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DivineDividerWaveEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_X = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Y = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Z = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(DivineDividerWaveEntity.class, EntityDataSerializers.INT);
    private static final int MAX_BLOCKS_SEVERED_PER_TICK = 384;
    private final Set<UUID> hitTargets = new HashSet<>();
    private final Set<BlockPos> severedBlocks = new HashSet<>();
    private UUID ownerUuid;

    public DivineDividerWaveEntity(EntityType<? extends DivineDividerWaveEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public DivineDividerWaveEntity(Level level, LivingEntity owner, MagicSkillResolvedStats stats) {
        this(MagicalEntities.DIVINE_DIVIDER_WAVE.get(), level);
        ownerUuid = owner.getUUID();
        Vec3 direction = owner.getLookAngle().normalize();
        setPos(owner.getX() + direction.x * 1.25D, owner.getEyeY() - 0.2D + direction.y * 1.25D, owner.getZ() + direction.z * 1.25D);
        entityData.set(DAMAGE, stats.damage());
        entityData.set(SPEED, waveSpeed(stats.speed()));
        entityData.set(WIDTH, 4.8F + stats.size() * 2.3F);
        entityData.set(HEIGHT, Math.max(100.0F, 60.0F + stats.size() * 13.5F));
        entityData.set(LIFE, Math.max(44, stats.durationTicks()));
        entityData.set(YAW, owner.getYRot());
        entityData.set(DIRECTION_X, (float) direction.x);
        entityData.set(DIRECTION_Y, (float) direction.y);
        entityData.set(DIRECTION_Z, (float) direction.z);
        entityData.set(COLOR, stats.definition().color());
    }

    public static DivineDividerWaveEntity scenarioWave(ServerLevel level, Vec3 position, Vec3 direction, MagicSkillResolvedStats stats) {
        DivineDividerWaveEntity wave = new DivineDividerWaveEntity(MagicalEntities.DIVINE_DIVIDER_WAVE.get(), level);
        Vec3 normalized = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        wave.setPos(position.x, position.y, position.z);
        wave.entityData.set(DAMAGE, stats.damage());
        wave.entityData.set(SPEED, waveSpeed(stats.speed()));
        wave.entityData.set(WIDTH, 4.8F + stats.size() * 2.3F);
        wave.entityData.set(HEIGHT, Math.max(100.0F, 60.0F + stats.size() * 13.5F));
        wave.entityData.set(LIFE, Math.max(44, stats.durationTicks()));
        wave.entityData.set(YAW, yawFromDirection(normalized));
        wave.entityData.set(DIRECTION_X, (float) normalized.x);
        wave.entityData.set(DIRECTION_Y, (float) normalized.y);
        wave.entityData.set(DIRECTION_Z, (float) normalized.z);
        wave.entityData.set(COLOR, stats.definition().color());
        return wave;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 24.0F);
        builder.define(SPEED, 1.1F);
        builder.define(WIDTH, 12.0F);
        builder.define(HEIGHT, 100.0F);
        builder.define(LIFE, 52);
        builder.define(YAW, 0.0F);
        builder.define(DIRECTION_X, 0.0F);
        builder.define(DIRECTION_Y, 0.0F);
        builder.define(DIRECTION_Z, 1.0F);
        builder.define(COLOR, 0xF8FCFF);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(direction().scale(speed()));
        if (!level().isClientSide()) {
            offerApproachCounters();
            damageTargets();
            severBlocks();
        }
        setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
        if (tickCount > life()) {
            discard();
        }
    }

    private void severBlocks() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 right = horizontalRight(yaw());
        Vec3 forward = horizontalForward(yaw());
        BlockPos center = blockPosition();
        int halfHeight = 12;
        int centerHalfWidth = 1;
        int severed = 0;
        for (int dy = 0; dy <= halfHeight && severed < MAX_BLOCKS_SEVERED_PER_TICK; dy++) {
            int layerHalfWidth = taperedHalfWidth(dy, halfHeight, centerHalfWidth);
            severed += severLayer(serverLevel, center.getY() + dy, center, right, forward, layerHalfWidth, MAX_BLOCKS_SEVERED_PER_TICK - severed);
            if (dy > 0 && severed < MAX_BLOCKS_SEVERED_PER_TICK) {
                severed += severLayer(serverLevel, center.getY() - dy, center, right, forward, layerHalfWidth, MAX_BLOCKS_SEVERED_PER_TICK - severed);
            }
        }
    }

    private static int taperedHalfWidth(int distanceFromCenter, int halfHeight, int centerHalfWidth) {
        float remaining = 1.0F - distanceFromCenter / (float) Math.max(1, halfHeight);
        return Math.max(0, Mth.ceil(centerHalfWidth * remaining - 0.15F));
    }

    private int severLayer(ServerLevel serverLevel, int y, BlockPos center, Vec3 right, Vec3 forward, int halfWidth, int remaining) {
        int severed = 0;
        for (int offset = -halfWidth; offset <= halfWidth && severed < remaining; offset++) {
            if (trySeverBlock(serverLevel, BlockPos.containing(center.getX() + 0.5D + right.x * offset, y, center.getZ() + 0.5D + right.z * offset))) {
                severed++;
            }
            if (severed >= remaining) {
                break;
            }
            if (trySeverBlock(serverLevel, BlockPos.containing(center.getX() + 0.5D + forward.x * offset, y, center.getZ() + 0.5D + forward.z * offset))) {
                severed++;
            }
        }
        return severed;
    }

    private boolean trySeverBlock(ServerLevel serverLevel, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!severedBlocks.add(immutable)) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(immutable);
        if (!canSeverBlock(serverLevel, immutable, state)) {
            return false;
        }
        serverLevel.levelEvent(2001, immutable, Block.getId(state));
        serverLevel.setBlock(immutable, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private static boolean canSeverBlock(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || serverLevel.getBlockEntity(pos) != null) {
            return false;
        }
        float hardness = state.getDestroySpeed(serverLevel, pos);
        return hardness >= 0.0F && hardness <= 50.0F;
    }

    private static Vec3 horizontalRight(float yaw) {
        float radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(radians), 0.0D, Mth.sin(radians)).normalize();
    }

    private static Vec3 horizontalForward(float yaw) {
        float radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians)).normalize();
    }

    private void offerApproachCounters() {
        Entity owner = ownerEntity();
        Vec3 forward = direction();
        double hitRadius = width() * 1.25D;
        double lookahead = hitRadius + speed() * MagicCounterService.QTE_WINDOW_TICKS + 8.0D;
        AABB area = new AABB(
                getX() - lookahead,
                getY() - height() * 0.58D,
                getZ() - lookahead,
                getX() + lookahead,
                getY() + height() * 0.58D,
                getZ() + lookahead);
        for (Entity entity : level().getEntities(this, area, target -> target instanceof ServerPlayer && target.isAlive() && target != owner)) {
            ServerPlayer player = (ServerPlayer) entity;
            if (hitTargets.contains(player.getUUID())) {
                continue;
            }
            Vec3 toTarget = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D).subtract(position());
            double forwardDistance = toTarget.dot(forward);
            if (forwardDistance <= hitRadius + 3.0D || forwardDistance > lookahead) {
                continue;
            }
            double perpendicularSqr = toTarget.subtract(forward.scale(forwardDistance)).lengthSqr();
            double counterWidth = hitRadius + 2.0D;
            if (perpendicularSqr <= counterWidth * counterWidth) {
                MagicCounterService.offerCounter(player, this, player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D));
            }
        }
    }

    private void damageTargets() {
        Entity owner = ownerEntity();
        double sideReach = width() * 1.25D;
        double impactThickness = Math.max(1.6D, speed() + 0.8D);
        double hitboxHalfHeight = height() * 0.58D;
        double broad = sideReach + impactThickness + 1.0D;
        AABB area = new AABB(
                getX() - broad,
                getY() - hitboxHalfHeight,
                getZ() - broad,
                getX() + broad,
                getY() + hitboxHalfHeight,
                getZ() + broad);
        Vec3 pushDirection = direction();
        Vec3 right = horizontalRight(yaw());
        for (Entity entity : level().getEntities(this, area, target -> target instanceof LivingEntity && target.isAlive() && target != owner)) {
            Vec3 toTarget = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).subtract(position());
            double forwardDistance = Math.abs(toTarget.dot(pushDirection));
            double lateralDistance = Math.abs(toTarget.dot(right));
            if (forwardDistance > impactThickness || lateralDistance > sideReach) {
                continue;
            }
            if (entity instanceof ServerPlayer player && MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
            if (!hitTargets.add(entity.getUUID())) {
                continue;
            }
            applyHit(entity, owner, pushDirection);
        }
    }

    private void applyHit(Entity entity, Entity owner, Vec3 pushDirection) {
        MagicDamageService.hurt(entity, damageSources().indirectMagic(this, owner == null ? this : owner), damage(), MagicContent.DIVINE_DIVIDER.id());
        entity.push(pushDirection.x * 1.25D, 0.08D, pushDirection.z * 1.25D);
    }

    private static float waveSpeed(float resolvedSpeed) {
        return Mth.clamp(0.8F + resolvedSpeed * 0.5F, 1.1F, 2.35F);
    }

    private static float yawFromDirection(Vec3 direction) {
        return (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
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
        entityData.set(SPEED, tag.getFloat("Speed"));
        entityData.set(WIDTH, tag.getFloat("Width"));
        entityData.set(HEIGHT, tag.getFloat("Height"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(YAW, tag.getFloat("Yaw"));
        entityData.set(DIRECTION_X, tag.getFloat("DirectionX"));
        entityData.set(DIRECTION_Y, tag.getFloat("DirectionY"));
        entityData.set(DIRECTION_Z, tag.getFloat("DirectionZ"));
        entityData.set(COLOR, tag.getInt("Color"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Damage", damage());
        tag.putFloat("Speed", speed());
        tag.putFloat("Width", width());
        tag.putFloat("Height", height());
        tag.putInt("Life", life());
        tag.putFloat("Yaw", yaw());
        tag.putFloat("DirectionX", (float) direction().x);
        tag.putFloat("DirectionY", (float) direction().y);
        tag.putFloat("DirectionZ", (float) direction().z);
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

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public net.minecraft.resources.ResourceLocation counterSkillId() {
        return MagicContent.DIVINE_DIVIDER.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return MagicContent.DIVINE_DIVIDER.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, color(), counterSkill.color());
        discard();
    }

    public float damage() {
        return entityData.get(DAMAGE);
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

    public float yaw() {
        return entityData.get(YAW);
    }

    public Vec3 direction() {
        Vec3 direction = new Vec3(entityData.get(DIRECTION_X), entityData.get(DIRECTION_Y), entityData.get(DIRECTION_Z));
        if (direction.lengthSqr() < 1.0E-6D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    public int color() {
        return entityData.get(COLOR);
    }
}
