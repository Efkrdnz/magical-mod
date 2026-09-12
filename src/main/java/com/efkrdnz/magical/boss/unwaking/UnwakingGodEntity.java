package com.efkrdnz.magical.boss.unwaking;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class UnwakingGodEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(UnwakingGodEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ENCOUNTER_HEALTH = SynchedEntityData.defineId(UnwakingGodEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DOMAIN_BODY = SynchedEntityData.defineId(UnwakingGodEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID runId;
    private boolean controllerPlacement;
    private BlockPos shrine = BlockPos.ZERO;

    public UnwakingGodEntity(EntityType<? extends UnwakingGodEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1024).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override protected void registerGoals() {}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
        builder.define(ENCOUNTER_HEALTH, 36000F);
        builder.define(DOMAIN_BODY, false);
    }

    // Vanilla's max-health attribute caps at 1024. Keep the boss's larger pool local to this
    // entity instead of changing the global attribute range for every mob and player.
    @Override public double getAttributeValue(Holder<Attribute> attribute) {
        return attribute.equals(Attributes.MAX_HEALTH) ? entityData.get(ENCOUNTER_HEALTH) : super.getAttributeValue(attribute);
    }

    public void encounterHealth(float maximum) { entityData.set(ENCOUNTER_HEALTH, maximum); setHealth(maximum); }

    public UnwakingPhase phase() { return UnwakingPhase.values()[Math.clamp(entityData.get(PHASE), 0, UnwakingPhase.values().length - 1)]; }
    public void phase(UnwakingPhase phase) { entityData.set(PHASE, phase.ordinal()); }
    public void domainBody(boolean hidden) { entityData.set(DOMAIN_BODY,hidden); }
    public boolean domainBody() { return entityData.get(DOMAIN_BODY); }
    public UUID runId() { return runId; }
    public BlockPos shrine() { return shrine; }
    public void bind(BlockPos shrine, UUID runId) { this.shrine = shrine.immutable(); this.runId = runId; }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) UnwakingEncounterService.get(level.getServer()).validateBody(this);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return UnwakingEncounterService.get(level.getServer()).hurtBody(this, source, amount);
    }

    /** Only the controller can enter the ordinary damage pipeline after validating phase/ownership. */
    boolean receiveDamage(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, amount);
    }

    @Override public void setHealth(float health) {
        if (runId != null && level() instanceof ServerLevel level) health = UnwakingEncounterService.get(level.getServer()).clampHealth(this, health);
        super.setHealth(health);
    }

    void place(Vec3 position) {
        controllerPlacement = true;
        try { super.setPos(position.x, position.y, position.z); }
        finally { controllerPlacement = false; }
    }
    @Override public void setPos(double x, double y, double z) {
        if (runId == null || level().isClientSide() || controllerPlacement) super.setPos(x, y, z);
    }
    @Override public void teleportTo(double x, double y, double z) { /* Position belongs to the encounter controller. */ }
    @Override public void setDeltaMovement(Vec3 movement) { super.setDeltaMovement(Vec3.ZERO); }
    @Override public void knockback(double strength, double x, double z) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return !domainBody() && !phase().hiddenBody() && super.isPickable(); }
    @Override public void checkDespawn() {}

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("UnwakingShrine", shrine.asLong());
        tag.putFloat("UnwakingMaxHealth", getMaxHealth());
        if (runId != null) tag.putUUID("UnwakingRun", runId);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        float maximum = tag.getFloat("UnwakingMaxHealth");
        if (Float.isFinite(maximum) && maximum >= 2400 && maximum <= 4000000) entityData.set(ENCOUNTER_HEALTH, maximum);
        super.readAdditionalSaveData(tag);
        shrine = BlockPos.of(tag.getLong("UnwakingShrine"));
        runId = tag.hasUUID("UnwakingRun") ? tag.getUUID("UnwakingRun") : null;
    }
}
