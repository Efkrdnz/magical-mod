package com.efkrdnz.magical.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The one rule for who any forge effect may touch: an Art's splash, a strike's own knockback and
 * rising lift, and an element rider's status all ask this before they touch a body.
 *
 * <p>Mobs always are. Another player only is when vanilla's own melee would let the wielder hit
 * them - {@code canHarmPlayer} is the check {@code ServerPlayer.hurtServer} runs, and it folds in
 * both the team's friendly-fire flag and the server's {@code pvp} setting. Damage inherits that
 * gate for free by going through {@code hurt}; a poison, a knockback, a lift, an ignite, a freeze,
 * a wither, a slow - every non-damage consequence - does not, so all of them are routed through
 * here instead. With no owner there is nobody the check could be run for, so nothing may land on
 * a player at all.</p>
 *
 * <p>The wielder is always a legal target for their own effect: Gale Step's dash and the healing
 * Arts act on the owner, and a team with friendly fire off must not stop a player moving
 * themselves.</p>
 *
 * <p>Lives in {@code forge}, not {@code forge.art}, because the strike and rider paths that also
 * need it are core forge code, not Art splash - and both already sit in this package, so neither
 * needs an import to reach it.</p>
 */
public final class ForgeTargeting {

    private ForgeTargeting() {}

    public static boolean canAffect(LivingEntity owner, Entity target) {
        if (target == owner) {
            return true;
        }
        if (!(target instanceof Player other)) {
            return true; // mobs are never gated: this changes nothing for them
        }
        if (owner instanceof ServerPlayer wielder) {
            return other.canHarmPlayer(wielder);
        }
        // A hostile mob wielding a forged weapon is not bound by rules that exist to stop players
        // hurting each other. It still needs an owner: with none there is nobody to check for.
        return owner != null;
    }
}
