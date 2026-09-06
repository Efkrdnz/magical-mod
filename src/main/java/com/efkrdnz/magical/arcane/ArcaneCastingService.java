package com.efkrdnz.magical.arcane;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class ArcaneCastingService {
    private ArcaneCastingService() {}

    public static boolean castActivePreset(ServerPlayer player) {
        ArcanePlayerData data = player.getData(MagicalAttachments.ARCANE_DATA);
        SpellPreset preset = data.activePreset();
        if (preset == null) {
            player.displayClientMessage(Component.translatable("message.magical.no_active_preset"), true);
            return false;
        }

        SpellResolution resolution = ArcaneSpellResolver.resolve(preset.recipe());
        if (!resolution.valid()) {
            player.displayClientMessage(Component.translatable("message.magical.invalid_preset"), true);
            return false;
        }

        if (!data.spendMana(resolution.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }

        data.sync(player);
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) {
            return false;
        }
        player.getCooldowns().addCooldown(player.getMainHandItem(), resolution.castCooldownTicks());

        if (resolution.unstable() && player.getRandom().nextFloat() < resolution.misfireChance()) {
            triggerMisfire(player, resolution);
            return false;
        }

        executeSpell(player, resolution);
        return true;
    }

    private static void executeSpell(ServerPlayer player, SpellResolution resolution) {
        switch (resolution.shape().shape()) {
            case BOLT -> castBolt(player, resolution);
            case WAVE -> castWave(player, resolution);
            case BARRIER -> castBarrier(player, resolution);
            case BURST -> castBurst(player, resolution);
        }
    }

    private static void castBolt(ServerPlayer player, SpellResolution resolution) {
        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 end = eyePos.add(player.getLookAngle().scale(resolution.range()));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(resolution.range())).inflate(1.5D),
                entity -> entity instanceof LivingEntity living && living != player && living.isAlive(),
                resolution.range() * resolution.range());

        List<LivingEntity> targets = new ArrayList<>();
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            targets.add(living);
        }
        targets.addAll(findExtraTargets(player, resolution, targets));

        if (targets.isEmpty()) {
            spawnTrail(level, eyePos, end);
        } else {
            targets.forEach(target -> applyRuneEffect(player, target, resolution));
        }

        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void castWave(ServerPlayer player, SpellResolution resolution) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 center = player.position().add(forward.scale(resolution.range() * 0.5F));
        AABB box = new AABB(center, center).inflate(resolution.range() * 0.5F, 2.5D, resolution.range() * 0.5F);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != player && entity.isAlive());
        targets.stream()
                .filter(entity -> player.position().vectorTo(entity.position()).normalize().dot(forward) > 0.35D)
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .limit(1L + resolution.extraTargets())
                .forEach(target -> applyRuneEffect(player, target, resolution));
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1.0D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4F, 1.4F);
    }

    private static void castBarrier(ServerPlayer player, SpellResolution resolution) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, resolution.durationTicks(), 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, resolution.durationTicks(), Math.max(0, Mth.floor(resolution.power() / 3.0F)), false, true));
        if (resolution.modifiers().stream().anyMatch(modifier -> modifier.effect() == ModifierEffect.DURATION)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, resolution.durationTicks() / 2, 0, false, true));
        }
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.5D, 1.0D, 0.5D, 0.02D);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    private static void castBurst(ServerPlayer player, SpellResolution resolution) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(resolution.range());
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != player && entity.isAlive());
        targets.stream()
                .limit(Math.max(3, 3 + resolution.extraTargets()))
                .forEach(target -> applyRuneEffect(player, target, resolution));
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 2.0D, 1.0D, 2.0D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void triggerMisfire(ServerPlayer player, SpellResolution resolution) {
        player.hurtServer(player.serverLevel(), player.damageSources().magic(), Math.max(1.0F, resolution.power() * 0.45F));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, true));
        if (resolution.rune().affinity() == Affinity.FIRE) {
            player.igniteForSeconds(3.0F);
        }
        player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.5D, 0.8D, 0.5D, 0.02D);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8F, 0.8F);
        player.displayClientMessage(Component.translatable("message.magical.misfire"), true);
    }

    private static List<LivingEntity> findExtraTargets(ServerPlayer player, SpellResolution resolution, List<LivingEntity> existingTargets) {
        if (resolution.extraTargets() <= 0) {
            return List.of();
        }
        AABB area = player.getBoundingBox().expandTowards(player.getLookAngle().scale(resolution.range())).inflate(5.0D);
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive() && !existingTargets.contains(entity))
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .limit(resolution.extraTargets())
                .toList();
    }

    private static void applyRuneEffect(ServerPlayer caster, LivingEntity target, SpellResolution resolution) {
        float damage = resolution.power();
        switch (resolution.rune().affinity()) {
            case FIRE -> {
                target.hurtServer(caster.serverLevel(), caster.damageSources().indirectMagic(caster, caster), damage + 1.0F);
                target.igniteForSeconds(Math.max(2.0F, resolution.durationTicks() / 40.0F));
            }
            case WATER -> {
                target.hurtServer(caster.serverLevel(), caster.damageSources().indirectMagic(caster, caster), damage - 1.0F);
                target.clearFire();
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, resolution.durationTicks(), 1, false, true));
            }
            case FORCE -> {
                target.hurtServer(caster.serverLevel(), caster.damageSources().indirectMagic(caster, caster), damage);
                Vec3 push = target.position().subtract(caster.position()).normalize().scale(0.8D + resolution.power() * 0.08D);
                target.push(push.x, 0.2D, push.z);
                target.hurtMarked = true;
            }
            case LIGHT -> {
                float totalDamage = damage + (target.isInvertedHealAndHarm() ? 2.0F : 0.0F);
                target.hurtServer(caster.serverLevel(), caster.damageSources().indirectMagic(caster, caster), totalDamage);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, resolution.durationTicks(), 0, false, true));
            }
            default -> MagicalMod.LOGGER.warn("Unhandled rune affinity {}", resolution.rune().affinity());
        }
        spawnImpactParticles(caster.serverLevel(), target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), resolution);
    }

    private static void spawnTrail(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 step = end.subtract(start).scale(1.0D / 10.0D);
        for (int i = 0; i < 10; i++) {
            Vec3 pos = start.add(step.scale(i));
            level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnImpactParticles(ServerLevel level, Vec3 position, SpellResolution resolution) {
        RandomSource random = level.random;
        for (int i = 0; i < 8; i++) {
            level.sendParticles(
                    resolution.rune().affinity() == Affinity.FIRE ? ParticleTypes.FLAME : ParticleTypes.ENCHANT,
                    position.x,
                    position.y,
                    position.z,
                    1,
                    random.nextGaussian() * 0.08D,
                    random.nextGaussian() * 0.08D,
                    random.nextGaussian() * 0.08D,
                    0.01D);
        }
    }
}
