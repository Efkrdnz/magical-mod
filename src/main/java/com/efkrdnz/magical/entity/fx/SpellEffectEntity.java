package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The generic non-physical effect entity: invisible gameplay carrier with a synced skill index,
 * owner/target, life, phase, radius, direction, seed, mode and a small data bag. Its server tick
 * delegates to the skill's {@link SpellBehavior}; the client draws it from the VisualProfile.
 * While its handler declares a counter window and the entity is still in its windup phase, nearby
 * hostile players are offered the counter QTE; a successful counter ends the effect.
 */
public class SpellEffectEntity extends Entity implements ProfiledEffect, CounterableSkillThreat {
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> EXTRA = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> VALUE = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(SpellEffectEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Common phase values behaviours agree on. */
    public static final byte PHASE_WINDUP = 0;
    public static final byte PHASE_ACTIVE = 1;
    public static final byte PHASE_CLOSING = 2;
    public static final byte PHASE_DONE = 3;

    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private UUID ownerUuid;
    private UUID targetUuid;
    private float damage;
    private float knockback;
    private float speed;
    private int duration;
    private CompoundTag serverData = new CompoundTag();
    private SpellBehavior behavior;
    private boolean spawned;

    public SpellEffectEntity(EntityType<? extends SpellEffectEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ------------------------------------------------------------------ spawning

    /** Spawn from a cast: stats, owner, skill and visual seed all come from the context. */
    public static SpellEffectEntity spawn(CastContext ctx, Vec3 pos, int life, float radius, Vec3 dir) {
        SpellEffectEntity entity = create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), pos, life, radius, dir, (int) (ctx.seed() & 63));
        entity.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
        ctx.level().addFreshEntity(entity);
        return entity;
    }

    public static SpellEffectEntity create(ServerLevel level, MagicSkillDefinition definition, MagicSkillResolvedStats stats, Entity owner, Vec3 pos, int life, float radius, Vec3 dir, int seed) {
        SpellEffectEntity entity = new SpellEffectEntity(MagicalEntities.SPELL_EFFECT.get(), level);
        entity.skillId = definition.id();
        entity.entityData.set(SKILL_INDEX, MagicContent.skillIndex(definition.id()));
        entity.setPos(pos.x, pos.y, pos.z);
        entity.entityData.set(LIFE, Math.max(1, life));
        entity.entityData.set(RADIUS, radius);
        entity.setDirection(dir);
        entity.entityData.set(SEED, seed & 63);
        if (owner != null) {
            entity.ownerUuid = owner.getUUID();
            entity.entityData.set(OWNER_ID, owner.getId());
        }
        if (stats != null) {
            entity.damage = stats.damage();
            entity.knockback = stats.knockback();
            entity.speed = stats.speed();
            entity.duration = stats.durationTicks();
        }
        return entity;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SKILL_INDEX, -1);
        builder.define(OWNER_ID, -1);
        builder.define(TARGET_ID, -1);
        builder.define(LIFE, 40);
        builder.define(PHASE, PHASE_WINDUP);
        builder.define(RADIUS, 1.0F);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 0.0F);
        builder.define(SEED, 0);
        builder.define(MODE, (byte) 0);
        builder.define(EXTRA, 0);
        builder.define(VALUE, 0.0F);
        builder.define(DATA, new CompoundTag());
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            if (!spawned) {
                spawned = true;
                behavior().onSpawn(this);
            }
            behavior().tick(this);
            if (!isRemoved()) {
                offerCounters();
            }
            if (!isRemoved() && life() > 0 && tickCount >= life()) {
                finish();
            }
        }
    }

    // ------------------------------------------------------------------ counter QTE

    private void offerCounters() {
        int window = SkillCastRegistry.has(skillId) ? SkillCastRegistry.get(skillId).counterWindowTicks() : 0;
        if (window <= 0 || (mode() & 2) != 0) {
            return; // not counterable, or a child of a controller
        }
        ServerLevel level = serverLevel();
        Entity owner = owner();
        double reach = Math.max(6.0D, radius() + 6.0D);
        AABB box = getBoundingBox().inflate(reach, reach * 0.75D, reach);
        boolean open = phase() == PHASE_WINDUP && tickCount < window;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p != owner && SkillTargets.isHostile(owner, p))) {
            if (open) {
                MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.25D, 0.0D), Math.max(3, window - tickCount));
            } else if (MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
        }
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public ResourceLocation counterSkillId() {
        return skillId;
    }

    @Override
    public MagicAttribute counterAttribute() {
        return definition().attribute();
    }

    @Override
    public Entity counterOwner() {
        return owner();
    }

    @Override
    public boolean canBeCounteredBy(LivingEntity defender) {
        return CounterableSkillThreat.super.canBeCounteredBy(defender) && !isRemoved() && phase() == PHASE_WINDUP;
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicCounterService.spawnClash(level, clashPosition, definition().color(), counterSkill.color());
        serverData.putBoolean("Countered", true);
        setPhase(PHASE_DONE);
        discard();
    }

    @Override
    public void onGluttonyCountered(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        CounterableSkillThreat.super.onGluttonyCountered(level, defender, clashPosition);
        setPhase(PHASE_DONE);
    }

    /** End the effect now: behaviour expiry hook, then discard. */
    public void finish() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel) {
            behavior().onExpire(this);
        }
        discard();
    }

    public SpellBehavior behavior() {
        if (behavior == null) {
            behavior = SpellBehaviors.get(skillId);
        }
        return behavior;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SkillId")) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("SkillId"));
            if (parsed != null && MagicContent.get(parsed) != null) {
                skillId = parsed;
                entityData.set(SKILL_INDEX, MagicContent.skillIndex(parsed));
            }
        }
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Target")) {
            targetUuid = tag.getUUID("Target");
        }
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(PHASE, tag.getByte("Phase"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DIR_X, tag.getFloat("DirX"));
        entityData.set(DIR_Y, tag.getFloat("DirY"));
        entityData.set(DIR_Z, tag.getFloat("DirZ"));
        entityData.set(SEED, tag.getInt("Seed"));
        entityData.set(MODE, tag.getByte("Mode"));
        entityData.set(EXTRA, tag.getInt("Extra"));
        entityData.set(VALUE, tag.getFloat("Value"));
        entityData.set(DATA, tag.getCompound("Synced"));
        damage = tag.getFloat("Damage");
        knockback = tag.getFloat("Knockback");
        speed = tag.getFloat("Speed");
        duration = tag.getInt("Duration");
        serverData = tag.getCompound("Data");
        spawned = true;
        behavior = null;
        if (level() instanceof ServerLevel) {
            behavior().onLoad(this);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("SkillId", skillId.toString());
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
        tag.putInt("Life", life());
        tag.putByte("Phase", phase());
        tag.putFloat("Radius", radius());
        tag.putFloat("DirX", entityData.get(DIR_X));
        tag.putFloat("DirY", entityData.get(DIR_Y));
        tag.putFloat("DirZ", entityData.get(DIR_Z));
        tag.putInt("Seed", entityData.get(SEED));
        tag.putByte("Mode", mode());
        tag.putInt("Extra", extra());
        tag.putFloat("Value", value());
        tag.put("Synced", entityData.get(DATA));
        tag.putFloat("Damage", damage);
        tag.putFloat("Knockback", knockback);
        tag.putFloat("Speed", speed);
        tag.putInt("Duration", duration);
        tag.put("Data", serverData);
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // ------------------------------------------------------------------ accessors

    public ResourceLocation skillId() {
        return skillId;
    }

    public MagicSkillDefinition definition() {
        MagicSkillDefinition definition = MagicContent.get(skillId);
        return definition != null ? definition : MagicContent.get(MagicContent.STARTER_SKILL);
    }

    @Override
    public VisualProfile profile() {
        return VisualProfiles.of(skillId);
    }

    @Override
    public int skillIndex() {
        return entityData.get(SKILL_INDEX);
    }

    @Override
    public int effectAge() {
        return tickCount;
    }

    @Override
    public int effectLife() {
        return life();
    }

    @Override
    public float effectPhase() {
        // behaviours may drive VALUE as the explicit phase by setting it in 0..1 and mode bit 2
        return life() <= 0 ? 0.5F : Math.max(0.0F, Math.min(1.0F, tickCount / (float) life()));
    }

    @Override
    public int effectSeed() {
        return entityData.get(SEED);
    }

    @Override
    public Vec3 effectDirection() {
        return direction();
    }

    @Override
    public float effectValue() {
        return value();
    }

    /** Mode bit 0 is the sneak variation; the bits above select the draw subset. */
    @Override
    public int effectDrawMode() {
        return (mode() & 0xFF) >> 1;
    }

    @Override
    public float effectRadius() {
        return radius();
    }

    @Override
    public Vec3 effectEndPoint() {
        Entity target = clientTarget();
        return target != null ? target.getBoundingBox().getCenter() : null;
    }

    @Override
    public CompoundTag effectData() {
        return syncedData();
    }

    public Entity owner() {
        if (level() instanceof ServerLevel serverLevel) {
            return ownerUuid != null ? serverLevel.getEntity(ownerUuid) : null;
        }
        int id = entityData.get(OWNER_ID);
        return id >= 0 ? level().getEntity(id) : null;
    }

    public LivingEntity livingOwner() {
        return owner() instanceof LivingEntity living ? living : null;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public Entity target() {
        if (level() instanceof ServerLevel serverLevel) {
            return targetUuid != null ? serverLevel.getEntity(targetUuid) : null;
        }
        return clientTarget();
    }

    private Entity clientTarget() {
        int id = entityData.get(TARGET_ID);
        return id >= 0 ? level().getEntity(id) : null;
    }

    public LivingEntity livingTarget() {
        return target() instanceof LivingEntity living ? living : null;
    }

    public void setTarget(Entity target) {
        targetUuid = target != null ? target.getUUID() : null;
        entityData.set(TARGET_ID, target != null ? target.getId() : -1);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public void setLife(int ticks) {
        entityData.set(LIFE, ticks);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    public void setPhase(byte phase) {
        entityData.set(PHASE, phase);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public void setRadius(float radius) {
        entityData.set(RADIUS, radius);
    }

    public Vec3 direction() {
        return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
    }

    public void setDirection(Vec3 dir) {
        entityData.set(DIR_X, (float) dir.x);
        entityData.set(DIR_Y, (float) dir.y);
        entityData.set(DIR_Z, (float) dir.z);
    }

    public byte mode() {
        return entityData.get(MODE);
    }

    public void setMode(byte mode) {
        entityData.set(MODE, mode);
    }

    public boolean sneakMode() {
        return (mode() & 1) != 0;
    }

    public int extra() {
        return entityData.get(EXTRA);
    }

    public void setExtra(int value) {
        entityData.set(EXTRA, value);
    }

    public float value() {
        return entityData.get(VALUE);
    }

    public void setValue(float value) {
        entityData.set(VALUE, value);
    }

    /** Small synced compound for irregular payloads (cell lists, paths); replace, don't mutate. */
    public CompoundTag syncedData() {
        return entityData.get(DATA);
    }

    public void setSyncedData(CompoundTag tag) {
        entityData.set(DATA, tag);
    }

    /** Server-only persistent scratch data. */
    public CompoundTag serverData() {
        return serverData;
    }

    /** Children spawned by a controller inherit its resolved stats. */
    public void copyStatsFrom(SpellEffectEntity template) {
        this.damage = template.damage;
        this.knockback = template.knockback;
        this.speed = template.speed;
        this.duration = template.duration;
    }

    public float damage() {
        return damage;
    }

    public float knockback() {
        return knockback;
    }

    public float speed() {
        return speed;
    }

    public int duration() {
        return duration;
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) level();
    }
}
