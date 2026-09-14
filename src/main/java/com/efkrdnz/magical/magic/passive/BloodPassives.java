package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.blood.CrimsonTitheSkill;
import com.efkrdnz.magical.magic.skill.blood.SecondHeartSkill;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Blood magic's economy: where the Vessel fills from, what an open wound costs, and the three
 * passives that make the school something a player can actually sustain.
 *
 * <p>Two of the things in here are not passives at all. Filling the Vessel from damage, and letting
 * a wound suppress healing, are the school's baseline rules - they apply to anyone holding a blood
 * skill, gated on that rather than on owning a passive, because a school whose resource only works
 * once you have also unlocked the right passive is a school nobody can start playing.
 *
 * <p>The third baseline rule is the harvest: what a blood mage kills bleeds toward them. That one
 * lives in {@link BloodHarvestEntity}, because the blood is a thing in the world - it waits, it is
 * seen, Vein Walk steps along it - and a thing in the world is an entity, not a map in here.
 */
public final class BloodPassives implements ClassPassiveHandler {

    /** Vessel per point of damage landed on something living, for anyone holding a blood skill. */
    private static final float HARVEST_PER_DAMAGE = 0.25F;

    private static final float BLOODSCENT_THRESHOLD = 0.4F;
    private static final float CLOTTING_WOUND_RELIEF = 0.5F;
    private static final int CLOTTING_TRICKLE = 1;
    private static final float OVERFLOW_THRESHOLD = 0.8F;
    private static final int OVERFLOW_BARRIER_PER_POINT = 1;

    /** How far above where the body fell the blood pools, so it sits on the ground rather than in it. */
    private static final double POOL_LIFT = 0.05D;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.BLOODSCENT.id(),
                MagicPassiveContent.CLOTTING.id(),
                MagicPassiveContent.VESSEL_OVERFLOWS.id());
    }

    // ---- the baseline economy -------------------------------------------------------------

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        harvest(player, state, amount, true);
        return amount;
    }

    @Override
    public void onMeleeHit(ServerPlayer player, PlayerMagicState state, Entity target) {
        if (target instanceof LivingEntity) {
            harvest(player, state, 2.0F, true);
        }
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        // Crimson Tithe pays out more for blood taken than for blood dealt. That is the pact: the
        // fastest way to fill the Vessel is to stand in the fight and be hit.
        harvest(player, state, amount, false);
        return amount;
    }

    private void harvest(ServerPlayer player, PlayerMagicState state, float amount, boolean dealt) {
        if (amount <= 0.0F || !BloodService.isBloodMage(state)) {
            return;
        }
        float share = HARVEST_PER_DAMAGE;
        if (state.passiveCounter(MagicContent.CRIMSON_TITHE.id()) > 0) {
            share += dealt ? CrimsonTitheSkill.HARVEST_DEALT : CrimsonTitheSkill.HARVEST_TAKEN;
        } else if (!dealt) {
            // Without the tithe running, being hit is simply being hit.
            return;
        }
        int gained = Math.round(amount * share);
        if (gained > 0) {
            state.addBloodVessel(gained);
            state.sync(player);
        }
    }

    /**
     * What a kill leaves: blood, pooled where the body fell, that comes to the killer when they are
     * near. The Vessel fills when it lands, not here - see {@link BloodHarvestEntity}.
     */
    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        if (!BloodService.isBloodMage(state)) {
            return;
        }
        // Bloodscent doubles the harvest, which is also what makes Vein Walk worth building around.
        int drops = ClassPassiveEffects.on(state, MagicPassiveContent.BLOODSCENT.id())
                && MagicStatusService.has(victim, MagicStatus.REVEALED) ? 2 : 1;
        BloodHarvestEntity.spawn(player.serverLevel(), player,
                victim.position().add(0.0D, POOL_LIFT, 0.0D), BloodHarvestRules.yield(drops));
    }

    // ---- the three passives ---------------------------------------------------------------

    @Override
    public float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        if (state.openWoundTicks() <= 0) {
            return amount;
        }
        // An open wound is what makes paying in health a real decision rather than a slow trade
        // against regeneration. Clotting halves it; it never closes it.
        float relief = ClassPassiveEffects.on(state, MagicPassiveContent.CLOTTING.id())
                ? CLOTTING_WOUND_RELIEF : 0.0F;
        return amount * relief;
    }

    @Override
    public int bonusMaxBarrier(ServerPlayer player, PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.VESSEL_OVERFLOWS.id())) {
            return 0;
        }
        int surplus = overflowSurplus(state);
        return surplus > 0 ? surplus * OVERFLOW_BARRIER_PER_POINT : 0;
    }

    /**
     * Whether the Vessel is past the line The Vessel Overflows spends from. The harvest asks this
     * as its blood lands, to show the surplus spilling out rather than quietly becoming barrier.
     */
    public static boolean overflowing(PlayerMagicState state) {
        return overflowSurplus(state) > 0;
    }

    private static int overflowSurplus(PlayerMagicState state) {
        int threshold = Math.round(PlayerMagicState.MAX_BLOOD_VESSEL * OVERFLOW_THRESHOLD);
        return state.bloodVessel() - threshold;
    }

    @Override
    public boolean cheatDeath(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        int reserve = state.passiveCounter(MagicContent.SECOND_HEART.id());
        if (reserve <= 0) {
            return false;
        }
        state.setPassiveCounter(MagicContent.SECOND_HEART.id(), 0);
        player.setHealth(reserve / (float) SecondHeartSkill.RESERVE_SCALE);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        PassiveHooks.puff(player, ParticleTypes.DAMAGE_INDICATOR, 24, 0.5D);
        state.sync(player);
        return true;
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!BloodService.isBloodMage(state)) {
            return;
        }
        for (int i = 0; i < ClassPassiveEffects.SLOW_TICK_INTERVAL; i++) {
            state.tickOpenWound();
        }
        int tithe = state.passiveCounter(MagicContent.CRIMSON_TITHE.id());
        if (tithe > 0) {
            state.setPassiveCounter(MagicContent.CRIMSON_TITHE.id(),
                    Math.max(0, tithe - ClassPassiveEffects.SLOW_TICK_INTERVAL));
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CLOTTING.id()) && state.openWoundTicks() <= 0) {
            state.addBloodVessel(CLOTTING_TRICKLE);
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOODSCENT.id())) {
            markTheWounded(player, state);
        }
    }

    /**
     * Nothing to drop: the only per-player state this school keeps is its pooled blood, and that
     * is in the world as {@link BloodHarvestEntity}, which notices for itself when its owner is
     * gone. Declared all the same, because the handler contract is checked by name.
     */
    @Override
    public void forget(UUID playerId) {
    }

    /**
     * Bloodscent: anything already bleeding is lit through walls, and worth double when it dies.
     * The range it sees is the range the harvest pulls from - what it can see, it can take.
     */
    private void markTheWounded(ServerPlayer player, PlayerMagicState state) {
        for (LivingEntity prey : SkillTargets.hostilesWithin(player.serverLevel(), player,
                player.position(), BloodHarvestRules.BLOODSCENT_PULL_RANGE)) {
            float max = prey.getMaxHealth();
            if (max > 0.0F && prey.getHealth() / max <= BLOODSCENT_THRESHOLD) {
                MagicStatusService.apply(prey, MagicStatus.REVEALED, 40,
                        MagicPassiveContent.BLOODSCENT.id(), player);
            }
        }
    }
}
