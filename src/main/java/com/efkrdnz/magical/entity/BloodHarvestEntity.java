package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
import com.efkrdnz.magical.magic.passive.BloodPassives;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The blood a blood mage's kill leaves behind, and the way it comes to them.
 *
 * <p>It pools at the corpse; when its owner comes within reach it lifts and streams into their
 * chest, and the Vessel fills as it lands. This entity is the pool - the only record of it, so Vein
 * Walk, the renderer and the payout cannot disagree about whether a pool is still there. Not saved
 * to disk, on purpose: spilled blood does not survive a relog, exactly as the motes it replaces did
 * not.
 *
 * <p>Invisible and untouchable, like every gameplay entity here. Everything seen is
 * {@code BloodHarvestRenderer}, reading the synced phase and the owner's live position.
 */
public final class BloodHarvestEntity extends Entity {

    public static final byte PHASE_POOLED = 0;
    public static final byte PHASE_STREAMING = 1;
    public static final byte PHASE_OVERFLOW = 2;

    /** Where on the owner the blood goes in, as a share of their height. */
    public static final double CHEST_HEIGHT = 0.6D;

    /** How near a pool Vein Walk's landing point has to be to spend it. */
    private static final double CONSUME_RADIUS = 1.0D;

    /** How far around a player their pools are looked for. Wider than any pull, so a cap counts all of them. */
    private static final double POOL_SEARCH = 256.0D;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> YIELD = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.BYTE);
    /** The tick this phase began, in this entity's own ticks, so the client needs no second clock. */
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;

    public BloodHarvestEntity(EntityType<? extends BloodHarvestEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ---- spawning -----------------------------------------------------------------------------

    /**
     * Spills {@code yield} worth of blood at {@code at} for {@code owner}, spending their oldest
     * pool first if they already have as many as the rules allow.
     */
    public static BloodHarvestEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at, int yield) {
        List<BloodHarvestEntity> pools = poolsOf(owner, level, POOL_SEARCH);
        if (pools.size() >= BloodHarvestRules.MAX_POOLS) {
            pools.stream().max((a, b) -> Integer.compare(a.tickCount, b.tickCount)).ifPresent(Entity::discard);
        }
        BloodHarvestEntity pool = new BloodHarvestEntity(MagicalEntities.BLOOD_HARVEST.get(), level);
        pool.setPos(at.x, at.y, at.z);
        pool.ownerUuid = owner.getUUID();
        pool.entityData.set(OWNER_ID, owner.getId());
        pool.entityData.set(YIELD, Math.max(0, yield));
        pool.entityData.set(SEED, level.random.nextInt(64));
        level.addFreshEntity(pool);
        return pool;
    }

    // ---- the pool, as Vein Walk sees it ----------------------------------------------------

    /** The furthest pool of the player's still within reach, which is where Vein Walk goes. */
    public static Optional<Vec3> furthestPool(ServerPlayer player, double reach) {
        Vec3 from = player.position();
        Vec3 best = null;
        double bestDistance = -1.0D;
        for (BloodHarvestEntity pool : poolsOf(player, player.serverLevel(), reach)) {
            double distance = from.distanceTo(pool.position());
            if (distance <= reach && distance > bestDistance) {
                bestDistance = distance;
                best = pool.position();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Spends the pool Vein Walk arrived at, so one pool of blood is not an infinite shuttle. */
    public static void consumePool(ServerPlayer player, Vec3 at) {
        for (BloodHarvestEntity pool : poolsOf(player, player.serverLevel(), CONSUME_RADIUS + 1.0D)) {
            if (pool.position().distanceToSqr(at) <= CONSUME_RADIUS * CONSUME_RADIUS) {
                pool.discard();
            }
        }
    }

    private static List<BloodHarvestEntity> poolsOf(ServerPlayer player, ServerLevel level, double within) {
        return level.getEntities(MagicalEntities.BLOOD_HARVEST.get(),
                player.getBoundingBox().inflate(within),
                pool -> pool.isOwnedBy(player) && pool.isPooled());
    }

    // ---- ticking ------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = serverOwner();
        switch (phase()) {
            case PHASE_POOLED -> tickPooled(level, owner);
            case PHASE_STREAMING -> tickStreaming(level, owner);
            default -> tickOverflow(owner);
        }
    }

    private void tickPooled(ServerLevel level, ServerPlayer owner) {
        if (tickCount >= BloodHarvestRules.POOL_LIFETIME) {
            discard();
            return;
        }
        if (owner == null || !BloodHarvestRules.canLift(tickCount)) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        boolean bloodscent = ClassPassiveEffects.on(state, MagicPassiveContent.BLOODSCENT.id());
        double distance = chestOf(owner).distanceTo(position());
        if (distance > BloodHarvestRules.pullRange(bloodscent)) {
            return;
        }
        entityData.set(PHASE, PHASE_STREAMING);
        entityData.set(PHASE_TICK, tickCount);
        entityData.set(FLIGHT, BloodHarvestRules.flightTicks(distance));
        level.playSound(null, blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    private void tickStreaming(ServerLevel level, ServerPlayer owner) {
        if (owner == null) {
            // Nobody to land in. The blood is lost rather than left hanging in the air.
            discard();
            return;
        }
        if (tickCount - phaseTick() < flight()) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        state.addBloodVessel(worth());
        boolean overflowing = ClassPassiveEffects.on(state, MagicPassiveContent.VESSEL_OVERFLOWS.id())
                && BloodPassives.overflowing(state);
        state.sync(owner);
        level.playSound(null, owner.blockPosition(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.35F, 0.6F);
        if (!overflowing) {
            discard();
            return;
        }
        entityData.set(PHASE, PHASE_OVERFLOW);
        entityData.set(PHASE_TICK, tickCount);
    }

    private void tickOverflow(ServerPlayer owner) {
        if (owner == null || tickCount - phaseTick() >= BloodHarvestRules.OVERFLOW_TICKS) {
            discard();
        }
    }

    /** The owner, if they are online, alive, and in this level. */
    private ServerPlayer serverOwner() {
        if (ownerUuid == null) {
            return null;
        }
        Player player = level().getPlayerByUUID(ownerUuid);
        if (player instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()) {
            return owner;
        }
        return null;
    }

    /** Where on a body the blood goes in. */
    public static Vec3 chestOf(Entity owner) {
        return owner.position().add(0.0D, owner.getBbHeight() * CHEST_HEIGHT, 0.0D);
    }

    // ---- accessors ----------------------------------------------------------------------------

    public boolean isOwnedBy(ServerPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public boolean isPooled() {
        return phase() == PHASE_POOLED;
    }

    /** The owner as the client sees them, or null while they are not tracked. */
    public Entity owner() {
        int id = entityData.get(OWNER_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Vessel this blood is worth when it lands. */
    public int worth() {
        return entityData.get(YIELD);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    public int phaseTick() {
        return entityData.get(PHASE_TICK);
    }

    public int flight() {
        return entityData.get(FLIGHT);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    // ---- entity contract ----------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(YIELD, 0);
        builder.define(PHASE, PHASE_POOLED);
        builder.define(PHASE_TICK, 0);
        builder.define(FLIGHT, BloodHarvestRules.MIN_FLIGHT_TICKS);
        builder.define(SEED, 0);
    }

    /** Spilled blood is not saved. A pool loaded from disk would have no owner to wait for anyway. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }
}
