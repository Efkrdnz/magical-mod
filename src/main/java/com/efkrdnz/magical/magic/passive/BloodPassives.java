package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.blood.CrimsonTitheSkill;
import com.efkrdnz.magical.magic.skill.blood.SecondHeartSkill;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Blood magic's economy: where the Vessel fills from, what an open wound costs, and the three
 * passives that make the school something a player can actually sustain.
 *
 * <p>Two of the things in here are not passives at all. Filling the Vessel from damage, and letting
 * a wound suppress healing, are the school's baseline rules - they apply to anyone holding a blood
 * skill, gated on that rather than on owning a passive, because a school whose resource only works
 * once you have also unlocked the right passive is a school nobody can start playing.
 */
public final class BloodPassives implements ClassPassiveHandler {

    /** Blood left where something died, waiting to be walked over. Not persisted, by design. */
    private record Mote(Vec3 pos, long spawnedTick) {}

    /**
     * Static because {@code VeinWalkSkill} reads the trail and this class owns it. Kept honest by
     * {@link #forget(UUID)}, which the handler contract requires and a test enforces.
     */
    private static final Map<UUID, List<Mote>> MOTES = new HashMap<>();

    private static final long MOTE_LIFETIME = 300L;
    private static final double MOTE_PICKUP_RANGE = 1.6D;
    private static final int MOTE_VESSEL = 12;
    private static final int MAX_MOTES = 32;

    /** Vessel per point of damage landed on something living, for anyone holding a blood skill. */
    private static final float HARVEST_PER_DAMAGE = 0.25F;

    private static final float BLOODSCENT_THRESHOLD = 0.4F;
    private static final double BLOODSCENT_RANGE = 20.0D;
    private static final float CLOTTING_WOUND_RELIEF = 0.5F;
    private static final int CLOTTING_TRICKLE = 1;
    private static final float OVERFLOW_THRESHOLD = 0.8F;
    private static final int OVERFLOW_BARRIER_PER_POINT = 1;

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

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        if (!BloodService.isBloodMage(state)) {
            return;
        }
        // Bloodscent doubles the trail, which is also what makes Vein Walk worth building around.
        int drops = ClassPassiveEffects.on(state, MagicPassiveContent.BLOODSCENT.id())
                && MagicStatusService.has(victim, MagicStatus.REVEALED) ? 2 : 1;
        List<Mote> trail = MOTES.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
        for (int i = 0; i < drops; i++) {
            trail.add(new Mote(victim.position(), player.serverLevel().getGameTime()));
        }
        while (trail.size() > MAX_MOTES) {
            trail.remove(0);
        }
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
        int threshold = Math.round(PlayerMagicState.MAX_BLOOD_VESSEL * OVERFLOW_THRESHOLD);
        int surplus = state.bloodVessel() - threshold;
        return surplus > 0 ? surplus * OVERFLOW_BARRIER_PER_POINT : 0;
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
        tickMotes(player, state);
    }

    /** Bloodscent: anything already bleeding is lit through walls, and worth double when it dies. */
    private void markTheWounded(ServerPlayer player, PlayerMagicState state) {
        for (LivingEntity prey : SkillTargets.hostilesWithin(player.serverLevel(), player,
                player.position(), BLOODSCENT_RANGE)) {
            float max = prey.getMaxHealth();
            if (max > 0.0F && prey.getHealth() / max <= BLOODSCENT_THRESHOLD) {
                MagicStatusService.apply(prey, MagicStatus.REVEALED, 40,
                        MagicPassiveContent.BLOODSCENT.id(), player);
            }
        }
    }

    private void tickMotes(ServerPlayer player, PlayerMagicState state) {
        List<Mote> trail = MOTES.get(player.getUUID());
        if (trail == null || trail.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Iterator<Mote> iterator = trail.iterator();
        while (iterator.hasNext()) {
            Mote mote = iterator.next();
            if (now - mote.spawnedTick() > MOTE_LIFETIME) {
                iterator.remove();
                continue;
            }
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mote.pos().x, mote.pos().y + 0.3D,
                    mote.pos().z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            if (player.position().distanceTo(mote.pos()) <= MOTE_PICKUP_RANGE) {
                iterator.remove();
                state.addBloodVessel(MOTE_VESSEL);
                state.sync(player);
                level.playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK.value(),
                        SoundSource.PLAYERS, 0.3F, 0.6F);
            }
        }
    }

    // ---- the trail, as Vein Walk sees it --------------------------------------------------

    /** The furthest mote still within reach, which is where Vein Walk goes. */
    public static Optional<Vec3> furthestMote(ServerPlayer player, double reach) {
        List<Mote> trail = MOTES.get(player.getUUID());
        if (trail == null || trail.isEmpty()) {
            return Optional.empty();
        }
        long now = player.serverLevel().getGameTime();
        Vec3 from = player.position();
        Vec3 best = null;
        double bestDistance = -1.0D;
        for (Mote mote : trail) {
            if (now - mote.spawnedTick() > MOTE_LIFETIME) {
                continue;
            }
            double distance = from.distanceTo(mote.pos());
            if (distance <= reach && distance > bestDistance) {
                bestDistance = distance;
                best = mote.pos();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Spends the mote Vein Walk arrived at, so one pool of blood is not an infinite shuttle. */
    public static void consumeMote(ServerPlayer player, Vec3 at) {
        List<Mote> trail = MOTES.get(player.getUUID());
        if (trail != null) {
            trail.removeIf(mote -> mote.pos().distanceToSqr(at) < 1.0E-4D);
        }
    }

    @Override
    public void forget(UUID playerId) {
        MOTES.remove(playerId);
    }
}
