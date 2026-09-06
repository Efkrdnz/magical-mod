package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The effects of one class line's passives.
 *
 * <p>Shaped like {@link com.efkrdnz.magical.magic.cast.SkillCastHandler}: every hook has a no-op or
 * identity default, so a line implements only what it actually uses. {@link ClassPassiveEffects}
 * owns the list of handlers and calls these; nothing else should.</p>
 *
 * <p>Handlers may keep short-lived per-player state in memory — cast streaks, a two second window,
 * a target mark — and are told to drop it in {@link #forget(UUID)}. Anything that must survive a
 * relog belongs in {@link PlayerMagicState#passiveCounters()} instead.</p>
 */
public interface ClassPassiveHandler {

    /** Every passive id this handler implements. Asserted to be exhaustive by the test suite. */
    Set<ResourceLocation> handled();

    /** Damage about to land on the player, after vanilla mitigation and before the barrier. */
    default float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return amount;
    }

    /** Damage the player's spell is about to deal. {@code skillId} may be null for untagged damage. */
    default float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        return amount;
    }

    /** Fold multipliers into {@code out} before the cast resolves. */
    default void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
    }

    /**
     * Offered the mana a cast is short by when every normal payment route has failed. Return the
     * amount covered; returning less than {@code missing} means the cast still fails.
     */
    default int payManaShortfall(ServerPlayer player, PlayerMagicState state, int missing) {
        return 0;
    }

    /** A cast succeeded and its cost has been paid. */
    default void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
    }

    /** The player killed something. Check {@link PassiveHooks#isSpellKill} for magic-only effects. */
    default void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
    }

    /** The player landed a melee attack. */
    default void onMeleeHit(ServerPlayer player, PlayerMagicState state, Entity target) {
    }

    /** Damage about to land on one of the player's own tamed animals. */
    default float petIncomingDamage(ServerPlayer owner, PlayerMagicState state, LivingEntity pet, DamageSource source, float amount) {
        return amount;
    }

    /** One of the player's own tamed animals died. */
    default void onPetDeath(ServerPlayer owner, PlayerMagicState state, LivingEntity pet) {
    }

    /** The player's barrier soaked {@code absorbed} damage. */
    default void onBarrierAbsorb(ServerPlayer player, PlayerMagicState state, float absorbed, DamageSource source) {
    }

    /** The player broke a block. */
    default void onHarvest(ServerPlayer player, PlayerMagicState state, net.minecraft.world.level.block.state.BlockState broken) {
    }

    /** The player finished eating something. */
    default void onEat(ServerPlayer player, PlayerMagicState state) {
    }

    /** Scales healing the player is about to receive. */
    default float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        return amount;
    }

    /** Scales the duration of a mod status about to be applied to the player. */
    default float statusDurationScale(ServerPlayer player, PlayerMagicState state, MagicStatus status) {
        return 1.0F;
    }

    /**
     * Offered a killing blow before it lands. Return true to cancel it entirely; the handler is
     * responsible for whatever the player pays instead.
     */
    default boolean cheatDeath(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return false;
    }

    /** Runs every {@link ClassPassiveEffects#SLOW_TICK_INTERVAL} ticks, not every tick. */
    default void slowTick(ServerPlayer player, PlayerMagicState state) {
    }

    /** Extra maximum mana, recomputed on the slow tick. */
    default int bonusMaxMana(ServerPlayer player, PlayerMagicState state) {
        return 0;
    }

    /** Extra maximum barrier, recomputed on the slow tick. */
    default int bonusMaxBarrier(ServerPlayer player, PlayerMagicState state) {
        return 0;
    }

    /** Drop any in-memory state held for this player; called on logout and dimension change. */
    default void forget(UUID playerId) {
    }
}
