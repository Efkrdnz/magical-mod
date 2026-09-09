package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.SoulBondEntity;
import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class SoulAuthorityService {
    private static final int COLLAPSE_TICKS = 20 * 70;
    private static final int ACTION_BIND = 0;
    private static final int ACTION_SWAP = 100;
    private static final int ACTION_CALL = 101;
    private static final int ACTION_SEVER = 102;
    private static final int ACTION_STEP = 103;

    private SoulAuthorityService() {}

    public static void castSoulVowMode(ServerPlayer player, int slot, int mode) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        ResourceLocation parentId = slot >= 0 && slot < MagicContent.LOADOUT_SIZE ? state.equippedSkill(slot) : null;
        castSoulVowMode(player, state, parentId, mode);
    }

    private static void castSoulVowMode(ServerPlayer player, PlayerMagicState state, ResourceLocation parentId, int mode) {
        if (!MagicContent.SOUL_VOW.id().equals(parentId)) {
            return;
        }
        if (!state.hasAuthority(AuthorityContent.SOUL) || !state.hasUnlocked(MagicContent.SOUL_VOW.id()) || SovereignAegisEntity.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (state.hasSoulBond() && mode != ACTION_BIND) {
            handleBondAction(player, state, mode);
            return;
        }
        MagicSkillDefinition subSkill = mode == 0 ? MagicContent.SOUL_VALLEY : null;
        if (subSkill == null || !state.hasUnlocked(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (state.isSkillOnCooldown(subSkill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, subSkill.resolve(state.tuningFor(subSkill.id())));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        if (!castSoulValley(player, state, stats)) {
            state.setMana(state.mana() + stats.manaCost());
            state.sync(player);
            return;
        }
        int cooldown = state.consumeCooldownEcho(subSkill.id()) ? 0 : stats.cooldownTicks();
        state.setSkillCooldown(subSkill.id(), cooldown);
        state.setSkillCooldown(MagicContent.SOUL_VOW.id(), Math.min(cooldown, 80));
        MagicSinService.afterSuccessfulCast(player, state, subSkill);
        state.sync(player);
    }

    private static boolean castSoulValley(ServerPlayer player, PlayerMagicState state, MagicSkillResolvedStats stats) {
        LivingEntity target = findTarget(player, 18.0D + stats.size() * 2.0D);
        if (target == null || target == player) {
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_no_target"), true);
            return false;
        }
        if (SovereignAegisEntity.isSealed(target)) {
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_sealed"), true);
            return false;
        }
        if (isHostileToPlayer(target, player)) {
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_hostile"), true);
            return false;
        }
        clearBondVisuals(player.serverLevel(), player);
        state.setSoulBond(target.level().dimension().location().toString(), target.getUUID());
        player.serverLevel().addFreshEntity(SoulBondEntity.create(player.serverLevel(), player, target));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.55F);
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 0.72F);
        player.displayClientMessage(Component.translatable("message.magical.soul_valley_bound", target.getDisplayName()), false);
        return true;
    }

    private static void handleBondAction(ServerPlayer player, PlayerMagicState state, int action) {
        if (action == ACTION_SEVER || player.isShiftKeyDown()) {
            severBond(player, Component.translatable("message.magical.soul_valley_released"));
            return;
        }
        LivingEntity target = boundEntity(player);
        if (target == null || !target.isAlive()) {
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_missing"), true);
            return;
        }
        if (SovereignAegisEntity.isSealed(target)) {
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_sealed"), true);
            return;
        }
        int manaCost = switch (action) {
            case ACTION_SWAP -> 18;
            case ACTION_CALL -> 12;
            case ACTION_STEP -> 10;
            default -> -1;
        };
        if (manaCost < 0) {
            return;
        }
        if (!MagicSinService.spendManaForSkill(player, state, manaCost)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        boolean success = switch (action) {
            case ACTION_SWAP -> soulSwap(player, target);
            case ACTION_CALL -> callSoul(player, target);
            case ACTION_STEP -> stepToSoul(player, target);
            default -> false;
        };
        if (!success) {
            state.setMana(state.mana() + manaCost);
            player.displayClientMessage(Component.translatable("message.magical.soul_valley_action_failed"), true);
            state.sync(player);
            return;
        }
        state.sync(player);
    }

    private static boolean soulSwap(ServerPlayer player, LivingEntity target) {
        ServerLevel playerLevel = player.serverLevel();
        ServerLevel targetLevel = target.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (targetLevel == null) {
            return false;
        }
        Vec3 playerPos = player.position();
        float playerYaw = player.getYRot();
        float playerPitch = player.getXRot();
        Vec3 targetPos = target.position();
        float targetYaw = target.getYRot();
        float targetPitch = target.getXRot();
        if (target instanceof ServerPlayer targetPlayer) {
            player.teleportTo(targetLevel, targetPos.x, targetPos.y, targetPos.z, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), targetYaw, targetPitch, true);
            targetPlayer.teleportTo(playerLevel, playerPos.x, playerPos.y, playerPos.z, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), playerYaw, playerPitch, true);
        } else {
            if (targetLevel != playerLevel) {
                return false;
            }
            target.teleportTo(playerPos.x, playerPos.y, playerPos.z);
            player.teleportTo(playerLevel, targetPos.x, targetPos.y, targetPos.z, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), targetYaw, targetPitch, true);
        }
        playSoulMoveEffects(playerLevel, playerPos, 0.92F);
        playSoulMoveEffects(targetLevel, targetPos, 1.18F);
        return true;
    }

    private static boolean callSoul(ServerPlayer player, LivingEntity target) {
        Vec3 destination = player.position().add(player.getLookAngle().normalize().scale(1.8D));
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.teleportTo(player.serverLevel(), destination.x, destination.y, destination.z, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), target.getYRot(), target.getXRot(), true);
        } else {
            if (target.level() != player.level()) {
                return false;
            }
            target.teleportTo(destination.x, destination.y, destination.z);
        }
        playSoulMoveEffects(player.serverLevel(), destination, 0.74F);
        return true;
    }

    private static boolean stepToSoul(ServerPlayer player, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel targetLevel)) {
            return false;
        }
        Vec3 destination = target.position().subtract(target.getLookAngle().normalize().scale(1.6D));
        player.teleportTo(targetLevel, destination.x, destination.y, destination.z, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), player.getYRot(), player.getXRot(), true);
        playSoulMoveEffects(targetLevel, destination, 1.05F);
        return true;
    }

    private static void playSoulMoveEffects(ServerLevel level, Vec3 position, float pitch) {
        level.playSound(null, position.x, position.y, position.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.65F, pitch);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 0.48F + pitch * 0.12F);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity dead)) {
            return;
        }
        if (dead instanceof ServerPlayer player && tryResurrectOwner(event, player)) {
            return;
        }
        ServerPlayer owner = findOwnerBoundTo(level.getServer().overworld(), dead);
        if (owner != null) {
            tryResurrectBoundEntity(event, owner, dead);
        }
    }

    public static void onSoulDamage(Entity victim, Entity attacker) {
        if (!(victim instanceof LivingEntity living) || attacker == null || !(living.level() instanceof ServerLevel level)) {
            return;
        }
        if (victim instanceof ServerPlayer owner && isBoundTarget(owner, attacker)) {
            severBond(owner, Component.translatable("message.magical.soul_valley_attacked"));
            return;
        }
        if (attacker instanceof ServerPlayer owner && isBoundTarget(owner, victim)) {
            severBond(owner, Component.translatable("message.magical.soul_valley_attacked_target"));
            return;
        }
        ServerPlayer owner = findOwnerBoundTo(level.getServer().overworld(), living);
        if (owner != null && attacker.getUUID().equals(owner.getUUID())) {
            severBond(owner, Component.translatable("message.magical.soul_valley_attacked_target"));
        }
    }

    private static boolean tryResurrectOwner(LivingDeathEvent event, ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasSoulBond()) {
            return false;
        }
        LivingEntity anchor = boundEntity(player);
        if (shouldFinalizePairedDeath(state, player.getUUID())) {
            severBond(player, Component.translatable("message.magical.soul_valley_broken"));
            return false;
        }
        if (anchor == null || !anchor.isAlive()) {
            severBond(player, Component.translatable("message.magical.soul_valley_broken"));
            return false;
        }
        event.setCanceled(true);
        state.startSoulBondCollapse(player.getUUID(), COLLAPSE_TICKS);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.35F));
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 90, 2, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1, false, true));
        player.teleportTo((ServerLevel) anchor.level(), anchor.getX(), anchor.getY() + 0.2D, anchor.getZ(), java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), player.getYRot(), player.getXRot(), true);
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.72F);
        return true;
    }

    private static boolean tryResurrectBoundEntity(LivingDeathEvent event, ServerPlayer owner, LivingEntity dead) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasSoulBond() || !dead.getUUID().equals(state.soulBondEntityUuid())) {
            return false;
        }
        if (shouldFinalizePairedDeath(state, dead.getUUID())) {
            severBond(owner, Component.translatable("message.magical.soul_valley_broken"));
            return false;
        }
        event.setCanceled(true);
        state.startSoulBondCollapse(dead.getUUID(), COLLAPSE_TICKS);
        dead.setHealth(Math.max(1.0F, dead.getMaxHealth() * 0.35F));
        dead.removeAllEffects();
        dead.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 90, 2, false, true));
        dead.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1, false, true));
        dead.teleportTo(owner.getX(), owner.getY() + 0.2D, owner.getZ());
        state.sync(owner);
        dead.level().playSound(null, dead.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.62F);
        return true;
    }

    private static boolean shouldFinalizePairedDeath(PlayerMagicState state, java.util.UUID currentDeathUuid) {
        return state.soulBondCollapseTicks() > 0
                && state.soulBondCollapseEntityUuid() != null
                && !state.soulBondCollapseEntityUuid().equals(currentDeathUuid);
    }

    private static LivingEntity findTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        HitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistance = blockHit.getType() == HitResult.Type.MISS ? range : eye.distanceTo(blockHit.getLocation());
        AABB area = player.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.25D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, area, target -> target instanceof LivingEntity && target.isAlive() && target != player)) {
            AABB box = entity.getBoundingBox().inflate(0.5D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(maxDistance)), box, candidate -> candidate == entity, maxDistance * maxDistance);
            if (hit == null) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private static boolean isHostileToPlayer(LivingEntity target, ServerPlayer player) {
        if (target instanceof Mob mob && mob.getTarget() != null && mob.getTarget().getUUID().equals(player.getUUID())) {
            return true;
        }
        return target.getLastHurtByMob() != null && target.getLastHurtByMob().getUUID().equals(player.getUUID());
    }

    private static boolean isBoundTarget(ServerPlayer owner, Entity entity) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        return entity != null && state.hasSoulBond() && entity.getUUID().equals(state.soulBondEntityUuid());
    }

    private static LivingEntity boundEntity(ServerPlayer owner) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasSoulBond()) {
            return null;
        }
        ServerLevel level = owner.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(state.soulBondDimension())));
        Entity entity = level == null ? null : level.getEntity(state.soulBondEntityUuid());
        return entity instanceof LivingEntity living ? living : null;
    }

    private static ServerPlayer findOwnerBoundTo(ServerLevel anyLevel, LivingEntity bound) {
        for (ServerPlayer player : anyLevel.getServer().getPlayerList().getPlayers()) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (state.hasSoulBond() && bound.getUUID().equals(state.soulBondEntityUuid())) {
                return player;
            }
        }
        return null;
    }

    private static void severBond(ServerPlayer owner, Component message) {
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasSoulBond()) {
            return;
        }
        clearBondVisuals(owner.serverLevel(), owner);
        state.clearSoulBond();
        state.sync(owner);
        owner.displayClientMessage(message, false);
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.55F);
    }

    private static void clearBondVisuals(ServerLevel level, ServerPlayer owner) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            serverLevel.getEntitiesOfClass(SoulBondEntity.class, new AABB(-3.0E7D, -4096.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D),
                    entity -> owner.getUUID().equals(entity.ownerUuid())).forEach(SoulBondEntity::discard);
        }
    }
}
