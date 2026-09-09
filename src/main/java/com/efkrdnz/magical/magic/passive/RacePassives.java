package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.race.MagicalRace;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The six race identity passives.
 *
 * <p>One handler for all of them rather than one per race, because a race owns exactly one passive
 * and six single-hook classes would be six files of ceremony.
 *
 * <p>Two of these are not implemented here and cannot be. Adaptable changes the tuning budget and
 * Corruption Resistance changes how fast sin accrues; both of those are decided inside
 * {@link PlayerMagicState}, at the one place that computes them, and a hook that ran afterwards
 * would be too late. They are still claimed by {@link #handled()} - the test suite checks that
 * every registered passive has an owner, and an unclaimed one would look like an oversight rather
 * than a deliberate placement. The numbers live here so all six read together.
 *
 * <p>Everything here stays inside the magic systems - mana, barrier, spell damage, healing, sin.
 * Race deliberately does not touch movement, reach or melee, so choosing one never changes how
 * Minecraft itself plays.
 */
public final class RacePassives implements ClassPassiveHandler {
    /** Human: the tuning ladder starts a step higher. Read by {@code PlayerMagicState.tuningLimit}. */
    public static final int ADAPTABLE_BONUS_POINTS = 1;
    /** Demon: sin accrues at this rate. Read by {@code PlayerMagicState.dampenSinGain}. */
    public static final float CORRUPTION_SIN_SCALE = 0.6F;

    private static final float DEEP_WELL_MANA_DISCOUNT = 0.88F;
    private static final float FORGEBORN_DAMAGE_TAKEN = 0.92F;
    private static final int QUICKENED_KILL_MANA = 6;
    private static final int QUICKENED_KILL_BARRIER = 2;
    private static final float BLESSED_HEAL_BONUS = 1.2F;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.ADAPTABLE.id(),
                MagicPassiveContent.DEEP_WELL.id(),
                MagicPassiveContent.FORGEBORN.id(),
                MagicPassiveContent.QUICKENED_INSTINCT.id(),
                MagicPassiveContent.CORRUPTION_RESISTANCE.id(),
                MagicPassiveContent.BLESSED.id());
    }

    /**
     * Every race's pool bonus, read from the race rather than from its passive.
     *
     * <p>All six races set these, so branching per passive would be six copies of one lookup.
     */
    @Override
    public int bonusMaxMana(ServerPlayer player, PlayerMagicState state) {
        MagicalRace race = state.race();
        return race == null ? 0 : race.bonusMaxMana();
    }

    @Override
    public int bonusMaxBarrier(ServerPlayer player, PlayerMagicState state) {
        MagicalRace race = state.race();
        return race == null ? 0 : race.bonusMaxBarrier();
    }

    /** Elf: a deeper well spends less to draw from. */
    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.DEEP_WELL.id())) {
            out.mana *= DEEP_WELL_MANA_DISCOUNT;
        }
    }

    /** Dwarf: built thicker. Flat proportional cut, stacking with the barrier rather than replacing it. */
    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.FORGEBORN.id())) {
            return amount * FORGEBORN_DAMAGE_TAKEN;
        }
        return amount;
    }

    /** Beastkin: the hunt sustains. Kills return a little of both pools. */
    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.QUICKENED_INSTINCT.id())) {
            return;
        }
        state.setMana(Math.min(state.maxMana(), state.mana() + QUICKENED_KILL_MANA));
        state.setBarrier(Math.min(state.maxBarrier(), state.barrier() + QUICKENED_KILL_BARRIER));
    }

    /** Celestial: mending answers more readily. */
    @Override
    public float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLESSED.id())) {
            return amount * BLESSED_HEAL_BONUS;
        }
        return amount;
    }

    @Override
    public void forget(UUID playerId) {
        // Nothing to drop: a race is fixed for the life of the character, so this handler keeps no
        // per-player scratch at all. Everything it reads comes from the state it is handed.
    }
}
