package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Class XP from class-flavoured actions, so the base you picked is the one that levels.
 *
 * <p>Before this existed the only faucets were runeforging and spell fusion, which left the
 * Warrior, Ranger, Mystic and Alchemist trees unreachable in normal play. Each award lands in the
 * base class's pool via {@link PlayerMagicState#addClassXp}, and is silently ignored when the
 * player does not own that base.</p>
 */
public final class ClassXpService {
    public static final int KILL_XP = 2;
    public static final int CAST_XP = 1;
    public static final int STATUS_XP = 1;

    private ClassXpService() {}

    /** A kill routed to Warrior or Ranger depending on whether it was struck up close or at range. */
    public static void onKill(ServerPlayer player, DamageSource source) {
        if (player == null) {
            return;
        }
        // Indirect kill: the thing that dealt the hit is not the player themselves (arrow, spell entity).
        boolean ranged = source != null && source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity();
        if (ranged) {
            award(player, MagicalClasses.RANGER, KILL_XP);
        } else {
            award(player, MagicalClasses.WARRIOR, KILL_XP);
        }
    }

    /** Any successful skill cast feeds the Mystic line. */
    public static void onSpellCast(ServerPlayer player) {
        award(player, MagicalClasses.MYSTIC, CAST_XP);
    }

    /** Applying a magical status to somebody else feeds the Alchemist line. */
    public static void onStatusApplied(ServerPlayer player, LivingEntity victim) {
        if (victim != player) {
            award(player, MagicalClasses.ALCHEMIST, STATUS_XP);
        }
    }

    private static void award(ServerPlayer player, ResourceLocation baseId, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasClass(baseId)) {
            return;
        }
        state.addClassXp(baseId, amount);
        state.sync(player);
    }
}
