package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The Blood Sacrifice ritual: what it costs, what it will accept, and what sealing one does.
 *
 * <p>The cast only opens the pact. The blood and the cooldown are both charged by {@link #seal}, so
 * opening the screen and thinking better of it costs nothing at all.
 */
public final class BloodSacrificeService {

    /**
     * A full Crimson Vessel, and nothing less.
     *
     * <p>Flat, and deliberately not a {@link BloodPrices} entry. That table is scaled by
     * {@code costScale}, so a Thrift-tuned ritual would ask for sixty - and "requires a full Vessel"
     * is the one rule the whole ability is built on.
     */
    public static final int RITUAL_COST = PlayerMagicState.MAX_BLOOD_VESSEL;

    /** How much longer a price runs than the boon it paid for. */
    public static final float PRICE_DURATION_FACTOR = 1.5F;

    /** What a Blood Debt adds, and never gives back. */
    public static final int BLOOD_DEBT_CORRUPTION = 25;

    /**
     * The Unknown's table, with the cumulative weights out of a hundred beneath it.
     *
     * <p>Three rolls in four are worth the two points it cost. One in ten is the debt nobody would
     * have chosen, which is the whole reason the gamble is priced in the middle of the range.
     */
    private static final ResourceLocation[] UNKNOWN_ROLLS = {
            MagicPassiveContent.SLOW_BLOOD.id(),
            MagicPassiveContent.MANA_DROUGHT.id(),
            MagicPassiveContent.GLASS_BONES.id(),
            MagicPassiveContent.SPELL_FIZZLE.id(),
            MagicPassiveContent.BLOOD_DEBT.id(),
    };
    private static final int[] UNKNOWN_WEIGHTS = {30, 55, 75, 90, 100};

    /** Why a pact was refused. Null means it was not. */
    public enum Refusal {
        NOTHING_CHOSEN,
        UNKNOWN_ENTRY,
        DUPLICATE,
        OVER_BUDGET,
        PRICES_TOO_CHEAP,
        VESSEL_NOT_FULL;

        /** The line the player is shown. One key per reason, so none of them is a shrug. */
        public Component message() {
            return Component.translatable("message.magical.sacrifice_" + name().toLowerCase(Locale.ROOT));
        }
    }

    private BloodSacrificeService() {
    }

    /** How long this player's boons would last, with their points on the ritual applied. */
    public static int boonTicks(PlayerMagicState state) {
        return stats(state).durationTicks();
    }

    /** And how long the prices outlive them. */
    public static int priceTicks(int boonTicks) {
        return Math.round(boonTicks * PRICE_DURATION_FACTOR);
    }

    private static MagicSkillResolvedStats stats(PlayerMagicState state) {
        return MagicContent.BLOOD_SACRIFICE.resolve(state.tuningFor(MagicContent.BLOOD_SACRIFICE.id()));
    }

    private static boolean hellbroker(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.HELLBROKER.id());
    }

    /**
     * Judges a pact without charging for it. Returns null when it is fine.
     *
     * <p>Both lists arrive from the client and neither is trusted: the budget is recomputed from the
     * player's own tuning, unknown ids and duplicates are refused outright, and the Vessel is
     * checked last so a refused pact never takes anything.
     */
    public static Refusal validate(PlayerMagicState state, List<ResourceLocation> boons,
            List<ResourceLocation> prices) {
        if (boons.isEmpty()) {
            return Refusal.NOTHING_CHOSEN;
        }
        Refusal shape = checkList(boons, true);
        if (shape != null) {
            return shape;
        }
        shape = checkList(prices, false);
        if (shape != null) {
            return shape;
        }
        boolean broker = hellbroker(state);
        int spent = SacrificeCatalogue.sum(boons, true);
        if (spent > SacrificeBudget.boonBudget(stats(state).tuning(), broker)) {
            return Refusal.OVER_BUDGET;
        }
        if (SacrificeCatalogue.sum(prices, false) < SacrificeBudget.priceRequired(spent, broker)) {
            return Refusal.PRICES_TOO_CHEAP;
        }
        if (state.bloodVessel() < RITUAL_COST) {
            return Refusal.VESSEL_NOT_FULL;
        }
        return null;
    }

    /** One list, checked for entries that are not on it and for anything chosen twice. */
    private static Refusal checkList(List<ResourceLocation> chosen, boolean boons) {
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation id : chosen) {
            if (boons ? !SacrificeCatalogue.isBoon(id) : !SacrificeCatalogue.isPrice(id)) {
                return Refusal.UNKNOWN_ENTRY;
            }
            if (!seen.add(id)) {
                return Refusal.DUPLICATE;
            }
        }
        return null;
    }

    /**
     * Charges the Vessel and grants the pact. False when the pact was refused, having taken nothing.
     *
     * <p>The cooldown starts here rather than at the cast, so a player who opens the screen and
     * closes it again has lost nothing but the walk.
     */
    public static boolean seal(ServerPlayer player, PlayerMagicState state, List<ResourceLocation> boons,
            List<ResourceLocation> prices) {
        Refusal refusal = validate(state, boons, prices);
        if (refusal != null) {
            player.displayClientMessage(refusal.message(), true);
            return false;
        }
        if (!BloodService.payFromVesselOnly(player, state, RITUAL_COST)) {
            return false;
        }
        MagicSkillResolvedStats stats = stats(state);
        int boonTicks = stats.durationTicks();
        int priceTicks = priceTicks(boonTicks);
        for (ResourceLocation id : boons) {
            state.grantRitualPassive(id, boonTicks);
        }
        long seed = player.serverLevel().getGameTime() * 31L + player.getId();
        for (ResourceLocation id : prices) {
            grantPrice(state, resolvePrice(id, seed), priceTicks);
        }
        state.setSkillCooldown(MagicContent.BLOOD_SACRIFICE.id(), stats.cooldownTicks());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 0.6F, 0.5F);
        state.sync(player);
        return true;
    }

    /** The Unknown becomes something real at the moment it is sealed; everything else is itself. */
    private static ResourceLocation resolvePrice(ResourceLocation chosen, long seed) {
        return MagicPassiveContent.THE_UNKNOWN.id().equals(chosen) ? rollUnknown(seed) : chosen;
    }

    private static void grantPrice(PlayerMagicState state, ResourceLocation id, int ticks) {
        if (MagicPassiveContent.BLOOD_DEBT.id().equals(id)) {
            // The one price with no clock. Corruption is permanent and Purification is the only way
            // out of it, which is exactly why it costs the top of the range.
            state.addCorruption(BLOOD_DEBT_CORRUPTION);
            return;
        }
        state.grantRitualPassive(id, ticks);
    }

    /**
     * Draws one price from the Unknown's table.
     *
     * <p>Seeded rather than free-running so a game test can force any outcome, and so a player who
     * reloads a save cannot reroll the price they already agreed to.
     */
    public static ResourceLocation rollUnknown(long seed) {
        int roll = new Random(seed).nextInt(100);
        for (int i = 0; i < UNKNOWN_WEIGHTS.length; i++) {
            if (roll < UNKNOWN_WEIGHTS[i]) {
                return UNKNOWN_ROLLS[i];
            }
        }
        return UNKNOWN_ROLLS[UNKNOWN_ROLLS.length - 1];
    }
}
