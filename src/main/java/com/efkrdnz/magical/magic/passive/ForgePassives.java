package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Blacksmith line: heat, repetition and the things a smith makes.
 *
 * <p>The line's first fork is deliberately a real choice. Bellows Rhythm rewards hammering one
 * skill over and over; Etched Grooves rewards rotating through the loadout and pays nothing for a
 * repeat. Taking both leaves you with two half-effects, which is the point.</p>
 */
public final class ForgePassives implements ClassPassiveHandler {
    /** Mana saved per consecutive repeat, and the cap on how many repeats count. */
    private static final float RHYTHM_DISCOUNT = 0.15F;
    private static final int RHYTHM_MAX_REPEATS = 3;
    /** How long a cast stays in the Engraver window, in game ticks. */
    private static final long GROOVE_WINDOW_TICKS = 160L;
    private static final float GROOVE_DAMAGE_PER_STACK = 0.08F;
    private static final int GROOVE_MAX_STACKS = 3;
    private static final int PLATE_BARRIER_PER_ARMOUR = 5;
    /** Anvil Debt refuses to lend more than this share of the pool, so it cannot spiral. */
    private static final float DEBT_MAX_SHARE = 0.4F;
    private static final float DEBT_INTEREST = 1.5F;
    private static final float QUENCH_REDUCTION = 0.3F;
    private static final float QUENCH_BARRIER_SHARE = 0.4F;
    private static final float ALIGNMENT_BONUS = 0.2F;
    private static final float COUNTERSUNK_SHARE = 0.25F;
    private static final int LEDGER_CAP = 60;
    private static final int HEAT_MAX = 10;
    private static final float HEAT_DAMAGE_PER_STACK = 0.03F;

    private final Map<UUID, Forge> scratch = new HashMap<>();

    /** Short-lived per-player state; none of it is worth persisting across a relog. */
    private static final class Forge {
        ResourceLocation lastSkill;
        int repeats;
        final List<Cast> recent = new ArrayList<>();
        float fireTaken;
        int heat;
        long lastHeatDecayTick;
    }

    private record Cast(ResourceLocation skillId, long tick) {}

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.BELLOWS_RHYTHM.id(),
                MagicPassiveContent.QUENCH.id(),
                MagicPassiveContent.ETCHED_GROOVES.id(),
                MagicPassiveContent.FITTED_PLATE.id(),
                MagicPassiveContent.ANVIL_DEBT.id(),
                MagicPassiveContent.EDGE_ALIGNMENT.id(),
                MagicPassiveContent.COUNTERSUNK.id(),
                MagicPassiveContent.GILDED_LEDGER.id(),
                MagicPassiveContent.FORGE_HEAT.id());
    }

    private Forge forge(ServerPlayer player) {
        return scratch.computeIfAbsent(player.getUUID(), id -> new Forge());
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        Forge forge = forge(player);

        // Bellows Rhythm: the same motion, again and again, costs less each time.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BELLOWS_RHYTHM.id())
                && definition.id().equals(forge.lastSkill) && forge.repeats > 0) {
            out.mana *= 1.0F - RHYTHM_DISCOUNT * Math.min(RHYTHM_MAX_REPEATS, forge.repeats);
        }

        // Etched Grooves: only *other* skills cast recently count, so repeating pays nothing here.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.ETCHED_GROOVES.id())) {
            int stacks = grooveStacks(player, forge, definition.id());
            if (stacks > 0) {
                out.damage *= 1.0F + GROOVE_DAMAGE_PER_STACK * stacks;
            }
        }

        // Forge Heat: the Warsmith runs their own body hot to hit harder.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.FORGE_HEAT.id()) && forge.heat > 0) {
            out.damage *= 1.0F + HEAT_DAMAGE_PER_STACK * forge.heat;
        }
    }

    private int grooveStacks(ServerPlayer player, Forge forge, ResourceLocation castingNow) {
        long now = player.serverLevel().getGameTime();
        forge.recent.removeIf(cast -> now - cast.tick() > GROOVE_WINDOW_TICKS);
        Set<ResourceLocation> distinct = new HashSet<>();
        for (Cast cast : forge.recent) {
            if (!cast.skillId().equals(castingNow)) {
                distinct.add(cast.skillId());
            }
        }
        return Math.min(GROOVE_MAX_STACKS, distinct.size());
    }

    @Override
    public void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        Forge forge = forge(player);
        forge.repeats = definition.id().equals(forge.lastSkill) ? forge.repeats + 1 : 0;
        forge.lastSkill = definition.id();
        forge.recent.add(new Cast(definition.id(), player.serverLevel().getGameTime()));
        if (forge.recent.size() > 16) {
            forge.recent.remove(0);
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.FORGE_HEAT.id())) {
            forge.heat = Math.min(HEAT_MAX, forge.heat + 1);
        }
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        float result = amount;
        // Quench: fire hurts a smith less, and the heat it leaves behind is worth something.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.QUENCH.id()) && source.is(DamageTypeTags.IS_FIRE)) {
            float reduced = result * (1.0F - QUENCH_REDUCTION);
            forge(player).fireTaken += result;
            result = reduced;
        }
        return result;
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        // Edge Alignment: a weaponwright finishes the piece.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.EDGE_ALIGNMENT.id())
                && target.getHealth() < target.getMaxHealth() * 0.5F) {
            return amount * (1.0F + ALIGNMENT_BONUS);
        }
        return amount;
    }

    @Override
    public int payManaShortfall(ServerPlayer player, PlayerMagicState state, int missing) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.ANVIL_DEBT.id())) {
            return 0;
        }
        ResourceLocation debtId = MagicPassiveContent.ANVIL_DEBT.id();
        // One debt at a time, and never more than a fraction of the pool: the rune runs on credit,
        // not on an overdraft that can never be cleared.
        if (state.passiveCounter(debtId) > 0 || missing > state.maxMana() * DEBT_MAX_SHARE) {
            return 0;
        }
        state.setPassiveCounter(debtId, Math.round(missing * DEBT_INTEREST));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.22F, 1.7F);
        return missing;
    }

    @Override
    public void onBarrierAbsorb(ServerPlayer player, PlayerMagicState state, float absorbed, DamageSource source) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.COUNTERSUNK.id())) {
            return;
        }
        LivingEntity attacker = PassiveHooks.attacker(player, source);
        if (attacker == null) {
            return;
        }
        // Straight back down the line it came from. Indirect magic so it cannot re-enter the barrier.
        MagicDamageService.hurt(attacker, player.damageSources().indirectMagic(player, player), absorbed * COUNTERSUNK_SHARE);
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        // Gilded Ledger: every spell kill is written down and paid out as a permanently larger pool.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.GILDED_LEDGER.id()) && PassiveHooks.isSpellKill(source)) {
            state.bumpPassiveCounter(MagicPassiveContent.GILDED_LEDGER.id(), 1, 0, LEDGER_CAP);
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        Forge forge = forge(player);

        // Quench: the barrier arrives when the fire finally goes out, not while you are still burning.
        if (forge.fireTaken > 0.0F && !player.isOnFire()) {
            if (ClassPassiveEffects.on(state, MagicPassiveContent.QUENCH.id())) {
                state.addBarrier(Math.max(1, Math.round(forge.fireTaken * QUENCH_BARRIER_SHARE)));
                player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.4F);
                PassiveHooks.puff(player, ParticleTypes.CLOUD, 8, 0.3D);
            }
            forge.fireTaken = 0.0F;
        }

        // Anvil Debt is repaid out of regen before the player sees it.
        int debt = state.passiveCounter(MagicPassiveContent.ANVIL_DEBT.id());
        if (debt > 0) {
            int repaid = Math.min(debt, state.mana());
            if (repaid > 0) {
                state.setMana(state.mana() - repaid);
                state.setPassiveCounter(MagicPassiveContent.ANVIL_DEBT.id(), debt - repaid);
            }
        }

        // Forge Heat bleeds off, and burns the Warsmith while it is at the top of the range.
        if (forge.heat > 0) {
            long now = player.serverLevel().getGameTime();
            if (now - forge.lastHeatDecayTick >= 40L) {
                forge.lastHeatDecayTick = now;
                forge.heat--;
            }
            if (forge.heat >= HEAT_MAX && ClassPassiveEffects.on(state, MagicPassiveContent.FORGE_HEAT.id())) {
                player.hurt(player.damageSources().onFire(), 1.0F);
                PassiveHooks.puff(player, ParticleTypes.FLAME, 6, 0.25D);
            }
        }
    }

    @Override
    public int bonusMaxBarrier(ServerPlayer player, PlayerMagicState state) {
        // Fitted Plate: armour a smith made themselves holds a ward as well as a blow.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.FITTED_PLATE.id())) {
            return 0;
        }
        return player.getArmorValue() * PLATE_BARRIER_PER_ARMOUR;
    }

    @Override
    public int bonusMaxMana(ServerPlayer player, PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.GILDED_LEDGER.id())) {
            return 0;
        }
        return state.passiveCounter(MagicPassiveContent.GILDED_LEDGER.id());
    }

    @Override
    public void forget(UUID playerId) {
        scratch.remove(playerId);
    }

    /** Exposed for the codex tooltip so the Warsmith can see their own heat. */
    public int heatOf(ServerPlayer player) {
        Forge forge = scratch.get(player.getUUID());
        return forge == null ? 0 : forge.heat;
    }
}
