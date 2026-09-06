package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.MagicBarrageFieldEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class MagicBarrageService {
    public static final int MAX_CHARGE_TICKS = 80;
    public static final float MIN_RADIUS = 6.0F;
    public static final float MAX_RADIUS = 24.0F;

    private MagicBarrageService() {}

    public static void handleHold(ServerPlayer player, int slot, boolean release, int chargeTicks) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!validSlot(state, slot)) {
            return;
        }
        MagicSkillDefinition definition = MagicContent.CIRCLE_ARSENAL;
        if (!release) {
            dismissActive(player, state, true);
            return;
        }
        if (hasActiveField(player, state)) {
            dismissActive(player, state, true);
            return;
        }
        if (state.isSkillOnCooldown(definition.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, definition.resolve(state.tuningFor(definition.id())));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }

        float radius = Mth.clamp(radiusForCharge(chargeTicks) + stats.size() * 0.8F, MIN_RADIUS, MAX_RADIUS);
        ServerLevel level = player.serverLevel();
        MagicBarrageFieldEntity field = MagicBarrageFieldEntity.create(level, player, radius, stats);
        level.addFreshEntity(field);
        state.setActiveMagicBarrageEntityId(field.getId());
        MagicSinService.afterSuccessfulCast(player, state, definition);
        state.sync(player);
    }

    public static float radiusForCharge(int chargeTicks) {
        float progress = Mth.clamp(chargeTicks / (float) MAX_CHARGE_TICKS, 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(eased, MIN_RADIUS, MAX_RADIUS);
    }

    public static int circleCountForRadius(float radius) {
        return Mth.clamp(Math.round(radius * 4.2F), 28, 96);
    }

    public static int manaDrainPerSecond(float radius) {
        return Math.max(6, Math.round(4.0F + radius * 0.35F + circleCountForRadius(radius) * 0.08F));
    }

    public static boolean blocksTeleport(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(MAX_RADIUS + 2.0F);
        for (MagicBarrageFieldEntity field : player.serverLevel().getEntitiesOfClass(MagicBarrageFieldEntity.class, area, Entity::isAlive)) {
            if (field.ownerId() != player.getId() && player.position().distanceToSqr(field.position()) <= field.radius() * field.radius()) {
                player.displayClientMessage(Component.translatable("message.magical.circle_arsenal_sealed"), true);
                return true;
            }
        }
        return false;
    }

    public static void finishField(ServerPlayer player, MagicBarrageFieldEntity field, boolean applyCooldown) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.activeMagicBarrageEntityId() == field.getId()) {
            state.setActiveMagicBarrageEntityId(-1);
        }
        if (applyCooldown) {
            MagicSkillResolvedStats stats = MagicContent.CIRCLE_ARSENAL.resolve(state.tuningFor(MagicContent.CIRCLE_ARSENAL.id()));
            int cooldown = stats.cooldownTicks() + Math.min(900, Math.max(0, field.activeTicks()) * 2);
            state.setSkillCooldown(MagicContent.CIRCLE_ARSENAL.id(), cooldown);
        }
        state.sync(player);
    }

    private static boolean validSlot(PlayerMagicState state, int slot) {
        ResourceLocation equipped = state.equippedSkill(slot);
        return slot >= 0
                && slot < MagicContent.LOADOUT_SIZE
                && MagicContent.CIRCLE_ARSENAL.id().equals(equipped)
                && state.hasUnlocked(MagicContent.CIRCLE_ARSENAL.id());
    }

    private static boolean hasActiveField(ServerPlayer player, PlayerMagicState state) {
        Entity entity = player.serverLevel().getEntity(state.activeMagicBarrageEntityId());
        if (entity instanceof MagicBarrageFieldEntity field && field.isAlive()) {
            return true;
        }
        if (state.activeMagicBarrageEntityId() != -1) {
            state.setActiveMagicBarrageEntityId(-1);
            state.sync(player);
        }
        return false;
    }

    private static void dismissActive(ServerPlayer player, PlayerMagicState state, boolean applyCooldown) {
        Entity entity = player.serverLevel().getEntity(state.activeMagicBarrageEntityId());
        if (entity instanceof MagicBarrageFieldEntity field && field.isAlive()) {
            field.close(applyCooldown);
            return;
        }
        if (state.activeMagicBarrageEntityId() != -1) {
            state.setActiveMagicBarrageEntityId(-1);
            state.sync(player);
        }
    }
}
