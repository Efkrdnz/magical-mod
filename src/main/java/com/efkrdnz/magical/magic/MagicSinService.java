package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MagicSinService {
    private static final int RESTED_STILLNESS_DURATION = 20 * 60 * 20;
    private static final int SLOTH_BED_REST_TICKS = 10 * 20;
    private static final int SLOTH_NIGHT_CHECK_START = 19000;
    private static final int SLOTH_NIGHT_CHECK_END = 19120;
    private static final double LUST_RANGE = 18.0D;

    private MagicSinService() {}

    public static void tickPlayer(ServerPlayer player, PlayerMagicState state) {
        tickSloth(player, state);
        tickLust(player, state);
    }

    public static MagicSkillResolvedStats adjustStatsBeforeCast(ServerPlayer player, PlayerMagicState state, MagicSkillResolvedStats stats) {
        float damage = stats.damage();
        float size = stats.size();
        int mana = stats.manaCost();
        int cooldown = stats.cooldownTicks();
        float knockback = stats.knockback();

        if (isOffensive(stats.definition())) {
            if (state.isSinEnabled(MagicPassiveContent.SIN_WRATH.id()) && state.wrathGauge() > 0) {
                float wrath = state.wrathGauge() / (float) PlayerMagicState.MAX_SIN_GAUGE;
                damage *= 1.0F + wrath * 0.45F;
                knockback *= 1.0F + wrath * 0.25F;
            }
            if (state.isSinEnabled(MagicPassiveContent.SIN_PRIDE.id()) && state.prideGauge() >= 120) {
                float pride = state.prideGauge() / (float) PlayerMagicState.MAX_SIN_GAUGE;
                damage *= 1.0F + pride * 0.35F;
                size *= 1.0F + pride * 0.16F;
            }
        }

        if (state.isSinEnabled(MagicPassiveContent.SIN_SLOTH.id()) && state.slothStillness() > 0) {
            float stillness = state.slothStillness() / (float) PlayerMagicState.MAX_SIN_GAUGE;
            mana = Math.max(4, Math.round(mana * (1.0F - stillness * 0.22F)));
            cooldown = Math.max(8, Math.round(cooldown * (1.0F - stillness * 0.18F)));
        }

        MagicSkillResolvedStats adjusted = new MagicSkillResolvedStats(stats.definition(), stats.tuning(), damage, stats.speed(), size, mana, cooldown, stats.durationTicks(), knockback, stats.barrierRestore());
        return ClassPassiveEffects.adjustCast(player, state, adjusted);
    }

    public static boolean spendManaForSkill(ServerPlayer player, PlayerMagicState state, int amount) {
        if (state.spendManaWithGreedHoard(amount)) {
            return true;
        }
        if (state.spendManaWithBarrierConversion(amount)) {
            return true;
        }
        if (state.spendManaWithVaultTap(amount)) {
            return true;
        }
        // Last resort: a class passive may be willing to pay the shortfall out of blood or on credit.
        return ClassPassiveEffects.payManaShortfall(player, state, amount);
    }

    public static void afterSuccessfulCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        // Every cast service funnels through here, which is why the loadout swap lock arms here and
        // nowhere else: a cast that was refused never reaches this line, so a failed cast - out of
        // mana, on cooldown, locked - cannot shut the player out of their own switcher.
        state.armLoadoutSwapLock();
        BlackFlamesService.onSuccessfulSkillCast(player, definition);
        ClassPassiveEffects.afterCast(player, state, definition);
        if (isOffensive(definition)) {
            if (state.isSinEnabled(MagicPassiveContent.SIN_WRATH.id()) && state.wrathGauge() > 0) {
                state.consumeWrathPower();
            }
            if (state.isSinEnabled(MagicPassiveContent.SIN_PRIDE.id()) && state.prideGauge() >= 120) {
                state.consumePridePower();
            }
        }
        if (state.isSinEnabled(MagicPassiveContent.SIN_SLOTH.id())) {
            state.reduceSlothStillness(definition.type() == MagicSkillType.BARRIER ? 25 : 45);
        }
    }

    public static float beforeBarrierDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float damage) {
        float adjusted = damage;
        if (isMagicDamage(source) && state.isSinEnabled(MagicPassiveContent.SIN_GLUTTONY.id()) && state.gluttonyCooldownTicks() <= 0 && damage > 2.0F) {
            float eaten = Math.min(damage * 0.32F, 18.0F);
            adjusted -= eaten;
            int manaGain = Math.max(1, Math.round(eaten * 0.55F));
            state.addMana(manaGain);
            if (state.mana() >= state.maxMana()) {
                int level = Math.min(5, 1 + Math.round(eaten / 6.0F));
                state.addManaCharge(level, 20 * (18 + level * 8));
            }
            state.setGluttonyCooldown(20 * 14);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.28F, 0.68F);
            MagicalNetwork.playFirstPersonImpact(player, 0x5C235E, 14, 0.16F, 5, 0.16F, 0, -1.2F);
        }
        if (damage > 0.0F && state.isSinEnabled(MagicPassiveContent.SIN_WRATH.id())) {
            state.addWrath(Math.max(4, Math.round(damage * 12.0F)));
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer other && state.isSinEnabled(MagicPassiveContent.SIN_PRIDE.id())) {
            PlayerMagicState otherState = other.getData(MagicalAttachments.MAGIC_STATE);
            int difference = otherState.proficiencyLevel() - state.proficiencyLevel();
            if (difference > 0) {
                state.addPride(Math.min(80, 12 + difference * 8 + Math.round(damage * 3.0F)));
            }
        }
        return Math.max(0.0F, adjusted);
    }

    public static void onMagicKill(ServerPlayer player, LivingEntity killed) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.isSinEnabled(MagicPassiveContent.SIN_GREED.id())) {
            return;
        }
        int gained = killed instanceof ServerPlayer ? 80 : 18 + Math.round(killed.getMaxHealth() * 0.5F);
        state.addGreedHoard(gained);
        state.sync(player);
    }

    public static void recordSkillExposure(ServerPlayer player, ResourceLocation skillId, float damage) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.recordEnvyExposure(player, skillId, damage);
        state.sync(player);
    }

    private static void tickLust(ServerPlayer player, PlayerMagicState state) {
        if (!state.isSinEnabled(MagicPassiveContent.SIN_LUST.id()) || player.tickCount % 10 != 0) {
            return;
        }
        LivingEntity target = lookedAtLiving(player, LUST_RANGE);
        if (target == null || !state.spendMana(1)) {
            return;
        }
        int amplifier = lustAmplifier(player, target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, amplifier, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, Math.max(0, amplifier - 1), false, true, true));
        if (amplifier >= 1) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, true, true));
        }
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getEyeY() + 0.15D, target.getZ(), 1, 0.2D, 0.18D, 0.2D, 0.01D);
        }
        state.sync(player);
    }

    private static void tickSloth(ServerPlayer player, PlayerMagicState state) {
        boolean enabled = state.isSinEnabled(MagicPassiveContent.SIN_SLOTH.id());
        long dayTime = player.level().getDayTime();
        long day = dayTime / 24000L;
        long time = dayTime % 24000L;
        if (player.isSleeping()) {
            state.incrementSlothBedTicks();
            if (state.slothBedTicks() >= SLOTH_BED_REST_TICKS) {
                state.markSlothRested(day, RESTED_STILLNESS_DURATION);
            }
        } else {
            state.resetSlothBedTicks();
        }
        if (!enabled) {
            return;
        }
        if (time >= SLOTH_NIGHT_CHECK_START && time <= SLOTH_NIGHT_CHECK_END && state.hasCurse(MagicPassiveContent.SIN_SLOTH_CURSE.id()) && state.lastSlothRestDay() < day && state.lastSlothPenaltyDay() < day) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 180, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 180, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 180, 0, false, true, true));
            state.markSlothPenalty(day);
            player.displayClientMessage(Component.translatable("message.magical.sloth_sleepless"), true);
        }
        boolean calmMovement = !player.isSprinting() && (player.onGround() || player.getAbilities().flying);
        if (calmMovement && player.hurtTime <= 0) {
            state.addSlothStillness(state.restedStillnessTicks() > 0 ? 2 : 1);
        } else {
            state.reduceSlothStillness(state.restedStillnessTicks() > 0 ? 2 : 4);
        }
    }

    private static LivingEntity lookedAtLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistanceSqr = blockHit.getType() == HitResult.Type.MISS ? range * range : eye.distanceToSqr(blockHit.getLocation());
        AABB area = new AABB(eye, end).inflate(1.1D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, area, candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != player)) {
            AABB box = entity.getBoundingBox().inflate(0.45D);
            if (box.clip(eye, end).isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(entity.position());
            if (distance <= maxDistanceSqr && distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private static int lustAmplifier(ServerPlayer player, LivingEntity target) {
        if (target instanceof ServerPlayer other) {
            PlayerMagicState own = player.getData(MagicalAttachments.MAGIC_STATE);
            PlayerMagicState theirs = other.getData(MagicalAttachments.MAGIC_STATE);
            return Math.max(0, Math.min(2, 1 + own.proficiencyLevel() - theirs.proficiencyLevel()));
        }
        return target.getMaxHealth() <= 20.0F ? 1 : 0;
    }

    private static boolean isMagicDamage(DamageSource source) {
        return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    private static boolean isOffensive(MagicSkillDefinition definition) {
        return definition.type() != MagicSkillType.BARRIER && definition.baseDamage() > 0.0F;
    }
}
