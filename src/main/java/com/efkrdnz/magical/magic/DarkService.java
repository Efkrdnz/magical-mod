package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.passive.PassiveHooks;
import com.efkrdnz.magical.magic.passive.RacePassives;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What dark magic costs, and what it has already cost you.
 *
 * <p>Blood charges you in the moment and lets you heal. Dark charges nothing at all at the moment
 * of casting - no mana, no health, no cooldown you would not have paid anyway - and instead writes
 * the price down. Corruption never decays, never ticks off, and is not spent on anything. It only
 * goes up, and the pools it eats stay eaten until a Tier 4 Light skill buys some of it back.
 *
 * <p>That is the whole design: the school is free to use and expensive to have used.
 */
public final class DarkService {

    /**
     * How much Corruption sits between one threshold and the next. Four steps to
     * {@link PlayerMagicState#MAX_CORRUPTION}, so the ladder is 25 / 50 / 75 / 100 and a player can
     * count it without a tooltip.
     */
    public static final int THRESHOLD_STEP = 25;

    /** Corruption at which {@code corruption_curse} attaches. The top of the ladder, by design. */
    public static final int CURSE_AT = PlayerMagicState.MAX_CORRUPTION;

    /** Willing: this share comes off every Corruption penalty. Read by the pool ceilings. */
    public static final float WILLING_PENALTY_RELIEF = 0.30F;

    /** Willing: and Corruption arrives this much faster in exchange. */
    public static final float WILLING_GAIN_SCALE = 1.20F;

    /**
     * Ledger: no single cast may add this much or more.
     *
     * <p>One below the step, which is exactly the cap that matters - a gain smaller than the step
     * can cross at most one threshold, so a book-keeper is never surprised by two at once. The only
     * skill that can produce a gain that large is Sever the Thread, which is the skill Ledger is
     * really there to survive.
     */
    public static final int LEDGER_MAX_SINGLE_GAIN = THRESHOLD_STEP - 1;

    private DarkService() {
    }

    /**
     * True when the player holds any dark skill at all - including the two that were already here
     * before the school existed, since {@code black_flames} and {@code abyssal_discharge} are
     * school {@code DARK} now and pay the same price as everything else on the layer.
     */
    public static boolean isDarkMage(PlayerMagicState state) {
        for (ResourceLocation id : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(id);
            if (skill != null && skill.school() == MagicSchool.DARK) {
                return true;
            }
        }
        return false;
    }

    /**
     * Signs for a cast. Always succeeds - this is the point of the school, and a dark skill that
     * could be refused for want of Corruption would just be a mana skill wearing a hood.
     *
     * @return how much Corruption was actually taken on, after every modifier
     */
    public static int corrupt(ServerPlayer player, PlayerMagicState state, int amount) {
        if (MagicPrice.waived(player) || amount <= 0) {
            return 0;
        }
        int before = state.corruption();
        state.addCorruption(scaleGain(state, amount));
        int taken = state.corruption() - before;
        if (taken <= 0) {
            // Already at the ceiling. Nothing more to write down, and no shriek for a no-op.
            return 0;
        }
        announceCrossing(player, state, before);
        return taken;
    }

    /**
     * Writes debt off. The only thing in the mod that lowers Corruption, and the reason Purification
     * exists at all.
     *
     * @return how much was actually cleared
     */
    public static int cleanse(ServerPlayer player, PlayerMagicState state, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = state.corruption();
        state.addCorruption(-amount);
        int cleared = before - state.corruption();
        if (cleared <= 0) {
            return 0;
        }
        // Dropping below the top of the ladder is what lifts the curse. Done here rather than in
        // Purification so that any future way of clearing Corruption lifts it too - the curse is a
        // statement about how much you are carrying, not about which spell you cast last.
        if (state.corruption() < CURSE_AT) {
            state.activeCurses().remove(MagicPassiveContent.CORRUPTION_CURSE.id());
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.magical.corruption_cleansed", cleared)
                    .withStyle(ChatFormatting.AQUA), true);
            PassiveHooks.puff(player, ParticleTypes.END_ROD, 14, 0.4D);
        }
        return cleared;
    }

    /**
     * Which rung of the ladder a player is standing on: 0 for clean, up to 4 at the ceiling.
     *
     * <p>Rung 4 is the cursed one. Everything below it is pure pool decay, which is deliberate -
     * the first three steps are supposed to feel survivable, so that the fourth lands.
     */
    public static int threshold(PlayerMagicState state) {
        return thresholdOf(state.corruption());
    }

    public static int thresholdOf(int corruption) {
        return Math.max(0, Math.min(4, corruption / THRESHOLD_STEP));
    }

    /**
     * Willing speeds the debt up; a Demon's corruption resistance slows it; Ledger caps how much
     * any one cast may add. Applied in that order, and never below one - a dark cast that wrote
     * down nothing at all would be a free dark cast.
     */
    private static int scaleGain(PlayerMagicState state, int amount) {
        float scaled = amount;
        if (state.isPassiveEnabled(MagicPassiveContent.WILLING.id())) {
            scaled *= WILLING_GAIN_SCALE;
        }
        // The Demon's resistance is named for this and until now only slowed sin gauges. It answers
        // Corruption too, or the passive on a Demon's sheet is telling them something untrue.
        if (state.isPassiveEnabled(MagicPassiveContent.CORRUPTION_RESISTANCE.id())) {
            scaled *= RacePassives.CORRUPTION_SIN_SCALE;
        }
        int gain = Math.max(1, Math.round(scaled));
        if (state.isPassiveEnabled(MagicPassiveContent.LEDGER.id())) {
            gain = Math.min(gain, LEDGER_MAX_SINGLE_GAIN);
        }
        return gain;
    }

    /** Says so when a cast moves you onto a new rung, and attaches the curse at the top one. */
    private static void announceCrossing(ServerPlayer player, PlayerMagicState state, int before) {
        int wasOn = thresholdOf(before);
        int nowOn = threshold(state);
        if (nowOn <= wasOn) {
            return;
        }
        if (state.corruption() >= CURSE_AT) {
            state.addCurse(MagicPassiveContent.CORRUPTION_CURSE.id());
        }
        if (player == null) {
            // The curse still attaches - it is a fact about the state, not about who is watching.
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.magical.corruption_threshold_" + nowOn)
                        .withStyle(ChatFormatting.DARK_PURPLE),
                false);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6F, 0.6F);
        PassiveHooks.puff(player, ParticleTypes.SCULK_SOUL, 12, 0.35D);
    }
}
