package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
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
import net.minecraft.world.phys.Vec3;

public final class SpaceSubspaceEntity extends Entity {
    private static final int NO_RULE = -1;
    private static final int LIFE_TICKS = 20 * 60 * 10;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FOLLOW_OWNER = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpaceSubspaceEntity.class, EntityDataSerializers.INT);
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
    private final Set<Integer> reversedAccelerationEntityIds = new HashSet<>();
    private UUID ownerUuid;

    public SpaceSubspaceEntity(EntityType<? extends SpaceSubspaceEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SpaceSubspaceEntity create(ServerLevel level, LivingEntity owner, float radius, boolean followOwner) {
        SpaceSubspaceEntity entity = new SpaceSubspaceEntity(MagicalEntities.SPACE_SUBSPACE.get(), level);
        entity.ownerUuid = owner.getUUID();
        entity.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        entity.entityData.set(RADIUS, Mth.clamp(radius, 5.0F, 16.0F));
        entity.entityData.set(FOLLOW_OWNER, followOwner);
        entity.entityData.set(OWNER_ID, owner.getId());
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 5.0F);
        builder.define(FOLLOW_OWNER, false);
        builder.define(OWNER_ID, -1);
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
        if (!(owner instanceof LivingEntity livingOwner) || !owner.isAlive() || tickCount > LIFE_TICKS) {
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
        applyRules(livingOwner);
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
        }
    }

    private void applyRules(LivingEntity owner) {
        AABB area = new AABB(getX() - radius(), getY() - radius(), getZ() - radius(), getX() + radius(), getY() + radius(), getZ() + radius());
        for (Entity entity : level().getEntities(this, area, target -> target.isAlive() && target != this && !(target instanceof SpaceSubspaceEntity))) {
            if (entity.position().distanceToSqr(position()) > radius() * radius()) {
                continue;
            }
            applyGravity(owner, entity);
            applyVelocity(owner, entity);
            applyAcceleration(owner, entity);
            applyAirResistance(owner, entity);
            applyPressure(owner, entity);
            applyMass(owner, entity);
            applyTimeFlow(owner, entity);
            applyVectorField(owner, entity);
            applyEntropy(owner, entity);
            applyFriction(owner, entity);
        }
    }

    private void applyGravity(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(GRAVITY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(GRAVITY_TARGET)))) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double y = switch (operation) {
            case REMOVE_GRAVITY -> Math.max(0.0D, motion.y);
            case DECREASE_GRAVITY -> motion.y + 0.035D;
            case INCREASE_GRAVITY -> motion.y - 0.075D;
            case REVERSE_GRAVITY -> motion.y + 0.13D;
            default -> motion.y;
        };
        entity.setDeltaMovement(motion.x, Mth.clamp(y, -1.2D, 1.2D), motion.z);
        entity.hasImpulse = true;
    }

    private void applyVelocity(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(VELOCITY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(VELOCITY_TARGET)))) {
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

    private void applyAcceleration(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(ACCELERATION_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(ACCELERATION_TARGET)))) {
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

    private void applyAirResistance(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(AIR_RESISTANCE_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(AIR_RESISTANCE_TARGET)))) {
            return;
        }
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
        if (entity instanceof LivingEntity living) {
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

    private void applyPressure(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(PRESSURE_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(PRESSURE_TARGET)))) {
            return;
        }
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
        if (entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.CRUSH_PRESSURE && tickCount % 12 == Math.floorMod(entity.getId(), 12)) {
                MagicDamageService.hurt(living, damageSources().magic(), 1.25F, MagicContent.MANIPULATE_SPACE.id(), false);
            } else if ((operation == SpaceRuleOperation.IMPLODE_PRESSURE || operation == SpaceRuleOperation.BURST_PRESSURE)
                    && tickCount % 18 == Math.floorMod(entity.getId(), 18)) {
                MagicDamageService.hurt(living, damageSources().magic(), 0.75F, MagicContent.MANIPULATE_SPACE.id(), false);
            }
        }
    }

    private void applyMass(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(MASS_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(MASS_TARGET)))) {
            return;
        }
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
        if (entity instanceof LivingEntity living) {
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

    private void applyTimeFlow(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(TIME_FLOW_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(TIME_FLOW_TARGET)))) {
            return;
        }
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
        if (entity instanceof LivingEntity living) {
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

    private void applyVectorField(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(VECTOR_FIELD_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(VECTOR_FIELD_TARGET)))) {
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

    private void applyEntropy(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(ENTROPY_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(ENTROPY_TARGET)))) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        Vec3 next = switch (operation) {
            case STABILIZE_ENTROPY -> stabilizeEntropy(entity, motion);
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
        if (entity instanceof LivingEntity living && operation == SpaceRuleOperation.CHAOTIC_MOTION && tickCount % 30 == Math.floorMod(entity.getId(), 30)) {
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 45, 0, false, false));
        }
    }

    private Vec3 stabilizeEntropy(Entity entity, Vec3 motion) {
        Vec3 next = motion.scale(0.82D);
        if (entity instanceof LivingEntity living) {
            living.removeEffect(MobEffects.CONFUSION);
        }
        return next;
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

    private void applyFriction(LivingEntity owner, Entity entity) {
        SpaceRuleOperation operation = operation(entityData.get(FRICTION_OPERATION));
        if (operation == null || !matchesTarget(owner, entity, target(entityData.get(FRICTION_TARGET)))) {
            return;
        }
        if (!entity.onGround()) {
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
        if (entity instanceof LivingEntity living) {
            if (operation == SpaceRuleOperation.STICKY) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
            } else if (operation == SpaceRuleOperation.SLIPPERY) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 1, false, false));
            }
        }
        entity.setDeltaMovement(capMotion(next, operation == SpaceRuleOperation.SLIPPERY ? 2.35D : 1.8D));
        entity.hasImpulse = true;
    }

    private boolean matchesTarget(LivingEntity owner, Entity entity, SpaceTargetGroup targetGroup) {
        return switch (targetGroup) {
            case EVERYTHING_EXCEPT_USER -> entity != owner;
            case EVERYTHING -> true;
            case LIVING_ENTITIES -> entity instanceof LivingEntity;
            case PROJECTILES -> entity instanceof Projectile || entity instanceof com.efkrdnz.magical.magic.service.InterceptableSpell;
            case PLAYERS -> entity instanceof Player;
        };
    }

    private Vec3 capMotion(Vec3 motion, double maxLength) {
        if (motion.lengthSqr() <= maxLength * maxLength) {
            return motion;
        }
        return motion.normalize().scale(maxLength);
    }

    private SpaceRuleOperation operation(int ordinal) {
        SpaceRuleOperation[] operations = SpaceRuleOperation.values();
        return ordinal >= 0 && ordinal < operations.length ? operations[ordinal] : null;
    }

    private SpaceTargetGroup target(int ordinal) {
        SpaceTargetGroup[] targets = SpaceTargetGroup.values();
        return ordinal >= 0 && ordinal < targets.length ? targets[ordinal] : SpaceTargetGroup.EVERYTHING_EXCEPT_USER;
    }

    private void followOwnerClient() {
        if (!followsOwner()) {
            return;
        }
        Entity owner = level().getEntity(entityData.get(OWNER_ID));
        if (owner != null && owner.isAlive()) {
            setPos(Mth.lerp(0.55D, getX(), owner.getX()), Mth.lerp(0.55D, getY(), owner.getY() + owner.getBbHeight() * 0.5D), Mth.lerp(0.55D, getZ(), owner.getZ()));
        }
    }

    private Entity ownerEntity() {
        return ownerUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(ownerUuid);
    }

    private void clearOwnerState(Entity owner) {
        if (owner instanceof net.minecraft.server.level.ServerPlayer player) {
            var state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (state.activeSubspaceEntityId() == getId()) {
                state.setActiveSubspaceEntityId(-1);
                state.sync(player);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(FOLLOW_OWNER, tag.getBoolean("FollowOwner"));
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putFloat("Radius", radius());
        tag.putBoolean("FollowOwner", followsOwner());
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

    public boolean followsOwner() {
        return entityData.get(FOLLOW_OWNER);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }
}
