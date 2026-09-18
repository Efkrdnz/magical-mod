package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.entity.domain.DomainEntity;
import com.efkrdnz.magical.magic.DomainPass;
import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.client.renderer.space.SubspaceCrossings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SpaceSubspaceEntity extends DomainEntity {
    private static final int NO_RULE = -1;
    /** How close to the shell an entity must be for a boundary rule to act on it. */
    private static final double BOUNDARY_THICKNESS = 1.45D;
    /** How far past the shell we still look, so a boundary can catch something on its way in. */
    private static final double BOUNDARY_OUTER_REACH = 1.35D;
    private static final EntityDataAccessor<Integer> GRAVITY_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRAVITY_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VELOCITY_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VELOCITY_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACCELERATION_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACCELERATION_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AIR_RESISTANCE_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AIR_RESISTANCE_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PRESSURE_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PRESSURE_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MASS_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MASS_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIME_FLOW_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIME_FLOW_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VECTOR_FIELD_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VECTOR_FIELD_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ENTROPY_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ENTROPY_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FRICTION_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FRICTION_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOUNDARY_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOUNDARY_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLLISION_OPERATION = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLLISION_TARGET = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
    private final Set<Integer> reversedAccelerationEntityIds = new HashSet<>();
    /** Players this subspace granted flight to, so it only ever revokes what it gave. */

    public SpaceSubspaceEntity(EntityType<? extends SpaceSubspaceEntity> entityType, Level level) {
        super(entityType, level);
    }

    // ------------------------------------------------------------------ what the boundary wears

    /** How long a law takes to settle onto the wall after it is written. */
    private static final float SETTLE_TICKS = 10.0F;
    /** How far past the shell a body is still watched, so a crossing is caught on the way through. */
    private static final double CROSSING_WATCH = 1.5D;

    private final int[] lawChangedAt = new int[SpaceRuleCategory.values().length];
    private final Map<Integer, Boolean> lastInside = new HashMap<>();
    private final SubspaceCrossings crossings = new SubspaceCrossings();
    private int[] lastLaws;

    /**
     * The twelve law ordinals in category order, which is the whole of what the boundary draws.
     *
     * <p>Handing the renderer one array rather than twenty-four accessors is what lets
     * {@code SubspaceLedger} be a pure class with a test: the reduction from ordinals to marks
     * happens somewhere a test can reach, instead of inside a render pass where nothing can.
     */
    public int[] lawOrdinals() {
        return new int[] {
            entityData.get(GRAVITY_OPERATION),
            entityData.get(VELOCITY_OPERATION),
            entityData.get(ACCELERATION_OPERATION),
            entityData.get(AIR_RESISTANCE_OPERATION),
            entityData.get(PRESSURE_OPERATION),
            entityData.get(MASS_OPERATION),
            entityData.get(TIME_FLOW_OPERATION),
            entityData.get(VECTOR_FIELD_OPERATION),
            entityData.get(ENTROPY_OPERATION),
            entityData.get(FRICTION_OPERATION),
            entityData.get(BOUNDARY_OPERATION),
            entityData.get(COLLISION_OPERATION),
        };
    }

    /** How far a law's mark has settled onto the wall: 0 the tick it arrives, 1 once it is written. */
    public float lawSettle01(int slot, float age) {
        if (slot < 0 || slot >= lawChangedAt.length) {
            return 1.0F;
        }
        return Mth.clamp((age - lawChangedAt[slot]) / SETTLE_TICKS, 0.0F, 1.0F);
    }

    /** Where bodies have lately gone through the wall. Client-side; empty on the server. */
    public SubspaceCrossings crossings() {
        return crossings;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            watchLaws();
            watchCrossings();
        }
    }

    /** Notices a law arriving or going dark, so its mark can settle in rather than blink on. */
    private void watchLaws() {
        int[] now = lawOrdinals();
        if (lastLaws != null) {
            for (int slot = 0; slot < now.length; slot++) {
                if (now[slot] != lastLaws[slot]) {
                    lawChangedAt[slot] = tickCount;
                }
            }
        }
        lastLaws = now;
    }

    /**
     * Notices bodies going through the wall.
     *
     * <p>Only a body seen on the previous tick can cross: something that merely came into range
     * already inside has not crossed anything, and firing a ring for it would ripple the wall
     * every time a domain loaded.
     */
    private void watchCrossings() {
        double radius = radius();
        AABB area = new AABB(position(), position()).inflate(radius + CROSSING_WATCH);
        Map<Integer, Boolean> seen = new HashMap<>();
        for (Entity entity : level().getEntities(this, area,
                target -> target.isAlive() && target instanceof LivingEntity)) {
            Vec3 fromCentre = entityBoundaryPoint(entity).subtract(position());
            double lengthSq = fromCentre.lengthSqr();
            boolean inside = lengthSq <= radius * radius;
            Boolean was = lastInside.get(entity.getId());
            if (was != null && was != inside && lengthSq > 1.0E-4D) {
                Vec3 direction = fromCentre.normalize();
                crossings.note((float) direction.x, (float) direction.y, (float) direction.z, tickCount);
            }
            seen.put(entity.getId(), inside);
        }
        lastInside.clear();
        lastInside.putAll(seen);
    }

    public static SpaceSubspaceEntity create(ServerLevel level, LivingEntity owner, float radius, boolean followOwner) {
        SpaceSubspaceEntity entity = new SpaceSubspaceEntity(MagicalEntities.SPACE_SUBSPACE.get(), level);
        entity.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        entity.setOwner(owner);
        entity.setRadius(Mth.clamp(radius, 5.0F, MAX_RADIUS));
        entity.setFollowOwner(followOwner);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GRAVITY_OPERATION, NO_RULE);
        builder.define(GRAVITY_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(VELOCITY_OPERATION, NO_RULE);
        builder.define(VELOCITY_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(ACCELERATION_OPERATION, NO_RULE);
        builder.define(ACCELERATION_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(AIR_RESISTANCE_OPERATION, NO_RULE);
        builder.define(AIR_RESISTANCE_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(PRESSURE_OPERATION, NO_RULE);
        builder.define(PRESSURE_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(MASS_OPERATION, NO_RULE);
        builder.define(MASS_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(TIME_FLOW_OPERATION, NO_RULE);
        builder.define(TIME_FLOW_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(VECTOR_FIELD_OPERATION, NO_RULE);
        builder.define(VECTOR_FIELD_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(ENTROPY_OPERATION, NO_RULE);
        builder.define(ENTROPY_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(FRICTION_OPERATION, NO_RULE);
        builder.define(FRICTION_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(BOUNDARY_OPERATION, NO_RULE);
        builder.define(BOUNDARY_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        builder.define(COLLISION_OPERATION, NO_RULE);
        builder.define(COLLISION_TARGET, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
    }

    public void applyRule(SpaceRuleCategory category, SpaceRuleOperation operation, SpaceTargetGroup targetGroup) {
        if (operation.category() != category) {
            return;
        }
        int op = operation.clear() ? NO_RULE : operation.ordinal();
        int target = targetGroup.ordinal();
        switch (category) {
            case GRAVITY -> {
                entityData.set(GRAVITY_OPERATION, op);
                entityData.set(GRAVITY_TARGET, target);
            }
            case VELOCITY -> {
                entityData.set(VELOCITY_OPERATION, op);
                entityData.set(VELOCITY_TARGET, target);
            }
            case ACCELERATION -> {
                entityData.set(ACCELERATION_OPERATION, op);
                entityData.set(ACCELERATION_TARGET, target);
                reversedAccelerationEntityIds.clear();
            }
            case AIR_RESISTANCE -> {
                entityData.set(AIR_RESISTANCE_OPERATION, op);
                entityData.set(AIR_RESISTANCE_TARGET, target);
            }
            case PRESSURE -> {
                entityData.set(PRESSURE_OPERATION, op);
                entityData.set(PRESSURE_TARGET, target);
            }
            case MASS -> {
                entityData.set(MASS_OPERATION, op);
                entityData.set(MASS_TARGET, target);
            }
            case TIME_FLOW -> {
                entityData.set(TIME_FLOW_OPERATION, op);
                entityData.set(TIME_FLOW_TARGET, target);
            }
            case VECTOR_FIELD -> {
                entityData.set(VECTOR_FIELD_OPERATION, op);
                entityData.set(VECTOR_FIELD_TARGET, target);
            }
            case ENTROPY -> {
                entityData.set(ENTROPY_OPERATION, op);
                entityData.set(ENTROPY_TARGET, target);
            }
            case FRICTION -> {
                entityData.set(FRICTION_OPERATION, op);
                entityData.set(FRICTION_TARGET, target);
            }
            case BOUNDARY -> {
                entityData.set(BOUNDARY_OPERATION, op);
                entityData.set(BOUNDARY_TARGET, target);
            }
            case COLLISION -> {
                entityData.set(COLLISION_OPERATION, op);
                entityData.set(COLLISION_TARGET, target);
            }
        }
    }

    /**
     * A subspace is a sphere, and it measures the centre of a body rather than the feet standing
     * under it - which is why a tall mob is inside before it looks inside.
     */
    @Override
    protected boolean contains(Entity entity) {
        return entityBoundaryPoint(entity).distanceToSqr(position()) <= radius() * radius();
    }

    /**
     * How far out this subspace has to look. A boundary rule acts on things approaching from
     * outside, so it reaches past its own shell; every other law stops at the radius.
     */
    @Override
    protected double searchRadius() {
        return radius() + (hasBoundaryRule() ? BOUNDARY_OUTER_REACH : 0.0D);
    }

    /** How far any subspace could ever reach, for a caller that has to find one before asking it. */
    public static double maxSearchRadius() {
        return MAX_RADIUS + BOUNDARY_OUTER_REACH;
    }

    /** One entity, one pass over the twelve laws. */
    @Override
    protected void applyLaws(Entity owner, Entity entity, DomainPass pass) {
        double radiusSqr = radius() * radius();
        double distanceSqr = entityBoundaryPoint(entity).distanceToSqr(position());
        if (distanceSqr > radiusSqr) {
            double searchRadius = searchRadius();
            if (hasBoundaryRule() && distanceSqr <= searchRadius * searchRadius) {
                applyBoundary(owner, entity, pass);
            }
            return;
        }
        applyGravity(owner, entity, pass);
        applyVelocity(owner, entity, pass);
        applyAcceleration(owner, entity, pass);
        applyAirResistance(owner, entity, pass);
        applyPressure(owner, entity, pass);
        applyMass(owner, entity, pass);
        applyTimeFlow(owner, entity, pass);
        applyVectorField(owner, entity, pass);
        applyEntropy(owner, entity, pass);
        applyFriction(owner, entity, pass);
        applyBoundary(owner, entity, pass);
        applyCollision(owner, entity, pass);
    }

    private void applyGravity(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(GRAVITY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(GRAVITY_TARGET)))) {
            return;
        }
        // Both sides forget the fall: the client so it stops counting it, the server so it stops billing it.
        if (operation == SpaceRuleOperation.REMOVE_GRAVITY) {
            entity.fallDistance = 0.0F;
        }
        if (pass.consequences() && operation == SpaceRuleOperation.CONTROL_GRAVITY
                && entity instanceof net.minecraft.server.level.ServerPlayer player) {
            grantCreativeFlight(player);
        }
        if (!pass.moves()) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double y = switch (operation) {
            case REMOVE_GRAVITY -> Math.max(0.0D, motion.y);
            case DECREASE_GRAVITY -> motion.y + 0.035D;
            case INCREASE_GRAVITY -> motion.y - 0.075D;
            case REVERSE_GRAVITY -> motion.y + 0.13D;
            case CONTROL_GRAVITY -> controlFlight(entity, motion);
            default -> motion.y;
        };
        double limit = gravityVerticalLimit(entity, operation);
        entity.setDeltaMovement(motion.x, Mth.clamp(y, -limit, limit), motion.z);
        entity.hasImpulse = true;
    }

    private void applyVelocity(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(VELOCITY_OPERATION));
        if (operation == null || !pass.moves() || !matchesTarget(owner, entity, target(entityData.get(VELOCITY_TARGET)))) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        Vec3 next = switch (operation) {
            case UNIFORM_MOTION -> new Vec3(motion.x, Math.abs(motion.y) < 0.005D ? 0.0D : motion.y * 0.985D, motion.z);
            case STOP -> motion.scale(0.62D);
            default -> motion;
        };
        entity.setDeltaMovement(next);
        entity.hasImpulse = true;
    }

    private void applyAcceleration(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(ACCELERATION_OPERATION));
        if (operation == null || !pass.moves() || !matchesTarget(owner, entity, target(entityData.get(ACCELERATION_TARGET)))) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        Vec3 next = switch (operation) {
            case ACCELERATE -> accelerate(entity, motion, 0.085D);
            case DECELERATE -> motion.scale(0.89D);
            case REMOVE_ACCELERATION -> removeAcceleration(entity, motion);
            case REVERSE_ACCELERATION -> reverseAccelerationOnce(entity, motion);
            default -> motion;
        };
        entity.setDeltaMovement(capMotion(next, 2.65D));
        entity.hasImpulse = true;
    }

    private Vec3 accelerate(Entity entity, Vec3 motion, double strength) {
        if (motion.lengthSqr() > 1.0E-5D) {
            return motion.add(motion.normalize().scale(strength));
        }
        Vec3 outward = entity.position().subtract(position());
        if (outward.lengthSqr() > 1.0E-5D) {
            return motion.add(outward.normalize().scale(strength));
        }
        return motion.add(0.0D, strength * 0.35D, 0.0D);
    }

    private Vec3 removeAcceleration(Entity entity, Vec3 motion) {
        Vec3 next = new Vec3(motion.x * 0.992D, motion.y, motion.z * 0.992D);
        if (!entity.onGround() && motion.y < 0.0D) {
            next = next.add(0.0D, Math.min(0.055D, -motion.y * 0.18D), 0.0D);
        }
        return next;
    }

    private Vec3 reverseAccelerationOnce(Entity entity, Vec3 motion) {
        if (!reversedAccelerationEntityIds.add(entity.getId())) {
            return motion;
        }
        if (motion.lengthSqr() > 1.0E-5D) {
            return motion.scale(-0.92D);
        }
        Vec3 fromCenter = entity.position().subtract(position());
        if (fromCenter.lengthSqr() > 1.0E-5D) {
            return fromCenter.normalize().scale(-0.28D);
        }
        return new Vec3(0.0D, 0.16D, 0.0D);
    }

    private void applyAirResistance(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(AIR_RESISTANCE_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(AIR_RESISTANCE_TARGET)))) {
            return;
        }
        if (pass.moves()) {
            Vec3 motion = entity.getDeltaMovement();
            Vec3 next = switch (operation) {
                case VACUUM -> motion.scale(1.018D);
                case THIN_AIR -> new Vec3(motion.x * 1.035D, motion.y * 1.018D, motion.z * 1.035D);
                case DENSE_AIR -> motion.scale(0.84D);
                case DRAG_LOCK -> motion.scale(0.48D);
                default -> motion;
            };
            entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.THIN_AIR || operation == SpaceRuleOperation.VACUUM ? 3.05D : 1.85D));
            entity.hasImpulse = true;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.VACUUM) {
                living.setAirSupply(Math.max(-20, living.getAirSupply() - 8));
                if (living.getAirSupply() <= 0 && tickCount % 20 == Math.floorMod(entity.getId(), 20)) {
                    MagicDamageService.hurt(living, damageSources().drown(), 2.0F, MagicContent.MANIPULATE_SPACE.id(), false);
                }
            } else if (operation == SpaceRuleOperation.THIN_AIR) {
                living.setAirSupply(Math.max(-5, living.getAirSupply() - 1));
            } else if (operation == SpaceRuleOperation.DENSE_AIR) {
                living.setAirSupply(Math.min(living.getMaxAirSupply(), living.getAirSupply() + 2));
            }
        }
    }

    private void applyPressure(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(PRESSURE_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(PRESSURE_TARGET)))) {
            return;
        }
        if (pass.moves()) {
            Vec3 fromCenter = entity.position().subtract(position());
            Vec3 radial = fromCenter.lengthSqr() < 1.0E-5D ? Vec3.ZERO : fromCenter.normalize();
            Vec3 motion = entity.getDeltaMovement();
            Vec3 next = switch (operation) {
                case CRUSH_PRESSURE -> motion.add(radial.scale(-0.055D)).scale(0.93D);
                case EXPAND_PRESSURE -> motion.add(radial.scale(0.075D));
                case IMPLODE_PRESSURE -> motion.add(radial.scale(-0.18D));
                case BURST_PRESSURE -> motion.add(radial.scale(0.22D));
                default -> motion;
            };
            entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.BURST_PRESSURE ? 2.85D : 2.25D));
            entity.hasImpulse = true;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.CRUSH_PRESSURE && tickCount % 12 == Math.floorMod(entity.getId(), 12)) {
                MagicDamageService.hurt(living, damageSources().magic(), 1.25F, MagicContent.MANIPULATE_SPACE.id(), false);
            } else if ((operation == SpaceRuleOperation.IMPLODE_PRESSURE || operation == SpaceRuleOperation.BURST_PRESSURE)
                    && tickCount % 18 == Math.floorMod(entity.getId(), 18)) {
                MagicDamageService.hurt(living, damageSources().magic(), 0.75F, MagicContent.MANIPULATE_SPACE.id(), false);
            }
        }
    }

    private void applyMass(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(MASS_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(MASS_TARGET)))) {
            return;
        }
        if (pass.moves()) {
            Vec3 motion = entity.getDeltaMovement();
            Vec3 next = switch (operation) {
                case LIGHTEN_MASS -> new Vec3(motion.x * 1.05D, motion.y + 0.026D, motion.z * 1.05D);
                case WEIGH_DOWN -> new Vec3(motion.x * 0.78D, motion.y - 0.09D, motion.z * 0.78D);
                case ANCHOR_MASS -> motion.scale(0.32D);
                case NORMALIZE_MASS -> new Vec3(motion.x * 0.92D, motion.y * 0.92D, motion.z * 0.92D);
                default -> motion;
            };
            entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.LIGHTEN_MASS ? 2.45D : 1.75D));
            entity.hasImpulse = true;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.LIGHTEN_MASS) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 16, 0, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.JUMP, 16, 1, false, false));
            } else if (operation == SpaceRuleOperation.WEIGH_DOWN) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 16, 1, false, false));
            } else if (operation == SpaceRuleOperation.ANCHOR_MASS) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 16, 5, false, false));
            }
        }
    }

    private void applyTimeFlow(Entity owner, Entity entity, DomainPass pass) {
        if (UnwakingCapabilities.controlled(entity)) return;
        SpaceRuleOperation operation = operation(entityData.get(TIME_FLOW_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(TIME_FLOW_TARGET)))) {
            return;
        }
        if (pass.moves()) {
            Vec3 motion = entity.getDeltaMovement();
            Vec3 next = switch (operation) {
                case HASTEN_TIME -> motion.scale(1.12D);
                case SLOW_TIME -> motion.scale(0.68D);
                case STASIS_TIME -> motion.scale(0.12D);
                case NORMALIZE_TIME -> motion.scale(motion.lengthSqr() > 1.0D ? 0.92D : 1.0D);
                default -> motion;
            };
            entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.HASTEN_TIME ? 2.85D : 1.85D));
            entity.hasImpulse = true;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.HASTEN_TIME) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 16, 1, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 16, 1, false, false));
            } else if (operation == SpaceRuleOperation.SLOW_TIME) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 16, 2, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 16, 1, false, false));
            } else if (operation == SpaceRuleOperation.STASIS_TIME) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 16, 8, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 16, 4, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 16, 1, false, false));
            }
        }
    }

    private void applyVectorField(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(VECTOR_FIELD_OPERATION));
        if (operation == null || !pass.moves() || !matchesTarget(owner, entity, target(entityData.get(VECTOR_FIELD_TARGET)))) {
            return;
        }
        Vec3 fromCenter = entity.position().subtract(position());
        Vec3 horizontal = new Vec3(fromCenter.x, 0.0D, fromCenter.z);
        Vec3 motion = entity.getDeltaMovement();
        Vec3 impulse = switch (operation) {
            case PULL_NORTH -> new Vec3(0.0D, 0.0D, -0.095D);
            case PULL_SOUTH -> new Vec3(0.0D, 0.0D, 0.095D);
            case ORBIT -> horizontal.lengthSqr() < 1.0E-5D ? Vec3.ZERO : new Vec3(-horizontal.z, 0.0D, horizontal.x).normalize().scale(0.12D);
            case CONVERGE -> horizontal.lengthSqr() < 1.0E-5D ? Vec3.ZERO : horizontal.normalize().scale(-0.12D);
            default -> Vec3.ZERO;
        };
        Vec3 next = motion.add(impulse);
        if (operation == SpaceRuleOperation.ORBIT || operation == SpaceRuleOperation.CONVERGE) {
            next = new Vec3(next.x, next.y * 0.96D, next.z);
        }
        entity.setDeltaMovement(capMotion(next, 2.45D));
        entity.hasImpulse = true;
    }

    private void applyEntropy(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(ENTROPY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(ENTROPY_TARGET)))) {
            return;
        }
        if (pass.moves()) {
            Vec3 motion = entity.getDeltaMovement();
            Vec3 next = switch (operation) {
                case STABILIZE_ENTROPY -> motion.scale(0.82D);
                case DESTABILIZE_ENTROPY -> motion.add(entropyImpulse(entity, 0.032D));
                case CHAOTIC_MOTION -> motion.add(entropyImpulse(entity, 0.085D));
                default -> motion;
            };
            if (operation == SpaceRuleOperation.CHAOTIC_MOTION && tickCount % 11 == Math.floorMod(entity.getId(), 11)) {
                Vec3 fromCenter = entity.position().subtract(position());
                if (fromCenter.lengthSqr() > 1.0E-5D) {
                    next = next.add(fromCenter.normalize().scale(0.12D * (Math.floorMod(tickCount + entity.getId(), 2) == 0 ? 1.0D : -1.0D)));
                }
            }
            entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.CHAOTIC_MOTION ? 2.75D : 2.15D));
            entity.hasImpulse = true;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.STABILIZE_ENTROPY) {
                living.removeEffect(MobEffects.CONFUSION);
            } else if (operation == SpaceRuleOperation.CHAOTIC_MOTION && tickCount % 30 == Math.floorMod(entity.getId(), 30)) {
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 45, 0, false, false));
            }
        }
    }

    private Vec3 entropyImpulse(Entity entity, double strength) {
        double seed = tickCount * 0.37D + entity.getId() * 2.173D;
        double x = Math.sin(seed * 1.71D);
        double y = Math.sin(seed * 2.13D + 1.2D) * 0.55D;
        double z = Math.cos(seed * 1.37D);
        Vec3 impulse = new Vec3(x, y, z);
        if (impulse.lengthSqr() < 1.0E-5D) {
            return Vec3.ZERO;
        }
        return impulse.normalize().scale(strength);
    }

    private void applyFriction(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(FRICTION_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(FRICTION_TARGET)))) {
            return;
        }
        if (!entity.onGround()) {
            return;
        }
        if (pass.consequences() && entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.STICKY) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
            } else if (operation == SpaceRuleOperation.SLIPPERY) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 1, false, false));
            }
        }
        if (!pass.moves()) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        Vec3 next = switch (operation) {
            case SLIPPERY -> new Vec3(motion.x * 1.14D, motion.y, motion.z * 1.14D);
            case STICKY -> new Vec3(motion.x * 0.42D, motion.y, motion.z * 0.42D);
            case NORMALIZE_FRICTION -> new Vec3(motion.x * 0.82D, motion.y, motion.z * 0.82D);
            default -> motion;
        };
        if (operation == SpaceRuleOperation.SLIPPERY) {
            Vec3 horizontal = new Vec3(next.x, 0.0D, next.z);
            if (horizontal.lengthSqr() > 1.0E-5D) {
                next = next.add(horizontal.normalize().scale(0.045D));
            }
        }
        entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.SLIPPERY ? 2.35D : 1.8D));
        entity.hasImpulse = true;
    }

    private boolean matchesTarget(Entity owner, Entity entity, SpaceTargetGroup targetGroup) {
        return switch (targetGroup) {
            case EVERYTHING_EXCEPT_USER -> entity != owner;
            case EVERYTHING -> true;
            case LIVING_ENTITIES -> entity instanceof LivingEntity;
            case PROJECTILES -> entity instanceof Projectile || entity instanceof com.efkrdnz.magical.magic.service.InterceptableSpell;
            case PLAYERS -> entity instanceof Player;
        };
    }

    private SpaceRuleOperation operation(int ordinal) {
        SpaceRuleOperation[] operations = SpaceRuleOperation.values();
        return ordinal >= 0 && ordinal < operations.length ? operations[ordinal] : null;
    }

    private SpaceTargetGroup target(int ordinal) {
        SpaceTargetGroup[] targets = SpaceTargetGroup.values();
        return ordinal >= 0 && ordinal < targets.length ? targets[ordinal] : SpaceTargetGroup.EVERYTHING_EXCEPT_USER;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(GRAVITY_OPERATION, tag.getInt("GravityOperation"));
        entityData.set(GRAVITY_TARGET, tag.getInt("GravityTarget"));
        entityData.set(VELOCITY_OPERATION, tag.getInt("VelocityOperation"));
        entityData.set(VELOCITY_TARGET, tag.getInt("VelocityTarget"));
        entityData.set(ACCELERATION_OPERATION, tag.contains("AccelerationOperation") ? tag.getInt("AccelerationOperation") : NO_RULE);
        entityData.set(ACCELERATION_TARGET, tag.contains("AccelerationTarget") ? tag.getInt("AccelerationTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(AIR_RESISTANCE_OPERATION, tag.contains("AirResistanceOperation") ? tag.getInt("AirResistanceOperation") : NO_RULE);
        entityData.set(AIR_RESISTANCE_TARGET, tag.contains("AirResistanceTarget") ? tag.getInt("AirResistanceTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(PRESSURE_OPERATION, tag.contains("PressureOperation") ? tag.getInt("PressureOperation") : NO_RULE);
        entityData.set(PRESSURE_TARGET, tag.contains("PressureTarget") ? tag.getInt("PressureTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(MASS_OPERATION, tag.contains("MassOperation") ? tag.getInt("MassOperation") : NO_RULE);
        entityData.set(MASS_TARGET, tag.contains("MassTarget") ? tag.getInt("MassTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(TIME_FLOW_OPERATION, tag.contains("TimeFlowOperation") ? tag.getInt("TimeFlowOperation") : NO_RULE);
        entityData.set(TIME_FLOW_TARGET, tag.contains("TimeFlowTarget") ? tag.getInt("TimeFlowTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(VECTOR_FIELD_OPERATION, tag.contains("VectorFieldOperation") ? tag.getInt("VectorFieldOperation") : NO_RULE);
        entityData.set(VECTOR_FIELD_TARGET, tag.contains("VectorFieldTarget") ? tag.getInt("VectorFieldTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(ENTROPY_OPERATION, tag.contains("EntropyOperation") ? tag.getInt("EntropyOperation") : NO_RULE);
        entityData.set(ENTROPY_TARGET, tag.contains("EntropyTarget") ? tag.getInt("EntropyTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(FRICTION_OPERATION, tag.getInt("FrictionOperation"));
        entityData.set(FRICTION_TARGET, tag.getInt("FrictionTarget"));
        // Guarded: a world saved before boundary and collision existed has no such keys, and
        // an unguarded getInt would read 0 there, which is a real operation rather than "none".
        entityData.set(BOUNDARY_OPERATION, tag.contains("BoundaryOperation") ? tag.getInt("BoundaryOperation") : NO_RULE);
        entityData.set(BOUNDARY_TARGET, tag.contains("BoundaryTarget") ? tag.getInt("BoundaryTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
        entityData.set(COLLISION_OPERATION, tag.contains("CollisionOperation") ? tag.getInt("CollisionOperation") : NO_RULE);
        entityData.set(COLLISION_TARGET, tag.contains("CollisionTarget") ? tag.getInt("CollisionTarget") : SpaceTargetGroup.EVERYTHING_EXCEPT_USER.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GravityOperation", entityData.get(GRAVITY_OPERATION));
        tag.putInt("GravityTarget", entityData.get(GRAVITY_TARGET));
        tag.putInt("VelocityOperation", entityData.get(VELOCITY_OPERATION));
        tag.putInt("VelocityTarget", entityData.get(VELOCITY_TARGET));
        tag.putInt("AccelerationOperation", entityData.get(ACCELERATION_OPERATION));
        tag.putInt("AccelerationTarget", entityData.get(ACCELERATION_TARGET));
        tag.putInt("AirResistanceOperation", entityData.get(AIR_RESISTANCE_OPERATION));
        tag.putInt("AirResistanceTarget", entityData.get(AIR_RESISTANCE_TARGET));
        tag.putInt("PressureOperation", entityData.get(PRESSURE_OPERATION));
        tag.putInt("PressureTarget", entityData.get(PRESSURE_TARGET));
        tag.putInt("MassOperation", entityData.get(MASS_OPERATION));
        tag.putInt("MassTarget", entityData.get(MASS_TARGET));
        tag.putInt("TimeFlowOperation", entityData.get(TIME_FLOW_OPERATION));
        tag.putInt("TimeFlowTarget", entityData.get(TIME_FLOW_TARGET));
        tag.putInt("VectorFieldOperation", entityData.get(VECTOR_FIELD_OPERATION));
        tag.putInt("VectorFieldTarget", entityData.get(VECTOR_FIELD_TARGET));
        tag.putInt("EntropyOperation", entityData.get(ENTROPY_OPERATION));
        tag.putInt("EntropyTarget", entityData.get(ENTROPY_TARGET));
        tag.putInt("FrictionOperation", entityData.get(FRICTION_OPERATION));
        tag.putInt("FrictionTarget", entityData.get(FRICTION_TARGET));
        tag.putInt("BoundaryOperation", entityData.get(BOUNDARY_OPERATION));
        tag.putInt("BoundaryTarget", entityData.get(BOUNDARY_TARGET));
        tag.putInt("CollisionOperation", entityData.get(COLLISION_OPERATION));
        tag.putInt("CollisionTarget", entityData.get(COLLISION_TARGET));
    }

    // ---------------------------------------------------------------- boundary

    /** Largest radius a subspace can have, so callers can bound their search for one. */
    public static final float MAX_RADIUS = 16.0F;

    /** True when this subspace is set to bounce projectiles rather than let them land. */
    public boolean hasProjectileBlockRicochet() {
        return operation(entityData.get(COLLISION_OPERATION)) == SpaceRuleOperation.RICOCHET_COLLISION
                && target(entityData.get(COLLISION_TARGET)) == SpaceTargetGroup.PROJECTILES;
    }

    public boolean containsEntity(Entity entity) {
        return entity.isAlive() && entityBoundaryPoint(entity).distanceToSqr(position()) <= radius() * radius();
    }

    /**
     * Reflects a projectile off the block face it was about to hit. Called from the projectile
     * impact event rather than the rule loop, because a block strike ends the projectile before
     * the next tick would ever reach it.
     */
    public void ricochetProjectileFromBlock(Projectile projectile, BlockHitResult hitResult) {
        if (!containsEntity(projectile)) {
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-5D) {
            return;
        }
        Vec3 normal = new Vec3(hitResult.getDirection().getStepX(), hitResult.getDirection().getStepY(), hitResult.getDirection().getStepZ());
        double intoSurface = motion.dot(normal);
        Vec3 reflected = intoSurface < 0.0D ? motion.subtract(normal.scale(intoSurface * 2.0D)) : motion.add(normal.scale(0.18D));
        projectile.setDeltaMovement(capMotion(reflected.scale(1.04D), 3.4D));
        projectile.teleportTo(hitResult.getLocation().x + normal.x * 0.08D,
                hitResult.getLocation().y + normal.y * 0.08D,
                hitResult.getLocation().z + normal.z * 0.08D);
        projectile.hasImpulse = true;
        projectile.fallDistance = 0.0F;
    }

    private boolean hasBoundaryRule() {
        return entityData.get(BOUNDARY_OPERATION) != NO_RULE;
    }

    /**
     * Acts on entities near the shell. Attraction reaches the whole volume; the other boundary
     * rules only bite within {@link #BOUNDARY_THICKNESS} of the surface, so the middle of a
     * subspace stays free to move through.
     */
    private void applyBoundary(Entity owner, Entity entity, DomainPass pass) {
        if (UnwakingCapabilities.controlled(entity)) return;
        SpaceRuleOperation operation = operation(entityData.get(BOUNDARY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(BOUNDARY_TARGET)))) {
            return;
        }
        Vec3 fromCentre = entityBoundaryPoint(entity).subtract(position());
        double distance = fromCentre.length();
        if (distance < 1.0E-5D) {
            return;
        }
        double signedDistance = distance - radius();
        Vec3 radial = fromCentre.normalize();
        if (operation == SpaceRuleOperation.ATTRACT_BOUNDARY) {
            if (pass.moves()) {
                attractToBoundary(entity, radial, signedDistance);
            }
            return;
        }
        if (Math.abs(signedDistance) > BOUNDARY_THICKNESS) {
            return;
        }
        switch (operation) {
            case SEAL_BOUNDARY -> sealBoundary(entity, radial, signedDistance, pass);
            case REPEL_BOUNDARY -> {
                if (pass.moves()) {
                    repelBoundary(entity, radial, signedDistance);
                }
            }
            case WRAP_BOUNDARY -> wrapBoundary(entity, radial, signedDistance, pass);
            default -> {
            }
        }
    }

    /** Pins the entity to the shell and kills any motion trying to cross it. */
    private void sealBoundary(Entity entity, Vec3 radial, double signedDistance, DomainPass pass) {
        boolean inside = signedDistance <= 0.0D;
        entity.fallDistance = 0.0F;
        // A body is moved by whoever owns its position - the server, which syncs a player's teleport.
        if (pass.consequences()) {
            double targetDistance = radius() + (inside ? -0.72D : 0.72D);
            moveByCorrection(entity, position().add(radial.scale(targetDistance)).subtract(entityBoundaryPoint(entity)));
        }
        if (!pass.moves()) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double outwardSpeed = motion.dot(radial);
        if ((inside && outwardSpeed > 0.0D) || (!inside && outwardSpeed < 0.0D)) {
            motion = motion.subtract(radial.scale(outwardSpeed * 1.35D));
        }
        entity.setDeltaMovement(capMotion(motion.add(radial.scale(inside ? -0.12D : 0.12D)), 2.35D));
        entity.hasImpulse = true;
    }

    /** Pushes away from the shell, hardest right at the surface. */
    private void repelBoundary(Entity entity, Vec3 radial, double signedDistance) {
        boolean inside = signedDistance <= 0.0D;
        double strength = 0.08D + (1.0D - Math.min(1.0D, Math.abs(signedDistance) / BOUNDARY_THICKNESS)) * 0.22D;
        Vec3 direction = inside ? radial.scale(-1.0D) : radial;
        entity.setDeltaMovement(capMotion(entity.getDeltaMovement().add(direction.scale(strength)), 2.65D));
        entity.hasImpulse = true;
    }

    /** Draws everything toward the shell, so the volume hollows out. */
    private void attractToBoundary(Entity entity, Vec3 radial, double signedDistance) {
        double strength = Mth.clamp(Math.abs(signedDistance) / Math.max(1.0D, radius()), 0.08D, 0.26D);
        Vec3 direction = signedDistance <= 0.0D ? radial : radial.scale(-1.0D);
        entity.setDeltaMovement(capMotion(entity.getDeltaMovement().add(direction.scale(strength)), 2.55D));
        entity.hasImpulse = true;
    }

    /** Teleports across the sphere, so leaving one side re-enters from the other. */
    private void wrapBoundary(Entity entity, Vec3 radial, double signedDistance, DomainPass pass) {
        boolean inside = signedDistance <= 0.0D;
        entity.fallDistance = 0.0F;
        if (pass.consequences()) {
            double targetDistance = inside ? radius() - 1.05D : radius() + 0.15D;
            moveByCorrection(entity, position().subtract(radial.scale(targetDistance)).subtract(entityBoundaryPoint(entity)));
        }
        if (!pass.moves()) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(capMotion(motion.subtract(radial.scale(motion.dot(radial) * 1.8D)), 2.65D));
        entity.hasImpulse = true;
    }

    // ---------------------------------------------------------------- collision

    /**
     * Rewrites how entities inside the subspace bump into each other. Contacts are found per
     * entity against its immediate neighbours, so cost scales with crowding rather than volume.
     */
    private void applyCollision(Entity owner, Entity entity, DomainPass pass) {
        SpaceRuleOperation operation = operation(entityData.get(COLLISION_OPERATION));
        SpaceTargetGroup targetGroup = target(entityData.get(COLLISION_TARGET));
        if (operation == null || !matchesTarget(owner, entity, targetGroup)) {
            return;
        }
        if (operation == SpaceRuleOperation.RICOCHET_COLLISION && targetGroup == SpaceTargetGroup.PROJECTILES && entity instanceof Projectile) {
            return;
        }
        AABB contactArea = entity.getBoundingBox().inflate(0.42D);
        for (Entity other : level().getEntities(entity, contactArea,
                candidate -> candidate.isAlive() && candidate != this && candidate != entity && !(candidate instanceof SpaceSubspaceEntity))) {
            if (entityBoundaryPoint(other).distanceToSqr(position()) > radius() * radius()) {
                continue;
            }
            Vec3 normal = collisionNormal(entity, other);
            if (normal.lengthSqr() < 1.0E-5D) {
                continue;
            }
            switch (operation) {
                case DISABLE_COLLISION -> {
                    if (pass.moves()) {
                        softenCollision(entity, normal, 0.055D);
                    }
                }
                case INTENSIFY_COLLISION -> intensifyCollision(entity, other, normal, pass);
                case SELECTIVE_COLLISION -> {
                    if (pass.moves() && !matchesTarget(owner, other, targetGroup)) {
                        softenCollision(entity, normal, 0.09D);
                    }
                }
                case RICOCHET_COLLISION -> {
                    // A bounce forgets the fall on both sides, the way the boundary laws do: the
                    // client so it stops counting it, the server so it stops billing it.
                    entity.fallDistance = 0.0F;
                    if (pass.moves()) {
                        ricochetCollision(entity, normal);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Direction from the other entity to this one; falls back to a spin when they overlap exactly. */
    private Vec3 collisionNormal(Entity entity, Entity other) {
        Vec3 fromOther = entityBoundaryPoint(entity).subtract(entityBoundaryPoint(other));
        if (fromOther.lengthSqr() > 1.0E-5D) {
            return fromOther.normalize();
        }
        double seed = tickCount * 0.47D + entity.getId() * 1.31D + other.getId() * 0.73D;
        return new Vec3(Math.sin(seed), 0.0D, Math.cos(seed)).normalize();
    }

    private void softenCollision(Entity entity, Vec3 normal, double slipStrength) {
        Vec3 motion = entity.getDeltaMovement();
        double intoSurface = motion.dot(normal);
        if (intoSurface < 0.0D) {
            motion = motion.subtract(normal.scale(intoSurface));
        }
        Vec3 slip = motion.lengthSqr() > 1.0E-5D ? motion.normalize().scale(slipStrength) : normal.scale(slipStrength * 0.35D);
        entity.setDeltaMovement(capMotion(motion.add(slip), 2.45D));
        entity.hasImpulse = true;
    }

    private void intensifyCollision(Entity entity, Entity other, Vec3 normal, DomainPass pass) {
        Vec3 motion = entity.getDeltaMovement();
        double relativeImpact = Math.max(0.05D, -motion.dot(normal) + other.getDeltaMovement().dot(normal));
        if (pass.moves()) {
            double strength = Mth.clamp(0.11D + relativeImpact * 0.24D, 0.11D, 0.42D);
            entity.setDeltaMovement(capMotion(motion.add(normal.scale(strength)), 2.8D));
            entity.hasImpulse = true;
        }
        // Staggered by entity id so a crowd does not all take damage on the same tick.
        if (pass.consequences() && entity instanceof LivingEntity living && tickCount % 10 == Math.floorMod(entity.getId(), 10)) {
            living.hurt(damageSources().magic(), Mth.clamp((float) (1.0D + relativeImpact * 2.2D), 1.0F, 4.0F));
        }
    }

    private void ricochetCollision(Entity entity, Vec3 normal) {
        Vec3 motion = entity.getDeltaMovement();
        double intoSurface = motion.dot(normal);
        Vec3 reflected = intoSurface < 0.0D
                ? motion.subtract(normal.scale(intoSurface * 2.0D)).scale(1.08D)
                : motion.add(normal.scale(0.18D));
        entity.setDeltaMovement(capMotion(reflected.add(normal.scale(0.08D)), 3.1D));
        entity.hasImpulse = true;
    }

    // ---------------------------------------------------------------- controlled gravity

    private double gravityVerticalLimit(Entity entity, SpaceRuleOperation operation) {
        if (operation != SpaceRuleOperation.REMOVE_GRAVITY) {
            return 1.2D;
        }
        return entity instanceof Projectile ? 3.4D : 2.4D;
    }

    /** Hands a player the controls: they fly under their own input rather than being pushed. */
    private double controlFlight(Entity entity, Vec3 motion) {
        if (entity instanceof Player player) {
            return player.getAbilities().flying ? motion.y : Math.max(0.0D, motion.y);
        }
        return motion.y;
    }
}
