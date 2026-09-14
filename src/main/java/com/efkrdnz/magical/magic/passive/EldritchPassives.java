package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.eldritch.GraspOfTheDeepSkill;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * What Notice does to an eldritch mage, twice a second: it cools, and past each rung it acts.
 *
 * <p>Watched: everything hostile in range turns to look. Noticed: the deep itself reaches up and
 * takes hold of the caster for a moment, every {@link EldritchService#REACH_INTERVAL} ticks.
 * The two passives are read here and by the service: Lidless slows the cooling and grows the
 * calls, Deep Bargain waives their mana at the top.
 */
public final class EldritchPassives implements ClassPassiveHandler {

    /** Lidless: every construct reaches and is drawn this much larger. */
    public static final float LIDLESS_SIZE = 1.2F;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(MagicPassiveContent.LIDLESS.id(), MagicPassiveContent.DEEP_BARGAIN.id());
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        if (definition.school() != MagicSchool.ELDRITCH) {
            return;
        }
        if (EldritchService.manaWaived(state)) {
            out.mana = 0.0F;
        }
        if (state.isPassiveEnabled(MagicPassiveContent.LIDLESS.id())) {
            out.size *= LIDLESS_SIZE;
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!EldritchService.isEldritchMage(state) || state.notice() <= 0) {
            return;
        }
        int rung = EldritchService.rung(state.notice());
        state.addNotice(-EldritchService.decayStep(state));
        if (rung >= 1) {
            for (LivingEntity hostile : SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), EldritchService.WATCHED_RANGE)) {
                if (hostile instanceof Mob mob && mob.getTarget() == null) {
                    mob.setTarget(player);
                }
            }
        }
        if (rung >= 2 && player.tickCount % EldritchService.REACH_INTERVAL < ClassPassiveEffects.SLOW_TICK_INTERVAL) {
            reachForTheCaster(player, state);
        }
    }

    /** The deep takes hold of what it has seen: a grasp under the caster, on the caster. */
    static void reachForTheCaster(ServerPlayer player, PlayerMagicState state) {
        GraspOfTheDeepSkill.reachFor(player, state);
    }

    @Override
    public void forget(UUID playerId) {
    }
}
