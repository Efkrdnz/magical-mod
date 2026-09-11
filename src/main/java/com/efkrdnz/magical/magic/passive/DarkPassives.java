package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.DarkService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.skill.dark.UmbralTenancySkill;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dark magic's book-keeping: the two windows its skills open, and the two passives that change what
 * the debt does to you.
 *
 * <p>Neither passive has its effect in this file. {@code willing} is read by
 * {@code PlayerMagicState.corruptionPenalty} - it has to be, because the pool ceilings are computed
 * on both sides and the client never runs a passive handler - and by {@code DarkService}.
 * {@code ledger} is read by {@code DarkService} and by the HUD. This class still claims both: the
 * handler contract is one owner per passive id, and a passive with no claimant fails the build
 * whether or not its behaviour happens to live here.
 */
public final class DarkPassives implements ClassPassiveHandler {

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.LEDGER.id(),
                MagicPassiveContent.WILLING.id());
    }

    /**
     * The window Umbral Tenancy opens on emergence. Gated on the counter rather than on holding a
     * passive, because it is the skill's own payoff - a player who has the skill has earned it.
     */
    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        if (state.passiveCounter(MagicContent.UMBRAL_TENANCY.id()) <= 0) {
            return amount;
        }
        // Spent on the first spell out of the shadow, whatever that spell was. Coming out and then
        // hoarding the window for something better is not the fantasy.
        state.passiveCounters().remove(MagicContent.UMBRAL_TENANCY.id());
        return amount * UmbralTenancySkill.EMERGENCE_BONUS;
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!DarkService.isDarkMage(state)) {
            return;
        }
        tickWindow(state, MagicContent.UMBRAL_TENANCY.id());
        if (tickWindow(state, MagicContent.LONG_DEBT.id())) {
            // The bargain's whole gift, reapplied twice a second. The skill's own behaviour settles
            // the wager; this only keeps the board clear while it runs.
            state.clearCooldowns();
        }
    }

    /** Counts one window down by a slow tick and reports whether it is still open. */
    private static boolean tickWindow(PlayerMagicState state, ResourceLocation id) {
        int left = state.passiveCounter(id);
        if (left <= 0) {
            return false;
        }
        int next = left - ClassPassiveEffects.SLOW_TICK_INTERVAL;
        if (next <= 0) {
            state.passiveCounters().remove(id);
            return false;
        }
        state.passiveCounters().put(id, next);
        return true;
    }

    /**
     * Nothing to forget. Both windows live in {@code passiveCounters}, which is part of the player's
     * own state and goes wherever that goes - so unlike Blood's mote trail there is no static map
     * here that could outlive a logout.
     */
    @Override
    public void forget(UUID playerId) {
    }
}
