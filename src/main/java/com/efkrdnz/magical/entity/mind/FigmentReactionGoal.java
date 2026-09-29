package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.mind.MindService;
import com.efkrdnz.magical.magic.mind.Reaction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

/** A figment answering the nearest mind that believes it, the way its script says. */
public final class FigmentReactionGoal extends Goal {
    private static final double NOTICE = 12.0;
    private static final double APPROACH_STOP_SQR = 6.25;
    private static final double STRIKE_REACH_SQR = 2.25;
    private static final int STRIKE_COOLDOWN = 20;

    private final FigmentEntity figment;
    private final Reaction reaction;
    private LivingEntity viewer;
    private int cooldown;

    public FigmentReactionGoal(FigmentEntity figment, Reaction reaction) {
        this.figment = figment;
        this.reaction = reaction;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (reaction == Reaction.IGNORE) {
            return false;
        }
        viewer = nearestBeliever();
        return viewer != null;
    }

    /** The strike cooldown counts ticks, so the goal has to be ticked every one of them. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * A reaction begins only against a believer. A chase, once begun, goes on after its quarry starts
     * to doubt - that is how a doubter is reached and struck hollow - and ends when the quarry has
     * seen through it entirely. Every other reaction lets go the moment belief falls.
     */
    @Override
    public boolean canContinueToUse() {
        if (viewer == null || !viewer.isAlive() || figment.distanceToSqr(viewer) >= NOTICE * NOTICE * 2.25) {
            return false;
        }
        if (reaction == Reaction.CHASE) {
            return !MindService.shattered(viewer, figment);
        }
        return MindService.believes(viewer, figment) >= Belief.CONVINCED;
    }

    @Override
    public void stop() {
        viewer = null;
        figment.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        figment.getLookControl().setLookAt(viewer, 30.0F, 30.0F);
        double distance = figment.distanceToSqr(viewer);
        switch (reaction) {
            case APPROACH -> {
                if (distance > APPROACH_STOP_SQR) {
                    figment.getNavigation().moveTo(viewer, 0.9);
                } else {
                    figment.getNavigation().stop();
                }
            }
            case FLEE -> {
                if (figment.getNavigation().isDone()) {
                    Vec3 away = DefaultRandomPos.getPosAway(figment, 12, 7, viewer.position());
                    if (away != null) {
                        figment.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                    }
                }
            }
            case STARE -> figment.getNavigation().stop();
            case CHASE -> {
                figment.getNavigation().moveTo(viewer, 1.1);
                if (distance < STRIKE_REACH_SQR && cooldown == 0) {
                    figment.swing(InteractionHand.MAIN_HAND);
                    MindService.figmentStrikes(figment, viewer);
                    cooldown = STRIKE_COOLDOWN;
                }
            }
            case IGNORE -> { }
        }
    }

    private LivingEntity nearestBeliever() {
        return figment.level().getEntitiesOfClass(LivingEntity.class, figment.getBoundingBox().inflate(NOTICE),
                        entity -> entity != figment && !(entity instanceof FigmentEntity) && entity.isAlive()
                                && !entity.getUUID().equals(figment.owner())
                                && MindService.believes(entity, figment) >= Belief.CONVINCED)
                .stream().min(Comparator.comparingDouble(figment::distanceToSqr)).orElse(null);
    }
}
