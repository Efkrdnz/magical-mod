package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
import com.efkrdnz.magical.magic.passive.BloodPassives;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.Comparator;
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
 * Blood on the ground, and the ways it moves.
 *
 * <p>One entity is one pool. A kill or a bleed leaves a <em>harvest</em>, which lifts and streams
 * into its owner's chest when they come near, filling the Vessel as it lands. The Rite and a Spear
 * impact set down a <em>battery</em>, which waits to be spent: Coagulate drinks it, the Spear
 * drinks it, a Vein Walk landing on it lifts it. A Vein Walk leaves a <em>trace</em> where it
 * began, worth nothing and only there to be stepped back to. The same entity, in its vein phase,
 * is the stream a walk leaves in the air. Not saved to disk, on purpose: spilled blood does not
 * survive a relog.
 *
 * <p>This entity is the only record of pooled blood, so the skills, the renderer and the payout
 * cannot disagree about whether a pool is still there. Invisible and untouchable, like every
 * gameplay entity here; everything seen is {@code BloodHarvestRenderer}, reading the synced
 * fields and the owner's live position.
 */
public final class BloodHarvestEntity extends Entity {

    public static final byte PHASE_POOLED = 0;
    public static final byte PHASE_STREAMING = 1;
    public static final byte PHASE_OVERFLOW = 2;
    /** A Vein Walk's stream: born round the chest it left, landing in the walker wherever they now are. */
    public static final byte PHASE_VEIN = 3;

    /** Where on the owner the blood goes in, as a share of their height. */
    public static final double CHEST_HEIGHT = 0.6D;

    /** How near a pool a Vein Walk has to land to spend it. */
    private static final double LANDING_RADIUS = 1.0D;

    /** How far around a player their pools are looked for. Wider than any pull, so a cap counts all of them. */
    private static final double POOL_SEARCH = 256.0D;

    /** {@code FEED_END} while a pool is still being fed. */
    private static final int FEEDING = Integer.MAX_VALUE;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> YIELD = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.BYTE);
    /** The tick this phase began, in this entity's own ticks, so the client needs no second clock. */
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    /** Ticks from spawn until the pool dries. Stretched by Clotting and by every drop fed in. */
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    /** The body a fed pool bleeds out of, so the renderer can draw the drops leaving it. -1 when none. */
    private static final EntityDataAccessor<Integer> SOURCE_ID = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    /** How long the feed was meant to run, so the renderer can spread the drops over it. 0 when never fed. */
    private static final EntityDataAccessor<Integer> FEED_TICKS = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);
    /** The tick the feed stopped; {@link #FEEDING} while it runs. */
    private static final EntityDataAccessor<Integer> FEED_END = SynchedEntityData.defineId(BloodHarvestEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;

    public BloodHarvestEntity(EntityType<? extends BloodHarvestEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ---- spawning -----------------------------------------------------------------------------

    /** A harvest: what a kill or a bleed leaves, lifting on its own when the owner comes near. */
    public static BloodHarvestEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at, int yield) {
        return spawn(level, owner, at, BloodHarvestRules.KIND_HARVEST, yield, lifetimeFor(owner));
    }

    /**
     * A pool of any kind, worth {@code yield}, living {@code life} ticks unless it is lifted or spent
     * first. Spends the owner's oldest pool if they already have as many as the rules allow.
     */
    public static BloodHarvestEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at, byte kind, int yield, int life) {
        List<BloodHarvestEntity> pools = poolsWithin(owner, POOL_SEARCH);
        if (pools.size() >= BloodHarvestRules.MAX_POOLS) {
            pools.stream().max(Comparator.comparingInt(pool -> pool.tickCount)).ifPresent(Entity::discard);
        }
        BloodHarvestEntity pool = new BloodHarvestEntity(MagicalEntities.BLOOD_HARVEST.get(), level);
        pool.setPos(at.x, at.y, at.z);
        pool.ownerUuid = owner.getUUID();
        pool.entityData.set(OWNER_ID, owner.getId());
        pool.entityData.set(KIND, kind);
        pool.entityData.set(YIELD, Math.max(0, yield));
        pool.entityData.set(LIFE, Math.max(1, life));
        pool.entityData.set(SEED, level.random.nextInt(64));
        level.addFreshEntity(pool);
        return pool;
    }

    /**
     * The stream a Vein Walk leaves: cubes born round {@code fromChest}, flying into the walker
     * wherever they now stand. Worth nothing and gone once it lands; never a pool.
     */
    public static BloodHarvestEntity vein(ServerLevel level, ServerPlayer owner, Vec3 fromChest) {
        BloodHarvestEntity vein = new BloodHarvestEntity(MagicalEntities.BLOOD_HARVEST.get(), level);
        vein.setPos(fromChest.x, fromChest.y, fromChest.z);
        vein.ownerUuid = owner.getUUID();
        vein.entityData.set(OWNER_ID, owner.getId());
        vein.entityData.set(KIND, BloodHarvestRules.KIND_TRACE);
        vein.entityData.set(PHASE, PHASE_VEIN);
        vein.entityData.set(FLIGHT, BloodHarvestRules.VEIN_FLIGHT_TICKS);
        vein.entityData.set(LIFE, BloodHarvestRules.VEIN_FLIGHT_TICKS);
        vein.entityData.set(SEED, level.random.nextInt(64));
        level.addFreshEntity(vein);
        return vein;
    }

    private static int lifetimeFor(ServerPlayer owner) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        return BloodHarvestRules.lifetime(ClassPassiveEffects.on(state, MagicPassiveContent.CLOTTING.id()));
    }

    // ---- the pools, as the skills see them ----------------------------------------------------

    /** The player's pools still waiting within {@code within} blocks, of every kind. */
    public static List<BloodHarvestEntity> poolsWithin(ServerPlayer player, double within) {
        return player.serverLevel().getEntities(MagicalEntities.BLOOD_HARVEST.get(),
                player.getBoundingBox().inflate(within),
                pool -> pool.isOwnedBy(player) && pool.isPooled());
    }

    /** The nearest of the player's pools within {@code within} blocks. */
    public static Optional<BloodHarvestEntity> nearestPool(ServerPlayer player, double within) {
        Vec3 from = player.position();
        return poolsWithin(player, within).stream()
                .filter(pool -> pool.position().distanceTo(from) <= within)
                .min(Comparator.comparingDouble(pool -> pool.position().distanceToSqr(from)));
    }

    /** The furthest pool of the player's still within reach, which is where Vein Walk goes. */
    public static Optional<Vec3> furthestPool(ServerPlayer player, double reach) {
        Vec3 from = player.position();
        Vec3 best = null;
        double bestDistance = -1.0D;
        for (BloodHarvestEntity pool : poolsWithin(player, reach)) {
            double distance = from.distanceTo(pool.position());
            if (distance <= reach && distance > bestDistance) {
                bestDistance = distance;
                best = pool.position();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A Vein Walk has landed at {@code at}. A battery there lifts into the walker and pays out as
     * it lands; a harvest or a trace is spent, so one pool of blood is not an infinite shuttle.
     *
     * @return how much blood is on its way into the walker
     */
    public static int landOn(ServerPlayer player, Vec3 at) {
        int lifted = 0;
        for (BloodHarvestEntity pool : poolsWithin(player, LANDING_RADIUS + 1.0D)) {
            if (pool.position().distanceToSqr(at) > LANDING_RADIUS * LANDING_RADIUS) {
                continue;
            }
            if (pool.kind() == BloodHarvestRules.KIND_BATTERY) {
                lifted += pool.worth();
                pool.liftInto(player);
            } else {
                pool.discard();
            }
        }
        return lifted;
    }

    /**
     * Takes the pool's worth now and sends the (now empty) blood flying into the owner, so what
     * is seen still leaves the ground and enters a body. Discarded outright if nobody is there to
     * fly to.
     *
     * @return the blood taken, which the caller decides what to do with
     */
    public int drink(ServerPlayer owner) {
        if (!isPooled()) {
            return 0;
        }
        int worth = worth();
        entityData.set(YIELD, 0);
        if (owner == null) {
            discard();
        } else {
            liftInto(owner);
        }
        return worth;
    }

    /** Lifts a waiting pool into the owner whatever its kind or age. What it is worth lands with it. */
    public void liftInto(ServerPlayer owner) {
        if (isPooled()) {
            lift(chestOf(owner).distanceTo(position()));
        }
    }

    // ---- feeding ------------------------------------------------------------------------------

    /**
     * Adds {@code amount} to a pool that a body is bleeding into.
     *
     * <p>A fed pool does not lift while it is fed, nor for a moment after, so a bleed is not
     * harvested drop by drop as it happens. Its life stretches with every drop, so it does not dry
     * under a long bleed. {@code plannedTicks} is how long the feed was meant to run, which the
     * renderer spreads the drops over; it is taken once, at the first drop.
     */
    public void feed(int amount, Entity source, int plannedTicks) {
        if (!isPooled() || amount <= 0) {
            return;
        }
        entityData.set(YIELD, Math.min(PlayerMagicState.MAX_BLOOD_VESSEL, worth() + amount));
        entityData.set(SOURCE_ID, source == null ? -1 : source.getId());
        if (feedTicks() <= 0) {
            entityData.set(FEED_TICKS, Math.max(1, plannedTicks));
        }
        entityData.set(FEED_END, FEEDING);
        ServerPlayer owner = serverOwner();
        int fresh = owner == null ? BloodHarvestRules.POOL_LIFETIME : lifetimeFor(owner);
        entityData.set(LIFE, Math.max(life(), tickCount + fresh));
    }

    /** The bleed is over: the pool may lift once the grace has passed. */
    public void endFeed() {
        if (feeding()) {
            entityData.set(FEED_END, tickCount);
        }
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
            case PHASE_POOLED -> tickPooled(owner);
            case PHASE_STREAMING -> tickStreaming(level, owner);
            case PHASE_VEIN -> tickVein(owner);
            default -> tickOverflow(owner);
        }
    }

    private void tickPooled(ServerPlayer owner) {
        if (tickCount >= life()) {
            discard();
            return;
        }
        if (owner == null || !BloodHarvestRules.liftsOnItsOwn(kind())
                || !BloodHarvestRules.canLift(tickCount, life())) {
            return;
        }
        if (feeding() || (fed() && tickCount < feedEnd() + BloodHarvestRules.FEED_GRACE_TICKS)) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        boolean bloodscent = ClassPassiveEffects.on(state, MagicPassiveContent.BLOODSCENT.id());
        double distance = chestOf(owner).distanceTo(position());
        if (distance > BloodHarvestRules.pullRange(bloodscent)) {
            return;
        }
        lift(distance);
    }

    private void lift(double distance) {
        entityData.set(PHASE, PHASE_STREAMING);
        entityData.set(PHASE_TICK, tickCount);
        entityData.set(FLIGHT, BloodHarvestRules.flightTicks(distance));
        level().playSound(null, blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.7F);
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
        if (worth() <= 0) {
            // Drunk on the ground, or a lifted trace: the flight was the whole of it.
            discard();
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

    private void tickVein(ServerPlayer owner) {
        if (owner == null || tickCount - phaseTick() >= flight()) {
            discard();
        }
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

    /** Where on a body the blood goes in, and comes out. */
    public static Vec3 chestOf(Entity body) {
        return body.position().add(0.0D, body.getBbHeight() * CHEST_HEIGHT, 0.0D);
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

    /** The body a fed pool bleeds out of, as the client sees it, or null. */
    public Entity source() {
        int id = entityData.get(SOURCE_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Vessel this blood is worth when it lands. */
    public int worth() {
        return entityData.get(YIELD);
    }

    public byte kind() {
        return entityData.get(KIND);
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

    /** Ticks from spawn until this pool dries. */
    public int life() {
        return entityData.get(LIFE);
    }

    /** How long the feed was meant to run; zero for a pool never fed. */
    public int feedTicks() {
        return entityData.get(FEED_TICKS);
    }

    /** The tick the feed stopped, or a very large number while it runs. */
    public int feedEnd() {
        return entityData.get(FEED_END);
    }

    public boolean fed() {
        return feedTicks() > 0;
    }

    public boolean feeding() {
        return fed() && feedEnd() == FEEDING;
    }

    // ---- entity contract ----------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(YIELD, 0);
        builder.define(KIND, BloodHarvestRules.KIND_HARVEST);
        builder.define(PHASE, PHASE_POOLED);
        builder.define(PHASE_TICK, 0);
        builder.define(FLIGHT, BloodHarvestRules.MIN_FLIGHT_TICKS);
        builder.define(SEED, 0);
        builder.define(LIFE, BloodHarvestRules.POOL_LIFETIME);
        builder.define(SOURCE_ID, -1);
        builder.define(FEED_TICKS, 0);
        builder.define(FEED_END, 0);
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
