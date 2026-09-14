package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What eldritch magic costs: being noticed.
 *
 * <p>Every call borrows a piece of something enormous under the world, and every borrowing makes it
 * more aware of the borrower. Notice is that awareness: it rises with each cast, cools in silence,
 * and pays back - the deep gives more to the ones it is watching - until it turns to look, and
 * then everything else does too.
 */
public final class EldritchService {
    /** Watched: hostile things nearby turn to the caster every slow tick. */
    public static final int WATCHED_AT = 50;
    /** Noticed: the deep reaches for the caster themselves. The top of the gauge. */
    public static final int NOTICED_AT = PlayerMagicState.MAX_NOTICE;
    /** A call made under the deep's full attention is this many times as strong. */
    public static final float MAX_POTENCY = 1.5F;
    public static final int DECAY_PER_SLOW_TICK = 1;
    /** How far the Watched rung reaches for things to turn toward the caster. */
    public static final double WATCHED_RANGE = 24.0D;
    /** Ticks between the deep's grasps at the top rung. */
    public static final int REACH_INTERVAL = 200;

    private EldritchService() {
    }

    public static boolean isEldritchMage(PlayerMagicState state) {
        for (ResourceLocation id : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(id);
            if (skill != null && skill.school() == MagicSchool.ELDRITCH) {
                return true;
            }
        }
        return false;
    }

    /** The Notice a cast of this skill draws with the points applied: never below one. */
    public static int cost(MagicSkillResolvedStats stats) {
        return scale(stats, EldritchPrices.base(stats.definition().id()));
    }

    public static int scale(MagicSkillResolvedStats stats, int base) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * stats.costScale()));
    }

    /**
     * Draws the deep's attention. Always succeeds - a call that could be refused for being too
     * loud is not the school - and announces each rung as it is crossed.
     *
     * @return how much Notice was actually taken on
     */
    public static int notice(ServerPlayer player, PlayerMagicState state, int amount) {
        if (MagicPrice.waived(player) || amount <= 0) {
            return 0;
        }
        int before = state.notice();
        state.addNotice(amount);
        int taken = state.notice() - before;
        if (taken > 0 && rung(state.notice()) > rung(before)) {
            announce(player, rung(state.notice()));
        }
        return taken;
    }

    public static float potency(PlayerMagicState state) {
        return 1.0F + (MAX_POTENCY - 1.0F) * state.notice() / (float) PlayerMagicState.MAX_NOTICE;
    }

    /** 0 unseen, 1 Watched, 2 Noticed. */
    public static int rung(int notice) {
        return notice >= NOTICED_AT ? 2 : notice >= WATCHED_AT ? 1 : 0;
    }

    /** Deep Bargain: at the top rung, and only there, the calls cost no mana. */
    public static boolean manaWaived(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.DEEP_BARGAIN.id()) && state.notice() >= NOTICED_AT;
    }

    /**
     * How much Notice cools this slow tick. Lidless halves it by skipping every other tick; the
     * phase is kept in the passive counter so the halving is exact rather than a coin toss.
     */
    public static int decayStep(PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.LIDLESS.id())) {
            return DECAY_PER_SLOW_TICK;
        }
        ResourceLocation id = MagicPassiveContent.LIDLESS.id();
        int phase = state.passiveCounter(id);
        state.setPassiveCounter(id, phase == 0 ? 1 : 0);
        return phase == 0 ? 0 : DECAY_PER_SLOW_TICK;
    }

    private static void announce(ServerPlayer player, int rung) {
        String key = rung >= 2 ? "message.magical.deep_noticed" : "message.magical.deep_watched";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.7F, rung >= 2 ? 0.5F : 0.7F);
    }
}
