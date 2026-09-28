package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Teaches every mob that would hunt or flee a creature to hunt or flee a figment of one - but only
 * a figment it believes. Added once, as the mob joins a level.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindMobEvents {
    private static final int TARGET_PRIORITY = 2;
    private static final int AVOID_PRIORITY = 1;
    private static final float AVOID_DISTANCE = 8.0F;

    private MindMobEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof FigmentEntity) {
            return;
        }
        String type = MindService.typeId(mob);
        if (FigmentHunts.huntsAny(type)) {
            mob.targetSelector.addGoal(TARGET_PRIORITY, new NearestAttackableTargetGoal<>(mob, FigmentEntity.class, true,
                    (target, level) -> target instanceof FigmentEntity figment
                            && FigmentHunts.hunts(type, figment.creatureId())
                            && MindService.believes(mob, figment) >= Belief.CONVINCED));
        }
        if (FigmentHunts.fearsAny(type) && mob instanceof PathfinderMob runner) {
            runner.goalSelector.addGoal(AVOID_PRIORITY, new AvoidEntityGoal<>(runner, FigmentEntity.class, AVOID_DISTANCE, 1.0, 1.2,
                    entity -> entity instanceof FigmentEntity figment
                            && FigmentHunts.fears(type, figment.creatureId())
                            && MindService.believes(runner, figment) >= Belief.CONVINCED));
        }
    }
}
