package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.Consensus;
import com.efkrdnz.magical.magic.mind.LiveScene;
import com.efkrdnz.magical.magic.mind.MindService;
import com.efkrdnz.magical.magic.mind.Script;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * A creature out of a reverie. The minds that believe it treat it as real - that is the point, a husk
 * has to be able to hunt it - but to everything else it is no body at all: it cannot be hurt, pushes
 * nothing, blocks no building, is no enemy to vanilla's targeting, is never a spell's target (see
 * {@link #isFigment}), burns, bathes and drinks nothing a doubter could see, and each client draws it
 * only for a mind that believes it - until enough minds agree on it, when it is real for everyone
 * ({@link #isManifested}).
 */
public class FigmentEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CREATURE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MANIFESTED = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int GUARD_RADIUS = 3;
    private static final double FOLLOW_START_SQR = 16.0;
    private static final double FOLLOW_STOP_SQR = 4.0;

    /** Whether this client can see a figment (scene id, element); set by the client in Task 12. */
    public static volatile BiPredicate<Integer, Integer> clientSees = (scene, element) -> true;

    private UUID owner;
    /** Client: the game time this figment became real, for the hardening rim; see {@link #hardening}. */
    private long hardenedAt = Long.MIN_VALUE;

    public FigmentEntity(EntityType<? extends FigmentEntity> type, Level level) {
        super(type, level);
        // LivingEntity sets this: a block may not be placed where a living body stands.
        this.blocksBuilding = false;
    }

    /**
     * Whether an entity is out of a reverie and not real: no body to anything outside the Mind code. A
     * figment enough minds agree on is real, and answers false - every spell, sweep and target can find it.
     * The rest of the mod asks it through {@link com.efkrdnz.magical.magic.service.Bodies}, which every
     * sweep for a living thing goes through, and through the target filters in {@code SkillTargets}.
     */
    public static boolean isFigment(Entity entity) {
        return entity instanceof FigmentEntity figment && !figment.isManifested();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CREATURE, "minecraft:villager");
        builder.define(SCENE, -1);
        builder.define(ELEMENT, -1);
        builder.define(MANIFESTED, false);
    }

    public static FigmentEntity spawn(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        FigmentEntity figment = new FigmentEntity(MagicalEntities.FIGMENT.get(), level);
        BlockPos at = element.figmentAt();
        figment.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, scene.turns() * 90.0F, 0.0F);
        figment.entityData.set(CREATURE, element.figment().creatureId());
        figment.entityData.set(SCENE, scene.id());
        figment.entityData.set(ELEMENT, element.index());
        figment.owner = scene.owner();
        figment.install(element.figment().script(), at);
        level.addFreshEntity(figment);
        return figment;
    }

    private void install(Script script, BlockPos home) {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FigmentReactionGoal(this, script.reaction()));
        switch (script.stance()) {
            case WANDER -> goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
            case GUARD -> {
                restrictTo(home, GUARD_RADIUS);
                goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
            }
            case FOLLOW -> goalSelector.addGoal(3, new FollowOwnerGoal());
            case IDLE -> { }
        }
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public String creatureId() {
        return entityData.get(CREATURE);
    }

    public int sceneId() {
        return entityData.get(SCENE);
    }

    public int element() {
        return entityData.get(ELEMENT);
    }

    public UUID owner() {
        return owner;
    }

    public boolean isManifested() {
        return entityData.get(MANIFESTED);
    }

    /** Becomes real: its kind's health, as much of it as the agreement allows, and its kind's bite. */
    public void manifest(float maxHealth, float attack) {
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(attack);
        }
        hold(maxHealth);
        setHealth(getMaxHealth());
        entityData.set(MANIFESTED, true);
    }

    /** Keeps a real figment's health within what the agreement still allows; never heals it. */
    public void hold(float maxHealth) {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.max(1.0F, maxHealth));
        }
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    public void unmanifest() {
        entityData.set(MANIFESTED, false);
    }

    /** Client: how much of the lilac rim is left on a figment that has just become real, 1 to 0. */
    public float hardening(float partial) {
        if (!isManifested() || hardenedAt == Long.MIN_VALUE) {
            return 0.0F;
        }
        float age = (level().getGameTime() - hardenedAt) + partial;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age / Consensus.HARDEN_TICKS));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MANIFESTED.equals(key)) {
            // Both sides: a client that still thought it no body would let a block be placed into it.
            this.blocksBuilding = isManifested();
            if (level().isClientSide() && isManifested()) {
                hardenedAt = level().getGameTime();
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            MindService.figmentSlain(this);
        }
    }


    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && MindService.scene(sceneId()) == null) {
            discard();
        }
    }

    /**
     * Nothing lands on a thing that is not there; a melee blow is evidence instead. Only /kill gets
     * through, so an operator can always clear one away.
     * A real figment is a body like any other.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isManifested() || source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurtServer(level, source, amount);
        }
        if (source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker) {
            MindService.figmentStruck(this, attacker);
        }
        return false;
    }

    /**
     * Arrows and the like pass through; the world sees them cross it, see {@code MindService.projectiles}.
     * A real figment is a body like any other.
     */
    @Override
    public boolean canBeHitByProjectile() {
        return isManifested() && super.canBeHitByProjectile();
    }

    /**
     * A figment leaves no steps, no sounds and no vibrations in a world it is not part of.
     * A real figment is a body like any other.
     */
    @Override
    protected MovementEmission getMovementEmission() {
        return isManifested() ? super.getMovementEmission() : MovementEmission.NONE;
    }

    /**
     * Nor does it trip a pressure plate or a tripwire.
     * A real figment is a body like any other.
     */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return !isManifested() || super.isIgnoringBlockTriggers();
    }

    /**
     * Nothing takes a figment for an enemy: this is what vanilla's {@code TargetingConditions} asks, so
     * the Wither, a zoglin or a warden passes it by. The minds that believe it hunt and flee it by the
     * Mind's own goals, which do not ask ({@code MindMobEvents}).
     * A real figment is a body like any other.
     */
    @Override
    public boolean canBeSeenAsEnemy() {
        return isManifested() && super.canBeSeenAsEnemy();
    }

    /**
     * Fire takes nothing to burn, so no client draws flames on it (with {@code fireImmune} on its type).
     * A real figment is a body like any other.
     */
    @Override
    public boolean displayFireAnimation() {
        return isManifested() && super.displayFireAnimation();
    }

    /**
     * No potion takes, so no swirl rises off it for everyone to see.
     * A real figment is a body like any other.
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeAffected(MobEffectInstance effect) {
        return isManifested() && super.canBeAffected(effect);
    }

    /**
     * It raises no splash walking into water.
     * A real figment is a body like any other.
     */
    @Override
    protected void doWaterSplashEffect() {
        if (isManifested()) {
            super.doWaterSplashEffect();
        }
    }

    /**
     * Nor does it drown, so no bubbles leave it under water.
     * A real figment is a body like any other.
     */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return isManifested() && super.canDrownInFluidType(type);
    }

    /**
     * A fall lands on nothing: no dust, no fall sound, no vibration, no trampled farmland. Only the fall
     * distance is kept, as vanilla keeps it, so the pathfinder still judges drops the same way.
     * A real figment is a body like any other.
     */
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (isManifested()) {
            super.checkFallDamage(y, onGround, state, pos);
            return;
        }
        if (onGround) {
            resetFallDistance();
        } else if (y < 0.0) {
            fallDistance -= (float) y;
        }
    }

    /**
     * A figment belongs to its scene and the scene to one level, so it never crosses a portal: it
     * ends with its scene where it stands. A copy in another dimension would be drawn for everyone
     * there, believed by no one, and gone from the scene that ticks it.
     */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return isManifested() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (isManifested()) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isPickable() {
        return !level().isClientSide() || isManifested() || clientSees.test(sceneId(), element());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    private final class FollowOwnerGoal extends Goal {
        private Player leader;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            leader = owner == null ? null : level().getPlayerByUUID(owner);
            return leader != null && distanceToSqr(leader) > FOLLOW_START_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && distanceToSqr(leader) > FOLLOW_STOP_SQR;
        }

        @Override
        public void tick() {
            getNavigation().moveTo(leader, 1.0);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}
