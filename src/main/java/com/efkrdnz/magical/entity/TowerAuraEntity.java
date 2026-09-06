package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Invisible, persistent anchor that projects the massive flux aura around a dungeon tower.
 * Place it at the center of the tower footprint, at floor level: the aura cylinder is
 * anchored at the entity's Y and rises {@code height()} blocks. Radius and height can be
 * overridden via the {@code Radius} / {@code Height} NBT tags when summoning.
 */
public final class TowerAuraEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(TowerAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(TowerAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final double RENDER_DISTANCE_SQR = 512.0D * 512.0D;

    public TowerAuraEntity(EntityType<? extends TowerAuraEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Default fits a 47-block-diameter tower with a small breathing margin.
        builder.define(RADIUS, 26.0F);
        builder.define(HEIGHT, 320.0F);
    }

    @Override
    public void tick() {
        super.tick();
        // Menace at the crown: a visual-only lightning strike at the aura's peak,
        // on average every ~11 seconds (with occasional double strikes).
        if (level() instanceof ServerLevel server && random.nextInt(220) == 0) {
            strikeCrown(server);
            if (random.nextInt(4) == 0) {
                strikeCrown(server);
            }
        }
    }

    private void strikeCrown(ServerLevel server) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(getX(), getY() + height(), getZ());
            server.addFreshEntity(bolt);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Radius")) {
            entityData.set(RADIUS, Math.max(2.0F, tag.getFloat("Radius")));
        }
        if (tag.contains("Height")) {
            entityData.set(HEIGHT, Math.max(4.0F, tag.getFloat("Height")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", radius());
        tag.putFloat("Height", height());
    }

    /** Culling volume spanning the whole aura cylinder; used by the renderer. */
    public AABB cullingBounds() {
        double r = radius() + 4.0D;
        return new AABB(getX() - r, getY() - 2.0D, getZ() - r, getX() + r, getY() + height() + 4.0D, getZ() + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE_SQR;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public static TowerAuraEntity create(ServerLevel level, double x, double y, double z, float radius, float height) {
        TowerAuraEntity aura = new TowerAuraEntity(MagicalEntities.TOWER_AURA.get(), level);
        aura.setPos(x, y, z);
        aura.entityData.set(RADIUS, radius);
        aura.entityData.set(HEIGHT, height);
        return aura;
    }
}
