package com.efkrdnz.magical.entity.domain;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.DomainPass;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A bounded region owned by one wielder, which ticks over whatever stands inside it.
 *
 * <p><b>Only the Authority of Space has one.</b> This was pulled out of the subspace on the
 * assumption that Mana and Life would want the same machine, and that assumption was wrong twice:
 * an Authority over mana owns prices rather than places, and an Authority over life owns a growing
 * graph rather than a ball. A region is the shape of exactly one of the nine, and giving the other
 * eight the same shape with different words on it is how nine powers become one power.
 *
 * <p>It is left standing because the subspace genuinely uses all of it and the split is what makes
 * the two halves of a law legible - who owns it, whether it follows them, how long it lives, how it
 * finds what is inside, which half of a law each machine may carry out, and the abilities it lent
 * out and must take back. If nothing else ever subclasses it, it should be folded back in rather
 * than left advertising tenants that never arrived. All of that is the same code, and it
 * lives here so that it is the same code in fact rather than by resemblance.
 *
 * <p><b>Why an abstract class and not one entity carrying a kind.</b> {@code
 * SynchedEntityData.defineId} is keyed per class and a subclass's ids continue its superclass's, so
 * a subclass gets its own accessors for free and Space keeps the twenty-four law ints it already
 * saves and syncs. A parameterised entity with a bag of rules in a {@code CompoundTag} would have
 * forced a save migration on an already-registered entity id for no gain, and would have denied
 * Life the synced scalars its tree has to be drawn from.
 *
 * <p><b>The half a domain may not carry out.</b> Every law is split by {@link DomainPass}, and the
 * reason is written there in full: the server cannot deliver a velocity to the player it is aimed
 * at, so a domain hands out consequences on the server and each client pushes the one player whose
 * movement it owns. A subclass that writes {@code setDeltaMovement} outside {@code pass.moves()}
 * will appear to work on every mob and do nothing whatsoever to a player.
 */
public abstract class DomainEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FOLLOW_OWNER =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.INT);

    /**
     * The furthest any domain of any kind could reach, for a caller that has to find one before it
     * can ask the domain anything. Space's subspace is the widest today: radius 16, plus the 1.35 a
     * boundary rule looks past its own shell.
     */
    public static final double MAX_CLIENT_REACH = 17.35D;

    /** Players this domain granted flight to, so that it only ever revokes what it gave. */
    private final Set<UUID> grantedFlightPlayers = new HashSet<>();
    private final Set<UUID> touchedFlightPlayers = new HashSet<>();
    private UUID ownerUuid;

    protected DomainEntity(EntityType<? extends DomainEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    // ----------------------------------------------------------- what a kind of domain supplies

    /**
     * How far out this domain has to look for subjects. Usually the radius; a domain with a law
     * that acts on things approaching from outside reaches past its own shell.
     */
    protected abstract double searchRadius();

    /** True when this subject is inside the region, by whatever shape the region actually has. */
    protected abstract boolean contains(Entity entity);

    /** {@link #contains} for callers outside the domain, such as a law consulted at cast time. */
    public boolean covers(Entity entity) {
        return contains(entity);
    }

    /** One subject, one pass over this domain's whole vocabulary. */
    protected abstract void applyLaws(Entity owner, Entity subject, DomainPass pass);

    /** Ticks after which the domain lapses on its own. */
    protected int lifeTicks() {
        return 20 * 60 * 10;
    }

    /** Anything the domain does once per server tick that is not about its subjects. */
    protected void tickDomain(LivingEntity owner) {}

    /**
     * Lets go of the handle the owner's state holds on this domain, so that a wielder whose domain
     * has lapsed is not left pointing at an entity that is gone.
     */
    protected void releaseOwnerHandle(ServerPlayer player) {}

    // ------------------------------------------------------------------------------ the machine

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 5.0F);
        builder.define(FOLLOW_OWNER, false);
        builder.define(OWNER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            followOwnerClient();
            return;
        }
        Entity owner = ownerEntity();
        if (!(owner instanceof LivingEntity livingOwner) || !owner.isAlive() || tickCount > lifeTicks()) {
            clearOwnerState(owner);
            discard();
            return;
        }
        if (followsOwner()) {
            setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        } else if (owner.distanceToSqr(position()) > radius() * radius()) {
            clearOwnerState(owner);
            discard();
            return;
        }
        tickDomain(livingOwner);
        applyRules(livingOwner);
    }

    private void applyRules(LivingEntity owner) {
        touchedFlightPlayers.clear();
        double searchRadius = searchRadius();
        AABB area = new AABB(getX() - searchRadius, getY() - searchRadius, getZ() - searchRadius,
                getX() + searchRadius, getY() + searchRadius, getZ() + searchRadius);
        for (Entity entity : level().getEntities(this, area,
                target -> target.isAlive() && target != this && !(target instanceof DomainEntity))) {
            // A player's movement belongs to their own client, which runs the other half of the
            // law through SpaceLawClient. All the server can do here is hand out what it costs.
            applyLaws(owner, entity, entity instanceof Player ? DomainPass.CONSEQUENCES : DomainPass.WHOLE);
        }
        revokeUntouchedGrantedFlight();
    }

    /**
     * Pushes the player sitting at this client, and only that player, through the movement half of
     * every law. Called just before the player's own physics run, so a law sets the velocity their
     * input then works against.
     */
    public void applyLocalPlayerMotion(Player player) {
        applyLaws(level().getEntity(entityData.get(OWNER_ID)), player, DomainPass.MOTION);
    }

    // ----------------------------------------------------------------------------------- shared

    protected Vec3 capMotion(Vec3 motion, double maxLength) {
        if (motion.lengthSqr() <= maxLength * maxLength) {
            return motion;
        }
        return motion.normalize().scale(maxLength);
    }

    /** Centre of a subject's body, which is what a domain measures against rather than its feet. */
    protected Vec3 entityBoundaryPoint(Entity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    protected void moveByCorrection(Entity entity, Vec3 correction) {
        entity.teleportTo(entity.getX() + correction.x, entity.getY() + correction.y, entity.getZ() + correction.z);
    }

    /**
     * Keeps a following domain on its owner, on the client, exactly - not eased toward them.
     *
     * <p>It used to lerp a little over half the way each tick, which is invisible on a cloud of
     * particles and very visible on a wall: a domain that wears a level horizon and an upright
     * standing due north is an instrument, and an instrument that swims behind the person holding
     * it reads as a mistake. The server sets the position outright, so easing here was also the
     * two sides disagreeing about where the wall is.
     */
    private void followOwnerClient() {
        if (!followsOwner()) {
            return;
        }
        Entity owner = level().getEntity(entityData.get(OWNER_ID));
        if (owner != null && owner.isAlive()) {
            setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        }
    }


    protected Entity ownerEntity() {
        return ownerUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(ownerUuid);
    }

    private void clearOwnerState(Entity owner) {
        if (owner instanceof ServerPlayer player) {
            releaseOwnerHandle(player);
        }
    }

    // ------------------------------------------------------------------------- flight on loan

    /**
     * Grants flight, but never to someone who could already fly. That guard is what keeps this from
     * fighting Mana Flight or creative mode: if the ability was not ours to give, it is not ours to
     * take away, and the player is never recorded in {@code grantedFlightPlayers}.
     */
    protected void grantCreativeFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        UUID uuid = player.getUUID();
        if (grantedFlightPlayers.contains(uuid)) {
            touchedFlightPlayers.add(uuid);
            player.fallDistance = 0.0F;
            return;
        }
        if (player.getAbilities().mayfly) {
            return;
        }
        touchedFlightPlayers.add(uuid);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        grantedFlightPlayers.add(uuid);
        player.fallDistance = 0.0F;
    }

    /** Takes flight back from anyone who left the domain this tick. */
    private void revokeUntouchedGrantedFlight() {
        grantedFlightPlayers.removeIf(uuid -> {
            if (touchedFlightPlayers.contains(uuid)) {
                return false;
            }
            revokeGrantedFlight(uuid);
            return true;
        });
    }

    private void revokeAllGrantedFlight() {
        for (UUID uuid : Set.copyOf(grantedFlightPlayers)) {
            revokeGrantedFlight(uuid);
        }
        grantedFlightPlayers.clear();
        touchedFlightPlayers.clear();
    }

    private void revokeGrantedFlight(UUID uuid) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    @Override
    public void remove(RemovalReason reason) {
        // Flight is on loan for as long as the domain exists; it must not outlive it.
        revokeAllGrantedFlight();
        super.remove(reason);
    }

    // -------------------------------------------------------------------------------- accessors

    public float radius() {
        return entityData.get(RADIUS);
    }

    protected void setRadius(float radius) {
        entityData.set(RADIUS, radius);
    }

    public boolean followsOwner() {
        return entityData.get(FOLLOW_OWNER);
    }

    protected void setFollowOwner(boolean followOwner) {
        entityData.set(FOLLOW_OWNER, followOwner);
    }

    protected int ownerId() {
        return entityData.get(OWNER_ID);
    }

    protected void setOwner(LivingEntity owner) {
        this.ownerUuid = owner.getUUID();
        entityData.set(OWNER_ID, owner.getId());
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    // ------------------------------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(FOLLOW_OWNER, tag.getBoolean("FollowOwner"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Radius", radius());
        tag.putBoolean("FollowOwner", followsOwner());
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
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
