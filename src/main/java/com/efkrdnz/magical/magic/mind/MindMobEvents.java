package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.function.Predicate;

/**
 * Teaches every mob that would hunt or flee a creature to hunt or flee a figment of one - but only
 * a figment it believes. Added once, as the mob joins a level.
 *
 * <p>A figment is no enemy to vanilla ({@link FigmentEntity#canBeSeenAsEnemy}), and vanilla's own
 * target and avoid goals ask exactly that through combat {@link TargetingConditions}. So both goals
 * here look through non-combat conditions instead: belief, not enmity, is what lets a mind see it.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindMobEvents {
    private static final int TARGET_PRIORITY = 2;
    private static final int AVOID_PRIORITY = 1;
    private static final float AVOID_DISTANCE = 8.0F;
    private static final double AVOID_HEIGHT = 3.0;
    private static final int AWAY_RANGE = 16;
    private static final int AWAY_HEIGHT = 7;

    private MindMobEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof FigmentEntity) {
            return;
        }
        String type = MindService.typeId(mob);
        if (FigmentHunts.huntsAny(type)) {
            mob.targetSelector.addGoal(TARGET_PRIORITY, new BelievedHuntGoal(mob,
                    (target, level) -> target instanceof FigmentEntity figment
                            && FigmentHunts.hunts(type, figment.creatureId())
                            && MindService.believes(mob, figment) >= Belief.CONVINCED));
        }
        if (FigmentHunts.fearsAny(type) && mob instanceof PathfinderMob runner) {
            runner.goalSelector.addGoal(AVOID_PRIORITY, new BelievedFleeGoal(runner,
                    entity -> entity instanceof FigmentEntity figment
                            && FigmentHunts.fears(type, figment.creatureId())
                            && MindService.believes(runner, figment) >= Belief.CONVINCED));
        }
    }

    /**
     * Vanilla's nearest-target goal with the enmity test taken out, finding and keeping a figment the
     * mob believes. The mob lets go when belief falls: {@code MindService.tickScene} clears the target.
     */
    static final class BelievedHuntGoal extends NearestAttackableTargetGoal<FigmentEntity> {
        private int unseenTicks;

        BelievedHuntGoal(Mob mob, TargetingConditions.Selector believed) {
            super(mob, FigmentEntity.class, true, believed);
            this.targetConditions = TargetingConditions.forNonCombat().range(getFollowDistance()).selector(believed);
        }

        @Override
        public void start() {
            super.start();
            unseenTicks = 0;
        }

        /**
         * {@code TargetGoal.canContinueToUse}, except that a figment is kept without {@code mob.canAttack},
         * which refuses every figment; anything else is held to it as vanilla holds it.
         */
        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget() != null ? mob.getTarget() : targetMob;
            if (target == null) {
                return false;
            }
            boolean kept = target instanceof FigmentEntity ? target.canBeSeenByAnyone() : mob.canAttack(target);
            if (!kept) {
                return false;
            }
            Team team = mob.getTeam();
            if (team != null && target.getTeam() == team) {
                return false;
            }
            double follow = getFollowDistance();
            if (mob.distanceToSqr(target) > follow * follow) {
                return false;
            }
            if (mustSee) {
                if (mob.getSensing().hasLineOfSight(target)) {
                    unseenTicks = 0;
                } else if (++unseenTicks > reducedTickDelay(unseenMemoryTicks)) {
                    return false;
                }
            }
            mob.setTarget(target);
            return true;
        }
    }

    /** Vanilla's avoid goal with the enmity test taken out, running from a figment the mob believes. */
    static final class BelievedFleeGoal extends AvoidEntityGoal<FigmentEntity> {
        private final TargetingConditions believedFeared;

        BelievedFleeGoal(PathfinderMob mob, Predicate<LivingEntity> believed) {
            super(mob, FigmentEntity.class, AVOID_DISTANCE, 1.0, 1.2, believed);
            this.believedFeared = TargetingConditions.forNonCombat().range(AVOID_DISTANCE)
                    .selector((entity, level) -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && believed.test(entity));
        }

        /** The nearest figment this mob believes and fears, in sight and in range; null when there is none. */
        FigmentEntity nearestFeared() {
            return getServerLevel(mob).getNearestEntity(
                    mob.level().getEntitiesOfClass(FigmentEntity.class, mob.getBoundingBox().inflate(maxDist, AVOID_HEIGHT, maxDist), figment -> true),
                    believedFeared, mob, mob.getX(), mob.getY(), mob.getZ());
        }

        /** {@code AvoidEntityGoal.canUse} over {@link #nearestFeared}. */
        @Override
        public boolean canUse() {
            toAvoid = nearestFeared();
            if (toAvoid == null) {
                return false;
            }
            Vec3 away = DefaultRandomPos.getPosAway(mob, AWAY_RANGE, AWAY_HEIGHT, toAvoid.position());
            if (away == null || toAvoid.distanceToSqr(away.x, away.y, away.z) < toAvoid.distanceToSqr(mob)) {
                return false;
            }
            path = pathNav.createPath(away.x, away.y, away.z, 0);
            return path != null;
        }
    }
}
