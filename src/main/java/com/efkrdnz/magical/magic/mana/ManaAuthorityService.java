package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.entity.domain.ManaWeaveEntity;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Raising the Weave, and writing on it.
 *
 * <p>The same two-part shape the Authority of Space has: an anchor that creates the context, and a
 * grammar that does nothing at all without it. {@code claim_weave} claims the local mana field;
 * {@code weave_rules} legislates inside it and refuses outside it.
 */
public final class ManaAuthorityService {

    private ManaAuthorityService() {}

    /** Claims the field, or lets it go if one is already standing. */
    public static boolean claimOrRelease(ServerPlayer player, PlayerMagicState state, float radius, boolean followOwner) {
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.CLAIM_WEAVE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        ManaWeaveEntity standing = activeWeave(player, state);
        if (standing != null) {
            standing.discard();
            state.setActiveWeaveEntityId(-1);
            state.sync(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 0.9F);
            return true;
        }
        if (state.isSkillOnCooldown(MagicContent.CLAIM_WEAVE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.CLAIM_WEAVE.resolve(state.tuningFor(MagicContent.CLAIM_WEAVE.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        ManaWeaveEntity weave = ManaWeaveEntity.create(level, player, radius, followOwner);
        level.addFreshEntity(weave);
        state.setActiveWeaveEntityId(weave.getId());
        state.setSkillCooldown(MagicContent.CLAIM_WEAVE.id(), stats.cooldownTicks());
        state.sync(player);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    /** Writes one rule onto the standing Weave. Outside a Weave there is nothing to write on. */
    public static boolean inscribe(ServerPlayer player, int aspectOrdinal, int operationOrdinal, int subjectOrdinal) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.WEAVE_RULES.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        WeaveAspect aspect = value(WeaveAspect.values(), aspectOrdinal);
        WeaveOperation operation = value(WeaveOperation.values(), operationOrdinal);
        WeaveSubject subject = value(WeaveSubject.values(), subjectOrdinal);
        if (aspect == null || operation == null || subject == null) {
            return false;
        }
        ManaWeaveEntity weave = activeWeave(player, state);
        if (weave == null) {
            state.setActiveWeaveEntityId(-1);
            state.sync(player);
            player.displayClientMessage(Component.translatable("message.magical.no_weave"), true);
            return false;
        }
        // A locked aspect refuses every hand, the wielder's included. That is what a lock is for.
        if (weave.operationOn(aspect) == WeaveOperation.LOCK && operation != WeaveOperation.RESTORE) {
            player.displayClientMessage(Component.translatable("message.magical.weave_locked"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.WEAVE_RULES.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.WEAVE_RULES.resolve(state.tuningFor(MagicContent.WEAVE_RULES.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        weave.inscribe(aspect, operation, subject);
        state.setSkillCooldown(MagicContent.WEAVE_RULES.id(), stats.cooldownTicks());
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.8F, 0.6F + operation.ordinal() * 0.12F);
        return true;
    }

    /** The Weave this wielder is holding open, or null if it has lapsed or was never raised. */
    public static ManaWeaveEntity activeWeave(ServerPlayer player, PlayerMagicState state) {
        int id = state.activeWeaveEntityId();
        if (id == -1) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(id);
        return entity instanceof ManaWeaveEntity weave && weave.isAlive() ? weave : null;
    }

    private static <T> T value(T[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
