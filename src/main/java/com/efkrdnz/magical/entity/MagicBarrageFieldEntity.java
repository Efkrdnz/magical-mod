package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicBarrageService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillType;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

public final class MagicBarrageFieldEntity extends Entity {
    public static final int SHATTER_DURATION = 52;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE_SCALE = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FIRE_INTERVAL = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHATTER_TICKS = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST_SHOT_INDEX = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST_SHOT_TICK = SynchedEntityData.defineId(MagicBarrageFieldEntity.class, EntityDataSerializers.INT);
    private UUID ownerUuid;

    public MagicBarrageFieldEntity(EntityType<? extends MagicBarrageFieldEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static MagicBarrageFieldEntity create(ServerLevel level, ServerPlayer owner, float radius, MagicSkillResolvedStats stats) {
        MagicBarrageFieldEntity entity = new MagicBarrageFieldEntity(MagicalEntities.MAGIC_BARRAGE_FIELD.get(), level);
        entity.ownerUuid = owner.getUUID();
        entity.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        entity.entityData.set(RADIUS, Mth.clamp(radius, MagicBarrageService.MIN_RADIUS, MagicBarrageService.MAX_RADIUS));
        entity.entityData.set(DAMAGE_SCALE, Mth.clamp(0.18F + stats.damage() * 0.015F, 0.16F, 0.42F));
        entity.entityData.set(FIRE_INTERVAL, Mth.clamp(Math.round(6.0F - stats.speed() * 1.05F), 2, 6));
        entity.entityData.set(COLOR, stats.definition().color());
        entity.entityData.set(OWNER_ID, owner.getId());
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, MagicBarrageService.MIN_RADIUS);
        builder.define(DAMAGE_SCALE, 0.3F);
        builder.define(FIRE_INTERVAL, 6);
        builder.define(COLOR, 0x8E7BFF);
        builder.define(OWNER_ID, -1);
        builder.define(SHATTER_TICKS, -1);
        builder.define(LAST_SHOT_INDEX, -1);
        builder.define(LAST_SHOT_TICK, -1000);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            return;
        }
        if (isShattering()) {
            int ticks = shatterTicks() + 1;
            entityData.set(SHATTER_TICKS, ticks);
            if (ticks >= SHATTER_DURATION) {
                discard();
            }
            return;
        }
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (owner.distanceToSqr(position()) > radius() * radius()) {
            close(true);
            return;
        }
        if (tickCount % 20 == 1 && !drainMana(owner)) {
            close(true);
            return;
        }
        sealBoundary(owner);
        if (tickCount % fireInterval() == 0) {
            fireVolley(owner);
        }
        if (tickCount % 60 == 0) {
            owner.serverLevel().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 0.55F + random.nextFloat() * 0.25F);
        }
    }

    public void close(boolean applyCooldown) {
        if (isShattering()) {
            return;
        }
        if (level() instanceof ServerLevel) {
            ServerPlayer owner = owner();
            if (owner != null) {
                MagicBarrageService.finishField(owner, this, applyCooldown);
                owner.serverLevel().playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.85F, 0.72F + random.nextFloat() * 0.16F);
                owner.serverLevel().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.45F, 0.62F);
            }
        }
        entityData.set(SHATTER_TICKS, 0);
    }

    private boolean drainMana(ServerPlayer owner) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        int drain = MagicBarrageService.manaDrainPerSecond(radius());
        boolean paid = MagicSinService.spendManaForSkill(owner, state, drain);
        state.sync(owner);
        return paid;
    }

    private void fireVolley(ServerPlayer owner) {
        List<LivingEntity> targets = targets(owner);
        if (targets.isEmpty()) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        List<MagicSkillDefinition> profiles = usableProfiles(state);
        if (profiles.isEmpty()) {
            return;
        }
        int shots = shotsForTargetCount(targets.size());
        for (int shot = 0; shot < shots; shot++) {
            LivingEntity target = targets.get((shot + random.nextInt(targets.size())) % targets.size());
            fireOne(owner, state, profiles, target);
        }
    }

    private void fireOne(ServerPlayer owner, PlayerMagicState state, List<MagicSkillDefinition> profiles, LivingEntity target) {
        MagicSkillDefinition definition = profiles.get(random.nextInt(profiles.size()));
        MagicSkillResolvedStats stats = definition.resolve(state.tuningFor(definition.id()));
        int count = MagicBarrageService.circleCountForRadius(radius());
        int index = random.nextInt(count);
        entityData.set(LAST_SHOT_INDEX, index);
        entityData.set(LAST_SHOT_TICK, tickCount);
        Vec3 start = position().add(surfacePoint(index, count, radius() * 0.94F));
        float damage = Math.max(1.5F, stats.damage() * damageScale() * (definition.tier() >= 4 ? 0.55F : 1.0F));
        float size = Mth.clamp(0.62F + stats.size() * 0.22F, 0.55F, 1.65F);
        if (random.nextInt(4) == 0) {
            Vec3 locked = target.getBoundingBox().getCenter();
            float beamDamage = Math.max(2.0F, damage * 1.18F);
            MagicBarrageBeamEntity beam = MagicBarrageBeamEntity.create(owner.serverLevel(), owner, start, locked, definition.id(), definition.color(), beamDamage, size);
            owner.serverLevel().addFreshEntity(beam);
            owner.serverLevel().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.16F, 1.85F + random.nextFloat() * 0.35F);
            return;
        }
        float speed = Mth.clamp(1.85F + stats.speed() * 0.34F, 1.6F, 2.85F);
        MagicBarrageShotEntity shot = MagicBarrageShotEntity.create(owner.serverLevel(), owner, start, target, definition.id(), definition.color(), damage, speed, size);
        owner.serverLevel().addFreshEntity(shot);
        owner.serverLevel().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.18F, 1.45F + random.nextFloat() * 0.45F);
    }

    private int shotsForTargetCount(int targetCount) {
        if (targetCount <= 1) {
            return 1;
        }
        int radiusBonus = radius() >= 18.0F ? 1 : 0;
        return Mth.clamp(1 + (targetCount + 1) / 3 + radiusBonus, 2, 7);
    }

    private void sealBoundary(ServerPlayer owner) {
        double radius = radius();
        double softEdge = radius * 0.78D;
        double hardEdge = radius + 1.35D;
        AABB area = new AABB(getX() - hardEdge, getY() - hardEdge, getZ() - hardEdge, getX() + hardEdge, getY() + hardEdge, getZ() + hardEdge);
        for (Entity entity : level().getEntities(this, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            Vec3 fromCenter = entity.position().subtract(position());
            double distance = fromCenter.length();
            if (distance < softEdge || distance > hardEdge || distance < 0.001D) {
                continue;
            }
            Vec3 inward = fromCenter.normalize().scale(-1.0D);
            double edgeProgress = Mth.clamp((distance - softEdge) / Math.max(0.1D, hardEdge - softEdge), 0.0D, 1.0D);
            Vec3 motion = entity.getDeltaMovement();
            double outwardSpeed = motion.dot(fromCenter.normalize());
            if (outwardSpeed > 0.0D) {
                motion = motion.subtract(fromCenter.normalize().scale(outwardSpeed * (0.85D + edgeProgress * 0.3D)));
            }
            Vec3 next = motion.add(inward.scale(0.08D + edgeProgress * 0.42D));
            entity.setDeltaMovement(next);
            entity.hasImpulse = true;
        }
    }

    private List<LivingEntity> targets(ServerPlayer owner) {
        AABB area = new AABB(getX() - radius(), getY() - radius(), getZ() - radius(), getX() + radius(), getY() + radius(), getZ() + radius());
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : level().getEntities(this, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            if (entity.position().distanceToSqr(position()) <= radius() * radius()) {
                targets.add((LivingEntity) entity);
            }
        }
        targets.sort(Comparator.comparingDouble(owner::distanceToSqr));
        int limit = Math.min(28, targets.size());
        return limit == targets.size() ? targets : new ArrayList<>(targets.subList(0, limit));
    }

    private List<MagicSkillDefinition> usableProfiles(PlayerMagicState state) {
        List<MagicSkillDefinition> profiles = new ArrayList<>();
        for (MagicSkillDefinition definition : MagicContent.allSkills()) {
            if (!state.hasUnlocked(definition.id())
                    || MagicContent.CIRCLE_ARSENAL.id().equals(definition.id())
                    || MagicContent.isAuthoritySkill(definition.id())
                    || definition.baseDamage() <= 0.0F
                    || definition.tier() < 0) {
                continue;
            }
            if (definition.type() == MagicSkillType.PROJECTILE || definition.type() == MagicSkillType.BURST) {
                profiles.add(definition);
            }
        }
        return profiles;
    }

    public static Vec3 surfacePoint(int index, int count, float radius) {
        float golden = 2.3999631F;
        float safeCount = Math.max(1.0F, count);
        float y = 1.0F - (index + 0.5F) * 2.0F / safeCount;
        float ring = Mth.sqrt(Math.max(0.0F, 1.0F - y * y));
        float angle = index * golden;
        return new Vec3(Mth.cos(angle) * ring * radius, y * radius, Mth.sin(angle) * ring * radius);
    }

    private int fireInterval() {
        return Mth.clamp(entityData.get(FIRE_INTERVAL), 2, 6);
    }

    private ServerPlayer owner() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(ownerUuid);
        return entity instanceof ServerPlayer player ? player : null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason != RemovalReason.DISCARDED) {
            ServerPlayer owner = owner();
            if (owner != null) {
                MagicBarrageService.finishField(owner, this, false);
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DAMAGE_SCALE, tag.getFloat("DamageScale"));
        entityData.set(FIRE_INTERVAL, tag.getInt("FireInterval"));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(OWNER_ID, tag.getInt("OwnerId"));
        entityData.set(SHATTER_TICKS, tag.contains("ShatterTicks") ? tag.getInt("ShatterTicks") : -1);
        entityData.set(LAST_SHOT_INDEX, tag.contains("LastShotIndex") ? tag.getInt("LastShotIndex") : -1);
        entityData.set(LAST_SHOT_TICK, tag.contains("LastShotTick") ? tag.getInt("LastShotTick") : -1000);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Radius", radius());
        tag.putFloat("DamageScale", damageScale());
        tag.putInt("FireInterval", fireInterval());
        tag.putInt("Color", color());
        tag.putInt("OwnerId", entityData.get(OWNER_ID));
        tag.putInt("ShatterTicks", shatterTicks());
        tag.putInt("LastShotIndex", lastShotIndex());
        tag.putInt("LastShotTick", lastShotTick());
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

    public int color() {
        return entityData.get(COLOR);
    }

    public float damageScale() {
        return entityData.get(DAMAGE_SCALE);
    }

    public int ownerId() {
        return entityData.get(OWNER_ID);
    }

    public int shatterTicks() {
        return entityData.get(SHATTER_TICKS);
    }

    public boolean isShattering() {
        return shatterTicks() >= 0;
    }

    public int lastShotIndex() {
        return entityData.get(LAST_SHOT_INDEX);
    }

    public int lastShotTick() {
        return entityData.get(LAST_SHOT_TICK);
    }

    public int activeTicks() {
        return tickCount;
    }
}
