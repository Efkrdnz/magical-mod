package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.SpacePocketService;
import com.efkrdnz.magical.magic.SpaceWalkerService;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpacePocketPortalEntity extends Entity {
    public static final int MODE_ENTRY_PORTAL = 0;
    public static final int MODE_EXIT_DOOR = 1;

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SpacePocketPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpacePocketPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpacePocketPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(SpacePocketPortalEntity.class, EntityDataSerializers.FLOAT);
    private final Set<UUID> recentlyTeleported = new HashSet<>();
    private ResourceLocation targetDimension;
    private Vec3 targetPosition = Vec3.ZERO;
    private float targetYaw;
    private float targetPitch;
    private UUID closeWhenEnteredBy;
    private boolean slowFallingOnArrival;

    public SpacePocketPortalEntity(EntityType<? extends SpacePocketPortalEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static SpacePocketPortalEntity create(ServerLevel level, Vec3 position, float yaw, int mode, int life, ResourceKey<Level> targetDimension, Vec3 targetPosition, float targetYaw, float targetPitch) {
        return create(level, position, yaw, mode, life, targetDimension, targetPosition, targetYaw, targetPitch, null, false);
    }

    public static SpacePocketPortalEntity create(ServerLevel level, Vec3 position, float yaw, int mode, int life, ResourceKey<Level> targetDimension, Vec3 targetPosition, float targetYaw, float targetPitch, UUID closeWhenEnteredBy) {
        return create(level, position, yaw, mode, life, targetDimension, targetPosition, targetYaw, targetPitch, closeWhenEnteredBy, false);
    }

    public static SpacePocketPortalEntity create(ServerLevel level, Vec3 position, float yaw, int mode, int life, ResourceKey<Level> targetDimension, Vec3 targetPosition, float targetYaw, float targetPitch, UUID closeWhenEnteredBy, boolean slowFallingOnArrival) {
        SpacePocketPortalEntity portal = new SpacePocketPortalEntity(MagicalEntities.SPACE_POCKET_PORTAL.get(), level);
        portal.setPos(position.x, position.y, position.z);
        portal.entityData.set(MODE, mode);
        portal.entityData.set(LIFE, life);
        portal.entityData.set(RADIUS, mode == MODE_EXIT_DOOR ? 1.45F : 1.9F);
        portal.entityData.set(YAW, yaw);
        portal.targetDimension = targetDimension.location();
        portal.targetPosition = targetPosition;
        portal.targetYaw = targetYaw;
        portal.targetPitch = targetPitch;
        portal.closeWhenEnteredBy = closeWhenEnteredBy;
        portal.slowFallingOnArrival = slowFallingOnArrival;
        return portal;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, MODE_ENTRY_PORTAL);
        builder.define(LIFE, 200);
        builder.define(RADIUS, 1.5F);
        builder.define(YAW, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int life = entityData.get(LIFE);
        if (life >= 0 && tickCount > life) {
            discard();
            return;
        }
        if (tickCount % 3 != 0) {
            return;
        }
        recentlyTeleported.removeIf(uuid -> level.getGameTime() % 20 == 0);
        AABB area = getBoundingBox().inflate(radius(), mode() == MODE_EXIT_DOOR ? 1.8D : 1.3D, radius());
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, player -> player.isAlive() && distanceToSqr(player) <= radius() * radius() + 2.0D)) {
            if (!recentlyTeleported.add(player.getUUID())) {
                continue;
            }
            SpacePocketService.teleportThroughPortal(player, targetDimension, targetPosition, targetYaw, targetPitch);
            if (slowFallingOnArrival) {
                SpaceWalkerService.applySlowFallingIfMidAir(player);
            }
            if (player.getUUID().equals(closeWhenEnteredBy)) {
                discard();
            }
            break;
        }
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float yaw() {
        return entityData.get(YAW);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(MODE, tag.getInt("Mode"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(YAW, tag.getFloat("Yaw"));
        if (tag.contains("TargetDimension")) {
            targetDimension = ResourceLocation.parse(tag.getString("TargetDimension"));
        }
        targetPosition = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
        targetYaw = tag.getFloat("TargetYaw");
        targetPitch = tag.getFloat("TargetPitch");
        if (tag.hasUUID("CloseWhenEnteredBy")) {
            closeWhenEnteredBy = tag.getUUID("CloseWhenEnteredBy");
        }
        slowFallingOnArrival = tag.getBoolean("SlowFallingOnArrival");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", mode());
        tag.putInt("Life", life());
        tag.putFloat("Radius", radius());
        tag.putFloat("Yaw", yaw());
        if (targetDimension != null) {
            tag.putString("TargetDimension", targetDimension.toString());
        }
        tag.putDouble("TargetX", targetPosition.x);
        tag.putDouble("TargetY", targetPosition.y);
        tag.putDouble("TargetZ", targetPosition.z);
        tag.putFloat("TargetYaw", targetYaw);
        tag.putFloat("TargetPitch", targetPitch);
        if (closeWhenEnteredBy != null) {
            tag.putUUID("CloseWhenEnteredBy", closeWhenEnteredBy);
        }
        tag.putBoolean("SlowFallingOnArrival", slowFallingOnArrival);
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
