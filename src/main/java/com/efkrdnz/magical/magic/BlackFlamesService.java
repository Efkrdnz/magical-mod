package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.BlackFlameArcEntity;
import com.efkrdnz.magical.entity.BlackFlameBrandEntity;
import com.efkrdnz.magical.entity.BlackFlameFieldEntity;
import com.efkrdnz.magical.entity.BlackFlameProjectileEntity;
import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlackFlamesService {
    private static final double BRAND_RANGE = 28.0D;
    private static final Map<UUID, BrandRecord> BRANDS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_ARC_SWING = new LinkedHashMap<>();

    private BlackFlamesService() {}

    public static boolean castImbue(ServerPlayer player, PlayerMagicState state, MagicSkillResolvedStats stats) {
        if (!(player.getMainHandItem().getItem() instanceof SwordItem || player.getMainHandItem().getItem() instanceof AxeItem)) {
            player.displayClientMessage(Component.translatable("message.magical.black_flames_imbue_requires_weapon"), true);
            return false;
        }
        int duration = Math.max(80, stats.durationTicks());
        state.setBlackFlamesImbue(duration, Math.max(1.0F, stats.damage()), stats.knockback());
        ServerLevel level = player.serverLevel();
        level.addFreshEntity(MagicCircleEffectEntity.createFollowing(level, player, 1.35F + stats.size() * 0.25F, 0x2A061E, Math.min(duration, 80), MagicCircleEffectEntity.STYLE_BLACK_FLAMES));
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.72F, 0.42F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.46F, 0.58F);
        MagicalNetwork.playFirstPersonImpact(player, 0x2A061E, 12, 0.16F, 3, 0.08F, 0, -0.4F);
        return true;
    }

    public static boolean castBrand(ServerPlayer player, MagicSkillResolvedStats stats) {
        LivingEntity target = findLookedAtLiving(player, BRAND_RANGE + stats.size() * 1.5D);
        if (target == null || target == player) {
            player.displayClientMessage(Component.translatable("message.magical.black_flames_brand_no_target"), true);
            return false;
        }
        player.serverLevel().addFreshEntity(BlackFlameBrandEntity.create(player.serverLevel(), player, target, stats.damage(), 2.25F + stats.size() * 0.65F, Math.max(80, stats.durationTicks())));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.min(120, stats.durationTicks()), 0, false, true), player);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true), player);
        player.serverLevel().addFreshEntity(MagicCircleEffectEntity.createFollowing(player.serverLevel(), target, 1.05F + stats.size() * 0.22F, 0x5A083A, Math.min(120, stats.durationTicks()), MagicCircleEffectEntity.STYLE_BLACK_FLAMES));
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.36F, 0.72F);
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.58F, 0.52F);
        return true;
    }

    public static void applyBrand(ServerLevel level, Entity owner, LivingEntity target, float damage, float radius, int durationTicks) {
        long expiresAt = level.getGameTime() + Math.max(80, durationTicks);
        BRANDS.put(target.getUUID(), new BrandRecord(owner == null ? null : owner.getUUID(), damage, radius, expiresAt));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.min(140, durationTicks), 0, false, true), owner);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true), owner);
        level.addFreshEntity(MagicCircleEffectEntity.createFollowing(level, target, 1.1F + radius * 0.14F, 0x3C0458, Math.min(120, durationTicks), MagicCircleEffectEntity.STYLE_BLACK_FLAMES));
        level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.55F, 0.48F);
    }

    public static void onMeleeAttack(ServerPlayer player, LivingEntity target) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasBlackFlamesImbue()) {
            return;
        }
        float damage = Math.max(1.0F, state.blackFlamesImbueDamage());
        MagicDamageService.hurt(target, player.damageSources().indirectMagic(player, player), damage, MagicContent.BLACK_FLAMES_IMBUE.id());
        BlackFlameProjectileEntity.applyCorrosion(target, player);
        Vec3 look = player.getLookAngle().normalize();
        target.push(look.x * (0.12D + state.blackFlamesImbueKnockback() * 0.18D), 0.04D, look.z * (0.12D + state.blackFlamesImbueKnockback() * 0.18D));
        player.serverLevel().addFreshEntity(BlackFlameFieldEntity.create(player.serverLevel(), player, target.position().add(0.0D, 0.08D, 0.0D), 1.75F, damage * 0.12F, 44, state.blackFlamesImbueKnockback()));
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.38F, 0.62F);
        state.setBlackFlamesImbue(state.blackFlamesImbueTicks(), Math.max(0.0F, damage * 0.93F), state.blackFlamesImbueKnockback());
        state.sync(player);
    }

    public static void onImbuedSwordSwing(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasBlackFlamesImbue() || !(player.getMainHandItem().getItem() instanceof SwordItem)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        long lastSwing = LAST_ARC_SWING.getOrDefault(player.getUUID(), -100L);
        if (now - lastSwing < 8L) {
            return;
        }
        LAST_ARC_SWING.put(player.getUUID(), now);
        float damage = Math.max(1.0F, state.blackFlamesImbueDamage() * 0.52F);
        BlackFlameArcEntity arc = BlackFlameArcEntity.create(player.serverLevel(), player, damage, 0.18F + state.blackFlamesImbueKnockback() * 0.35F);
        player.serverLevel().addFreshEntity(arc);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.85F, 0.48F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.34F, 0.7F);
        state.setBlackFlamesImbue(state.blackFlamesImbueTicks(), Math.max(0.0F, state.blackFlamesImbueDamage() * 0.985F), state.blackFlamesImbueKnockback());
        state.sync(player);
    }

    public static void onSuccessfulSkillCast(ServerPlayer caster, MagicSkillDefinition definition) {
        if (definition == null || MagicContent.BLACK_FLAMES_BRAND.id().equals(definition.id())) {
            return;
        }
        BrandRecord brand = BRANDS.get(caster.getUUID());
        if (brand == null) {
            return;
        }
        long now = caster.serverLevel().getGameTime();
        if (brand.expiresAtGameTime() < now) {
            BRANDS.remove(caster.getUUID());
            return;
        }
        BRANDS.remove(caster.getUUID());
        Entity owner = brand.ownerUuid() == null ? null : caster.serverLevel().getEntity(brand.ownerUuid());
        detonateBrand(caster.serverLevel(), owner, caster, brand);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, BrandRecord>> iterator = BRANDS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtGameTime() < now) {
                iterator.remove();
            }
        }
        LAST_ARC_SWING.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
    }

    private static void detonateBrand(ServerLevel level, Entity owner, LivingEntity target, BrandRecord brand) {
        Vec3 center = target.position().add(0.0D, 0.1D, 0.0D);
        level.addFreshEntity(BlackFlameFieldEntity.create(level, owner, center, brand.radius(), Math.max(1.0F, brand.damage() * 0.18F), 86, 0.25F));
        level.addFreshEntity(MagicCircleEffectEntity.createStatic(level, center.add(0.0D, 0.08D, 0.0D), brand.radius() * 0.86F, 0x5A083A, 50, MagicCircleEffectEntity.STYLE_BLACK_FLAMES, 0.0F, 90.0F, 0.0F));
        MagicDamageService.hurt(target, level.damageSources().indirectMagic(owner == null ? target : owner, owner == null ? target : owner), brand.damage() * 1.15F, MagicContent.BLACK_FLAMES_BRAND.id());
        BlackFlameProjectileEntity.applyCorrosion(target, owner);
        AABB area = target.getBoundingBox().inflate(brand.radius());
        for (Entity entity : level.getEntities(target, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            double distance = Math.max(0.35D, entity.distanceTo(target));
            float falloff = (float) Math.max(0.0D, 1.0D - distance / brand.radius());
            if (falloff <= 0.0F) {
                continue;
            }
            MagicDamageService.hurt(entity, level.damageSources().indirectMagic(owner == null ? target : owner, owner == null ? target : owner), brand.damage() * 0.42F * falloff, MagicContent.BLACK_FLAMES_BRAND.id());
            if (entity instanceof LivingEntity living) {
                BlackFlameProjectileEntity.applyCorrosion(living, owner);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.42F);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.5F, 0.58F);
    }

    private static LivingEntity findLookedAtLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistanceSqr = blockHit.getType() == HitResult.Type.MISS ? range * range : eye.distanceToSqr(blockHit.getLocation());
        AABB area = new AABB(eye, end).inflate(1.35D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != player)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.62D).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance <= maxDistanceSqr && distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private record BrandRecord(UUID ownerUuid, float damage, float radius, long expiresAtGameTime) {}
}
