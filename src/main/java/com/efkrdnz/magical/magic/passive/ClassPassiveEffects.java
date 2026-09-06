package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

/**
 * The one door between the game and every class passive.
 *
 * <p>Before this existed, a passive was a hand-written {@code if (state.isPassiveEnabled(X))} at
 * whichever site happened to suit it. Forty-five of those would be unmaintainable, so the effects
 * now live in one {@link ClassPassiveHandler} per class line and the game calls the methods below
 * instead. Each call site in the mod gains exactly one line.</p>
 */
public final class ClassPassiveEffects {
    /** The slow tick deliberately does not run every tick; nothing here needs 20 Hz. */
    public static final int SLOW_TICK_INTERVAL = 10;

    private static final List<ClassPassiveHandler> HANDLERS = List.of(
            new ForgePassives(),
            new WarPassives(),
            new WildPassives(),
            new ArcanePassives(),
            new BrewPassives());

    private ClassPassiveEffects() {}

    public static List<ClassPassiveHandler> handlers() {
        return HANDLERS;
    }

    /** True when the player owns the passive and has not switched it off in the codex. */
    public static boolean on(PlayerMagicState state, ResourceLocation passiveId) {
        return state.isPassiveEnabled(passiveId);
    }

    public static float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        float result = amount;
        for (ClassPassiveHandler handler : HANDLERS) {
            result = handler.incomingDamage(player, state, source, result);
        }
        return Math.max(0.0F, result);
    }

    public static float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        float result = amount;
        for (ClassPassiveHandler handler : HANDLERS) {
            result = handler.outgoingSpellDamage(player, state, target, skillId, result);
        }
        return Math.max(0.0F, result);
    }

    /** Applies every handler's cast multipliers and returns the adjusted stats. */
    public static MagicSkillResolvedStats adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillResolvedStats stats) {
        CastAdjustment adjustment = new CastAdjustment();
        MagicSkillDefinition definition = stats.definition();
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.adjustCast(player, state, definition, adjustment);
        }
        if (adjustment.damage == 1.0F && adjustment.size == 1.0F && adjustment.mana == 1.0F
                && adjustment.cooldown == 1.0F && adjustment.knockback == 1.0F) {
            return stats;
        }
        return new MagicSkillResolvedStats(
                definition,
                stats.tuning(),
                stats.damage() * adjustment.damage,
                stats.speed(),
                stats.size() * adjustment.size,
                Math.max(0, Math.round(stats.manaCost() * adjustment.mana)),
                Math.max(0, Math.round(stats.cooldownTicks() * adjustment.cooldown)),
                stats.durationTicks(),
                stats.knockback() * adjustment.knockback,
                stats.barrierRestore());
    }

    /**
     * Last chance to pay for a cast the player cannot afford. Returns true when some handler covered
     * the whole shortfall, in which case the mana has already been taken from wherever it came from.
     */
    public static boolean payManaShortfall(ServerPlayer player, PlayerMagicState state, int cost) {
        int missing = cost - state.mana();
        if (missing <= 0) {
            return false;
        }
        for (ClassPassiveHandler handler : HANDLERS) {
            if (handler.payManaShortfall(player, state, missing) >= missing) {
                state.setMana(0);
                return true;
            }
        }
        return false;
    }

    public static void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.afterCast(player, state, definition);
        }
    }

    public static void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onKill(player, state, victim, source);
        }
    }

    public static void onMeleeHit(ServerPlayer player, PlayerMagicState state, Entity target) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onMeleeHit(player, state, target);
        }
    }

    /**
     * Damage aimed at a tamed animal. Called with the animal, not the player, so it resolves the
     * owner itself and does nothing when the owner is offline or is not a player.
     */
    public static float petIncomingDamage(LivingEntity pet, DamageSource source, float amount) {
        if (!(pet instanceof TamableAnimal tamed) || !tamed.isTame()
                || !(tamed.getOwner() instanceof ServerPlayer owner)) {
            return amount;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        float result = amount;
        for (ClassPassiveHandler handler : HANDLERS) {
            result = handler.petIncomingDamage(owner, state, pet, source, result);
        }
        return Math.max(0.0F, result);
    }

    public static void onPetDeath(LivingEntity pet) {
        if (!(pet instanceof TamableAnimal tamed) || !tamed.isTame()
                || !(tamed.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onPetDeath(owner, state, pet);
        }
    }

    public static void onBarrierAbsorb(ServerPlayer player, PlayerMagicState state, float absorbed, DamageSource source) {
        if (absorbed <= 0.0F) {
            return;
        }
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onBarrierAbsorb(player, state, absorbed, source);
        }
    }

    public static void onHarvest(ServerPlayer player, PlayerMagicState state, net.minecraft.world.level.block.state.BlockState broken) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onHarvest(player, state, broken);
        }
    }

    public static void onEat(ServerPlayer player, PlayerMagicState state) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.onEat(player, state);
        }
    }

    public static float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        float result = amount;
        for (ClassPassiveHandler handler : HANDLERS) {
            result = handler.adjustHeal(player, state, result);
        }
        return Math.max(0.0F, result);
    }

    /** Duration multiplier for a mod status about to land on this entity; 1 when nothing applies. */
    public static float statusDurationScale(LivingEntity target, MagicStatus status) {
        if (!(target instanceof ServerPlayer player)) {
            return 1.0F;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        float result = 1.0F;
        for (ClassPassiveHandler handler : HANDLERS) {
            result *= handler.statusDurationScale(player, state, status);
        }
        return Math.max(0.0F, result);
    }

    /** True when some passive paid to keep the player alive through an otherwise lethal blow. */
    public static boolean cheatDeath(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        for (ClassPassiveHandler handler : HANDLERS) {
            if (handler.cheatDeath(player, state, source, amount)) {
                return true;
            }
        }
        return false;
    }

    /** Runs the periodic effects and refreshes the derived pool bonuses in one pass. */
    public static void slowTick(ServerPlayer player, PlayerMagicState state) {
        int mana = 0;
        int barrier = 0;
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.slowTick(player, state);
            mana += handler.bonusMaxMana(player, state);
            barrier += handler.bonusMaxBarrier(player, state);
        }
        state.setClassPoolBonuses(mana, barrier);
    }

    /** Drops in-memory state for a player who has logged out or changed dimension. */
    public static void forget(UUID playerId) {
        for (ClassPassiveHandler handler : HANDLERS) {
            handler.forget(playerId);
        }
    }
}
