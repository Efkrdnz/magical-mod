package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalChunkTickets;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SovereignAegisEntity extends Entity {
    public static final int MODE_ULTIMATE_PROTECTION = 0;
    public static final int MODE_SANCTUARY = 1;
    public static final int MODE_PERFECT_SEAL = 2;

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MANA_PER_HIT = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MANA_COOLDOWN = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HIT_FLASH = SynchedEntityData.defineId(SovereignAegisEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private UUID targetUuid;
    private Vec3 sealCenter = Vec3.ZERO;
    private int manaCooldownTicks;
    private int forcedChunkX = Integer.MIN_VALUE;
    private int forcedChunkZ = Integer.MIN_VALUE;
    private boolean forcedChunkActive;
    private boolean targetNoAiCaptured;
    private boolean targetHadNoAi;

    public SovereignAegisEntity(EntityType<? extends SovereignAegisEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SovereignAegisEntity createUltimate(ServerLevel level, ServerPlayer owner, MagicSkillResolvedStats stats) {
        SovereignAegisEntity entity = create(level, owner, owner, MODE_ULTIMATE_PROTECTION, 1.6F + stats.size() * 0.28F, -1);
        entity.entityData.set(MANA_PER_HIT, Math.max(12, Math.round(stats.manaCost() * 0.44F)));
        entity.entityData.set(MANA_COOLDOWN, Math.max(8, 16 - stats.tuning().speed()));
        return entity;
    }

    public static SovereignAegisEntity createSanctuary(ServerLevel level, ServerPlayer owner, MagicSkillResolvedStats stats) {
        return create(level, owner, null, MODE_SANCTUARY, 5.5F + stats.size() * 1.7F, Math.max(100, stats.durationTicks() + 120));
    }

    public static SovereignAegisEntity createSeal(ServerLevel level, ServerPlayer owner, LivingEntity target, MagicSkillResolvedStats stats) {
        closeSealFor(level, owner.getUUID());
        SovereignAegisEntity entity = create(level, owner, target, MODE_PERFECT_SEAL, 2.4F + stats.size() * 0.45F, -1);
        entity.sealCenter = target.position();
        entity.markTargetPersistent(target);
        entity.captureSealedMobAiState(target);
        entity.suppressSealedTargetAi(target);
        entity.ensureSealChunkTicket(level);
        return entity;
    }

    private static SovereignAegisEntity create(ServerLevel level, ServerPlayer owner, Entity target, int mode, float radius, int life) {
        SovereignAegisEntity entity = new SovereignAegisEntity(MagicalEntities.SOVEREIGN_AEGIS.get(), level);
        entity.ownerUuid = owner.getUUID();
        entity.targetUuid = target == null ? null : target.getUUID();
        entity.entityData.set(MODE, mode);
        entity.entityData.set(TARGET_ID, target == null ? -1 : target.getId());
        entity.entityData.set(RADIUS, radius);
        entity.entityData.set(LIFE, life);
        Vec3 position = target == null ? owner.position() : target.position();
        entity.setPos(position.x, position.y + 0.05D, position.z);
        return entity;
    }

    public static boolean toggleUltimate(ServerLevel level, ServerPlayer owner, MagicSkillResolvedStats stats) {
        SovereignAegisEntity active = activeUltimate(owner);
        if (active != null) {
            active.discard();
            level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.55F, 1.45F);
            return false;
        }
        level.addFreshEntity(createUltimate(level, owner, stats));
        level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.7F);
        level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.2F);
        return true;
    }

    public static boolean hasUltimate(ServerPlayer owner) {
        return activeUltimate(owner) != null;
    }

    public static float rewriteIncomingDamage(LivingEntity target, DamageSource source, float damage) {
        if (damage <= 0.0F) {
            return damage;
        }
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (isSealed(target) || isSealed(attacker) || isSealed(direct)) {
            return 0.0F;
        }
        boolean judgementDamage = isJudgementDamage(source);
        if (target instanceof ServerPlayer player) {
            SovereignAegisEntity ultimate = activeUltimate(player);
            if (ultimate != null && !judgementDamage) {
                Entity impactSource = attacker == null ? direct : attacker;
                boolean melee = attacker != null && (direct == null || direct == attacker);
                return ultimate.tryUltimateProtection(player, impactSource, melee) ? 0.0F : damage;
            }
        }
        if (!judgementDamage && protectedBySanctuary(target, attacker == null ? direct : attacker)) {
            return 0.0F;
        }
        if (attacker instanceof ServerPlayer player && isInsideOffenseBlockingSanctuary(player)) {
            return 0.0F;
        }
        return damage;
    }

    private static boolean isJudgementDamage(DamageSource source) {
        return source.getDirectEntity() instanceof JudgementBeamEntity || source.getEntity() instanceof JudgementBeamEntity;
    }

    public static boolean isInsideOffenseBlockingSanctuary(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        for (SovereignAegisEntity aegis : level.getEntitiesOfClass(SovereignAegisEntity.class, entity.getBoundingBox().inflate(32.0D), candidate -> candidate.mode() == MODE_SANCTUARY)) {
            if (aegis.distanceToSqr(entity) <= aegis.radius() * aegis.radius()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSealed(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return !level.getEntitiesOfClass(SovereignAegisEntity.class, entity.getBoundingBox().inflate(6.0D),
                candidate -> candidate.mode() == MODE_PERFECT_SEAL && entity.getUUID().equals(candidate.targetUuid)).isEmpty();
    }

    public static boolean closeSealForTarget(ServerLevel level, ServerPlayer owner, Entity target) {
        if (target == null) {
            return false;
        }
        boolean closed = false;
        for (SovereignAegisEntity aegis : level.getEntitiesOfClass(SovereignAegisEntity.class, target.getBoundingBox().inflate(8.0D),
                candidate -> candidate.mode() == MODE_PERFECT_SEAL && owner.getUUID().equals(candidate.ownerUuid) && target.getUUID().equals(candidate.targetUuid))) {
            aegis.discard();
            closed = true;
        }
        if (closed) {
            level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.18F);
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 0.62F);
        }
        return closed;
    }

    private static boolean protectedBySanctuary(LivingEntity target, Entity attacker) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        for (SovereignAegisEntity aegis : level.getEntitiesOfClass(SovereignAegisEntity.class, target.getBoundingBox().inflate(32.0D), candidate -> candidate.mode() == MODE_SANCTUARY)) {
            boolean targetInside = aegis.distanceToSqr(target) <= aegis.radius() * aegis.radius();
            boolean attackerInside = attacker != null && aegis.distanceToSqr(attacker) <= aegis.radius() * aegis.radius();
            if (targetInside && !attackerInside) {
                return true;
            }
        }
        return false;
    }

    private static SovereignAegisEntity activeUltimate(ServerPlayer player) {
        AABB search = player.getBoundingBox().inflate(4.0D);
        for (SovereignAegisEntity aegis : player.serverLevel().getEntitiesOfClass(SovereignAegisEntity.class, search,
                candidate -> candidate.mode() == MODE_ULTIMATE_PROTECTION && player.getUUID().equals(candidate.ownerUuid))) {
            return aegis;
        }
        return null;
    }

    private static void closeSealFor(ServerLevel level, UUID ownerUuid) {
        for (SovereignAegisEntity aegis : level.getEntitiesOfClass(SovereignAegisEntity.class, new AABB(-3.0E7D, -4096.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D),
                candidate -> candidate.mode() == MODE_PERFECT_SEAL && ownerUuid.equals(candidate.ownerUuid))) {
            aegis.discard();
        }
    }

    private boolean tryUltimateProtection(ServerPlayer player, Entity impactSource, boolean melee) {
        entityData.set(HIT_FLASH, 12);
        if (melee && impactSource != null && impactSource != player) {
            pushBackAttacker(player, impactSource);
        }
        if (manaCooldownTicks > 0) {
            level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.42F, 1.9F);
            return true;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.spendMana(manaPerHit())) {
            discard();
            state.sync(player);
            level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.75F, 0.82F);
            return false;
        }
        state.sync(player);
        manaCooldownTicks = manaCooldown();
        level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.55F, 2.0F);
        return true;
    }

    private static void pushBackAttacker(ServerPlayer player, Entity attacker) {
        Vec3 direction = attacker.position().subtract(player.position());
        double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalLength < 0.001D) {
            Vec3 look = player.getLookAngle();
            direction = new Vec3(-look.x, 0.0D, -look.z);
            horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        }
        if (horizontalLength < 0.001D) {
            return;
        }
        attacker.push(direction.x / horizontalLength * 0.72D, 0.16D, direction.z / horizontalLength * 0.72D);
        attacker.hasImpulse = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, MODE_ULTIMATE_PROTECTION);
        builder.define(TARGET_ID, -1);
        builder.define(RADIUS, 3.0F);
        builder.define(LIFE, 100);
        builder.define(MANA_PER_HIT, 14);
        builder.define(MANA_COOLDOWN, 12);
        builder.define(HIT_FLASH, 0);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (manaCooldownTicks > 0) {
            manaCooldownTicks--;
        }
        if (hitFlash() > 0) {
            entityData.set(HIT_FLASH, hitFlash() - 1);
        }
        if (!level().isClientSide()) {
            tickServer();
        }
    }

    private void tickServer() {
        if (mode() == MODE_ULTIMATE_PROTECTION) {
            Entity owner = ownerEntity();
            if (owner == null || owner.isRemoved()) {
                discard();
                return;
            }
            if (owner instanceof ServerPlayer player) {
                cleanseUltimateProtectionStates(player);
            }
            setPos(owner.getX(), owner.getY(0.52D), owner.getZ());
            return;
        }
        if (mode() == MODE_PERFECT_SEAL) {
            Entity target = targetEntity();
            if (!(target instanceof LivingEntity living) || target.isRemoved() || !living.isAlive()) {
                discard();
                return;
            }
            markTargetPersistent(living);
            captureSealedMobAiState(living);
            suppressSealedTargetAi(living);
            ensureSealChunkTicket((ServerLevel) level());
            if (target.distanceToSqr(sealCenter) > 0.05D) {
                target.teleportTo(sealCenter.x, sealCenter.y, sealCenter.z);
            }
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0.0F;
            setPos(sealCenter.x, sealCenter.y + 0.05D, sealCenter.z);
            return;
        }
        if (life() > 0 && tickCount > life()) {
            discard();
        }
    }

    private Entity targetEntity() {
        Entity byId = level().getEntity(entityData.get(TARGET_ID));
        if (byId != null) {
            return byId;
        }
        if (!(level() instanceof ServerLevel level) || targetUuid == null) {
            return null;
        }
        return level.getEntity(targetUuid);
    }

    private Entity ownerEntity() {
        if (!(level() instanceof ServerLevel level) || ownerUuid == null) {
            return null;
        }
        return level.getEntity(ownerUuid);
    }

    private static void cleanseUltimateProtectionStates(ServerPlayer player) {
        player.clearFire();
        player.setTicksFrozen(0);
        for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    private void markTargetPersistent(Entity target) {
        if (target instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    private void captureSealedMobAiState(Entity target) {
        if (!targetNoAiCaptured && target instanceof Mob mob) {
            targetHadNoAi = mob.isNoAi();
            targetNoAiCaptured = true;
        }
    }

    private void suppressSealedTargetAi(Entity target) {
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setLastHurtMob(null);
            mob.setLastHurtByMob(null);
            mob.setAggressive(false);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.goalSelector.getAvailableGoals().forEach(goal -> goal.stop());
            mob.targetSelector.getAvailableGoals().forEach(goal -> goal.stop());
            mob.setNoAi(true);
        }
    }

    private void restoreSealedTargetAi() {
        Entity target = targetEntity();
        if (targetNoAiCaptured && target instanceof Mob mob && !target.isRemoved()) {
            mob.setNoAi(targetHadNoAi);
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    private void ensureSealChunkTicket(ServerLevel level) {
        if (mode() != MODE_PERFECT_SEAL) {
            return;
        }
        ChunkPos chunk = new ChunkPos(BlockPos.containing(sealCenter));
        if (forcedChunkActive && forcedChunkX == chunk.x && forcedChunkZ == chunk.z) {
            return;
        }
        releaseSealChunkTicket();
        forcedChunkX = chunk.x;
        forcedChunkZ = chunk.z;
        forcedChunkActive = true;
        MagicalChunkTickets.SOVEREIGN_SEALS.forceChunk(level, getUUID(), forcedChunkX, forcedChunkZ, true, true);
    }

    private void releaseSealChunkTicket() {
        if (!forcedChunkActive || !(level() instanceof ServerLevel level)) {
            forcedChunkActive = false;
            return;
        }
        MagicalChunkTickets.SOVEREIGN_SEALS.forceChunk(level, getUUID(), forcedChunkX, forcedChunkZ, false, true);
        forcedChunkActive = false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            if (mode() == MODE_PERFECT_SEAL) {
                restoreSealedTargetAi();
            }
            releaseSealChunkTicket();
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        sealCenter = new Vec3(tag.getDouble("SealX"), tag.getDouble("SealY"), tag.getDouble("SealZ"));
        entityData.set(MODE, tag.getInt("Mode"));
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(MANA_PER_HIT, tag.getInt("ManaPerHit"));
        entityData.set(MANA_COOLDOWN, tag.getInt("ManaCooldown"));
        entityData.set(HIT_FLASH, tag.getInt("HitFlash"));
        manaCooldownTicks = tag.getInt("ManaCooldownTicks");
        targetNoAiCaptured = tag.getBoolean("TargetNoAiCaptured");
        targetHadNoAi = tag.getBoolean("TargetHadNoAi");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putDouble("SealX", sealCenter.x);
        tag.putDouble("SealY", sealCenter.y);
        tag.putDouble("SealZ", sealCenter.z);
        tag.putInt("Mode", mode());
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putFloat("Radius", radius());
        tag.putInt("Life", life());
        tag.putInt("ManaPerHit", manaPerHit());
        tag.putInt("ManaCooldown", manaCooldown());
        tag.putInt("HitFlash", hitFlash());
        tag.putInt("ManaCooldownTicks", manaCooldownTicks);
        tag.putBoolean("TargetNoAiCaptured", targetNoAiCaptured);
        tag.putBoolean("TargetHadNoAi", targetHadNoAi);
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int manaPerHit() {
        return entityData.get(MANA_PER_HIT);
    }

    public int manaCooldown() {
        return entityData.get(MANA_COOLDOWN);
    }

    public int hitFlash() {
        return entityData.get(HIT_FLASH);
    }

    public float fade(float partialTick) {
        if (life() < 0) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - (tickCount + partialTick) / Math.max(1.0F, life()), 0.0F, 1.0F);
    }
}
