package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A rolling solid body with real physics (gravity, step-up, wall reflection, ground friction) and
 * a synced roll angle. In vehicle mode the rider steers it by looking.
 */
public class RollingBodyEntity extends SpellEffectEntity {
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(RollingBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(RollingBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> VEHICLE = SynchedEntityData.defineId(RollingBodyEntity.class, EntityDataSerializers.BOOLEAN);
    private float gravity = 0.06F;
    private float friction = 0.965F;
    private float wallBounce = 0.6F;
    private float distanceRolled;

    public RollingBodyEntity(EntityType<? extends RollingBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    public static RollingBodyEntity create(ServerLevel level, SpellEffectEntity template, Vec3 pos, float size, Vec3 velocity, boolean vehicle) {
        RollingBodyEntity entity = new RollingBodyEntity(MagicalEntities.ROLLING_BODY.get(), level);
        CompoundTag tag = new CompoundTag();
        template.addAdditionalSaveData(tag);
        tag.remove("Synced");
        tag.remove("Data");
        entity.readAdditionalSaveData(tag);
        entity.setLife(template.life());
        entity.entityData.set(SIZE, size);
        entity.entityData.set(VEHICLE, vehicle);
        entity.refreshDimensions();
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(velocity);
        if (velocity.lengthSqr() > 1.0E-6D) {
            entity.setDirection(velocity.normalize());
        }
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLL, 0.0F);
        builder.define(SIZE, 1.2F);
        builder.define(VEHICLE, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = Math.max(0.3F, entityData.get(SIZE));
        return EntityDimensions.fixed(s, s);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel) {
            Vec3 before = position();
            Vec3 v = getDeltaMovement();
            if (isVehicle() && getControllingPassenger() instanceof LivingEntity rider) {
                Vec3 look = rider.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                if (flat.lengthSqr() > 1.0E-4D) {
                    flat = flat.normalize().scale(speed() > 0.0F ? speed() : 0.6F);
                    v = new Vec3(flat.x, v.y, flat.z);
                }
            }
            v = v.add(0.0D, -gravity, 0.0D);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            Vec3 after = position();
            Vec3 moved = after.subtract(before);
            // wall reflection: blocked axes bounce
            Vec3 nv = getDeltaMovement();
            if (horizontalCollision) {
                double nx = Math.abs(moved.x) < 1.0E-4D && Math.abs(v.x) > 1.0E-3D ? -v.x * wallBounce : nv.x;
                double nz = Math.abs(moved.z) < 1.0E-4D && Math.abs(v.z) > 1.0E-3D ? -v.z * wallBounce : nv.z;
                nv = new Vec3(nx, nv.y, nz);
            }
            if (onGround()) {
                nv = new Vec3(nv.x * friction, Math.max(0.0D, nv.y), nv.z * friction);
            }
            setDeltaMovement(nv);
            double dist = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            distanceRolled += (float) dist;
            float r = Math.max(0.2F, entityData.get(SIZE) * 0.5F);
            entityData.set(ROLL, (float) Math.toDegrees(distanceRolled / r) % 360.0F);
            if (dist > 1.0E-4D) {
                setDirection(new Vec3(moved.x, 0.0D, moved.z).normalize());
            }
            hurtMarked = true;
        }
        super.tick();
    }

    @Override
    public float effectValue() {
        return entityData.get(ROLL);
    }

    @Override
    public float effectRadius() {
        return entityData.get(SIZE) * 0.5F;
    }

    public float bodySize() {
        return entityData.get(SIZE);
    }

    public float distanceRolled() {
        return distanceRolled;
    }

    public boolean isVehicleMode() {
        return entityData.get(VEHICLE);
    }

    public void setPhysics(float gravity, float friction, float wallBounce) {
        this.gravity = gravity;
        this.friction = friction;
        this.wallBounce = wallBounce;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return isVehicleMode();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isVehicleMode() && getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, dimensions.height() * 0.85D, 0.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BodySize")) {
            entityData.set(SIZE, tag.getFloat("BodySize"));
            entityData.set(VEHICLE, tag.getBoolean("Vehicle"));
            distanceRolled = tag.getFloat("Rolled");
            refreshDimensions();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BodySize", bodySize());
        tag.putBoolean("Vehicle", isVehicleMode());
        tag.putFloat("Rolled", distanceRolled);
    }
}
