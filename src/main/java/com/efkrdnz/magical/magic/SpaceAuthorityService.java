package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.SpaceRuleAppliedPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;

public final class SpaceAuthorityService {
    public static final int MAX_CHARGE_TICKS = 60;
    private static final float BASE_RADIUS = 5.0F;
    private static final float MAX_RADIUS = 16.0F;

    private SpaceAuthorityService() {}

    public static void handleSubspaceHold(ServerPlayer player, int slot, boolean release, int chargeTicks, boolean followOwner) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!validAuthoritySkill(state, slot, MagicContent.CREATE_SUBSPACE.id())) {
            return;
        }
        if (release) {
            createOrDismissSubspace(player, state, chargeTicks, followOwner);
        }
    }

    public static boolean createOrDismissSubspace(ServerPlayer player, PlayerMagicState state, int chargeTicks, boolean followOwner) {
        if (!state.hasAuthority(AuthorityContent.SPACE) || !state.hasUnlocked(MagicContent.CREATE_SUBSPACE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (hasAnyOwnedSubspace(player, state)) {
            closeAllDomains(player, state, true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.75F, 1.55F);
            return true;
        }
        if (state.isSkillOnCooldown(MagicContent.CREATE_SUBSPACE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.CREATE_SUBSPACE.resolve(state.tuningFor(MagicContent.CREATE_SUBSPACE.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        float radius = radiusForCharge(chargeTicks);
        ServerLevel level = player.serverLevel();
        SpaceSubspaceEntity subspace = SpaceSubspaceEntity.create(level, player, radius, followOwner);
        level.addFreshEntity(subspace);
        state.setActiveSubspaceEntityId(subspace.getId());
        state.setSkillCooldown(MagicContent.CREATE_SUBSPACE.id(), stats.cooldownTicks());
        state.sync(player);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.85F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.55F);
        return true;
    }

    public static void applyRule(ServerPlayer player, int categoryOrdinal, int operationOrdinal, int targetOrdinal) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.SPACE) || !state.hasUnlocked(MagicContent.MANIPULATE_SPACE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return;
        }
        SpaceRuleCategory category = enumValue(SpaceRuleCategory.values(), categoryOrdinal);
        SpaceRuleOperation operation = enumValue(SpaceRuleOperation.values(), operationOrdinal);
        SpaceTargetGroup targetGroup = enumValue(SpaceTargetGroup.values(), targetOrdinal);
        if (category == null || operation == null || targetGroup == null || operation.category() != category) {
            return;
        }
        SpaceSubspaceEntity subspace = activeSubspace(player, state);
        if (subspace == null) {
            state.setActiveSubspaceEntityId(-1);
            state.sync(player);
            player.displayClientMessage(Component.translatable("message.magical.no_subspace"), true);
            return;
        }
        if (state.isSkillOnCooldown(MagicContent.MANIPULATE_SPACE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicContent.MANIPULATE_SPACE.resolve(state.tuningFor(MagicContent.MANIPULATE_SPACE.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        subspace.applyRule(category, operation, targetGroup);
        state.setSkillCooldown(MagicContent.MANIPULATE_SPACE.id(), stats.cooldownTicks());
        state.sync(player);
        // Bystanders keep the chime; the caster hears the flash's own cue instead.
        player.level().playSound(player, player.blockPosition(), operation.clear() ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, operation.clear() ? 0.75F : 1.35F);
        MagicalNetwork.sendSpaceRuleApplied(player, new SpaceRuleAppliedPayload(category.ordinal(), operation.ordinal(), targetGroup.ordinal()));
    }

    /**
     * Raises a subspace with none of the gates the skill has - no authority, no unlock, no
     * cooldown, no mana. For tests and unattended captures, which need a domain standing by tick
     * sixty rather than a progression walked up to it. Any domain already owned is closed first,
     * so repeated calls leave one.
     */
    public static SpaceSubspaceEntity raiseDebugSubspace(ServerPlayer player, float radius, boolean followOwner) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        closeAllDomains(player, state, false);
        ServerLevel level = player.serverLevel();
        SpaceSubspaceEntity subspace = SpaceSubspaceEntity.create(level, player, radius, followOwner);
        level.addFreshEntity(subspace);
        state.setActiveSubspaceEntityId(subspace.getId());
        state.sync(player);
        return subspace;
    }

    /**
     * Writes a law straight onto the caster's standing subspace, past the cooldown and the mana.
     * It ends in the same two calls {@link #applyRule} does, so a capture drives the real rule
     * loop and the real flash rather than a stand-in for either.
     *
     * @return false when the caster has no subspace to write onto
     */
    public static boolean applyDebugRule(ServerPlayer player, SpaceRuleCategory category, SpaceRuleOperation operation, SpaceTargetGroup targetGroup) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        SpaceSubspaceEntity subspace = activeSubspace(player, state);
        if (subspace == null) {
            return false;
        }
        subspace.applyRule(category, operation, targetGroup);
        MagicalNetwork.sendSpaceRuleApplied(player, new SpaceRuleAppliedPayload(category.ordinal(), operation.ordinal(), targetGroup.ordinal()));
        return true;
    }

    public static SpaceSubspaceEntity activeSubspace(ServerPlayer player, PlayerMagicState state) {
        if (state.activeSubspaceEntityId() < 0) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(state.activeSubspaceEntityId());
        return entity instanceof SpaceSubspaceEntity subspace && subspace.isAlive() ? subspace : null;
    }

    public static int closeAllDomains(ServerPlayer player, PlayerMagicState state, boolean sync) {
        List<SpaceSubspaceEntity> owned = ownedSubspaces(player);
        for (SpaceSubspaceEntity subspace : owned) {
            subspace.discard();
        }
        state.setActiveSubspaceEntityId(-1);
        if (sync) {
            state.sync(player);
        }
        return owned.size();
    }

    private static boolean hasAnyOwnedSubspace(ServerPlayer player, PlayerMagicState state) {
        SpaceSubspaceEntity active = activeSubspace(player, state);
        return active != null || !ownedSubspaces(player).isEmpty();
    }

    private static List<SpaceSubspaceEntity> ownedSubspaces(ServerPlayer player) {
        List<SpaceSubspaceEntity> owned = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof SpaceSubspaceEntity subspace && player.getUUID().equals(subspace.ownerUuid())) {
                owned.add(subspace);
            }
        }
        return owned;
    }

    private static boolean validAuthoritySkill(PlayerMagicState state, int slot, net.minecraft.resources.ResourceLocation expectedSkill) {
        return state.hasAuthority(AuthorityContent.SPACE)
                && slot >= 0
                && slot < MagicContent.LOADOUT_SIZE
                && expectedSkill.equals(state.equippedSkill(slot))
                && state.hasUnlocked(expectedSkill);
    }

    private static float radiusForCharge(int chargeTicks) {
        float progress = Math.max(0.0F, Math.min(MAX_CHARGE_TICKS, chargeTicks)) / (float) MAX_CHARGE_TICKS;
        return BASE_RADIUS + (MAX_RADIUS - BASE_RADIUS) * progress;
    }

    private static <T> T enumValue(T[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
