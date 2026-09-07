package com.efkrdnz.magical.entity;

import java.util.UUID;

import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.art.ForgeZones;
import com.efkrdnz.magical.registry.MagicalEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The lingering sibling of {@link ForgeEffectEntity}: an area an Art leaves standing for a while -
 * a flame disc, a gravity well, a cyclone, or a bolt that has not fallen yet.
 *
 * <p>Unlike the one-shot effect entity this one <em>persists</em>. Everything it needs to keep
 * working after a save and reload is written to NBT, and the per-tick behaviour is chosen from the
 * synced {@link ForgeZoneKind} ordinal through {@link ForgeZones} rather than from a captured
 * lambda, which would not survive the round trip at all.</p>
 */
public final class ForgeZoneEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND = defineInt();
    private static final EntityDataAccessor<Integer> COLOR = defineInt();
    private static final EntityDataAccessor<Integer> COLOR2 = defineInt();
    private static final EntityDataAccessor<Integer> LIFE = defineInt();
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(ForgeZoneEntity.class,
            EntityDataSerializers.FLOAT);

    private static final String TAG_KIND = "Kind";
    private static final String TAG_COLOR = "Color";
    private static final String TAG_COLOR2 = "Color2";
    private static final String TAG_LIFE = "Life";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_POWER = "Power";
    private static final String TAG_AGE = "Age";
    private static final String TAG_OWNER = "Owner";

    private static final float MIN_RADIUS = 0.5f;
    private static final int MIN_LIFE = 1;

    /** The damage (or heal) one pulse of this zone is worth; meaningless for the kinds that ignore it. */
    private float power;
    private UUID ownerUuid;
    private int age;

    public ForgeZoneEntity(EntityType<? extends ForgeZoneEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    /** Opens a zone at {@code pos}. The owner is remembered by UUID, so a relog does not orphan it. */
    public static ForgeZoneEntity open(ServerLevel level, ServerPlayer owner, Vec3 pos, ForgeZoneKind kind,
            int color, int color2, float radius, int life, float power) {
        ForgeZoneEntity zone = new ForgeZoneEntity(MagicalEntities.FORGE_ZONE.get(), level);
        zone.setPos(pos.x, pos.y, pos.z);
        zone.ownerUuid = owner == null ? null : owner.getUUID();
        zone.power = power;
        zone.entityData.set(KIND, kind.ordinal());
        zone.entityData.set(COLOR, color);
        zone.entityData.set(COLOR2, color2);
        zone.entityData.set(RADIUS, Math.max(MIN_RADIUS, radius));
        zone.entityData.set(LIFE, Math.max(MIN_LIFE, life));
        level.addFreshEntity(zone);
        return zone;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, ForgeZoneKind.PYRE_WHEEL.ordinal());
        builder.define(COLOR, 0xFFFFFF);
        builder.define(COLOR2, 0xFFFFFF);
        builder.define(LIFE, 20);
        builder.define(RADIUS, 2.5F);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (age > life()) {
            discard();
            return;
        }
        ForgeZones.tick(server, this, owner(server), age);
    }

    /** The wielder if they are still online and in this level, else null - the zone then idles out. */
    private ServerPlayer owner(ServerLevel server) {
        if (ownerUuid == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(ownerUuid);
        return player != null && player.level() == server ? player : null;
    }

    public ForgeZoneKind kind() {
        return ForgeZoneKind.byOrdinal(entityData.get(KIND));
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int secondaryColor() {
        return entityData.get(COLOR2);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float power() {
        return power;
    }

    public int age() {
        return age;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt(TAG_KIND));
        entityData.set(COLOR, tag.getInt(TAG_COLOR));
        entityData.set(COLOR2, tag.getInt(TAG_COLOR2));
        entityData.set(LIFE, Math.max(MIN_LIFE, tag.getInt(TAG_LIFE)));
        entityData.set(RADIUS, Math.max(MIN_RADIUS, tag.getFloat(TAG_RADIUS)));
        power = tag.getFloat(TAG_POWER);
        age = tag.getInt(TAG_AGE);
        ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_KIND, entityData.get(KIND));
        tag.putInt(TAG_COLOR, color());
        tag.putInt(TAG_COLOR2, secondaryColor());
        tag.putInt(TAG_LIFE, life());
        tag.putFloat(TAG_RADIUS, radius());
        tag.putFloat(TAG_POWER, power);
        tag.putInt(TAG_AGE, age);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
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

    private static EntityDataAccessor<Integer> defineInt() {
        return SynchedEntityData.defineId(ForgeZoneEntity.class, EntityDataSerializers.INT);
    }
}
