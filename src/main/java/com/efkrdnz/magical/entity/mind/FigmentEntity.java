package com.efkrdnz.magical.entity.mind;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * A creature out of a reverie. The server's AI treats it as real - that is the point, a husk has to
 * be able to hunt it - but it cannot be hurt, pushes nothing, and each client draws it only for a
 * mind that believes it.
 */
public class FigmentEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CREATURE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final int GUARD_RADIUS = 3;
    private static final double FOLLOW_START_SQR = 16.0;
    private static final double FOLLOW_STOP_SQR = 4.0;

    /** Whether this client can see a figment (scene id, element); set by the client in Task 12. */
    public static volatile BiPredicate<Integer, Integer> clientSees = (scene, element) -> true;

    private UUID owner;

    public FigmentEntity(EntityType<? extends FigmentEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CREATURE, "minecraft:villager");
        builder.define(SCENE, -1);
        builder.define(ELEMENT, -1);
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && MindService.scene(sceneId()) == null) {
            discard();
        }
    }

    /** Nothing lands on a thing that is not there; the blow is evidence instead. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            MindService.figmentStruck(this, attacker);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return !level().isClientSide() || clientSees.test(sceneId(), element());
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
