package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The spectral wolf companion: a real pathfinding mob that heels to its owner, hunts the nearest
 * hostile (preferring what the owner last damaged), leaps and bites on a cadence, howls once on
 * its first sighting, and can be told to guard a spot instead.
 */
public class SpiritWolfEntity extends PathfinderMob implements ProfiledEffect {
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(SpiritWolfEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpiritWolfEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(SpiritWolfEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LEAP = SynchedEntityData.defineId(SpiritWolfEntity.class, EntityDataSerializers.FLOAT);
    private static final int BITE_CADENCE = 14;
    private static final double HUNT_RANGE = 20.0D;
    private static final double GUARD_RANGE = 6.0D;
    private static final long PLAYER_GRUDGE_TICKS = 200L;

    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private UUID ownerUuid;
    private float biteDamage = 6.0F;
    private int biteCooldown;
    private boolean howled;
    private Vec3 guardPos;

    public SpiritWolfEntity(EntityType<? extends SpiritWolfEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    public static SpiritWolfEntity create(ServerLevel level, MagicSkillDefinition definition, LivingEntity owner, Vec3 pos, int life, float biteDamage, int seed, Vec3 guardPos) {
        SpiritWolfEntity wolf = new SpiritWolfEntity(MagicalEntities.SPIRIT_WOLF.get(), level);
        wolf.skillId = definition.id();
        wolf.entityData.set(SKILL_INDEX, MagicContent.skillIndex(definition.id()));
        wolf.entityData.set(LIFE, life);
        wolf.entityData.set(SEED, seed & 63);
        wolf.ownerUuid = owner.getUUID();
        wolf.biteDamage = biteDamage;
        wolf.guardPos = guardPos;
        wolf.setPos(pos.x, pos.y, pos.z);
        wolf.setYRot(owner.getYRot());
        return wolf;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL_INDEX, -1);
        builder.define(LIFE, 240);
        builder.define(SEED, 0);
        builder.define(LEAP, 0.0F);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    private LivingEntity owner(ServerLevel level) {
        return ownerUuid != null && level.getEntity(ownerUuid) instanceof LivingEntity living ? living : null;
    }

    private boolean mayHunt(LivingEntity owner, LivingEntity candidate) {
        if (!SkillTargets.isHostile(owner, candidate) || candidate instanceof SpiritWolfEntity) {
            return false;
        }
        if (candidate instanceof Player player) {
            // never a player unless they hit the owner in the last ten seconds
            return owner.getLastHurtByMob() == player && owner.tickCount - owner.getLastHurtByMobTimestamp() < PLAYER_GRUDGE_TICKS;
        }
        return true;
    }

    private LivingEntity pickTarget(ServerLevel level, LivingEntity owner) {
        Vec3 centre = guardPos != null ? guardPos : position();
        double range = guardPos != null ? GUARD_RANGE : HUNT_RANGE;
        LivingEntity preferred = owner.getLastHurtMob();
        if (preferred != null && preferred.isAlive() && preferred.distanceToSqr(centre) <= range * range && mayHunt(owner, preferred)) {
            return preferred;
        }
        List<LivingEntity> near = SkillTargets.hostilesWithin(level, owner, centre, range);
        for (LivingEntity candidate : near) {
            if (mayHunt(owner, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity owner = owner(level);
        if (owner == null || !owner.isAlive() || tickCount >= life()) {
            dissolve();
            return;
        }
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        entityData.set(LEAP, Math.max(0.0F, entityData.get(LEAP) - 0.12F));
        if (tickCount % 5 == 0 || getTarget() == null || !getTarget().isAlive()) {
            setTarget(pickTarget(level, owner));
        }
        LivingEntity target = getTarget();
        if (target != null) {
            if (!howled) {
                howled = true;
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                SpellFx.impact(level, definition(), getEyePosition(), new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.2F);
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = distanceTo(target);
            if (dist > 2.2D) {
                getNavigation().moveTo(target, 1.25D);
            } else if (biteCooldown <= 0) {
                biteCooldown = BITE_CADENCE;
                Vec3 leap = target.position().subtract(position());
                leap = new Vec3(leap.x, 0.0D, leap.z).normalize().scale(0.45D).add(0.0D, 0.3D, 0.0D);
                setDeltaMovement(getDeltaMovement().add(leap));
                hurtMarked = true;
                entityData.set(LEAP, 1.0F);
                SkillTargets.hurt(level, owner, target, biteDamage, definition(), true);
                MagicStatusService.apply(target, MagicStatus.HARRIED, 60, definition().id(), owner);
            }
            return;
        }
        if (guardPos != null) {
            if (distanceToSqr(guardPos) > 2.0D) {
                getNavigation().moveTo(guardPos.x, guardPos.y, guardPos.z, 1.0D);
            }
            return;
        }
        double toOwner = distanceTo(owner);
        if (toOwner > 24.0D) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        } else if (toOwner > 6.0D) {
            getNavigation().moveTo(owner, 1.1D);
        } else if (toOwner < 2.0D) {
            getNavigation().stop();
        }
    }

    private void dissolve() {
        if (level() instanceof ServerLevel level) {
            SpellFx.decal(level, definition(), position(), new Vec3(0.0D, 1.0D, 0.0D), 1.2F);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        dissolve();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() != null && ownerUuid != null && ownerUuid.equals(source.getEntity().getUUID())) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    public MagicSkillDefinition definition() {
        MagicSkillDefinition definition = MagicContent.get(skillId);
        return definition != null ? definition : MagicContent.get(MagicContent.STARTER_SKILL);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public void refresh(int life, Vec3 guardPos) {
        entityData.set(LIFE, tickCount + life);
        this.guardPos = guardPos;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("SkillId"));
        if (parsed != null && MagicContent.get(parsed) != null) {
            skillId = parsed;
            entityData.set(SKILL_INDEX, MagicContent.skillIndex(parsed));
        }
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(SEED, tag.getInt("Seed"));
        biteDamage = tag.getFloat("Bite");
        howled = tag.getBoolean("Howled");
        if (tag.contains("GuardX")) {
            guardPos = new Vec3(tag.getDouble("GuardX"), tag.getDouble("GuardY"), tag.getDouble("GuardZ"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", skillId.toString());
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Life", life());
        tag.putInt("Seed", entityData.get(SEED));
        tag.putFloat("Bite", biteDamage);
        tag.putBoolean("Howled", howled);
        if (guardPos != null) {
            tag.putDouble("GuardX", guardPos.x);
            tag.putDouble("GuardY", guardPos.y);
            tag.putDouble("GuardZ", guardPos.z);
        }
    }

    // ---- ProfiledEffect ----

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
    public int effectSeed() {
        return entityData.get(SEED);
    }

    @Override
    public Vec3 effectDirection() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    /** Leap phase (1 right after a pounce, decaying) for the painter's stretch. */
    @Override
    public float effectValue() {
        return entityData.get(LEAP);
    }

    @Override
    public int effectDrawMode() {
        return 0;
    }

    @Override
    public Vec3 effectEndPoint() {
        Entity target = getTarget();
        return target != null ? target.getBoundingBox().getCenter() : null;
    }
}
