package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.SacrificeCatalogue;
import com.efkrdnz.magical.magic.status.MagicStatus;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every effect a Blood Sacrifice grants or charges, under one handler.
 *
 * <p>One handler rather than two because the coverage test wants exactly one owner per passive, and
 * because the two halves talk to each other: Bloodborne Fury reads the price count, and the
 * Hellbroker scales the prices. The effects themselves live in {@link SacrificeBoons} and
 * {@link SacrificeCurses}; this file is only the wiring between the hooks and them.
 *
 * <p>The Hellbroker is claimed here and does nothing on its own. It is read by
 * {@code SacrificeBudget} when a pact is built and by {@link SacrificeCurses} when one is being
 * paid, which is the whole of it.
 */
public final class SacrificePassives implements ClassPassiveHandler {

    private static final Set<ResourceLocation> HANDLED = buildHandled();

    private static Set<ResourceLocation> buildHandled() {
        Set<ResourceLocation> handled = new LinkedHashSet<>(SacrificeCatalogue.all());
        handled.add(MagicPassiveContent.HELLBROKER.id());
        return Set.copyOf(handled);
    }

    @Override
    public Set<ResourceLocation> handled() {
        return HANDLED;
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        float dealt = SacrificeBoons.outgoing(state, target, amount);
        // Both of these read the damage actually dealt, so they run after it is decided rather than
        // on the number that came in: Haemophage drinks what landed, Misery echoes what landed.
        SacrificeBoons.drink(player, state, dealt);
        SacrificeCurses.recoil(player, state, dealt);
        return dealt;
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return SacrificeCurses.incoming(state, SacrificeBoons.incoming(state, amount));
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition,
            CastAdjustment out) {
        SacrificeBoons.adjustCast(state, out);
        SacrificeCurses.adjustCast(state, out);
    }

    @Override
    public void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        SacrificeCurses.afterCast(player, state);
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        if (SacrificeCurses.refusesTheKill(state)) {
            // A Weeping Vessel refuses the whole kill, Vessel Siphon included. Taking the boon and
            // the price together is a legal pact that cancels out, which is the player's problem.
            return;
        }
        SacrificeBoons.onKill(state, source);
    }

    @Override
    public float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        return SacrificeCurses.heal(state, amount);
    }

    @Override
    public float statusDurationScale(ServerPlayer player, PlayerMagicState state, MagicStatus status) {
        return SacrificeBoons.statusScale(state) * SacrificeCurses.statusScale(state);
    }

    @Override
    public boolean cheatDeath(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return SacrificeBoons.cheatDeath(player, state);
    }

    @Override
    public int bonusMaxBarrier(ServerPlayer player, PlayerMagicState state) {
        return SacrificeBoons.bonusBarrier(state);
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        SacrificeBoons.attributes(player, state);
        SacrificeCurses.attributes(player, state);
        SacrificeCurses.hemorrhage(player, state);
    }

    @Override
    public void forget(java.util.UUID playerId) {
        // Nothing to forget, and that is deliberate. Every other handler keeps a scratch map of
        // windows and streaks; a pact keeps all of its state in PlayerMagicState, where it has to
        // be anyway to survive a relog and to reach the codex. Declared rather than inherited so
        // the next person to read this knows the omission was a decision.
    }
}
