package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.classes.BlacksmithInfusion;
import com.efkrdnz.magical.entity.MeleeArcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fast, forgiving melee. A swing that lands in melee range (a target under the crosshair, or a
 * nearby enemy the aim snaps onto) makes an instant sharp cut in front of the player; a swing at
 * open air instead throws a flying blade slash forward. Both carry the weapon's rune element on a
 * runeforged sword, shaped by its temper.
 *
 * The aim-assist re-uses vanilla {@link net.minecraft.world.entity.player.Player#attack}, so crits,
 * enchants, durability, and {@code AttackEntityEvent} (the weapon's on-hit proc) fire exactly once.
 */
public final class MeleeCombatService {
    private static final double MELEE_RANGE = 4.0D;
    private static final double MELEE_CONE_DOT = 0.5D; // ~60 degrees half-cone for the snap
    private static final float MIN_STRENGTH = 0.85F;

    private MeleeCombatService() {}

    public static void onSwing(ServerPlayer player, boolean whiff) {
        ItemStack weapon = player.getMainHandItem();
        if (!isMeleeWeapon(weapon) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.getAttackStrengthScale(0.5F) <= MIN_STRENGTH) {
            return; // anti-spam: only committed swings do anything
        }
        // A target under the crosshair (not a whiff) or a nearby enemy the aim can snap onto means
        // we're fighting in melee range -> an instant cut. Otherwise the swing sails out as a slash.
        LivingEntity snap = whiff ? nearestInCone(player, MELEE_RANGE, MELEE_CONE_DOT) : null;
        if (!whiff || snap != null) {
            if (snap != null) {
                player.attack(snap); // full vanilla melee -> AttackEntityEvent -> weapon on-hit proc
                player.resetAttackStrengthTicker();
            }
            spawnArc(player, level, weapon, true);
        } else {
            spawnArc(player, level, weapon, false);
        }
    }

    private static void spawnArc(ServerPlayer player, ServerLevel level, ItemStack weapon, boolean cut) {
        BlacksmithInfusion.Infusion infusion = BlacksmithInfusion.infusionFor(weapon);
        BlacksmithInfusion.RuneTemper temper = infusion != null ? infusion.temper() : null;
        float weaponAtk = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        float damage;
        float halfWidth;
        float arcDegrees;
        float speed;
        float knockback;
        float crit = temper != null ? temper.critChance() : 0.0F;
        int life;

        if (cut) {
            // A stationary, near-instant sweep right in front. Modest damage (vanilla melee already
            // hit the primary); its job is the sharp look and an elemental cleave to the cluster.
            damage = weaponAtk * 0.35F + (infusion != null ? infusion.grade().baseDamage() * infusion.temperMultiplier() * 0.3F : 0.0F);
            halfWidth = temper == BlacksmithInfusion.RuneTemper.HEAVY ? 2.9F : 2.3F;
            arcDegrees = 190.0F;
            speed = 0.0F;
            knockback = temper == BlacksmithInfusion.RuneTemper.HEAVY ? 0.55F : 0.3F;
            life = 4;
        } else {
            damage = weaponAtk * 0.6F + (infusion != null ? infusion.grade().baseDamage() * infusion.temperMultiplier() * 0.55F : 0.0F);
            halfWidth = 1.6F;
            arcDegrees = 150.0F;
            speed = 1.05F;
            knockback = 0.4F;
            life = 12;
            if (temper != null) {
                switch (temper) {
                    case HEAVY -> {
                        halfWidth = 2.3F;
                        arcDegrees = 190.0F;
                        speed = 0.85F;
                        knockback = 0.7F;
                    }
                    case KEEN -> {
                        halfWidth = 1.5F;
                        arcDegrees = 140.0F;
                        speed = 1.25F;
                        life = 16;
                    }
                    case SWIFT -> {
                        halfWidth = 1.5F;
                        arcDegrees = 135.0F;
                        speed = 1.2F;
                        life = 11;
                    }
                }
            }
        }

        int color = infusion != null ? infusion.attribute().color() : 0xD8E4FF;
        boolean divine = infusion != null && infusion.grade() == BlacksmithInfusion.RuneGrade.DIVINE;
        int gradeIndex = infusion != null ? infusion.grade().ordinal() : 0;
        BlacksmithInfusion.RuneAttribute attribute = infusion != null ? infusion.attribute() : null;

        Vec3 look = player.getLookAngle();
        MeleeArcEntity.spawn(level, player, look, color, halfWidth, arcDegrees, damage, knockback, speed, life, crit, cut, attribute, gradeIndex, divine);
        if (cut) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.5F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.5F, 1.3F);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, infusion != null ? 0.9F : 1.1F);
        }
    }

    private static LivingEntity nearestInCone(ServerPlayer player, double range, double coneDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB area = player.getBoundingBox().inflate(range);
        for (Entity entity : player.serverLevel().getEntities(player, area, candidate ->
                candidate instanceof LivingEntity living && living.isAlive() && candidate != player && candidate.isAttackable())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || to.lengthSqr() < 1.0E-6D) {
                continue;
            }
            if (to.normalize().dot(look) < coneDot) {
                continue;
            }
            if (!player.hasLineOfSight(entity)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
