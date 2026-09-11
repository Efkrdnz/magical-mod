package com.efkrdnz.magical.magic;

import net.minecraft.world.entity.player.Player;

/**
 * One question, asked at every place a cast charges for itself.
 *
 * <p>The forbidden schools each bill in their own currency - Blood takes health, Dark takes
 * Corruption, Eldritch takes Regard - and none of those flow through the mana pipeline, so there is
 * no single deduction to guard. This is the shared gate instead: every cost site asks here first,
 * and a creative player is charged nothing by any of them.
 *
 * <p>Spectators are covered too. They cannot cast, but a skill that ticks on after the caster
 * changed mode should not start billing them for it.
 */
public final class MagicPrice {

    private MagicPrice() {
    }

    /** Creative pays nothing: no mana, no health, no Corruption, no Vessel, no Regard. */
    public static boolean waived(Player player) {
        return player != null && (player.isCreative() || player.isSpectator());
    }
}
